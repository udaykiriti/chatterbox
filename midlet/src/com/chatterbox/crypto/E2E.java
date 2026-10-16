package com.chatterbox.crypto;

import java.util.Vector;

import com.chatterbox.proto.Sasl;
import com.chatterbox.proto.Utf8;

/**
 * Pre-shared-key end-to-end encryption for IRC messages.
 *
 * Design goals: two or more phones hold the same passphrase; every text is
 * ChaCha20-Poly1305-encrypted under a key derived from it, and travels over
 * IRC as an ordinary PRIVMSG with an "E2E:1:<base64>" body. The IRC relay
 * (server or gateway) still sees who sent what and when, but not the text.
 *
 * Authenticated encryption:
 *  - Key: HKDF-SHA256(passphrase), fixed salt/info so all peers agree.
 *    Passphrases are never used directly as keying material.
 *  - Nonce: first 12 bytes of SHA-256(key || sender || per-message counter).
 *    The nonce travels in the clear with each message, so receivers need no
 *    counter state; uniqueness is guaranteed per key because the pair
 *    (sender nick, counter) is unique — the counter is seeded from the epoch
 *    at construction and increments per message, so a nick can never repeat a
 *    (counter) value across reconnects or device restarts.
 *  - AAD: folded sender nick + folded target, binding a ciphertext to exactly
 *    the conversation it was produced in.
 *
 * Wire format: "E2E:1:" + base64( 12-byte nonce || ciphertext || 16-byte tag ).
 * The leading "1" is a format version, not a signal property.
 *
 * Note on casemapping: this file must not depend on com.chatterbox.core, so
 * lower() duplicates the rfc1459 folding used by IrcCase.
 *
 * Known limitations (documented in README/PROJECT): no forward secrecy, no
 * future secrecy, no key renegotiation — sharing the passphrase means every
 * holder can read the whole history. The DoubleRatchet library in this
 * package is the roadmap for that; this has to be reliable first.
 */
public final class E2E {

    /** Prefix an encrypted body starts with. The digit is the wire version. */
    public static final String PREFIX = "E2E:1:";

    /** First plaintext byte: normal message. */
    public static final byte KIND_CHAT = 'M';
    /** First plaintext byte: /me action. */
    public static final byte KIND_ACTION = 'A';

    /** Room for the ":nick!user@host" prefix an ircd adds before our line. */
    private static final int SERVER_PREFIX_ROOM = 96;
    /** Total PRIVMSG line budget in UTF-8 bytes (RFC 2812), CRLF included. */
    private static final int LINE_BUDGET = 512;

    /** Upper bound on decoded plaintext, so a peer cannot make us allocate freely. */
    private static final int MAX_PLAINTEXT = 4096;

    private final byte[] key;
    private final String selfNick;
    private long counter;

    /**
     * @param passphrase shared secret; empty or null disables encode()
     * @param selfNick   our nick, part of every nonce
     */
    public E2E(String passphrase, String selfNick) {
        this.key = deriveKey(passphrase == null ? "" : passphrase);
        this.selfNick = selfNick == null || selfNick.length() == 0 ? "?" : selfNick;
        // Seeded from wall-clock so a fresh session cannot reuse a counter the
        // previous session (or another device with the same nick) already sent.
        this.counter = System.currentTimeMillis() / 1000L;
    }

    /** Derive the 32-byte traffic key from a passphrase. */
    public static byte[] deriveKey(String passphrase) {
        byte[] ikm = Utf8.encode(passphrase == null ? "" : passphrase);
        return HKDF.hkdf(Utf8.encode("chatterbox-e2e-salt-v1"), ikm,
                Utf8.encode("chatterbox-e2e-key"), 32);
    }

    private static final char[] HEX = {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        'A', 'B', 'C', 'D', 'E', 'F'
    };

