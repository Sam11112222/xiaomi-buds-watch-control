package dev.budswatch.ui;

import dev.budswatch.transport.Session;

/** What pages need from the activity. */
public interface Host {
    Session session();
    void push(Page p);
    void pop();
    void disconnectAndPick();
}
