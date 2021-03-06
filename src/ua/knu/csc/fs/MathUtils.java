package ua.knu.csc.fs;

public final class MathUtils {
    //binary:
    // BITMASKS[0] = 10000000000000...
    // BITMASKS[1] = 01000000000000...
    // ...
    private static final long[] BITMASKS = new long[Long.SIZE];
    
    static {
        BITMASKS[BITMASKS.length - 1] = 1;
        for (int i = BITMASKS.length - 2; i >= 0; i--) {
            BITMASKS[i] = BITMASKS[i + 1] << 1;
        }
    }
    
    private MathUtils() {}

    /**
     * Divide two ints and round up.
     */
    public static int divideCeil(int dividend, int divisor) {
        return dividend / divisor + (dividend % divisor == 0 ? 0 : 1);
    }

    /**
     * @return position of first 0 bit, starting from most significant bit. -1 if not found.
     */
    public static int findZeroByte(long bitmap) {
        for (int i = 0; i < Long.SIZE; i++) {
            if ((bitmap & BITMASKS[0]) == 0)
                return i;

            bitmap = bitmap << 1;
        }
        return -1;
    }

    /**
     * Set bit at index to 1, starting from most significant bit.
     * @return new bitmap
     */
    public static long setOneByte(long bitmap, int index) {
        return bitmap | BITMASKS[index];
    }

    public static long setZeroByte(long bitmap, int index) {
        return bitmap & ~BITMASKS[index];
    }
    
    public static void toBytes(long l, byte[] buffer) {
        for (int i = Long.BYTES - 1; i >= 0; i--) {
            buffer[i] = (byte)(l & 0xFF);
            l = l >> 1;
        }
    }
    
    public static long toLong(byte[] bytes) {
        long result = 0;
        for (int i = 0; i < Long.BYTES; i++) {
            result = result << 1;
            result |= (bytes[i] & 0xFF);
        }
        return result;
    }
}
