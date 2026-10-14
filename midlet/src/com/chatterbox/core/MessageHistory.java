package com.chatterbox.core;

import java.util.Vector;

/**
 * Message history - stores sent messages for UP/DOWN navigation in input line.
 * Per-window history, max 50 entries.
 */
public final class MessageHistory {

    private final Vector history = new Vector();
    private final int maxSize;
    private int index = -1; // -1 = at bottom (current input)
    private String currentInput = "";

    public MessageHistory() {
        this(50);
    }

    public MessageHistory(int max) {
        this.maxSize = max > 0 ? max : 50;
    }

    /** Add a sent message to history. */
    public synchronized void add(String msg) {
        if (msg == null || msg.trim().length() == 0) {
            return;
        }
        String m = msg.trim();
        // Don't add duplicate of last entry
        if (history.size() > 0 && m.equals(history.elementAt(history.size() - 1))) {
            return;
        }
        if (history.size() >= maxSize) {
            history.removeElementAt(0);
        }
        history.addElement(m);
        index = -1;
        currentInput = "";
    }

    /** Get previous message (UP key). Returns null if at top. */
    public synchronized String previous() {
        if (history.size() == 0) {
            return null;
        }
        if (index == -1) {
            // First UP: save current input, go to last history item
            index = history.size() - 1;
            return (String) history.elementAt(index);
        }
        if (index > 0) {
            index--;
            return (String) history.elementAt(index);
        }
        return (String) history.elementAt(0); // at top, stay
    }

    /** Get next message (DOWN key). Returns null if at bottom. */
    public synchronized String next() {
        if (history.size() == 0 || index == -1) {
            return null;
        }
        if (index < history.size() - 1) {
            index++;
            return (String) history.elementAt(index);
        }
        // At last item - go to "current input" (-1) and return the last item
        index = -1;
        return (String) history.elementAt(history.size() - 1);
    }

    /** Save the current input buffer when leaving history navigation. */
    public synchronized void setCurrentInput(String input) {
        if (index == -1) {
            currentInput = input == null ? "" : input;
        }
    }

    /** Clear history. */
    public synchronized void clear() {
        history.removeAllElements();
        index = -1;
        currentInput = "";
    }

    public synchronized int size() {
        return history.size();
    }

    /** For debugging / UI. */
    public synchronized Vector getAll() {
        Vector v = new Vector(history.size());
        for (int i = 0; i < history.size(); i++) {
            v.addElement(history.elementAt(i));
        }
        return v;
    }
}