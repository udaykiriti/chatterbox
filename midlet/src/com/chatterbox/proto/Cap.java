package com.chatterbox.proto;

import java.util.Vector;

/**
 * IRCv3 CAP (Client Capabilities) negotiation.
 *
 * Lightweight implementation for the few caps we care about:
 *  - server-time      : attaches @time= tag to messages
 *  - account-track    : ACCOUNT messages for nick account changes
 *  - batch            : labeled message batches (e.g. history replay)
 *  - account-notify   : account in JOIN/PART/QUIT
 *  - extended-join    : account+realname in JOIN
 *  - invite-notify    : INVITE notifications
 *  - chghost          : CHGHOST notifications
 *  - multi-prefix     : show all prefix modes in NAMES
 *
 * Pure CLDC, no deps. The Session will call this during registration.
 */
public final class Cap {

    private Cap() {
    }

    public static final String LS = "LS";
    public static final String LIST = "LIST";
    public static final String REQ = "REQ";
    public static final String ACK = "ACK";
    public static final String NAK = "NAK";
    public static final String END = "END";
    public static final String NEW = "NEW";
    public static final String DEL = "DEL";

    /** Caps we want to request. Add more as needed. */
    private static final String[] DESIRED = {
        "server-time",
        "account-track",
        "batch",
        "account-notify",
        "extended-join",
        "invite-notify",
        "chghost",
        "multi-prefix"
    };

    /** Build CAP REQ line for all desired caps. */
    public static String reqAll() {
        StringBuffer sb = new StringBuffer("CAP REQ :");
        for (int i = 0; i < DESIRED.length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(DESIRED[i]);
        }
        return sb.toString() + "\r\n";
    }

    /** Build CAP END to finish negotiation. */
    public static String end() {
        return "CAP END\r\n";
    }

    /** Parse a CAP line from server. Returns the subcommand (ACK, NAK, LIST, etc.). */
    public static String parseSubcommand(IrcLine line) {
        String[] params = line.getParams();
        if (params == null || params.length < 2) {
            return null;
        }
        return params[1].toUpperCase();
    }

    /** Parse acknowledged caps from CAP ACK. */
    public static String[] parseAckCaps(IrcLine line) {
        String[] params = line.getParams();
        if (params == null || params.length < 3) {
            return new String[0];
        }
        // params[2] is the cap list (space-separated, may be trailing)
        String list = params.length > 3 ? params[2] + " " + params[3] : params[2];
        Vector v = com.chatterbox.core.Str.words(list);
        String[] out = new String[v.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = (String) v.elementAt(i);
        }
        return out;
    }

    /** Check if a specific cap was acknowledged. */
    public static boolean isAcked(String[] acked, String cap) {
        if (acked == null) {
            return false;
        }
        for (int i = 0; i < acked.length; i++) {
            if (cap.equals(acked[i])) {
                return true;
            }
        }
        return false;
    }

    /** Extract @time= timestamp from a message tag. Returns millis since epoch or -1. */
    public static long parseServerTime(String tags) {
        if (tags == null || !tags.startsWith("@")) {
            return -1;
        }
        // Format: @time=2024-01-15T12:34:56.789Z;other=val
        int idx = tags.indexOf("time=");
        if (idx < 0) {
            return -1;
        }
        idx += 5; // skip "time="
        int end = tags.indexOf(';', idx);
        if (end < 0) {
            end = tags.indexOf(' ', idx);
        }
        if (end < 0) {
            end = tags.length();
        }
        String ts = tags.substring(idx, end);
        return parseIso8601(ts);
    }

    /** Minimal ISO-8601 parser for server-time. Returns millis or -1. */
    private static long parseIso8601(String s) {
        // 2024-01-15T12:34:56.789Z  or  2024-01-15T12:34:56Z
        if (s.length() < 19) {
            return -1;
        }
        try {
            int year = Integer.parseInt(s.substring(0, 4));
            int month = Integer.parseInt(s.substring(5, 7)) - 1;
            int day = Integer.parseInt(s.substring(8, 10));
            int hour = Integer.parseInt(s.substring(11, 13));
            int min = Integer.parseInt(s.substring(14, 16));
            int sec = Integer.parseInt(s.substring(17, 19));
            int ms = 0;
            if (s.length() > 20 && s.charAt(19) == '.') {
                int dotEnd = s.indexOf('Z', 20);
                if (dotEnd < 0) {
                    dotEnd = s.length();
                }
                String frac = s.substring(20, dotEnd);
                // Pad/truncate to 3 digits
                while (frac.length() < 3) {
                    frac += "0";
                }
                if (frac.length() > 3) {
                    frac = frac.substring(0, 3);
                }
                ms = Integer.parseInt(frac);
            }
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.set(year, month, day, hour, min, sec);
            cal.set(java.util.Calendar.MILLISECOND, ms);
            return cal.getTimeInMillis();
        } catch (Exception e) {
            return -1;
        }
    }

    /** Parse account name from account-notify/extended-join tag. */
    public static String parseAccount(String tags) {
        if (tags == null || !tags.startsWith("@")) {
            return null;
        }
        int idx = tags.indexOf("account=");
        if (idx < 0) {
            return null;
        }
        idx += 8;
        int end = tags.indexOf(';', idx);
        if (end < 0) {
            end = tags.indexOf(' ', idx);
        }
        if (end < 0) {
            end = tags.length();
        }
        String acc = tags.substring(idx, end);
        return "*".equals(acc) ? null : acc; // * means no account
    }

    /** Parse batch label from @batch= tag. */
    public static String parseBatch(String tags) {
        if (tags == null || !tags.startsWith("@")) {
            return null;
        }
        int idx = tags.indexOf("batch=");
        if (idx < 0) {
            return null;
        }
        idx += 6;
        int end = tags.indexOf(';', idx);
        if (end < 0) {
            end = tags.indexOf(' ', idx);
        }
        if (end < 0) {
            end = tags.length();
        }
        return tags.substring(idx, end);
    }
}