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

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.TimeUnit;

public class MemoryPerformanceBenchmark {
    private static final int ITERATIONS = 100_000;
    private static final long FILE_SIZE = 1024 * 1024; // 1MB

    public static void main(String[] args) throws Exception {
        Path tempFile = createTempFile(FILE_SIZE);

        // Warm-up
        benchmarkMemorySegment(tempFile);
        benchmarkMappedByteBuffer(tempFile);

        // Actual benchmarks
        long memorySegmentTime = benchmarkMemorySegment(tempFile);
        long mappedByteBufferTime = benchmarkMappedByteBuffer(tempFile);

        System.out.println("MemorySegment Time: " + memorySegmentTime + " ms");
        System.out.println("MappedByteBuffer Time: " + mappedByteBufferTime + " ms");
    }

    private static long benchmarkMemorySegment(Path file) throws Exception {
        long start = System.nanoTime();

        try (Arena arena = Arena.ofShared()) {
            try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
                MemorySegment segment = channel.map(
                        FileChannel.MapMode.READ_ONLY,
                        0,
                        FILE_SIZE,
                        arena);

                for (int i = 0; i < ITERATIONS; i++) {
                    long sum = 0;
                    for (long j = 0; j < segment.byteSize(); j++) {
                        sum += segment.get(ValueLayout.JAVA_BYTE, j) & 0xFF;
                    }
                }
            }
        }

        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    private static long benchmarkMappedByteBuffer(Path file) throws Exception {
        long start = System.nanoTime();

        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            MappedByteBuffer buffer = channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    0,
                    FILE_SIZE);

            for (int i = 0; i < ITERATIONS; i++) {
                long sum = 0;
                buffer.position(0);
                while (buffer.hasRemaining()) {
                    sum += buffer.get() & 0xFF;
                }
            }
        }

        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    private static Path createTempFile(long size) throws Exception {
        Path tempFile = Files.createTempFile("benchmark", ".dat");
        try (FileChannel channel = FileChannel.open(
                tempFile,
                StandardOpenOption.WRITE,
                StandardOpenOption.READ)) {
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            for (long written = 0; written < size;) {
                buffer.clear();
                int toWrite = (int) Math.min(buffer.capacity(), size - written);
                buffer.limit(toWrite);
                for (int i = 0; i < toWrite; i++) {
                    buffer.put((byte) i);
                }
                buffer.flip();
                written += channel.write(buffer);
            }
        }
        return tempFile;
    }
}
