package com.chatterbox.proto;

import java.util.Vector;

/**
 * Byte-level IRC line framing.
 *
 * Feed it whatever the socket hands you; it returns zero or more complete
 * lines, decoded to Strings. Enforces the 512-byte RFC limit by discarding the
 * over-long line and resynchronising at the next LF (this is what real servers
 * do, and it prevents a malicious server from eating the MIDlet's 1 MB heap).
 *
 * Assumptions:
 *  - The socket delivers an unbroken TCP stream; partial lines are normal.
 *  - We accept bare LF as well as CRLF. Some gateways and test harnesses send
 *    "\n" only.
 *  - Decoding is UTF-8. If a line fails the UTF-8 sanity check we fall back to
 *    a latin-1 reading, which never throws and matches what most legacy IRC
 *    servers emit anyway.
 */
public final class LineReader {

    private byte[] buf = new byte[2048];
    private int count;
    private boolean overflow;
    private int overflowBytes;
    /** Sticky: stays set until clearOverflow(), so callers can detect a desync. */
    private boolean overflowSeen;

    /** Hard cap on the internal buffer, protects the heap. */
    private static final int MAX_BUF = 8192;

    public void reset() {
        count = 0;
        overflow = false;
        overflowBytes = 0;
    }

    /** True if any over-long line has been discarded since construction. */
    public boolean hadOverflow() {
        return overflowSeen;
    }

    public void clearOverflow() {
        overflowSeen = false;
        overflowBytes = 0;
    }

    public int overflowBytes() {
        return overflowBytes;
    }

    /**
     * Append bytes and return every complete line they produced.
     * @return Vector of String; empty Vector when no line completed yet.
     */
    public Vector feed(byte[] data, int off, int len) {
        Vector out = new Vector();
        if (data == null || len <= 0) {
            return out;
        }
        ensure(len);
        int i = off;
        int end = off + len;
        while (i < end) {
            byte b = data[i];
            if (b == '\n') {
                if (overflow) {
                    // The over-long line just ended; swallow it silently.
                    reset();
                    i++;
                    continue;
                }
                int lineLen = count;
                // Strip a trailing CR.
                if (lineLen > 0 && buf[lineLen - 1] == '\r') {
                    lineLen--;
                }
                out.addElement(decodeLine(lineLen));
                count = 0;
                i++;
                continue;
            }
            if (!overflow) {
                if (count < IrcLine.MAX_LINE_BYTES) {
                    buf[count++] = b;
                } else {
                    overflow = true;
                    overflowSeen = true;
                }
                if (overflow) {
                    overflowBytes = IrcLine.MAX_LINE_BYTES;
                }
            } else {
                overflowBytes++;
            }
            i++;
        }
        return out;
    }

    public Vector feed(byte[] data) {
        return feed(data, 0, data.length);
    }

    /**
     * Called when the peer closes. Returns any trailing partial line so the
     * last PRIVMSG is not lost.
     */
    public Vector flush() {
        Vector out = new Vector();
        if (count > 0 && !overflow) {
            int lineLen = count;
            if (lineLen > 0 && buf[lineLen - 1] == '\r') {
                lineLen--;
            }
            out.addElement(decodeLine(lineLen));
        }
        reset();
        return out;
    }

    private String decodeLine(int len) {
        if (len == 0) {
            return "";
        }
        if (Utf8.looksUtf8(buf, 0, len)) {
            return Utf8.decode(buf, 0, len);
        }
        return decodeLatin1(len);
    }

    /** Every byte becomes the code point of the same value. Never throws. */
    private String decodeLatin1(int len) {
        char[] c = new char[len];
        for (int i = 0; i < len; i++) {
            c[i] = (char) (buf[i] & 0xFF);
        }
        return new String(c);
    }

    private void ensure(int extra) {
        int need = count + extra;
        if (need <= buf.length) {
            return;
        }
        if (need > MAX_BUF) {
            // Should be unreachable because of the 512 cap, but never let the
            // buffer grow without bound.
            need = MAX_BUF;
            extra = need - count;
            if (extra <= 0) {
                return;
            }
        }
        int newSize = buf.length * 2;
        if (newSize < need) {
            newSize = need;
        }
        if (newSize > MAX_BUF) {
            newSize = MAX_BUF;
        }
        byte[] nb = new byte[newSize];
        System.arraycopy(buf, 0, nb, 0, count);
        buf = nb;
    }
}
