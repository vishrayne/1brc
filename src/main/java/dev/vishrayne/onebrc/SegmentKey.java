package dev.vishrayne.onebrc;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;

public class SegmentKey implements Comparable<SegmentKey> {
    private final MemorySegment sourceSegment;
    private final int start;
    private final int length;
    private int preComputedHashCode = 0;
    private String cachedStationName = null;

    public SegmentKey(MemorySegment lineSegment) {
        this.sourceSegment = lineSegment;
        this.start = 0;
        this.length = (int) lineSegment.byteSize();
    }

    public SegmentKey(MemorySegment lineSegment, long length) {
        this.sourceSegment = lineSegment;
        this.start = 0;
        this.length = (int) length;
    }

    public SegmentKey(MemorySegment lineSegment, long start, long length) {
        this.sourceSegment = lineSegment;
        this.start = (int) start;
        this.length = (int) length;
    }

    @Override
    public int hashCode() {
        if (preComputedHashCode == 0 && length > 0) {
            preComputedHashCode = computeHashCode2();
        }

        return preComputedHashCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;

        if (o == null || getClass() != o.getClass())
            return false;

        SegmentKey other = (SegmentKey) o;
        if (this.length != other.length)
            return false;

        return this.sourceSegment.asSlice(start, length)
                .mismatch(other.sourceSegment.asSlice(start, length)) == -1;
    }

    @Override
    public int compareTo(SegmentKey other) {
        int len1 = this.length;
        int len2 = other.length;
        int commonLength = Math.min(len1, len2);

        long mismatchOffset = -1;
        if (commonLength > 0) {
            // Ensure we're comparing the correct slices if sourceSegment isn't already minimal
            mismatchOffset = this.sourceSegment.asSlice(start, commonLength)
                    .mismatch(other.sourceSegment.asSlice(start, commonLength));
        }

        if (mismatchOffset == -1) {
            // Prefixes are equal (or one/both were empty)
            return Integer.compare(len1, len2); // Shorter string comes first
        }
        else {
            // Mismatch found, compare the bytes at that offset
            byte b1 = this.sourceSegment.get(ValueLayout.JAVA_BYTE, mismatchOffset);
            byte b2 = other.sourceSegment.get(ValueLayout.JAVA_BYTE, mismatchOffset);
            return Integer.compare(b1 & 0xFF, b2 & 0xFF); // Unsigned byte comparison
        }
    }

    public String asString() {
        if (cachedStationName == null) {
            byte[] tempArray = new byte[length];
            MemorySegment.copy(sourceSegment, ValueLayout.JAVA_BYTE, start, tempArray, 0, length);
            cachedStationName = new String(tempArray, StandardCharsets.UTF_8);
        }

        return cachedStationName;
    }

    private int computeHashCode2() {
        int result = 1;
        for (int i = start; i < length; i++) {
            // Access directly from sourceSegment
            result = 31 * result + (sourceSegment.get(ValueLayout.JAVA_BYTE, i) & 0xFF);
        }

        return result;
    }
}
