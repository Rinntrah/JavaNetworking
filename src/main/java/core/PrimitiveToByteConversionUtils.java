package core;

/**
 *
 * Utility class used to convert primitive types to byte arrays and vice-versa.
 *
 * @author Michał Furgał
 */
public class PrimitiveToByteConversionUtils {


    /**
     * Converts given <code>bytes</code> to 32 bit integer by using bit shift.
     *
     * @param bytes to convert.
     * @return converted <code>bytes</code> as an int.
     */
    public static int convertByteArrayToInt(byte[] bytes) {
        return ((bytes[0] & 0xFF) << 24)
                | ((bytes[1] & 0xFF) << 16)
                | ((bytes[2] & 0xFF) << 8)
                | ((bytes[3] & 0xFF) << 0);
    }
    /**
     * Converts given <code>value</code> integer to byte array by using bit
     * shift.
     *
     * @param value to convert.
     * @return converted <code>value</code> as byte[] array.
     */
    public static byte[] convertIntToByteArray(int value) {
        return new byte[]{(byte) (value >> 24), (byte) (value >> 16), (byte) (value >> 8), (byte) value};
    }

}
