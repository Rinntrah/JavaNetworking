package core;

/**
 * Represents customized protocol message able to be transmitted via
 * {@link SocketTransceiver}.
 *
 * @author Michał Furgał
 */
public abstract class AbstractNetMessage {
    public abstract void fromBytes(byte[] bytes);

    public abstract void intoBytes(byte[] bytes);

    public abstract byte[] intoBytes2();


}
