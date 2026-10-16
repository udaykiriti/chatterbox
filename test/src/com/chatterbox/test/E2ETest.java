package com.chatterbox.test;

import java.util.Vector;

import com.chatterbox.core.ChannelState;
import com.chatterbox.core.CommandProcessor;
import com.chatterbox.core.IrcEventListener;
import com.chatterbox.core.Message;
import com.chatterbox.core.Sender;
import com.chatterbox.core.ServerState;
import com.chatterbox.core.Session;
import com.chatterbox.crypto.E2E;

import junit.framework.TestCase;

/**
 * Tests for the pre-shared-key E2E layer: the codec itself, its failure modes,
 * and the integration points into Session and CommandProcessor.
 */
public final class E2ETest extends TestCase {

    private static final String PASSPHRASE = "correct horse battery staple";

    // ---- Key derivation ----

    public void testDeriveKeyDeterministic() {
        byte[] a = E2E.deriveKey(PASSPHRASE);
        byte[] b = E2E.deriveKey(PASSPHRASE);
        assertEquals("derived key is 32 bytes", 32, a.length);
        assertArrayEquals("same passphrase derives the same key", a, b);
    }

    public void testDeriveKeyDiffers() {
        byte[] a = E2E.deriveKey("one");
        byte[] b = E2E.deriveKey("two");
        assertFalse("different passphrases must derive different keys",
                toString(a).equals(toString(b)));
    }

    // ---- Codec round trip ----

    public void testRoundTripChat() {
        E2E out = new E2E(PASSPHRASE, "alice");
        byte[] key = E2E.deriveKey(PASSPHRASE);
        String payload = out.encode("#chan", E2E.KIND_CHAT, "hello world")[0];

        assertTrue("payload carries the E2E:1: prefix", E2E.isE2e(payload));
        E2E.Plain p = E2E.decode(key, "alice", "#chan", payload);
        assertNotNull(p);
        assertEquals("kind must be chat", E2E.KIND_CHAT, p.kind);
        assertEquals("text round trips", "hello world", p.text);
    }

    public void testRoundTripAction() {
        E2E out = new E2E(PASSPHRASE, "alice");
        byte[] key = E2E.deriveKey(PASSPHRASE);
        String payload = out.encode("#chan", E2E.KIND_ACTION, "waves")[0];
        E2E.Plain p = E2E.decode(key, "alice", "#chan", payload);
        assertNotNull(p);
        assertEquals(E2E.KIND_ACTION, p.kind);
        assertEquals("waves", p.text);
    }

    public void testRoundTripUnicode() {
        E2E out = new E2E(PASSPHRASE, "alice");
        byte[] key = E2E.deriveKey(PASSPHRASE);
        String text = "Привет #chat — 世界! emojiDP: \uD83C\uDF89";
        String payload = out.encode("priv-chat", E2E.KIND_CHAT, text)[0];
        E2E.Plain p = E2E.decode(key, "alice", "priv-chat", payload);
        assertNotNull(p);
        assertEquals("surrogate pairs and non-ASCII round trip", text, p.text);
    }

    public void testPayloadFitsIrcLine() {
        E2E out = new E2E(PASSPHRASE, "alice");
        String text = "";
        for (int i = 0; i < 40; i++) {
            text += "word ";
        }
        String[] payloads = out.encode("#test", E2E.KIND_CHAT, text);
        String payload = payloads[payloads.length - 1];
        int line = payload.length() + "PRIVMSG ".length() + "#test".length()
                + " :".length() + 2 + 90; // 90 = room for the server prefix
        assertTrue("whole line " + line + " must fit the 512 budget", line <= 512);
    }

    public void testNoncesAreUniquePerMessage() {
        E2E out = new E2E(PASSPHRASE, "alice");
        String a = out.encode("#chan", E2E.KIND_CHAT, "first")[0];
        String b = out.encode("#chan", E2E.KIND_CHAT, "second")[0];
        assertFalse("two encodes under one key must not repeat a nonce", a.equals(b));
    }

