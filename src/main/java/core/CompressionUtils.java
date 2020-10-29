package core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterOutputStream;


/**
 *
 * Utility class used to compress and decompress byte arrays.
 *
 * @author Michał Furgał
 */
public class CompressionUtils {


    /**
     * Compresses given byte array using
     * {@link java.util.zip.DeflaterOutputStream}.
     *
     * @param in the byte array to compress.
     * @throws IOException if an I/O error occurs.
     * @return the compressed <code>in</code> array as a new array.
     */
    public static byte[] compressByteArray(byte[] in) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try (final DeflaterOutputStream dos = new DeflaterOutputStream(os)) {
            dos.write(in);
        }
        return os.toByteArray();
    }
    /**
     * Decompresses given byte array using
     * {@link java.util.zip.InflaterOutputStream}.
     *
     * @param in the byte array to decompressByteArray.
     * @throws IOException if an I/O error occurs.
     * @return the decompressed <code>in</code> array as a new array.
     */
    public static byte[] decompressByteArray(byte[] in) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try (final InflaterOutputStream ios = new InflaterOutputStream(os)) {
            ios.write(in);
            ios.flush();
        }
        return os.toByteArray();
    }

}
