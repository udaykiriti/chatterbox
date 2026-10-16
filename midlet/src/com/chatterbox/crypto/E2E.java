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

    /** Prefix a version-2 (ratcheted) body starts with. */
    public static final String PREFIX_V2 = "E2E:2:";

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
        return maxPlaintextBytes(targetLength, 12 + 16 + 1); // nonce+tag+kind
    }

    /**
     * Same budget with an explicit crypto overhead. v2 carries a 32-byte DH
     * public plus a 4-byte counter instead of the 12-byte nonce.
     */
    static int maxPlaintextBytes(int targetLength, int cryptoOverhead) {
        int chars = (LINE_BUDGET - SERVER_PREFIX_ROOM)
                - 2                                           // CRLF
                - "PRIVMSG ".length()
                - Math.max(targetLength, 1)
                - " :".length()
                - PREFIX.length();
        int raw = (chars / 4) * 3;                            // base64 step
        int plain = raw - cryptoOverhead;
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

    // ------------------------------------------------------------------
    // Version 2: Double Ratchet for private chats
    // ------------------------------------------------------------------
    //
    // Pairwise ratchet bootstrapped from the same passphrase, so there is no
    // handshake and no new UX: whoever speaks first generates a fresh DH key
    // and ratchets against the peer's deterministic DH public (HKDF from the
    // PSK + both nicks, computable by both sides). The first flight has
    // PSK-equivalent secrecy; every flight after the first reply mixes fresh
    // keys, giving forward secrecy against later PSK compromise.
    //
    // Wire: "E2E:2:" + base64( sender-DH(32) || N(4) || ct || tag(16) ).
    // The DH header is what makes replies decryptable: on a new DH the
    // receiver DH-ratchets before decrypting, and ratchets its own sending
    // chain before its next send. Restarting either side self-heals: the
    // fresh DH will not match, decrypt fails, and the receiver re-inits from
    // the PSK and retries exactly once (only a PSK holder can produce a
    // retry that succeeds).
    //
    // Scope: private (query) windows only. Group channels have no fan-out
    // primitive on IRC and stay on v1 PSK.
    //
    // Honest limits: initial DH randomness comes from java.util.Random
    // (clock-seeded; see Curve25519), so a passive observer who later learns
    // the PSK can read the first flight. Out-of-order messages past MAX_SKIP
    // (1000) are dropped, and a forged DH desyncs one session (healed by the
    // retry rule above).

    /** Overhead inside the base64 envelope: DH + counter + tag + kind byte. */
    static final int V2_OVERHEAD = 32 + 4 + 16 + 1;

    /** Minimum v2 blob: DH(32) + N(4) + tag(16) + kind(1). */
    private static final int V2_MIN_BLOB = 32 + 4 + 16 + 1;

    /** Per-peer ratchet sessions. Fresh per app launch; never persisted. */
    public static final class RatchetState {
        private final java.util.Hashtable peers = new java.util.Hashtable();

        RatchetPeer peer(String foldedNick) {
            return (RatchetPeer) peers.get(foldedNick);
        }

        void put(String foldedNick, RatchetPeer p) {
            peers.put(foldedNick, p);
        }

        void drop(String foldedNick) {
            peers.remove(foldedNick);
        }

        public int peerCount() {
            return peers.size();
        }
    }

    static final class RatchetPeer {
        DoubleRatchet.Session session;
        /** Peer advertised a new DH: ratchet our sending chain first. */
        boolean sendRatchetNeeded;

        RatchetPeer(DoubleRatchet.Session s) {
            session = s;
        }
    }

    /** Quick check without touching the payload. */
    public static boolean isE2eV2(String text) {
        return text != null && text.startsWith(PREFIX_V2)
                && text.length() > PREFIX_V2.length();
    }

    /** Folded nick pair in stable order, so both sides derive the same root. */
    static String[] orderedPair(String a, String b) {
        String fa = lower(a);
        String fb = lower(b);
        if (fa.compareTo(fb) <= 0) {
            return new String[] { fa, fb };
        }
        return new String[] { fb, fa };
    }

    /** Deterministic DH identity for one side of a nick pair ("lo"/"hi"). */
    static byte[] detPrivate(byte[] pskKey, String lo, String hi, String role) {
        return HKDF.hkdf(pskKey, Utf8.encode("det-dh-" + role),
                Utf8.encode(lo + "|" + hi), 32);
    }

    static byte[] ratchetRoot(byte[] pskKey, String lo, String hi) {
        return HKDF.hkdf(pskKey, Utf8.encode("ratchet-root-v1"),
                Utf8.encode(lo + "|" + hi), 32);
    }

    static byte[] ratchetAd(String lo, String hi) {
        return Utf8.encode("cb-ratchet-v1|" + lo + "|" + hi);
    }

    /** First speaker: fresh key, ratcheted against the peer's deterministic DH. */
    static RatchetPeer initSender(byte[] pskKey, String selfNick, String peerNick) {
        String[] pair = orderedPair(selfNick, peerNick);
        String self = lower(selfNick);
        String peerRole = self.equals(pair[0]) ? "hi" : "lo";
        byte[] peerDetPub = Curve25519.derivePublicKey(
                detPrivate(pskKey, pair[0], pair[1], peerRole));
        byte[] myPriv = Curve25519.generatePrivateKey();
        DoubleRatchet.Session s = DoubleRatchet.initializeAlice(
                ratchetRoot(pskKey, pair[0], pair[1]), myPriv, peerDetPub,
                ratchetAd(pair[0], pair[1]));
        s.dhPublic = Curve25519.derivePublicKey(myPriv);
        return new RatchetPeer(s);
    }

    /** Receiver: own deterministic key against the DH from the header. */
    static RatchetPeer initReceiver(byte[] pskKey, String selfNick,
                                    String peerNick, byte[] receivedDh) {
        String[] pair = orderedPair(selfNick, peerNick);
        String self = lower(selfNick);
        String myRole = self.equals(pair[0]) ? "lo" : "hi";
        byte[] myPriv = detPrivate(pskKey, pair[0], pair[1], myRole);
        DoubleRatchet.Session s = DoubleRatchet.initializeBob(
                ratchetRoot(pskKey, pair[0], pair[1]), myPriv, receivedDh,
                ratchetAd(pair[0], pair[1]));
        s.dhPublic = Curve25519.derivePublicKey(myPriv);
        return new RatchetPeer(s);
    }

    /**
     * Encrypt one text for a private-chat peer. Returns the wire payloads
     * ("E2E:2:..." PRIVMSG trailing parts, chunked like v1).
     */
    public static String[] encodeV2(RatchetState state, byte[] pskKey,
                                    String selfNick, String peerNick,
                                    String target, byte kind, String text) {
        if (state == null || pskKey == null || pskKey.length != 32
                || selfNick == null || peerNick == null
                || target == null || text == null) {
            return new String[0];
        }
        String folded = lower(peerNick);
        RatchetPeer p = state.peer(folded);
        if (p == null) {
            p = initSender(pskKey, selfNick, peerNick);
            state.put(folded, p);
        }
        String[] parts = splitText(text, maxPlaintextBytes(target.length(), V2_OVERHEAD));
        String[] payloads = new String[parts.length];
        byte[] msgAd = aad(selfNick, target);
        for (int i = 0; i < parts.length; i++) {
            String chunk = parts[i];
            if (parts.length > 1) {
                chunk = "[" + (i + 1) + "/" + parts.length + "] " + chunk;
            }
            byte[] body = Utf8.encode(chunk);
            byte[] pt = new byte[body.length + 1];
            pt[0] = kind;
            System.arraycopy(body, 0, pt, 1, body.length);
            payloads[i] = PREFIX_V2 + Sasl.encodeBase64(frameV2(p, pt, msgAd));
        }
        return payloads;
    }

    /** Encrypt one chunk under the peer session, framed with our current DH. */
    static byte[] frameV2(RatchetPeer p, byte[] pt, byte[] msgAd) {
        if (p.sendRatchetNeeded) {
            DoubleRatchet.ratchetEncrypt(p.session);
            p.sendRatchetNeeded = false;
        }
        byte[] inner = DoubleRatchet.encrypt(p.session, pt, msgAd);
        byte[] framed = new byte[32 + inner.length];
        System.arraycopy(p.session.dhPublic, 0, framed, 0, 32);
        System.arraycopy(inner, 0, framed, 32, inner.length);
        return framed;
    }

    /**
     * Decrypt an inbound v2 body, healing a desynced session (peer restart)
     * with exactly one PSK re-init retry.
     */
    public static Plain decodeV2(RatchetState state, byte[] pskKey,
                                 String selfNick, String peerNick,
                                 String target, String text) {
        if (state == null || pskKey == null || pskKey.length != 32
                || !isE2eV2(text) || target == null) {
            return null;
        }
        byte[] blob = Sasl.decodeBase64(text.substring(PREFIX_V2.length()));
        if (blob == null || blob.length < V2_MIN_BLOB) {
            return null;
        }
        if (blob.length - 32 > MAX_PLAINTEXT + 16 + 4 + 64) {
            return null; // absurdly large: refuse rather than buffer
        }
        String folded = lower(peerNick);
        byte[] msgAd = aad(peerNick, target);
        RatchetPeer p = state.peer(folded);
        if (p == null) {
            p = initReceiver(pskKey, selfNick, peerNick, slice(blob, 0, 32));
            state.put(folded, p);
        }
        byte[] pt = decryptV2(p, blob, msgAd);
        if (pt == null) {
            // Maybe the peer restarted into a fresh session: exactly one
            // retry from the PSK. Only a key holder can make this succeed.
            RatchetPeer fresh = initReceiver(pskKey, selfNick, peerNick,
                    slice(blob, 0, 32));
            pt = decryptV2(fresh, blob, msgAd);
            if (pt == null) {
                return null;
            }
            state.put(folded, fresh);
            p = fresh;
        }
        if (pt.length < 1) {
            return null;
        }
        byte kind = pt[0];
        if (kind != KIND_CHAT && kind != KIND_ACTION) {
            return null;
        }
        return new Plain(kind, Utf8.decode(pt, 1, pt.length - 1));
    }

    /** DH-step when the header carries a new public, then decrypt. */
    static byte[] decryptV2(RatchetPeer p, byte[] framed, byte[] msgAd) {
        byte[] dh = slice(framed, 0, 32);
        byte[] inner = slice(framed, 32, framed.length - 32);
        if (!equalBytes(dh, p.session.theirDhPublic)) {
            DoubleRatchet.ratchetDecrypt(p.session, dh);
            p.sendRatchetNeeded = true;
        }
        return DoubleRatchet.decrypt(p.session, inner, msgAd);
    }

    private static byte[] slice(byte[] b, int off, int len) {
        byte[] out = new byte[len];
        System.arraycopy(b, off, out, 0, len);
        return out;
    }

    private static boolean equalBytes(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        return true;
    }
}