    // ---- Failure modes ----

    public void testRejectsWrongKey() {
        E2E out = new E2E(PASSPHRASE, "alice");
        String payload = out.encode("#chan", E2E.KIND_CHAT, "secret")[0];
        assertNull("passphrase \"three\" must not decrypt",
                E2E.decode(E2E.deriveKey("different"), "bob", "#chan", payload));
    }

    public void testRejectsTamperedPayload() {
        E2E out = new E2E(PASSPHRASE, "alice");
        String payload = out.encode("#chan", E2E.KIND_CHAT, "tamperme")[0];
        byte[] blob = com.chatterbox.proto.Sasl.decodeBase64(payload.substring(E2E.PREFIX.length()));
        blob[13] ^= 0x40;                       // inside the ciphertext
        String damaged = E2E.PREFIX + com.chatterbox.proto.Sasl.encodeBase64(blob);
        assertNull("flipped bits must not decrypt",
                E2E.decode(E2E.deriveKey(PASSPHRASE), "bob", "#chan", damaged));
    }

    public void testRejectsWrongTargetBinding() {
        E2E out = new E2E(PASSPHRASE, "alice");
        String payload = out.encode("#chan", E2E.KIND_CHAT, "bound")[0];
        assertNull("ciphertext bound to #chan cannot be replayed to #other",
                E2E.decode(E2E.deriveKey(PASSPHRASE), "bob", "#other", payload));
    }

    public void testRejectsMalformedPayloads() {
        byte[] key = E2E.deriveKey(PASSPHRASE);
        assertNull(E2E.decode(key, "bob", "#chan", "E2E:1:!!!"));
        assertNull(E2E.decode(key, "bob", "#chan", "E2E:1:QUJD"));        // too short
        assertNull(E2E.decode(key, "bob", "#chan", "E2E:2:AAAAAAAA"));    // unknown version
        assertNull(E2E.decode(null, "bob", "#chan", "E2E:1:AAAAAAAA"));   // no key
    }

    public void testPlainBodyIsNotE2e() {
        assertFalse(E2E.isE2e("normal message"));
        assertFalse(E2E.isE2e(null));
        assertFalse(E2E.isE2e("E2E:1:"));
    }

    public void testOversizedPayloadRejected() {
        byte[] key = E2E.deriveKey(PASSPHRASE);
        StringBuffer junk = new StringBuffer(E2E.PREFIX);
        for (int i = 0; i < 4000; i++) junk.append('A');  // ~3 KB of fake data
        assertNull(E2E.decode(key, "bob", "#chan", junk.toString()));
    }

    // ---- Chunking ----

    public void testLongMessageSplits() {
        E2E out = new E2E(PASSPHRASE, "alice");
        byte[] key = E2E.deriveKey(PASSPHRASE);
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < 50; i++) {
            sb.append("hello from the long-message chunking test ");
        }
        String[] parts = out.encode("#chan", E2E.KIND_CHAT, sb.toString());
        assertTrue("long text must split into several payloads", parts.length > 1);

