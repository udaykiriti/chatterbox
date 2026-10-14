package com.chatterbox.core;

/**
 * One renderable line of history. Immutable apart from the highlight flag.
 *
 * Kinds map onto a colour choice and an alignment choice in ChatView:
 *   CHAT / ACTION / NOTICE  user content
 *   JOIN/PART/QUIT/NICK/...  events, dimmed
 *   SERVER                   numeric and error text
 *   RAW                     unparsed fallback, only shown by /debug
 */
public final class Message {

    public static final int CHAT = 0;
    public static final int ACTION = 1;
    public static final int NOTICE = 2;
    public static final int JOIN = 3;
    public static final int PART = 4;
    public static final int QUIT = 5;
    public static final int NICK = 6;
    public static final int KICK = 7;
    public static final int MODE = 8;
    public static final int TOPIC = 9;
    public static final int SERVER = 10;
    public static final int RAW = 11;

    public final long time;
    public final int kind;
    /** Author nick for CHAT/ACTION, subject nick for events, null otherwise. */
    public final String nick;
    /** Body text, already stripped of any prefix. */
    public final String text;
    /** Channel or nick this line belongs to (target of the view). */
    public String target;
    /** True when the local user authored it. */
    public final boolean self;
    /** True when the body mentions the local user's nick. */
    public boolean highlight;

    public Message(long time, int kind, String nick, String text, String target,
                   boolean self, boolean highlight) {
        this.time = time;
        this.kind = kind;
        this.nick = nick == null ? "" : nick;
        this.text = text == null ? "" : text;
        this.target = target == null ? "" : target;
        this.self = self;
        this.highlight = highlight;
    }

    public static Message chat(long time, String nick, String text, boolean self) {
        return new Message(time, CHAT, nick, text, "", self, false);
    }

    public static Message action(long time, String nick, String text, boolean self) {
        return new Message(time, ACTION, nick, text, "", self, false);
    }

    public static Message event(long time, int kind, String nick, String text) {
        return new Message(time, kind, nick, text, "", false, false);
    }

    public static Message server(long time, String text) {
        return new Message(time, SERVER, null, text, "", false, false);
    }

    public boolean isEvent() {
        return kind != CHAT && kind != ACTION && kind != NOTICE;
    }

    /** "HH:MM" without java.text.SimpleDateFormat, which CLDC may not expose. */
    public String timeStamp() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTime(new java.util.Date(time));
        return Str.two(c.get(java.util.Calendar.HOUR_OF_DAY)) + ":"
                + Str.two(c.get(java.util.Calendar.MINUTE));
    }

    /** Number of characters this message occupies before wrapping. */
    public int length() {
        return nick.length() + text.length() + 2;
    }

    public String toString() {
        return "[" + timeStamp() + "] <" + nick + "> " + text;
    }
}
