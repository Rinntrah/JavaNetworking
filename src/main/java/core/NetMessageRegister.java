package core;

import java.util.HashMap;


/**
 *
 * Messages which are possible to send and receive via {@link core.Transceiver}
 * must be registered here and have unique id(UID), which is used do determine
 * message type after receiving it. Both client and server implementations must
 * have identical UID's for same {@link core.AbstractNetMessage}.
 *
 * @author Michał Furgał
 */
public class NetMessageRegister {

    private final HashMap<Integer, Class<? extends AbstractNetMessage>> messageIds = new HashMap<>();
    private final HashMap<Class<? extends AbstractNetMessage>, Integer> messageIds2 = new HashMap<>();

    public Class<? extends AbstractNetMessage> get(int id) {
        return messageIds.get(id);
    }

    /**
     * Get the unique identifier for specified {@link AbstractNetMessage} class
     * type.
     *
     * @param clazz the class extending {@link AbstractNetMessage} to search for
     * unique id.
     * @return unique identifier for specified <code> clazz</code> class.
     */
    public int get(Class<? extends AbstractNetMessage> clazz) {
        return messageIds2.get(clazz);
    }

    /**
     * Registers specified <code>clazz</code> class under specified
     * <code>uid</code> unique identifier.
     *
     * @param clazz the class extending {@link AbstractNetMessage} to register.
     * @param uid the integer specified to uniquely identify that message type.
     */
    public void register(Class<? extends AbstractNetMessage> clazz, int uid) {
        if (uid == 0 || uid == -1) {
            throw new RuntimeException("It is forbidden to register message under type under uid:'" + uid + "'.");
        }
        if (clazz == null) {
            throw new RuntimeException("Tried to register null message class type under uid:'" + uid + "'.");
        }

        Class<? extends AbstractNetMessage> conflictingMessageClass = messageIds.get(uid);
        if (conflictingMessageClass != null) {
            throw new UniqueIdentifierCollisionException("Tried to register message with uid:'" + uid + "', which is currently in use by:'" + conflictingMessageClass + "'.");
        }
        messageIds.put(uid, clazz);
        messageIds2.put(clazz, uid);
    }

    private static class UniqueIdentifierCollisionException extends RuntimeException {

        public UniqueIdentifierCollisionException(String errorMessage) {
            super(errorMessage);
        }
    }

}
