package core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import sun.tools.jar.resources.jar;


/**
 * Encapsulates a {@link java.net.socket}, allowing for both way(read and write)
 * communication using custom protocol based on {@link AbstractNetMessage}.
 *
 * @author Michał Furgał
 */
public class SocketTransceiver {

    public static final int PACKET_SIZE_BYTES = 128;
    private volatile ConcurrentLinkedQueue<AbstractNetMessage> justReceivedMessages = new ConcurrentLinkedQueue<>();
    private Runnable readerRunnable = new Runnable() {
        @Override
        public void run() {
            while (socket != null && !socket.isClosed() && socket.isConnected()) {
                try {
                    AbstractNetMessage msg = read();
                    if (msg != null) {
                        justReceivedMessages.add(msg);
                    } else {
                        throw new Exception("[TRANSCEIVER]A null message has been received, closing the transceiver.");
                    }
                } catch (IOException ex) {
                    onReaderError(ex);
                    break;
                } catch (IllegalAccessException ex) {
                    onReaderError(ex);
                    break;
                } catch (Exception ex) {
                    onReaderError(ex);
                    break;
                }
            }
            Logger.getLogger(this.getClass().getName()).log(Level.INFO, "[TRANSCEIVER]reader thread exitting");
        }

        private void onReaderError(Exception ex2) {
            Logger.getLogger(this.getClass().getName()).log(Level.INFO, "[TRANSCEIVER]Read thread expected an error during reading data from socket.");
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
                socket = null;
            } catch (IOException ex) {
                Logger.getLogger(SocketTransceiver.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    };
    private Thread readerThread;
    private ByteBuffer receiveBuffer = ByteBuffer.allocate(PACKET_SIZE_BYTES);
    private NetMessageRegister register;
    private volatile Socket socket;
    private byte[] writeBuffer = new byte[PACKET_SIZE_BYTES];

    public SocketTransceiver(NetMessageRegister register, Socket socket) {
        this.register = register;
        this.socket = socket;
    }

    public SocketTransceiver(NetMessageRegister register) {
        this.register = register;
    }

    /**
     * Tries to stop the connection gracefully by closing the socket
     * encapsulated by this {@link SocketTransceiver} object and any
     * interrupting any of the worker threads.
     */
    public void disconnect() {
        if (socket != null) {
            if (!socket.isClosed()) {
                try {
                    socket.close();
                } catch (IOException ex) {
                    Logger.getLogger(SocketTransceiver.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            socket = null;
        }

        if (readerThread != null) {
            readerThread.interrupt();
            readerThread = null;
        }
    }

    /**
     * Returns all messages received by this {@link SocketTransceiver} object.
     * The returned list is an instance of {@link ConcurrentLinkedQueue} and is
     * constantly used by worker threads.
     *
     * @return {@link ConcurrentLinkedQueue} containing all
     * {@link AbstractNetMessage} received by this {@link SocketTransceiver}
     * object.
     */
    public ConcurrentLinkedQueue<AbstractNetMessage> getReceivedMessages() {
        return justReceivedMessages;
    }

    /**
     * A {@link SocketTransceiver} is dead if an IO error occured during
     * communication via socket encapsulated by this {@link SocketTransceiver}
     * object.
     *
     * @return true if socket encapsulated by this {@link SocketTransceiver}
     * object is closed or null.
     */
    public boolean isDead() {
        return socket == null || socket.isClosed();
    }

    /**
     * Tries to read next {@link AbstractNetMessage} from current
     * connection.Calls {@link InputStream#read()} internally, which blocks the
     * current thread until data is available.
     *
     * @return the received message.
     * @throws java.io.IOException if error occurs during read from current
     * connections {@link InputStream}.
     * @throws java.lang.InstantiationException, if no valid
     * {@link AbstractNetMessage} has been found in
     * {@link SocketTransceiver#register}.
     * @throws java.lang.Exception if any other exception occurred during
     * message read.
     */
    public AbstractNetMessage read() throws IOException, InstantiationException, Exception {
        receiveBuffer.clear();
        InputStream in = socket.getInputStream();
        socket.setSoTimeout(0);//keep waiting until some data comes
        int messageId;
        {//step 1. read message id
            byte[] _int4Bytes = new byte[4];
            in.read(_int4Bytes);
            messageId = PrimitiveToByteConversionUtils.convertByteArrayToInt(_int4Bytes);
            socket.setSoTimeout(2000);//data transmit may stop halfway, so we keep a timeout of 2 seconds for that convenience
        }
        AbstractNetMessage m = null;

        if (messageId == -1 || messageId == 0) {
            return null;
        }
        //step 2. create instance of that message type
        final Class<? extends AbstractNetMessage> get = register.get(messageId);
        if (get == null) {
            throw new Exception("Received non existing message type:" + messageId);
        }
        m = get.newInstance();

        //step 3. receive the length of bytes to receive for that message
        int howManyBytesToReceive;
        {
            byte[] _int4Bytes = new byte[4];
            for (int i = 0; i < 4; i++) {
                int x = in.read();
                _int4Bytes[i] = (byte) x;
            }
            howManyBytesToReceive = PrimitiveToByteConversionUtils.convertByteArrayToInt(_int4Bytes);
        }
        //sanity check
        if (howManyBytesToReceive <= 0) {
            throw new Exception("howManyBytesToReceive <= 0, " + socket);
        }

        //step 4. receive the exact amount of bytes needed for that message
        int totalCountReceived = 0;
        do {
            byte[] exactExpectedSizeBa = new byte[howManyBytesToReceive - totalCountReceived];
            int howManyReadenThisStep = in.read(exactExpectedSizeBa);
            if (howManyReadenThisStep > 0) {
                receiveBuffer.put(exactExpectedSizeBa, 0, howManyReadenThisStep);
                totalCountReceived += howManyReadenThisStep;
            } else {
                throw new Exception("hit the EOF, totalCount of readen bytes so far=, " + totalCountReceived);
            }
            Logger.getLogger(SocketTransceiver.class.getName()).log(Level.FINEST, "totalBytesCountReceived/howManyReceiveWasDeclared=" + totalCountReceived + "/" + howManyBytesToReceive + " howManyBytesReadenThisStep:" + howManyReadenThisStep);
        } while (totalCountReceived < howManyBytesToReceive);
        if (totalCountReceived != howManyBytesToReceive) {
            throw new Exception("broken packet:totalCount != howManyBytesToReceive");
        }
        receiveBuffer.position(0);

        //copy received data from receiveBuffer
        byte[] receivedBytes = new byte[totalCountReceived];
        for (int i = 0; i < totalCountReceived; i++) {
            receivedBytes[i] = receiveBuffer.get();
        }
        //decompress received data bytes
        receivedBytes = CompressionUtils.decompressByteArray(receivedBytes);
        //recreate message from received bytes
        m.fromBytes(receivedBytes);
        return m;
    }

    protected void run() {
        readerThread = new Thread(readerRunnable);
        readerThread.start();
    }

    public synchronized boolean send(Collection<AbstractNetMessage> toSend) {
        for (AbstractNetMessage msg : toSend) {
            try {
                write(msg);
            } catch (Exception ex) {
                Logger.getLogger(SocketTransceiver.class.getName()).log(Level.SEVERE, null, ex);
                return false;
            }
        }
        return true;
    }

    public synchronized boolean send(AbstractNetMessage... stringMessage) {
        for (AbstractNetMessage msg : stringMessage) {
            try {
                write(msg);
            } catch (Exception ex) {
                Logger.getLogger(SocketTransceiver.class.getName()).log(Level.SEVERE, null, ex);
                return false;
            }
        }
        return true;
    }

    /**
     * Tries to connect to specifed host.
     *
     * @param host the host ip adress
     * @param port the host port number
     * @return true if successfully conected
     */
    public boolean tryConnect(String host, int port) {
        if (socket == null) {
            socket = new Socket();
        }

        try {
            socket.connect(new InetSocketAddress(host, port));
            run();
            return true;
        } catch (IOException ex) {
            Logger.getLogger(SocketTransceiver.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
    }

    private void write(AbstractNetMessage message) throws IOException, Exception {
        //Reset write buffer
        for (int i = 0; i < writeBuffer.length; i++) {
            writeBuffer[i] = 0;
        }
        //Convert message into bytes
        message.intoBytes(writeBuffer);
        OutputStream out = socket.getOutputStream();
        //Compress message bytes previously written to writeBuffer
        final byte[] dataCompressed = CompressionUtils.compressByteArray(writeBuffer);
        //In following steps,write data to the 'out' OutputStream:
        //step 1. write message uid
        final int messageId = register.get(message.getClass());
        final byte[] ba = PrimitiveToByteConversionUtils.convertIntToByteArray(messageId);
        out.write(ba);
        //step 2. write data(byte array) length
        out.write(PrimitiveToByteConversionUtils.convertIntToByteArray(dataCompressed.length));
        //step 3. write data byte array
        out.write(dataCompressed);
    }

}
