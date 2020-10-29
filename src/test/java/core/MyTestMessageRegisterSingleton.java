package core;

import core.NetMessageRegister;


/**
 * A test Message register.
 *
 * @author Michał Furgał
 */
public class MyTestMessageRegisterSingleton {

    private static NetMessageRegister my;

    public static NetMessageRegister getSingleton() {
        if (my == null) {
            my = new NetMessageRegister();
            my.register(MyTestStringMessage.class, 1337);
        }
        return my;
    }

}
