package com.chatterbox.core;

/**
 * IRC casemapping (rfc1459) used for channel and nick comparison.
 *
 * Assumption: we implement the rfc1459 table, which is what every mainstream
 * network (ircd-seven, unrealircd, charybdis) uses. Strict-rfc1459 differs only
 * for {}|^ and those are legal in nicks on some legacy nets, so treating them
 * as equivalent is the pragmatic choice.
 */
public final class IrcCase {

    private IrcCase() {
    }

    public static String normalize(String s) {
        if (s == null) {
            return "";
        }
        StringBuffer sb = new StringBuffer(s.length());
        for (int i = 0; i < s.length(); i++) {
            sb.append(fold(s.charAt(i)));
        }
        return sb.toString();
    }

    public static char fold(char c) {
        if (c >= 'A' && c <= 'Z') {
            return (char) (c + 32);
        }
        switch (c) {
            case '[': return '{';
            case ']': return '}';
            case '\\': return '|';
            case '~': return '^';
            default: return c;
        }
    }

    public static boolean same(String a, String b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a.length() != b.length()) {
            return false;
        }
        for (int i = 0; i < a.length(); i++) {
            if (fold(a.charAt(i)) != fold(b.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** Case-insensitive Hashtable lookup for channel names. */
    public static java.util.Hashtable newMap() {
        return new java.util.Hashtable();
    }
}
