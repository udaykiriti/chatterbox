package com.chatterbox.test;

import com.chatterbox.crypto.Curve25519;
import com.chatterbox.crypto.DoubleRatchet;
import com.chatterbox.crypto.E2E;
import com.chatterbox.proto.Utf8;

import junit.framework.TestCase;

/**
 * Double Ratchet library tests: two sessions driven through the full
 * Alice-speaks-first, Bob-replies, DH-steps-forward flow, with the DH
 * public relayed explicitly (on the wire this rides the v2 header).
 */
public final class RatchetTest extends TestCase {

    private static byte[] fixedKey(int fill) {
        byte[] k = new byte[32];
        for (int i = 0; i < 32; i++) {
            k[i] = (byte) (fill + i);
        }
        return k;
    }

    private static byte[] shared() {
        byte[] s = new byte[32];
        for (int i = 0; i < 32; i++) {
            s[i] = (byte) (0xA0 + i);
        }
        return s;
    }

    private static byte[] ad() {
        return Utf8.encode("test-ad");
    }

    private static String str(byte[] b) {
        return Utf8.decode(b, 0, b.length);
    }

    public void testAliceToBobOneWay() {
        byte[] aPriv = fixedKey(1);
        byte[] bPriv = fixedKey(2);
        byte[] aPub = Curve25519.derivePublicKey(aPriv);
        byte[] bPub = Curve25519.derivePublicKey(bPriv);
        DoubleRatchet.Session alice =
                DoubleRatchet.initializeAlice(shared(), aPriv, bPub, ad());
        DoubleRatchet.Session bob =
                DoubleRatchet.initializeBob(shared(), bPriv, aPub, ad());

        byte[] ct = DoubleRatchet.encrypt(alice, Utf8.encode("hello"), ad());
        byte[] pt = DoubleRatchet.decrypt(bob, ct, ad());
        assertNotNull(pt);
        assertEquals("hello", str(pt));
    }

    public void testReplyNeedsDhStep() {
        byte[] aPriv = fixedKey(1);
        byte[] bPriv = fixedKey(2);
        byte[] aPub = Curve25519.derivePublicKey(aPriv);
        byte[] bPub = Curve25519.derivePublicKey(bPriv);
        DoubleRatchet.Session alice =
                DoubleRatchet.initializeAlice(shared(), aPriv, bPub, ad());
        DoubleRatchet.Session bob =
                DoubleRatchet.initializeBob(shared(), bPriv, aPub, ad());

        // Alice speaks first; Bob reads.
        byte[] m1 = DoubleRatchet.encrypt(alice, Utf8.encode("one"), ad());
        assertEquals("one", str(DoubleRatchet.decrypt(bob, m1, ad())));

        // Bob replies: encrypt() ratchets onto a fresh keypair internally.
        byte[] m2 = DoubleRatchet.encrypt(bob, Utf8.encode("two"), ad());
        // Alice cannot read it until she learns Bob's new DH public...
        assertNull(DoubleRatchet.decrypt(alice, m2, ad()));
        // ...which the v2 header carries. Relay it explicitly here.
        DoubleRatchet.ratchetDecrypt(alice, bob.dhPublic);
        assertEquals("two", str(DoubleRatchet.decrypt(alice, m2, ad())));

        // Alice answers under the new root; Bob steps forward the same way.
        DoubleRatchet.ratchetEncrypt(alice);
        byte[] m3 = DoubleRatchet.encrypt(alice, Utf8.encode("three"), ad());
        assertNull(DoubleRatchet.decrypt(bob, m3, ad()));
        DoubleRatchet.ratchetDecrypt(bob, alice.dhPublic);
        assertEquals("three", str(DoubleRatchet.decrypt(bob, m3, ad())));
    }