    /**
     * Short human-verifiable identity of a derived key: SHA-256 of the key,
     * first 8 bytes folded into four hex groups ("12AB CD34 EF56 78CD").
     * Both peers must see identical groups before trusting a channel; the
     * display never reveals usable key material by itself. Empty string for
     * null/empty input.
     */
    public static String fingerprint(byte[] key) {
        if (key == null || key.length == 0) {
            return "";
        }
        byte[] h = SHA256.hash(key);
        StringBuffer sb = new StringBuffer(23);
        for (int i = 0; i < 4; i++) {
            int v = ((h[i * 2] & 0xFF) << 8) | (h[i * 2 + 1] & 0xFF);
            if (i > 0) {
                sb.append(' ');
            }
            for (int s = 12; s >= 0; s -= 4) {
                sb.append(HEX[(v >>> s) & 0xF]);
            }
        }
        return sb.toString();
    }

    /** True when the key material is usable. */
    public boolean hasKey() {
        return key != null;
    }

    // ------------------------------------------------------------------
    // Encode
    // ------------------------------------------------------------------

    /**
     * Encrypt one text for one target. Returns the wire payloads to send as
     * PRIVMSG trailing parts (one per chunk, most messages are one).
     *
     * @param target channel or nick the message is going to
     * @param kind   KIND_CHAT or KIND_ACTION
     * @param text   plain UTF-8 text
     */
    public String[] encode(String target, byte kind, String text) {
        if (target == null || text == null || key == null || key.length != 32) {
            return new String[0];
        }
        String[] parts = splitText(text, maxPlaintextBytes(target.length()));
        String[] payloads = new String[parts.length];
        byte[] aad = aad(selfNick, target);
        for (int i = 0; i < parts.length; i++) {
            String chunk = parts[i];
            if (parts.length > 1) {
                chunk = "[" + (i + 1) + "/" + parts.length + "] " + chunk;
            }
            byte[] body = Utf8.encode(chunk);
            byte[] pt = new byte[body.length + 1];
            pt[0] = kind;
            System.arraycopy(body, 0, pt, 1, body.length);

            long seq = counter++;
            byte[] nonce = deriveNonce(key, selfNick, seq);
            byte[] ct = new byte[pt.length + 16];
            ChaCha20Poly1305.encrypt(key, nonce, aad, pt, ct);

            byte[] blob = new byte[12 + ct.length];
            System.arraycopy(nonce, 0, blob, 0, 12);
            System.arraycopy(ct, 0, blob, 12, ct.length);
            payloads[i] = PREFIX + Sasl.encodeBase64(blob);
        }
        return payloads;
    }

    /**
     * Base64 expands 3 bytes to 4 characters; subtract the nonce, tag, kind
     * byte and the fixed framing to stay under the 512-byte line budget even
     * with a long server prefix.
     */
    static int maxPlaintextBytes(int targetLength) {
        int chars = (LINE_BUDGET - SERVER_PREFIX_ROOM)
                - 2                                           // CRLF
                - "PRIVMSG ".length()
                - Math.max(targetLength, 1)
                - " :".length()
                - PREFIX.length();
        int raw = (chars / 4) * 3;                            // base64 step
        int plain = raw - 12 - 16 - 1;                        // nonce+tag+kind
        return Math.max(plain, 16);                           // sanity floor
    }

    // ------------------------------------------------------------------
    // Decode
    // ------------------------------------------------------------------

    /** Result of a successful decode. */
    public static final class Plain {
        public final byte kind;
        public final String text;

        Plain(byte kind, String text) {
            this.kind = kind;
            this.text = text;
        }
    }

    /** Quick check without touching the payload. */
    public static boolean isE2e(String text) {
        return text != null && text.startsWith(PREFIX) && text.length() > PREFIX.length();
    }

