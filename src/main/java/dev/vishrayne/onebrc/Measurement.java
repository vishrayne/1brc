package dev.vishrayne.onebrc;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public class Measurement {
    private final SegmentKey stationSegmentKey;
    public double value;

    public SegmentKey getStationSegmentKey() {
        return stationSegmentKey;
    }

    final byte SEPARATOR_CHAR = (byte) ';';

    public Measurement(MemorySegment lineSegment) {
        long separatorIndex = -1;
        long limit = lineSegment.byteSize();
        for (int i = 0; i < limit; i++) {
            if (lineSegment.get(ValueLayout.JAVA_BYTE, i) == SEPARATOR_CHAR) {
                separatorIndex = i;
                break;
            }
        }

        // MemorySegment stationNameSlice = lineSegment.asSlice(0, separatorIndex);
        stationSegmentKey = new SegmentKey(lineSegment, separatorIndex);
        // stationSegmentKey = SegmentKeyInterner.threadLocalIntern(stationNameSlice);

        value = parseDoubleManually2(lineSegment, separatorIndex + 1);
    }

    private double parseDoubleManually2(MemorySegment segment, long startIndex) {
        long len = segment.byteSize();

        // Early bounds check with default return to avoid branches later
        if (startIndex >= len) {
            return Double.NaN;
        }

        long i = startIndex;
        byte firstByte = segment.get(ValueLayout.JAVA_BYTE, i);

        // Handle negative sign without branching
        boolean negative = (firstByte == '-');
        i += (negative ? 1 : 0);

        // Return NaN if just a '-' without branching
        boolean validAfterSign = (i < len);
        if (!validAfterSign) {
            return Double.NaN;
        }

        // Parse digits before decimal point
        double result = 0;
        while (i < len) {
            byte b = segment.get(ValueLayout.JAVA_BYTE, i);

            // Break on decimal point
            if (b == '.') {
                i++;
                break;
            }

            // Calculate digit value
            int digit = b - '0';
            result = result * 10 + digit;
            i++;
        }

        // Parse decimal part if any (i was incremented past the decimal point in the loop above)
        double factor = 0.1;
        while (i < len) {
            byte b = segment.get(ValueLayout.JAVA_BYTE, i);
            int digit = b - '0';
            result += digit * factor;
            factor *= 0.1;
            i++;
        }

        // Apply sign
        return negative ? -result : result;
    }
}
