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

        // For very short strings (e.g., typical station names or short lines),
        // the setup cost of vector operations might outweigh the benefits of a simple loop.
        //
        // VectorSpecies<Byte> SPECIES = ByteVector.SPECIES_PREFERRED;
        // for (long offset = 0; offset < limit; offset += SPECIES.length()) {
        // // Create a mask for the remaining bytes if near the end
        // VectorMask<Byte> mask = SPECIES.maskAll(true);
        // if (offset + SPECIES.length() > limit) {
        // mask = SPECIES.indexInRange(offset, limit);
        // }
        //
        // ByteVector vec = ByteVector.fromMemorySegment(SPECIES, lineSegment, offset, ByteOrder.nativeOrder(), mask);
        // VectorMask<Byte> vecEqSeparator = vec.eq(SEPARATOR_CHAR);
        //
        // if (vecEqSeparator.anyTrue()) {
        // separatorIndex = offset + vecEqSeparator.firstTrue();
        // break;
        // }
        //
        // // TODO: Ensure separatorIndex is found before proceeding
        // // But all entries are guaranteed to have a separator (;)
        // // so we can skip this check for the time being.
        // }

        MemorySegment stationNameSlice = lineSegment.asSlice(0, separatorIndex);
        stationSegmentKey = new SegmentKey(stationNameSlice);

        value = parseDoubleManually2(lineSegment, separatorIndex + 1);
    }

    // Specialized double parser from Claude
    // private double parseDoubleManually(MemorySegment segment, int startIndex) {
    // double result = 0;
    // boolean negative = false;
    // int i = startIndex;
    // long len = segment.byteSize();
    //
    // // Check if i is within bounds before accessing
    // if (i >= len) {
    // // This case should ideally not happen if lines always have a value
    // // Or, handle as an error or default value
    // System.err.println("Warning: parseDoubleManually called with startIndex out of bounds or empty value string.");
    // return Double.NaN; // Or throw an exception
    // }
    //
    // if (segment.get(ValueLayout.JAVA_BYTE, i) == '-') {
    // negative = true;
    // i++;
    //
    // if (i >= len) { // just a '-'
    // System.err.println("Warning: parseDoubleManually encountered just '-'");
    // return Double.NaN;
    // }
    // }
    //
    // // Parse the whole number part
    // for (; i < len && segment.get(ValueLayout.JAVA_BYTE, i) != '.'; i++) {
    // result = result * 10 + (segment.get(ValueLayout.JAVA_BYTE, i) - '0');
    // }
    //
    // // Parse decimal part if present
    // if (i < len && segment.get(ValueLayout.JAVA_BYTE, i) == '.') {
    // double factor = 0.1;
    // i++;
    // for (; i < len; i++) {
    // result += (segment.get(ValueLayout.JAVA_BYTE, i) - '0') * factor;
    // factor *= 0.1;
    // }
    // }
    //
    // return negative ? -result : result;
    // }

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