    /**
     * Decrypt an inbound body.
     *
     * @param key    traffic key of the sender, or null when E2E is off
     * @param from   sender nick as the server relayed it
     * @param target channel or nick the message was addressed to
     * @param text   the PRIVMSG trailing (the "E2E:1:..." body)
     * @return the plaintext, or null when it is not E2E, is malformed, was
     *         tampered with, or was not encrypted under this key.
     */
    public static Plain decode(byte[] key, String from, String target, String text) {
        if (key == null || key.length != 32 || !isE2e(text) || target == null) {
            return null;
        }
        byte[] blob = Sasl.decodeBase64(text.substring(PREFIX.length()));
        if (blob == null || blob.length < 12 + 16 + 1) {
            return null;
        }
        if (blob.length - 12 > MAX_PLAINTEXT + 16) {
            return null; // absurdly large: refuse rather than buffer
        }
        byte[] nonce = new byte[12];
        System.arraycopy(blob, 0, nonce, 0, 12);

        byte[] aad = aad(from, target);
        byte[] plain = new byte[blob.length - 12];
        int n = ChaCha20Poly1305.decrypt(key, nonce, aad, blob, 12, blob.length - 12, plain);
        if (n < 1) {
            return null;
        }
        byte kind = plain[0];
        if (kind != KIND_CHAT && kind != KIND_ACTION) {
            return null;
        }
        return new Plain(kind, Utf8.decode(plain, 1, n - 1));
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private static byte[] aad(String from, String target) {
        return Utf8.encode("cb-e2e-v1|" + lower(from) + "|" + lower(target));
    }

    /**
     * RFC 1459 casemapping, folded so that "#Test" typed by the sender and the
     * canonical form the receiver sees are the same AAD. Duplicated from
     * IrcCase on purpose: this package must not depend on core.
     */
    private static String lower(String s) {
        if (s == null) {
            s = "";
        }
        StringBuffer sb = new StringBuffer(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                c = (char) (c + 32);
            } else if (c == '[') {
                c = '{';
            } else if (c == ']') {
                c = '}';
            } else if (c == '\\') {
                c = '|';
            } else if (c == '~') {
                c = '^';
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Nonce = first 12 bytes of SHA-256 over a length-prefixed
     * (key || sender || counter) so the concatenation is unambiguous.
     */
    static byte[] deriveNonce(byte[] key, String senderNick, long seq) {
        byte[] nick = Utf8.encode(senderNick == null ? "" : senderNick);
        byte[] mix = new byte[1 + key.length + 1 + nick.length + 8];
        int p = 0;
        mix[p++] = (byte) key.length;
        System.arraycopy(key, 0, mix, p, key.length);
        p += key.length;
        mix[p++] = (byte) nick.length;
        System.arraycopy(nick, 0, mix, p, nick.length);
        p += nick.length;
        for (int i = 0; i < 8; i++) {
            mix[p + i] = (byte) (seq >>> (56 - 8 * i));
        }
        byte[] hash = SHA256.hash(mix);
        byte[] nonce = new byte[12];
        System.arraycopy(hash, 0, nonce, 0, 12);
        return nonce;
    }

    /** Split into chunks at UTF-8 character boundaries. */
    static String[] splitText(String text, int maxBytes) {
        Vector parts = new Vector();
        int start = 0;
        while (start < text.length()) {
            int bytes = 0;
            int end = start;
            while (end < text.length()) {
                char c = text.charAt(end);
                int cp = c;
                int step = 1;
                if (c >= 0xD800 && c <= 0xDBFF && end + 1 < text.length()) {
                    char lo = text.charAt(end + 1);
                    if (lo >= 0xDC00 && lo <= 0xDFFF) {
                        cp = 0x10000 + ((c - 0xD800) << 10) + (lo - 0xDC00);
                        step = 2;
                    }
                }
                int len = cp < 0x80 ? 1 : cp < 0x800 ? 2 : cp < 0x10000 ? 3 : 4;
                if (bytes + len > maxBytes) {
                    break;
                }
                bytes += len;
                end += step;
            }
            if (end == start) {
                // A single character wider than the budget: cut it anyway
                // rather than spin.
                end = start + 1;
            }
            parts.addElement(text.substring(start, end));
            start = end;
        }
        if (parts.size() == 0) {
            parts.addElement("");
        }
        String[] out = new String[parts.size()];
        for (int i = 0; i < parts.size(); i++) {
            out[i] = (String) parts.elementAt(i);
        }
        return out;
    }
}
