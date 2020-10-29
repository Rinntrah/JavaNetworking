package core;

import core.AbstractNetMessage;
import core.PrimitiveToByteConversionUtils;
import core.SocketTransceiver;
import java.nio.charset.Charset;
import java.text.DecimalFormat;


/**
 * A test message.
 *
 * @author Michał Furgał
 */
public class MyTestStringMessage extends AbstractNetMessage {

    /*
     * This charset should be used to read and write strings.
     */
    public static final Charset USED_CHARSET = Charset.forName("UTF-8");
    public String s1;

    @Override
    public void fromBytes(byte[] bytes) {
        int s1_ba_len = PrimitiveToByteConversionUtils.convertByteArrayToInt(bytes);
        s1 = new String(bytes, 4, s1_ba_len, USED_CHARSET);
    }

    @Override
    public void intoBytes(byte[] bytes) {
        byte[] b1 = s1.getBytes(USED_CHARSET);
        int off = 0;
        //save 4 bytes indicating len of str
        bytes[off++] = (byte) (b1.length >> 24);
        bytes[off++] = (byte) (b1.length >> 16);
        bytes[off++] = (byte) (b1.length >> 8);
        bytes[off++] = (byte) (b1.length);
        for (int i = 0; i < b1.length; i++) {
            bytes[off++] = b1[i];
        }
    }

    @Override
    public byte[] intoBytes2() {
        byte[] b1 = s1.getBytes(USED_CHARSET);
        byte[] bytes = new byte[4 + b1.length];
        int off = 0;
        //save 4 bytes indicating len of str
        bytes[off++] = (byte) (b1.length >> 24);
        bytes[off++] = (byte) (b1.length >> 16);
        bytes[off++] = (byte) (b1.length >> 8);
        bytes[off++] = (byte) (b1.length);
        for (int i = 0; i < b1.length; i++) {
            bytes[off++] = b1[i];
        }
        return bytes;

    }

    @Override
    public String toString() {
        return "stringMsg:" + s1;
    }

}
