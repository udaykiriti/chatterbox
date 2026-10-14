package com.chatterbox.proto;

/**
 * CTCP (Client-To-Client Protocol) helpers.
 *
 * Assumptions:
 *  - \u0001 is the CTCP quote character. Some clients use \u0002 by mistake; we
 *    detect both on the way in. (Written as numeric constants rather than
 *    literal escapes so no editor or compiler encoding setting can mangle the
 *    source file.)
 *  - An ACTION body may be empty; that is still a valid /me.
 */
public final class Ctcp {

    public static final char DEL = (char) 0x01;
    public static final char ALT_DEL = (char) 0x02;

    public static final String ACTION = "ACTION";
    public static final String VERSION = "VERSION";
    public static final String PING = "PING";
    public static final String TIME = "TIME";
    public static final String CLIENTINFO = "CLIENTINFO";

    private Ctcp() {
    }

    private static boolean isQuote(char c) {
        return c == DEL || c == ALT_DEL;
    }

    public static boolean isCtcp(String text) {
        if (text == null || text.length() < 2) {
            return false;
        }
        if (!isQuote(text.charAt(0))) {
            return false;
        }
        return isQuote(text.charAt(text.length() - 1));
    }

    public static String wrap(String tag, String body) {
        return DEL + tag + " " + (body == null ? "" : body) + DEL;
    }

    /**
     * @return the CTCP tag, or null if the text is not CTCP.
     */
    public static String tag(String text) {
        if (!isCtcp(text)) {
            return null;
        }
        String body = inner(text);
        int sp = body.indexOf(' ');
        if (sp < 0) {
            return body.toUpperCase();
        }
        return body.substring(0, sp).toUpperCase();
    }

    /**
     * @return the payload after the tag, or "" if absent.
     */
    public static String body(String text) {
        if (!isCtcp(text)) {
            return "";
        }
        String body = inner(text);
        int sp = body.indexOf(' ');
        if (sp < 0) {
            return "";
        }
        return body.substring(sp + 1);
    }

    private static String inner(String text) {
        return text.substring(1, text.length() - 1);
    }

    public static boolean isAction(String text) {
        return ACTION.equals(tag(text));
    }

    public static boolean isVersionQuery(String text) {
        return VERSION.equals(tag(text));
    }

    /** Strip CTCP framing if present, otherwise return the input unchanged. */
    public static String unwrap(String text) {
        if (!isCtcp(text)) {
            return text == null ? "" : text;
        }
        return body(text);
    }
}
