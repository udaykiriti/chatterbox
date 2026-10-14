package com.chatterbox.core;

import java.util.Vector;

/**
 * Ignore list - nicks/hosts to suppress messages from.
 * Supports wildcards: *nick, nick*, *host*, user@host
 */
public final class IgnoreList {

    private final Vector entries = new Vector();
    private static final int MAX_ENTRIES = 50;

    public static final class Entry {
        public String pattern;  // e.g. "*spam*", "baduser", "user@*.evil.com"
        public boolean matchHost; // true if pattern includes @ (hostmask)

        public Entry(String p) {
            this.pattern = p == null ? "" : p.trim();
            this.matchHost = pattern.indexOf('@') >= 0;
        }

        /** Check if a nick!user@host matches this pattern. */
        public boolean matches(String nick, String user, String host) {
            if (pattern.length() == 0) {
                return false;
            }
            String target = matchHost ? (user + "@" + host) : nick;
            return matchPattern(target, pattern);
        }

        private boolean matchPattern(String text, String pattern) {
            // Simple wildcard: * matches anything, ? matches single char
            int ti = 0, pi = 0;
            while (ti < text.length() && pi < pattern.length()) {
                char pc = pattern.charAt(pi);
                if (pc == '*') {
                    pi++;
                    if (pi >= pattern.length()) {
                        return true; // * at end matches rest
                    }
                    // Find next pattern char in text
                    char next = pattern.charAt(pi);
                    while (ti < text.length() && text.charAt(ti) != next) {
                        ti++;
                    }
                } else if (pc == '?') {
                    ti++;
                    pi++;
                } else {
                    char tc = text.charAt(ti);
                    if (IrcCase.fold(tc) != IrcCase.fold(pc)) {
                        return false;
                    }
                    ti++;
                    pi++;
                }
            }
            // Skip trailing * in pattern
            while (pi < pattern.length() && pattern.charAt(pi) == '*') {
                pi++;
            }
            return ti >= text.length() && pi >= pattern.length();
        }
    }

    public synchronized void add(String pattern) {
        if (pattern == null || pattern.trim().length() == 0) {
            return;
        }
        Entry e = new Entry(pattern);
        // Don't add duplicates
        for (int i = 0; i < entries.size(); i++) {
            Entry ex = (Entry) entries.elementAt(i);
            if (ex.pattern.equalsIgnoreCase(e.pattern)) {
                return;
            }
        }
        if (entries.size() >= MAX_ENTRIES) {
            entries.removeElementAt(0);
        }
        entries.addElement(e);
    }

    public synchronized void remove(String pattern) {
        if (pattern == null) {
            return;
        }
        for (int i = 0; i < entries.size(); i++) {
            Entry e = (Entry) entries.elementAt(i);
            if (e.pattern.equalsIgnoreCase(pattern.trim())) {
                entries.removeElementAt(i);
                return;
            }
        }
    }

    public synchronized boolean isIgnored(String nick, String user, String host) {
        for (int i = 0; i < entries.size(); i++) {
            Entry e = (Entry) entries.elementAt(i);
            if (e.matches(nick, user, host)) {
                return true;
            }
        }
        return false;
    }

    public synchronized Vector getEntries() {
        Vector v = new Vector(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            v.addElement(entries.elementAt(i));
        }
        return v;
    }

    public synchronized void clear() {
        entries.removeAllElements();
    }

    public synchronized int size() {
        return entries.size();
    }
}