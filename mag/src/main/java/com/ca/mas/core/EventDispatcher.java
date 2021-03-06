package com.ca.mas.core;

import java.util.Observable;

/**
 * Dispatch event to Observer which listen for the event
 */
public class EventDispatcher extends Observable {

    public static final EventDispatcher LOGOUT = new EventDispatcher();
    public static final EventDispatcher DE_REGISTER = new EventDispatcher();
    public static final EventDispatcher RESET_LOCALLY = new EventDispatcher();
    public static final EventDispatcher BEFORE_GATEWAY_SWITCH = new EventDispatcher();
    public static final EventDispatcher AFTER_GATEWAY_SWITCH = new EventDispatcher();

    @Override
    public synchronized boolean hasChanged() {
        return true;
    }

}
