package dev.vishrayne.onebrc;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public class Measurement {
    public final SegmentKey stationSegmentKey;
    public final double value;

    public Measurement(MemorySegment lineSegment, long startPosition, int separatorPosition, int lineLength) {
        stationSegmentKey = new SegmentKey(
                lineSegment,
                startPosition,
                separatorPosition);

        value = parseDoubleManually2(lineSegment, separatorPosition + 1, lineLength);
    }

    private static double parseDoubleManually2(MemorySegment segment, long startIndex, long endIndex) {
        // Early bounds check with default return to avoid branches later
        if (startIndex >= endIndex) {
            return Double.NaN;
        }

        long i = startIndex;
        byte firstByte = segment.get(ValueLayout.JAVA_BYTE, i);

        // Handle negative sign without branching
        boolean negative = (firstByte == '-');
        i += (negative ? 1 : 0);

        // Return NaN if just a '-' without branching
        boolean validAfterSign = (i < endIndex);
        if (!validAfterSign) {
            return Double.NaN;
        }

        // Parse digits before decimal point
        double result = 0;
        while (i < endIndex) {
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
        while (i < endIndex) {
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
