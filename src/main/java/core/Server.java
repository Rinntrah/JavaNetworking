package core;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Creates {@link ServerSocket} and manages incoming connections asynchronically
 * on other thread by encapsulating incoming socket connections in
 * {@link  SocketTransceiver}. Allows to respond for received client messages by
 * using {@link OnClientMessageListener} and other callbacks available via set
 * methods of this class.
 *
 * @author Michał Furgał
 */
public class Server {


    private ConcurrentLinkedQueue<ClientConnection> activeConnections;
    private OnClientConnectedListener onClientConnectedListener;
    private OnClientDisconnectedListener onClientDisconnectedListener;
    private OnClientMessageListener onClientMessageListener;
    private Thread serverIncomingConnectionAcceptorThread;
    private volatile boolean serverIncomingConnectionAcceptorThreadRunning = false;
    /*
     * A value in milliseconds used to specify how often to check for received messages
     * from clients.
     */
    private long serverLoopIntervalInMilliseconds = 16;
    private Thread serverMessageReceiverThread;
    private volatile boolean serverMessageReceiverThreadRunning = false;
    private volatile boolean serverRunning;
    private volatile ServerSocket serverSocketUsedByServerThread;

    public OnClientConnectedListener getOnClientConnectedListener() {
        return onClientConnectedListener;
    }

    /**
     * Sets the {@link OnClientConnectedListener} which fires after new client
     * connects to server.
     *
     * @param onClientConnectedListener to set to
     */
    public void setOnClientConnectedListener(OnClientConnectedListener onClientConnectedListener) {
        this.onClientConnectedListener = onClientConnectedListener;
    }

    /**
     * Sets the {@link OnClientDisconnectedListener} which fires after client
     * disconnects to server.
     *
     * @param onClientDisconnectedListener to set to
     */
    public void setOnClientDisconnectedListener(OnClientDisconnectedListener onClientDisconnectedListener) {
        this.onClientDisconnectedListener = onClientDisconnectedListener;
    }

    /**
     * Sets the {@link OnClientMessageListener} which fires after message is
     * received from one of the connected clients. disconnects to server.
     *
     * @param onClientMessageListener to set to
     */
    public void setOnClientMessageListener(OnClientMessageListener onClientMessageListener) {
        this.onClientMessageListener = onClientMessageListener;
    }

    /**
     *
     * @return A value in milliseconds used to specify how often to receive
     * messages from clients.
     */
    public long getServerLoopIntervalInMilliseconds() {
        return serverLoopIntervalInMilliseconds;
    }

    /**
     * Changes the server loop interval, which is used to gather and propagate
     * messages from clients.
     *
     * @param serverLoopIntervalInMilliseconds A value in milliseconds used to
     * specify how often to receive messages from clients.
     */
    public void setServerLoopIntervalInMilliseconds(long serverLoopIntervalInMilliseconds) {
        this.serverLoopIntervalInMilliseconds = serverLoopIntervalInMilliseconds;
    }

    /**
     *
     * @return true if server is in running state and accepting new connections
     * and messages.
     */
    public boolean isRunning() {
        return serverRunning;
    }

    /**
     * Starts the server on specified port and with specified
     * {@link NetMessageRegister}.
     *
     * @param portNumber
     * @param registers
     */
    public void startServer(int portNumber, final NetMessageRegister registers) {
        activeConnections = new ConcurrentLinkedQueue<>();
        this.serverIncomingConnectionAcceptorThread = startServerAcceptorThread(portNumber, registers);

        serverMessageReceiverThread = new Thread(() -> {
            runServerMessageReceiverThread();
        });
        serverMessageReceiverThread.start();

        //Wait until server-socket is created
        while (!Thread.currentThread().isInterrupted() && serverSocketUsedByServerThread == null) {
            try {
                Thread.sleep(1);
            } catch (Exception ex) {
                Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
                return;
            }
        }

        int timeout = 10;
        while (timeout-- > 0 && serverMessageReceiverThreadRunning != true && serverIncomingConnectionAcceptorThreadRunning != true) {
            try {
                Thread.sleep(250);
            } catch (InterruptedException ex) {
                Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
                stopServer();
            }
        }

        if (timeout <= 0) {
            Logger.getLogger(Server.class.getName()).log(Level.SEVERE, "[SERVER]Could not start server.(Timeout)");
            stopServer();
        } else {
            Logger.getLogger(Server.class.getName()).log(Level.INFO, "[SERVER]Server started and worker threads are running.");
        }

    }

