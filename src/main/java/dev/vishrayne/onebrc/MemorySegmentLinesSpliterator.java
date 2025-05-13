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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class MemorySegmentLinesSpliterator implements Spliterator<MemorySegment> {
    private final MemorySegment fileSegment;
    private final Arena arena;
    private long currentPosition;
    private final long limit;

    // // Reusable StringBuilder to avoid allocations
    // private final StringBuilder reusableBuilder = new StringBuilder(1024);
    private final boolean isRoot;

    private AtomicBoolean closed = new AtomicBoolean(false);

    public MemorySegmentLinesSpliterator(Path path) throws IOException {
        this.arena = Arena.ofShared();
        this.isRoot = true;

        try {
            FileChannel channel = FileChannel.open(path, StandardOpenOption.READ);
            this.fileSegment = channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    0,
                    channel.size(),
                    arena);

            this.currentPosition = 0;
            this.limit = this.fileSegment.byteSize();
        }
        catch (IOException e) {
            arena.close();
            throw e;
        }
    }

    // Additional constructor for splitting
    private MemorySegmentLinesSpliterator(
                                          MemorySegment fullSegment,
                                          long start,
                                          long end,
                                          Arena arena,
                                          AtomicBoolean closed) {
        this.fileSegment = fullSegment;
        this.currentPosition = start;
        this.limit = end;
        this.arena = arena;
        this.isRoot = false;
        this.closed = closed;
    }

    // @Override
    // public boolean tryAdvance(Consumer<? super MemorySegment> action) {
    // if (currentPosition >= limit || closed.get()) {
    // return false;
    // }
    //
    // long lineEndPosition = currentPosition;
    // // Find the end of the current line (\n)
    // while (lineEndPosition < limit && fileSegment.get(ValueLayout.JAVA_BYTE, lineEndPosition) != '\n') {
    // lineEndPosition++;
    // }
    //
    // MemorySegment lineSlice;
    //
    // if (lineEndPosition < limit) {
    // // Create a slice for the current line, excluding the newline character
    // long lineLength = lineEndPosition - currentPosition;
    // // Handle potential \r\n endings by reducing length if \r is present
    // if (lineLength > 0 && fileSegment.get(ValueLayout.JAVA_BYTE, currentPosition + lineLength - 1) == '\r') {
    // lineLength--;
    // }
    // lineSlice = fileSegment.asSlice(currentPosition, lineLength);
    // currentPosition = lineEndPosition + 1; // Move to the start of the next line
    // }
    // else {
    // if (currentPosition < limit) { // Ensure there's data left
    // long lineLength = limit - currentPosition;
    // lineSlice = fileSegment.asSlice(currentPosition, lineLength);
    // currentPosition = limit; // Consumed everything
    // }
    // else {
    // return false; // No more data
    // }
    // }
    //
    // action.accept(lineSlice);
    //
    // return true;
    // }

    // @Override
    // public Spliterator<MemorySegment> trySplit() {
    // if (closed.get()) {
    // return null;
    // }
    //
    // long remainingBytes = limit - currentPosition;
    //
    // // Increased minimum split size slightly, can be tuned.
    // // Too small can lead to overhead.
    // final long MIN_SPLIT_SIZE = 8192; // e.g., 8KB
    //
    // if (remainingBytes <= MIN_SPLIT_SIZE) {
    // return null; // Don't split if remaining part is too small
    // }
    //
    // long splitAttemptPosition = currentPosition + (remainingBytes >>> 1); // Midpoint
    //
    // // Adjust splitAttemptPosition to the end of a line (right after a '\n')
    // // Search forward from midpoint
    // while (splitAttemptPosition < limit && fileSegment.get(ValueLayout.JAVA_BYTE, splitAttemptPosition - 1) != '\n') {
    // splitAttemptPosition++;
    // }
    //
    // // If we hit the end or couldn't find a newline (e.g. very long last line in chunk)
    // // or if the split point is too close to the current position, don't split.
    // if (splitAttemptPosition >= limit || splitAttemptPosition <= currentPosition) {
    // return null;
    // }
    //
    // // Create a new spliterator for the first half
    // MemorySegmentLinesSpliterator prefix = new MemorySegmentLinesSpliterator(
    // fileSegment,
    // currentPosition,
    // splitAttemptPosition,
    // arena,
    // closed);
    //
    // // update current spliterator's start
    // currentPosition = splitAttemptPosition;
    //
    // return prefix;
    //
    // }

    final byte NEWLINE_CHAR = (byte) '\n';

    @Override
    public boolean tryAdvance(Consumer<? super MemorySegment> action) {
        // Single check combining both conditions
        boolean canAdvance = (currentPosition < limit && !closed.get());
        if (!canAdvance) {
            return false;
        }

        // Find the end of the current line (\n)

        // long lineEndPosition = currentPosition;
        // while (lineEndPosition < limit) {
        // if (fileSegment.get(ValueLayout.JAVA_BYTE, lineEndPosition) == '\n') {
        // break;
        // }
        // lineEndPosition++;
        // }

        VectorSpecies<Byte> SPECIES = ByteVector.SPECIES_PREFERRED;
        long searchLimit = this.limit;
        long searchStart = this.currentPosition;
        long foundPosition = -1;

        for (long offset = searchStart; offset < searchLimit; offset += SPECIES.length()) {
            // Create a mask for the remaining bytes if near the end
            VectorMask<Byte> mask = SPECIES.maskAll(true);
            if (offset + SPECIES.length() > searchLimit) {
                mask = SPECIES.indexInRange(offset, searchLimit);
            }

            ByteVector vec = ByteVector.fromMemorySegment(SPECIES, this.fileSegment, offset, ByteOrder.nativeOrder(), mask);
            VectorMask<Byte> vecEqNewLine = vec.eq(NEWLINE_CHAR);

            if (vecEqNewLine.anyTrue()) {
                foundPosition = offset + vecEqNewLine.firstTrue();
                break;
            }
        }

        long lineEndPosition = foundPosition != -1 ? foundPosition : searchLimit;

        // Calculate line length
        long lineLength = lineEndPosition - currentPosition;
        byte lastChar = lineLength > 0 ? fileSegment.get(ValueLayout.JAVA_BYTE, currentPosition + lineLength - 1) : 0;
        boolean hasCarriageReturn = (lastChar == '\r');
        lineLength -= (hasCarriageReturn ? 1 : 0);

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
    public Spliterator<MemorySegment> trySplit() {
        // Fast path: don't split if closed or too small
        final long MIN_SPLIT_SIZE = 8192; // 8KB
        final long HALF_MIN_SPLIT_SIZE = MIN_SPLIT_SIZE >>> 1; // Pre-calculate half size
        long remainingBytes = limit - currentPosition;

        // Combined early exit check
        boolean shouldNotSplit = closed.get() || (remainingBytes < MIN_SPLIT_SIZE);
        if (shouldNotSplit) {
            return null;
        }

        // Calculate midpoint
        long splitAttemptPosition = currentPosition + (remainingBytes >>> 1);

        // Option 1: Scan forward from midpoint (your current good part)
        long bestSplitPos = -1;
        long scanStart = splitAttemptPosition;
        // Scan a reasonable window around the midpoint, e.g., +/- 10% of MIN_SPLIT_SIZE or a fixed window
        long scanWindow = Math.min(remainingBytes >>> 3, 2048); // Smaller scan window?
        long effectiveScanLimit = Math.min(limit, splitAttemptPosition + scanWindow);
        long effectiveScanStart = Math.max(currentPosition + 1, splitAttemptPosition - scanWindow); // Don't scan before current

        // Scan forward for a newline
        for (long p = scanStart; p < effectiveScanLimit; p++) {
            if (fileSegment.get(ValueLayout.JAVA_BYTE, p - 1) == NEWLINE_CHAR) { // p-1 is the \n, so p is start of new line
                bestSplitPos = p;
                break;
            }
        }

        // Option 2: If not found scanning forward, try scanning backward a bit
        if (bestSplitPos == -1) {
            for (long p = scanStart - 1; p >= effectiveScanStart; p--) {
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
                fileSegment,
                currentPosition,
                bestSplitPos,
                arena,
                closed);

        // Update current position
        currentPosition = bestSplitPos;

        return prefix;
    }

    @Override
    public long estimateSize() {
        return limit - currentPosition;
    }

    @Override
    public int characteristics() {
        return ORDERED | NONNULL | IMMUTABLE;
    }

    public void close() {
        // Only close if this is the root spliterator and it hasn't been closed yet
        if (isRoot && closed.compareAndSet(false, true)) {
            arena.close();
        }
    }
}
