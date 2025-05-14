/*
 *  Copyright 2023 The original authors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package dev.vishrayne.onebrc;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorSpecies;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Spliterator;
import java.util.function.Consumer;

public class MemorySegmentLinesSpliterator implements Spliterator<MemorySegment> {
    private final FileChannel fileChannel;
    private final MemorySegment fileSegment;
    private final Arena arena;
    private long currentPosition;
    private final long limit;

    private final byte NEWLINE_CHAR = (byte) '\n';
    private final byte SEPARATOR_CHAR = (byte) ';';
    private final long MIN_SPLIT_SIZE = 8192; // 8KB
    private final long HALF_MIN_SPLIT_SIZE = MIN_SPLIT_SIZE >>> 1; // Pre-calculate half size

    public MemorySegmentLinesSpliterator(Path path, Arena arena) throws IOException {
        this.arena = arena;
        this.fileChannel = FileChannel.open(path, StandardOpenOption.READ);
        this.fileSegment = fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                0,
                fileChannel.size(),
                arena);

        this.currentPosition = 0;
        this.limit = this.fileSegment.byteSize();
    }

    // Additional constructor for splitting
    private MemorySegmentLinesSpliterator(
                                          FileChannel fileChannel,
                                          MemorySegment fullSegment,
                                          long start,
                                          long end,
                                          Arena arena) {
        this.fileChannel = fileChannel;
        this.fileSegment = fullSegment;
        this.currentPosition = start;
        this.limit = end;
        this.arena = arena;
    }

    @Override
    public Spliterator<MemorySegment> trySplit() {
        long remainingBytes = limit - currentPosition;

        // Fast path: don't split if too small
        if (remainingBytes < MIN_SPLIT_SIZE) {
            return null;
        }

        // Calculate midpoint
        long splitAttemptPosition = currentPosition + (remainingBytes >>> 1);

        // Option 1: Scan forward from midpoint (your current good part)
        long bestSplitPos = -1;
        // Scan a reasonable window around the midpoint, e.g., +/- 10% of MIN_SPLIT_SIZE or a fixed window
        long scanWindow = Math.min(remainingBytes >>> 3, 2048); // Smaller scan window?
        long effectiveScanLimit = Math.min(limit, splitAttemptPosition + scanWindow);
        long effectiveScanStart = Math.max(currentPosition + 1, splitAttemptPosition - scanWindow); // Don't scan before current

        // Scan backwards from the ideal split point to find the nearest preceding newline.
        // This ensures the new (left) spliterator ends on a complete line.
        for (long p = splitAttemptPosition - 1; p >= effectiveScanStart; p--) {
            if (fileSegment.get(ValueLayout.JAVA_BYTE, p - 1) == NEWLINE_CHAR) { // p-1 is the \n, so p is start of new line
                bestSplitPos = p;
                break;
            }
        }

        // Option 2: If not found scanning backward, Scan forward for a newline
        if (bestSplitPos == -1) {
            for (long p = splitAttemptPosition; p < effectiveScanLimit; p++) {
                if (fileSegment.get(ValueLayout.JAVA_BYTE, p - 1) == NEWLINE_CHAR) { // p-1 is the \n, so p is start of new line
                    bestSplitPos = p;
                    break;
                }
            }
        }

        // If still no good split point found near the middle, or if a split would make a very small remaining chunk
        if (bestSplitPos == -1 || (limit - bestSplitPos < HALF_MIN_SPLIT_SIZE) || (bestSplitPos - currentPosition < HALF_MIN_SPLIT_SIZE)) {
            return null; // Don't split if no clean newline found or chunks too small
        }

        // Create new spliterator
        MemorySegmentLinesSpliterator prefix = new MemorySegmentLinesSpliterator(
                fileChannel,
                fileSegment,
                currentPosition,
                bestSplitPos,
                arena);

        // Update current position
        currentPosition = bestSplitPos;

        return prefix;
    }

    @Override
    public boolean tryAdvance(Consumer<? super MemorySegment> action) {
        // Fast path: don't advance if EOF
        boolean canAdvance = currentPosition < limit;
        if (!canAdvance) {
            return false;
        }

        VectorSpecies<Byte> SPECIES = ByteVector.SPECIES_PREFERRED;
        long searchLimit = this.limit;
        long searchStart = this.currentPosition;
        long newLinePosition = -1;
        long separatorPosition = -1;

        for (long offset = searchStart; offset < searchLimit; offset += SPECIES.length()) {
            // Create a mask for the remaining bytes if near the end
            VectorMask<Byte> mask = SPECIES.maskAll(true);
            if (offset + SPECIES.length() > searchLimit) {
                mask = SPECIES.indexInRange(offset, searchLimit);
            }

            ByteVector vec = ByteVector.fromMemorySegment(SPECIES, this.fileSegment, offset, ByteOrder.nativeOrder(), mask);

            // Find separator position
            VectorMask<Byte> vecEqSeparator = vec.eq(SEPARATOR_CHAR);
            if (vecEqSeparator.anyTrue()) {
                separatorPosition = offset + vecEqSeparator.firstTrue();
            }

            // Find new line position
            VectorMask<Byte> vecEqNewLine = vec.eq(NEWLINE_CHAR);
            if (vecEqNewLine.anyTrue()) {
                newLinePosition = offset + vecEqNewLine.firstTrue();
                break;
            }
        }

        long lineEndPosition = newLinePosition != -1 ? newLinePosition : searchLimit;

        // Calculate line length
        long lineLength = lineEndPosition - currentPosition;
        byte lastChar = lineLength > 0 ? fileSegment.get(ValueLayout.JAVA_BYTE, currentPosition + lineLength - 1) : 0;
        boolean hasCarriageReturn = (lastChar == '\r');
        lineLength -= (hasCarriageReturn ? 1 : 0);

        // Calculate station length
        long lineSeparatedPosition = separatorPosition - currentPosition;
        // Create line slice
        MemorySegment lineSlice = fileSegment.asSlice(currentPosition, lineLength);

        // Update position for next iteration
        boolean foundNewline = (lineEndPosition < limit);
        currentPosition = lineEndPosition + (foundNewline ? 1 : 0);

        // Process the line
        action.accept(lineSlice);

        return true;
    }

    @Override
    public long estimateSize() {
        return limit - currentPosition;
    }

    @Override
    public int characteristics() {
        return ORDERED | NONNULL | IMMUTABLE;
    }

    public void close() throws IOException {
        fileChannel.close();
    }
}
