package core;

import core.Server;
import core.AbstractNetMessage;
import core.NetMessageRegister;
import core.SocketTransceiver;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;


/**
 *
 * @author Michał
 */
public class Tester {

    static final String hostName = "127.0.0.1";
    static final int portNumber = 1234;
    private Server server;

    @Test
    public void testServerClientCommunication() throws InterruptedException {
        server = new MyTestServer();
        server.startServer(portNumber, MyTestMessageRegisterSingleton.getSingleton());

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                runNewClientAndSendSomeMessages();
                runNewClientAndSendSomeMessages();
            }
        });
        t.start();
        runNewClientAndSendSomeMessages();
        runNewClientAndSendSomeMessages();
        server.stopServer();
    }

    @Test
    public void testServerRunAndStop() {
        System.out.println("testServerRunAndStop");
        server = new MyTestServer();
        server.startServer(portNumber, MyTestMessageRegisterSingleton.getSingleton());

        try {
            Thread.sleep(1000);
        } catch (InterruptedException ex) {
            Logger.getLogger(Tester.class.getName()).log(Level.SEVERE, null, ex);
        }
        stopServer();
    }

    private void runNewClientAndSendSomeMessages() {

        try {
            Thread.sleep(1000);
        } catch (InterruptedException ex) {
            Logger.getLogger(Tester.class.getName()).log(Level.SEVERE, null, ex);
        }

        final SocketTransceiver st = new SocketTransceiver(MyTestMessageRegisterSingleton.getSingleton());
        boolean connected = st.tryConnect(hostName, portNumber);
        Assert.assertEquals(connected, true);

        if (connected) {
            int count = 0;
            while (count++ < 4) {
                //Push message
                final MyTestStringMessage stringMessage = new MyTestStringMessage();
                stringMessage.s1 = "Hello from client!";

                final List<AbstractNetMessage> toSend = new ArrayList<AbstractNetMessage>();
                toSend.add(stringMessage);
                if (!st.send(toSend)) {
                    break;
                }

                //Get some messages if available
                if (st.getReceivedMessages().size() > 0) {
                    ConcurrentLinkedQueue<AbstractNetMessage> receivedMessages = st.getReceivedMessages();
                    for (Iterator<AbstractNetMessage> iterator = receivedMessages.iterator(); iterator.hasNext();) {
                        AbstractNetMessage next = iterator.next();
                        System.err.println("[CLIENT] RECEIVED MESSAGE->" + next);
                        iterator.remove();
                    }
                }

                try {
                    Thread.sleep(1234);
                } catch (InterruptedException ex) {
                }
            }
        }

        System.err.println("[CLIENT] DISCONNECTING FROM SERVER...");
        st.disconnect();
    }

    private void stopServer() {
        server.stopServer();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ex) {
            Logger.getLogger(Tester.class.getName()).log(Level.SEVERE, null, ex);
        }
        System.err.println("Check is server running after stop:" + server.isRunning());
        Assert.assertEquals(server.isRunning(), false);
    }

    private class MyTestServer extends Server {

        public MyTestServer() {
            this.setOnClientConnectedListener(new Server.OnClientConnectedListener() {
                @Override
                public void onClientConnected(Server.ClientConnection clientConnection) {
                    //Send welcome message
                    final List<AbstractNetMessage> toSend = new ArrayList<AbstractNetMessage>();
                    final MyTestStringMessage welcomeMessage = new MyTestStringMessage();
                    welcomeMessage.s1 = "Welcome to the server!";
                    toSend.add(welcomeMessage);
                    clientConnection.transceiver.send(toSend);
                }
            });

            this.setOnClientMessageListener(new Server.OnClientMessageListener() {
                @Override
                public void onClientMessage(Server.ClientConnection clientConnection, ConcurrentLinkedQueue receivedMessages) {
                    for (Iterator<AbstractNetMessage> iterator = receivedMessages.iterator(); iterator.hasNext();) {
                        AbstractNetMessage next = iterator.next();
                        System.err.println("[SERVER] RECEIVED MESSAGE FROM CONNECTION UID:" + clientConnection.uid + " MESSAGE=" + next);
                        iterator.remove();
                    }
                    MyTestStringMessage stringMessage = new MyTestStringMessage();
                    stringMessage.s1 = "Thank you for messages.";
                    clientConnection.transceiver.send(stringMessage);

                }
            });
        }

    }
}