        StringBuffer rebuilt = new StringBuffer();
        for (int i = 0; i < parts.length; i++) {
            E2E.Plain p = E2E.decode(key, "alice", "#chan", parts[i]);
            assertNotNull("part " + i + " must decrypt", p);
            rebuilt.append(stripPrefix(p.text));
        }
        assertEquals("reconstruction matches the original", sb.toString(), rebuilt.toString());
    }

    /** "[i/n] " is a display hint in the plaintext; the loop just strips it. */
    private static String stripPrefix(String s) {
        if (s.startsWith("[")) {
            int sp = s.indexOf(' ', 2);
            return s.substring(sp + 1);
        }
        return s;
    }

    // ---- Base64 (Sasl.decodeBase64, now real) ----

    public void testBase64RoundTrip() {
        for (int len = 0; len <= 66; len++) {   // covers the 0..3 byte tails twice
            byte[] data = new byte[len];
            for (int i = 0; i < len; i++) {
                data[i] = (byte) ((i * 77 + len) & 0xFF);
            }
            String enc = com.chatterbox.proto.Sasl.encodeBase64(data);
            byte[] back = com.chatterbox.proto.Sasl.decodeBase64(enc);
            assertNotNull("decode must accept encoder output for len=" + len, back);
            assertArrayEquals("length " + len + " round trips", data, back);
        }
    }

    public void testBase64PaddingStyles() {
        byte[] one = new byte[] { 'A' };
        byte[] two = new byte[] { 'A', 'B' };
        byte[] three = new byte[] { 'A', 'B', 'C' };
        assertTrue(com.chatterbox.proto.Sasl.encodeBase64(one).endsWith("=="));
        assertTrue(com.chatterbox.proto.Sasl.encodeBase64(two).endsWith("="));
        assertEquals("QUJD", com.chatterbox.proto.Sasl.encodeBase64(three));
        // Unpadded (padding omitted by the peer) still decodes.
        assertEquals("A", new String(com.chatterbox.proto.Sasl.decodeBase64("QQ")));
        assertEquals("AB", new String(com.chatterbox.proto.Sasl.decodeBase64("QUI")));
        // Whitespace is ignored.
        assertNotNull(com.chatterbox.proto.Sasl.decodeBase64("QQ =="));
        // Hostile input is rejected, never throws.
        assertNull("garbage must be rejected", com.chatterbox.proto.Sasl.decodeBase64("$"));
        assertNull("a lone '=' must be rejected", com.chatterbox.proto.Sasl.decodeBase64("=QQ="));
        assertNull("single base64 char is impossible", com.chatterbox.proto.Sasl.decodeBase64("A"));
    }

    // ---- Session integration (outbound) ----

    public void testSessionEncryptsOutbound() {
        ServerState s = newServer(true);
        RecordingSender sender = new RecordingSender();
        Session sess = new Session(s, sender);

        assertTrue(sess.isE2eActive());
        String[] payloads = sess.e2eEncode("#chan", E2E.KIND_CHAT, "wire hi");
        assertEquals(1, payloads.length);
        assertTrue("wire payload looks encrypted", E2E.isE2e(payloads[0]));
        assertFalse("plaintext in payload", payloads[0].contains("wire hi"));

        // ...and the full wire line round-trips through a peer.
        byte[] key = E2E.deriveKey(PASSPHRASE);
        E2E.Plain p = E2E.decode(key, "me", "#chan", payloads[0]);
        assertNotNull(p);
        assertEquals("wire hi", p.text);
    }

    public void testSessionOffWhenDisabled() {
        ServerState s = newServer(false);
        Session sess = new Session(s, new RecordingSender());
        assertFalse(sess.isE2eActive());
        assertEquals(0, sess.e2eEncode("#chan", E2E.KIND_CHAT, "plain here").length);
    }

    public void testSessionOffWithEmptyKey() {
        ServerState s = newServer(true);
        s.setE2eKey("");
        Session sess = new Session(s, new RecordingSender());
        assertFalse(sess.isE2eActive());
        assertEquals(0, sess.e2eEncode("#chan", E2E.KIND_CHAT, "x").length);
    }

    // ---- Session integration (inbound) ----

    public void testSessionDecryptsInbound() {
        ServerState s = newServer(true);
        CaptureListener listener = new CaptureListener();
        Session sess = new Session(s, new RecordingSender());
        sess.setListener(listener);

        E2E bob = new E2E(PASSPHRASE, "bob");
        String payload = bob.encode("#chan", E2E.KIND_CHAT, "hi from bob")[0];
        sess.feedLine(":bob!bob@example.com PRIVMSG #chan :" + payload);

        assertNotNull(listener.lastMessage);
        assertEquals("bob", listener.lastMessage.nick);
        assertEquals("hi from bob", listener.lastMessage.text);
        assertEquals("#chan", listener.lastMessage.target);
    }

    public void testSessionDecryptsInboundAction() {
        ServerState s = newServer(true);
        CaptureListener listener = new CaptureListener();
        Session sess = new Session(s, new RecordingSender());
        sess.setListener(listener);

        E2E bob = new E2E(PASSPHRASE, "bob");
        String payload = bob.encode("#chan", E2E.KIND_ACTION, "waves at #chan")[0];
        sess.feedLine(":bob!bob@example.com PRIVMSG #chan :" + payload);

        assertNotNull(listener.lastMessage);
        assertEquals(Message.ACTION, listener.lastMessage.kind);
        assertEquals("waves at #chan", listener.lastMessage.text);
    }

    public void testSessionPlaceholderWithoutKey() {
        ServerState s = new ServerState("t", "irc.test", 6697, true);
        s.setNick("me");
        s.setSelfNick("me");
        s.setE2eOn(false);
        s.setE2eKey("");
        CaptureListener listener = new CaptureListener();
        Session sess = new Session(s, new RecordingSender());
        sess.setListener(listener);

        E2E bob = new E2E(PASSPHRASE, "bob");
        String payload = bob.encode("#chan", E2E.KIND_CHAT, "you cannot read this")[0];
        sess.feedLine(":bob!bob@example.com PRIVMSG #chan :" + payload);

        assertNotNull(listener.lastMessage);
        assertTrue("placeholder expected, got: " + listener.lastMessage.text,
                listener.lastMessage.text.startsWith("[E2E]"));
    }

    public void testSessionUndecryptableShowsPlaceholder() {
        ServerState s = newServer(true);
        CaptureListener listener = new CaptureListener();
        Session sess = new Session(s, new RecordingSender());
        sess.setListener(listener);

        E2E eve = new E2E("totally different secret", "bob");
        String payload = eve.encode("#chan", E2E.KIND_CHAT, "wrong key")[0];
        sess.feedLine(":bob!bob@example.com PRIVMSG #chan :" + payload);

        assertNotNull(listener.lastMessage);
        assertTrue(listener.lastMessage.text.startsWith("[E2E]"));
    }

    // ---- Session v2 (ratcheted private chat) ----

    private static ServerState newPeerServer(String nick) {
        ServerState s = new ServerState("t", "irc.test", 6697, true);
        s.setNick(nick);
        s.setSelfNick(nick);
        s.setE2eOn(true);
        s.setE2eKey(PASSPHRASE);
        return s;
    }

    public void testSessionV2PrivateChatRoundTrip() {
        ServerState sa = newPeerServer("alice");
        ServerState sb = newPeerServer("bob");
        CaptureListener la = new CaptureListener();
        CaptureListener lb = new CaptureListener();
        Session alice = new Session(sa, new RecordingSender());
        Session bob = new Session(sb, new RecordingSender());
        alice.setListener(la);
        bob.setListener(lb);

        // Alice -> Bob: v2 envelope on the wire...
        String[] out = alice.e2eEncode("bob", E2E.KIND_CHAT, "hello ratchet");
        assertEquals(1, out.length);
        assertTrue(out[0].startsWith("E2E:2:"));
        bob.feedLine(":alice!a@h PRIVMSG bob :" + out[0]);
        assertNotNull(lb.lastMessage);
        assertEquals("hello ratchet", lb.lastMessage.text);

        // ...Bob -> Alice exercises the DH step the other way.
        String[] back = bob.e2eEncode("alice", E2E.KIND_CHAT, "hi back");
        alice.feedLine(":bob!b@h PRIVMSG alice :" + back[0]);
        assertNotNull(la.lastMessage);
        assertEquals("hi back", la.lastMessage.text);
    }

    public void testSessionV2ChannelStaysV1() {
        ServerState sa = newPeerServer("alice");
        Session alice = new Session(sa, new RecordingSender());
        String[] out = alice.e2eEncode("#chan", E2E.KIND_CHAT, "group chat");
        assertEquals(1, out.length);
        assertTrue("channels stay on v1 PSK: " + out[0],
                out[0].startsWith("E2E:1:"));
    }

    public void testSessionV2SelfEchoDropped() {
        ServerState sa = newPeerServer("alice");
        CaptureListener la = new CaptureListener();
        Session alice = new Session(sa, new RecordingSender());
        alice.setListener(la);
        String[] out = alice.e2eEncode("bob", E2E.KIND_CHAT, "echo me");
        alice.feedLine(":alice!a@h PRIVMSG alice :" + out[0]);
        assertNull("own v2 echo must not duplicate the local echo",
                la.lastMessage);
    }

    public void testSessionV2HealsAfterPeerRestart() {
        ServerState sa = newPeerServer("alice");
        Session alice = new Session(sa, new RecordingSender());
        CaptureListener lb = new CaptureListener();
        Session bob = new Session(newPeerServer("bob"), new RecordingSender());
        bob.setListener(lb);

        String[] p1 = alice.e2eEncode("bob", E2E.KIND_CHAT, "one");
        bob.feedLine(":alice!a@h PRIVMSG bob :" + p1[0]);
        assertEquals("one", lb.lastMessage.text);

        // Bob restarts: fresh Session, same passphrase.
        lb.lastMessage = null;
        bob = new Session(newPeerServer("bob"), new RecordingSender());
        bob.setListener(lb);
        String[] p2 = alice.e2eEncode("bob", E2E.KIND_CHAT, "two");
        bob.feedLine(":alice!a@h PRIVMSG bob :" + p2[0]);
        assertNotNull("restart must self-heal", lb.lastMessage);
        assertEquals("two", lb.lastMessage.text);
    }

    // ---- CommandProcessor: uses sendE2e when active ----

    public void testCommandProcessorEncryptsSendLine() {
        ServerState s = newServer(true);
        RecordingSender sender = new RecordingSender();
        Session sess = new Session(s, sender);
        CommandProcessor cp = new CommandProcessor(s, sess, sender);
        s.setActive(s.window("#chan", ChannelState.TYPE_CHANNEL));

        cp.execute(s.getActive(), "let us encrypt this");

        String sent = (String) sender.lines.elementAt(sender.lines.size() - 1);
        assertTrue(sent.contains("E2E:1:"));
        assertFalse("plaintext must not go out", sent.contains("let us encrypt this"));
        // Local echo stays in plaintext so the user can read it back.
        boolean sawEcho = false;
        Message[] msgs = s.window("#chan").getRing().snapshot();
        for (int i = 0; i < msgs.length; i++) {
            if (msgs[i].text.indexOf("let us encrypt this") >= 0) {
                sawEcho = true;
            }
        }
        assertTrue("local echo shows what was typed", sawEcho);
    }

    // ---- Fakes & helpers ----

    private static ServerState newServer(boolean e2eOn) {
        ServerState s = new ServerState("t", "irc.test", 6697, true);
        s.setNick("me");
        s.setSelfNick("me");
        s.setE2eOn(e2eOn);
        s.setE2eKey(PASSPHRASE);
        return s;
    }

    private static final class RecordingSender implements Sender {
        final Vector lines = new Vector();

        public void send(String line) {
            lines.addElement(line);
        }

        public boolean isConnected() {
            return true;
        }

        public void close() {
        }
    }

    private static final class CaptureListener implements IrcEventListener {
        Message lastMessage;

        public void onMessage(ServerState server, ChannelState window, Message message) {
            lastMessage = message;
        }

        public void onStateChanged(ServerState server, int newState, String statusText) {
        }

        public void onNickInUse(ServerState server, String attempted, String chosen) {
        }

        public void onWindowOpened(ServerState server, ChannelState window) {
        }

        public void onWindowClosed(ServerState server, ChannelState window) {
        }

        public void onTopicChanged(ServerState server, ChannelState channel, String topic) {
        }

        public void onError(ServerState server, String reason) {
        }

        public void onAlert(ServerState server, ChannelState window, boolean isMention, boolean isPrivate) {
        }

        public void onLag(ServerState server, long millis) {
        }
    }

    // ---- Byte helpers (no assertArrayEquals in the shim) ----

    private static void assertArrayEquals(String msg, byte[] a, byte[] b) {
        assertEquals(msg + " (length)", a.length, b.length);
        for (int i = 0; i < a.length; i++) {
            assertEquals(msg, a[i], b[i]);
        }
    }

    private static String toString(byte[] b) {
        return toHex(b);
    }

    private static String toHex(byte[] b) {
        StringBuffer sb = new StringBuffer(b.length * 2);
        for (int i = 0; i < b.length; i++) {
            int v = b[i] & 0xFF;
            if (v < 16) sb.append('0');
            sb.append(Integer.toHexString(v));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Key fingerprint (passphrase verification via /e2e)
    // ------------------------------------------------------------------

    /** Same derived key always yields the same 4-group fingerprint. */
    public void testFingerprintDeterministic() {
        byte[] k = E2E.deriveKey(PASSPHRASE);
        String f1 = E2E.fingerprint(k);
        String f2 = E2E.fingerprint(E2E.deriveKey(PASSPHRASE));
        assertNotNull(f1);
        assertEquals("same passphrase -> same fingerprint", f1, f2);
    }

    /** Different keys yield different fingerprints; empty input is empty. */
    public void testFingerprintDistinctAndDefaults() {
        byte[] k1 = E2E.deriveKey("passone");
        byte[] k2 = E2E.deriveKey("passtwo");
        assertTrue("distinct keys differ", !E2E.fingerprint(k1).equals(E2E.fingerprint(k2)));
        assertEquals("", E2E.fingerprint(new byte[0]));
        assertEquals("", E2E.fingerprint(null));
    }

    /** Format: exactly four space-separated uppercase hex groups of four. */
    public void testFingerprintFormat() {
        String f = E2E.fingerprint(E2E.deriveKey(PASSPHRASE));
        assertEquals("four hex groups", 4, splitGroups(f).length);
        for (int i = 0; i < splitGroups(f).length; i++) {
            assertEquals("group length 4", 4, splitGroups(f)[i].length());
            assertTrue("uppercase hex", isUpperHex(splitGroups(f)[i]));
        }
    }

    /** Session exposes the fingerprint of the active key, "" when off. */
    public void testSessionFingerprintFollowsKey() {
        ServerState s = new ServerState("t", "irc.test", 6697, true);
        s.setNick("me");
        s.setSelfNick("me");
        s.setE2eOn(true);
        s.setE2eKey(PASSPHRASE);
        Session sess = new Session(s, new RecordingSender());
        String on = sess.e2eFingerprint();
        assertEquals("same as raw fingerprint of derived key",
                on, E2E.fingerprint(E2E.deriveKey(PASSPHRASE)));

        s.setE2eKey("");
        assertEquals("off -> empty", "", sess.e2eFingerprint());
    }

    private static String[] splitGroups(String f) {
        Vector parts = new Vector();
        int from = 0;
        while (from <= f.length()) {
            int idx = f.indexOf(' ', from);
            parts.addElement(idx < 0 ? f.substring(from) : f.substring(from, idx));
            if (idx < 0) break;
            from = idx + 1;
        }
        String[] out = new String[parts.size()];
        for (int i = 0; i < out.length; i++) out[i] = (String) parts.elementAt(i);
        return out;
    }

    private static boolean isUpperHex(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F'))) return false;
        }
        return true;
    }
}