    public void testOutOfOrderUsesSkippedKeys() {
        byte[] aPriv = fixedKey(1);
        byte[] bPriv = fixedKey(2);
        DoubleRatchet.Session alice = DoubleRatchet.initializeAlice(shared(), aPriv,
                Curve25519.derivePublicKey(bPriv), ad());
        DoubleRatchet.Session bob = DoubleRatchet.initializeBob(shared(), bPriv,
                Curve25519.derivePublicKey(aPriv), ad());

        byte[] m1 = DoubleRatchet.encrypt(alice, Utf8.encode("m1"), ad());
        byte[] m2 = DoubleRatchet.encrypt(alice, Utf8.encode("m2"), ad());
        byte[] m3 = DoubleRatchet.encrypt(alice, Utf8.encode("m3"), ad());
        // Deliver 3, 1, 2.
        assertEquals("m3", str(DoubleRatchet.decrypt(bob, m3, ad())));
        assertEquals("m1", str(DoubleRatchet.decrypt(bob, m1, ad())));
        assertEquals("m2", str(DoubleRatchet.decrypt(bob, m2, ad())));
    }

    public void testHugeGapRejectedWithoutHang() {
        byte[] aPriv = fixedKey(1);
        byte[] bPriv = fixedKey(2);
        DoubleRatchet.Session bob = DoubleRatchet.initializeBob(shared(), bPriv,
                Curve25519.derivePublicKey(aPriv), ad());
        // Forged header claiming message number 2^30: must fail fast,
        // not derive a billion keys.
        byte[] forged = new byte[4 + 16];
        forged[0] = 0x40;
        long start = System.currentTimeMillis();
        assertNull(DoubleRatchet.decrypt(bob, forged, ad()));
        assertTrue(System.currentTimeMillis() - start < 5000);
    }

    public void testTamperedCiphertextRejected() {
        byte[] aPriv = fixedKey(1);
        byte[] bPriv = fixedKey(2);
        DoubleRatchet.Session alice = DoubleRatchet.initializeAlice(shared(), aPriv,
                Curve25519.derivePublicKey(bPriv), ad());
        DoubleRatchet.Session bob = DoubleRatchet.initializeBob(shared(), bPriv,
                Curve25519.derivePublicKey(aPriv), ad());
        byte[] m1 = DoubleRatchet.encrypt(alice, Utf8.encode("secret"), ad());
        m1[m1.length - 1] ^= 0x01;
        assertNull(DoubleRatchet.decrypt(bob, m1, ad()));
    }

    public void testWrongAdRejected() {
        byte[] aPriv = fixedKey(1);
        byte[] bPriv = fixedKey(2);
        DoubleRatchet.Session alice = DoubleRatchet.initializeAlice(shared(), aPriv,
                Curve25519.derivePublicKey(bPriv), ad());
        DoubleRatchet.Session bob = DoubleRatchet.initializeBob(shared(), bPriv,
                Curve25519.derivePublicKey(aPriv), ad());
        byte[] m1 = DoubleRatchet.encrypt(alice, Utf8.encode("secret"), ad());
        assertNull(DoubleRatchet.decrypt(bob, m1, Utf8.encode("other-ad")));
    }

    // ---- Version-2 codec: no-handshake ratchet between two states ----

    private static final String PSK = "correct horse battery staple v2";

    public void testV2RoundTripBothDirections() {
        byte[] key = E2E.deriveKey(PSK);
        roundTripBothWays(aState(), bState(), key);
    }

    private void roundTripBothWays(E2E.RatchetState a, E2E.RatchetState b, byte[] key) {
        // Alice speaks first; Bob replies; Alice answers: two DH steps.
        String[] p1 = E2E.encodeV2(a, key, "alice", "bob", "bob",
                E2E.KIND_CHAT, "hello bob");
        assertEquals(1, p1.length);
        assertTrue(p1[0].startsWith("E2E:2:"));
        E2E.Plain d1 = E2E.decodeV2(b, key, "bob", "alice", "bob", p1[0]);
        assertNotNull(d1);
        assertEquals("hello bob", d1.text);

        String[] p2 = E2E.encodeV2(b, key, "bob", "alice", "alice",
                E2E.KIND_CHAT, "hi alice");
        E2E.Plain d2 = E2E.decodeV2(a, key, "alice", "bob", "alice", p2[0]);
        assertNotNull(d2);
        assertEquals("hi alice", d2.text);

        String[] p3 = E2E.encodeV2(a, key, "alice", "bob", "bob",
                E2E.KIND_ACTION, "waves");
        E2E.Plain d3 = E2E.decodeV2(b, key, "bob", "alice", "bob", p3[0]);
        assertNotNull(d3);
        assertEquals(E2E.KIND_ACTION, d3.kind);
        assertEquals("waves", d3.text);
        assertEquals(1, a.peerCount());
        assertEquals(1, b.peerCount());
    }

