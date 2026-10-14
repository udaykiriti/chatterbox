package com.chatterbox.proto;

import java.util.Vector;

/**
 * DCC (Direct Client-to-Client) protocol support.
 *
 * Handles:
 *  - DCC SEND (file transfer)
 *  - DCC CHAT (direct encrypted chat)
 *  - Passive DCC (for firewalled clients)
 *
 * Pure CLDC, no UI deps. The Session will parse DCC offers and
 * create DccTransfer objects that the UI can present to the user.
 *
 * Note: On MIDP, ServerSocketConnection is optional. We use
 * ClientSocketConnection for outgoing, and listen for incoming
 * only if the platform supports it.
 */
public final class Dcc {

    public static final int TYPE_SEND = 0;
    public static final int TYPE_CHAT = 1;
    public static final int TYPE_PASSIVE = 2;

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_CONNECTING = 1;
    public static final int STATUS_TRANSFERRING = 2;
    public static final int STATUS_DONE = 3;
    public static final int STATUS_FAILED = 4;
    public static final int STATUS_CANCELLED = 5;

    private Dcc() {
    }

    /** Parse a CTCP DCC offer. Returns DccOffer or null if not a DCC. */
    public static DccOffer parseOffer(String ctcpBody) {
        if (ctcpBody == null) {
            return null;
        }
        java.util.Vector v = com.chatterbox.core.Str.words(ctcpBody);
        String[] parts = new String[v.size()];
        for (int i = 0; i < parts.length; i++) parts[i] = (String) v.elementAt(i);
        if (parts.length < 4) {
            return null;
        }
        String type = parts[0].toUpperCase();
        if (!type.equals("DCC")) {
            return null;
        }
        String subType = parts[1].toUpperCase();
        String filename = parts[2];
        String ipStr = parts[3];
        int port = com.chatterbox.core.Str.parseInt(parts.length > 4 ? parts[4] : "0", 0);
        long size = parts.length > 5 ? Long.parseLong(parts[5]) : 0;

        int t = TYPE_SEND;
        if (subType.equals("CHAT")) {
            t = TYPE_CHAT;
        } else if (subType.equals("SEND")) {
            t = TYPE_SEND;
        } else if (subType.equals("PASSIVE")) {
            t = TYPE_PASSIVE;
        }

        DccOffer offer = new DccOffer();
        offer.type = t;
        offer.filename = filename;
        offer.ip = parseIp(ipStr);
        offer.port = port;
        offer.size = size;
        return offer;
    }

    /** Parse "1234567890" or "192.168.1.1" into long IP. */
    private static long parseIp(String s) {
        if (s == null) {
            return 0;
        }
        // If it's already a number (some IRCds send long IP)
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            // Parse dotted quad
            String[] octets = s.split("\\.");
            if (octets.length == 4) {
                long ip = 0;
                for (int i = 0; i < 4; i++) {
                    ip = (ip << 8) | (Long.parseLong(octets[i]) & 0xFF);
                }
                return ip;
            }
        }
        return 0;
    }

    /** Format IP long as dotted quad. */
    public static String formatIp(long ip) {
        return ((ip >> 24) & 0xFF) + "." + ((ip >> 16) & 0xFF) + "."
                + ((ip >> 8) & 0xFF) + "." + (ip & 0xFF);
    }

    /** Build a DCC SEND CTCP. */
    public static String buildSend(String filename, long ip, int port, long size) {
        return "DCC SEND " + filename + " " + ip + " " + port + " " + size;
    }

    /** Build a DCC CHAT CTCP. */
    public static String buildChat(long ip, int port) {
        return "DCC CHAT chat " + ip + " " + port;
    }

    /** DCC transfer/offer object. */
    public static final class DccOffer {
        public int type;
        public String filename;
        public long ip;
        public int port;
        public long size;
        public String sender;
        public String target; // channel or nick

        public String getIpString() {
            return formatIp(ip);
        }
    }

    /** Active transfer state. */
    public static final class DccTransfer {
        public DccOffer offer;
        public int status = STATUS_PENDING;
        public long bytesDone = 0;
        public long startTime = System.currentTimeMillis();
        public String errorMessage;
        public java.io.InputStream in;
        public java.io.OutputStream out;
        public java.net.Socket socket;

        public long getSpeed() {
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed == 0) {
                return 0;
            }
            return (bytesDone * 1000L) / elapsed; // bytes/sec
        }

        public int getPercent() {
            if (offer.size <= 0) {
                return 0;
            }
            return (int) ((bytesDone * 100L) / offer.size);
        }
    }
}