    /**
     * Stops the server gracefully by shutting down threads and closing server
     * socket.
     */
    public void stopServer() {
        serverRunning = false;
        Logger.getLogger(Server.class.getName()).log(Level.INFO, "[SERVER]stopping server...");
        if (serverSocketUsedByServerThread != null && serverSocketUsedByServerThread.isClosed() == false) {
            try {
                Logger.getLogger(Server.class.getName()).log(Level.INFO, "[SERVER]closing serverSocket...");
                serverSocketUsedByServerThread.close();
            } catch (IOException ex) {
                Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        if (serverIncomingConnectionAcceptorThread != null) {
            Logger.getLogger(Server.class.getName()).log(Level.INFO, "[SERVER]interrupting  serverIncomingConnectionAcceptorThread...");
            serverIncomingConnectionAcceptorThread.interrupt();
        }
        if (serverMessageReceiverThread != null) {
            Logger.getLogger(Server.class.getName()).log(Level.INFO, "[SERVER]interrupting  serverMessageReceiverThread...");
            serverMessageReceiverThread.interrupt();
        }
        serverMessageReceiverThread = null;
        serverIncomingConnectionAcceptorThread = null;
    }

    private void dropDeadClientConnections() {
        //Iterate through each active connection to send some messages,get received messages, remove dead connections.
        for (Iterator<ClientConnection> it = activeConnections.iterator(); it.hasNext();) {
            final ClientConnection next = it.next();
            if (next.isDead()) {
                Logger.getLogger(Server.class.getName()).log(Level.INFO, "[SERVER]Dropping dead connection:" + next);
                next.transceiver.disconnect();
                it.remove();
                if (onClientDisconnectedListener != null) {
                    onClientDisconnectedListener.onClientDisconnected(next);
                }
            }
        }
    }

    private void receiveAndPropagateMessageFromClients() {
        for (Iterator<ClientConnection> it = activeConnections.iterator(); it.hasNext();) {
            final ClientConnection clientConn = it.next();

            if (clientConn.transceiver.getReceivedMessages().size() > 0) {
                final ConcurrentLinkedQueue<AbstractNetMessage> receivedMessages = clientConn.transceiver.getReceivedMessages();
                if (onClientMessageListener != null) {
                    onClientMessageListener.onClientMessage(clientConn, receivedMessages);
                } else {
                    receivedMessages.clear();
                }

            }
        }
    }

    private void runServerMessageReceiverThread() {

        serverMessageReceiverThreadRunning = true;
        while (!Thread.interrupted()) {
            dropDeadClientConnections();
            receiveAndPropagateMessageFromClients();
            try {
                Thread.sleep(15);
            } catch (InterruptedException ex) {
                Logger.getLogger(Server.class.getName()).log(Level.INFO, "[SERVER]exitting runServerMessageReceiverThread()");
                serverMessageReceiverThreadRunning = false;
                break;
            }
        }
        serverMessageReceiverThreadRunning = false;
        Logger.getLogger(Server.class.getName()).log(Level.FINE, "[SERVER]runServerMessageReceiverThread() has exited");
    }

    private Thread startServerAcceptorThread(int portNumber, final NetMessageRegister registers) {
        final Runnable acceptorRunnable = new Runnable() {
            @Override
            public void run() {
                //Create the server socket
                final ServerSocket serverSocket;
                try {
                    serverSocket = new ServerSocket(portNumber);
                    Logger.getLogger(Server.class.getName()).log(Level.INFO, "[SERVER]server started on port:" + portNumber);
                    serverSocketUsedByServerThread = serverSocket;
                    serverRunning = true;
                } catch (IOException ex) {
                    Logger.getLogger(Server.class
                            .getName()).log(Level.SEVERE, null, ex);

                    return;
                }

                serverIncomingConnectionAcceptorThreadRunning = true;
                //Start the accepting thread
                while (!Thread.interrupted()) {
                    try {
                        //Accept next incoming connection
                        final Socket clientSocket = serverSocket.accept();
                        Logger.getLogger(Server.class.getName()).log(Level.INFO, "[SERVER]Accepting incoming connection from socket:" + clientSocket);
                        final ClientConnection clientConnection = new ClientConnection(clientSocket, registers);
                        activeConnections.add(clientConnection);
                        if (onClientConnectedListener != null) {
                            onClientConnectedListener.onClientConnected(clientConnection);
                        }

                    } catch (Exception ex) {
                        Logger.getLogger(Server.class.getName()).log(Level.INFO, "[SERVER]exitting startServerAcceptorThread()");
                        serverIncomingConnectionAcceptorThreadRunning = false;
                        break;
                    }
                }
                Logger.getLogger(Server.class.getName()).log(Level.INFO, "[SERVER]startServerAcceptorThread() has exited");
                serverIncomingConnectionAcceptorThreadRunning = false;
            }
        };

        final Thread t = new Thread(acceptorRunnable);
        t.start();
        return t;
    }

    /**
     * Fired after one of the connected clients sends an message to server.
     */
    public static interface OnClientMessageListener {

        public void onClientMessage(ClientConnection clientConnection, ConcurrentLinkedQueue receivedMessages);
    }

    /**
     * Fired after one of the connected clients connects to server.
     */
    public static interface OnClientConnectedListener {

        public void onClientConnected(ClientConnection clientConnection);
    }

    /**
     * Fired after one of the connected clients disconnects from server.
     */
    public static interface OnClientDisconnectedListener {

        public void onClientDisconnected(ClientConnection clientConnection);
    }

    /**
     * Represents one of the clients currently connected to server.
     */
    public static final class ClientConnection {

        private static volatile long pool = 0;
        public Socket clientSocket;
        public SocketTransceiver transceiver;
        public final long uid = pool++;

        public ClientConnection(Socket clientSocket, NetMessageRegister registers) {
            this.clientSocket = clientSocket;
            transceiver = new SocketTransceiver(registers, clientSocket);
            transceiver.run();
        }

        @Override
        public String toString() {
            return "ClientConnection[" + "uid=" + uid + "clientSocket=" + clientSocket + "isDead= " + isDead() + "]";
        }

        private boolean isDead() {
            return transceiver.isDead();
        }
    }

}