    private static E2E.RatchetState aState() {
        return new E2E.RatchetState();
    }

    private static E2E.RatchetState bState() {
        return new E2E.RatchetState();
    }

    public void testV2HealsAfterPeerRestart() {
        byte[] key = E2E.deriveKey(PSK);
        E2E.RatchetState a = aState();
        E2E.RatchetState b = bState();
        String[] p1 = E2E.encodeV2(a, key, "alice", "bob", "bob",
                E2E.KIND_CHAT, "one");
        assertNotNull(E2E.decodeV2(b, key, "bob", "alice", "bob", p1[0]));

        // Bob "restarts": brand-new state, same passphrase.
        b = bState();
        String[] p2 = E2E.encodeV2(a, key, "alice", "bob", "bob",
                E2E.KIND_CHAT, "two");
        E2E.Plain d2 = E2E.decodeV2(b, key, "bob", "alice", "bob", p2[0]);
        assertNotNull("restart must self-heal", d2);
        assertEquals("two", d2.text);

        // And Bob can answer on the healed session.
        String[] p3 = E2E.encodeV2(b, key, "bob", "alice", "alice",
                E2E.KIND_CHAT, "three");
        assertEquals("three",
                E2E.decodeV2(a, key, "alice", "bob", "alice", p3[0]).text);
    }

    public void testV2RejectsWrongKey() {
        byte[] key = E2E.deriveKey(PSK);
        String[] p1 = E2E.encodeV2(aState(), key, "alice", "bob", "bob",
                E2E.KIND_CHAT, "secret");
        assertNull(E2E.decodeV2(bState(), E2E.deriveKey("other"), "bob",
                "alice", "bob", p1[0]));
    }

    public void testV2RejectsWrongTarget() {
        byte[] key = E2E.deriveKey(PSK);
        String[] p1 = E2E.encodeV2(aState(), key, "alice", "bob", "bob",
                E2E.KIND_CHAT, "secret");
        assertNull(E2E.decodeV2(bState(), key, "bob", "alice", "mallory", p1[0]));
    }

    public void testV2RejectsTamperedPayload() {
        byte[] key = E2E.deriveKey(PSK);
        String[] p1 = E2E.encodeV2(aState(), key, "alice", "bob", "bob",
                E2E.KIND_CHAT, "secret");
        String bad = p1[0].substring(0, p1[0].length() - 2) + "AA";
        assertNull(E2E.decodeV2(bState(), key, "bob", "alice", "bob", bad));
    }

    public void testV2RejectsMalformed() {
        byte[] key = E2E.deriveKey(PSK);
        E2E.RatchetState b = bState();
        assertNull(E2E.decodeV2(b, key, "bob", "alice", "bob", "E2E:2:!!!"));
        assertNull(E2E.decodeV2(b, key, "bob", "alice", "bob", "E2E:2:QUJD"));
        assertNull(E2E.decodeV2(b, null, "bob", "alice", "bob", "E2E:2:QUJD"));
        assertFalse(E2E.isE2eV2("E2E:1:AAAA"));
        assertFalse(E2E.isE2eV2("hello"));
        assertTrue(E2E.isE2eV2("E2E:2:AAAA"));
    }

    public void testV1PrefixUntouchedByV2() {
        // v1 decode must keep rejecting v2 bodies (separate entry points).
        byte[] key = E2E.deriveKey(PSK);
        String[] p1 = E2E.encodeV2(aState(), key, "alice", "bob", "bob",
                E2E.KIND_CHAT, "secret");
        assertNull(E2E.decode(key, "alice", "bob", p1[0]));
    }
}
