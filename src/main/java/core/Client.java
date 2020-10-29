package core;

import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;


/**
 * A convenience utility class used to easen client management using
 * {@link SocketTransceiver}.
 *
 * @author Michał
 */
public class Client {

    private final String host;

    private final int port;

    private SocketTransceiver socketTransceiver;

    public Client(String host, int port, NetMessageRegister register) {
        socketTransceiver = new SocketTransceiver(register);
        this.host = host;
        this.port = port;

    }


    public boolean connect() {
        return socketTransceiver.tryConnect(host, port);
    }

    public void disconnect() {
        socketTransceiver.disconnect();
    }
    /**
     *
     * @return messages received from connected server
     */
    public ConcurrentLinkedQueue<AbstractNetMessage> getMessages() {
        return socketTransceiver.getReceivedMessages();
    }
    /**
     *
     * @param toSend messages to send to connected server
     * @return
     */
    public boolean sendMessages(Collection<AbstractNetMessage> toSend) {
        return socketTransceiver.send(toSend);
    }
}
