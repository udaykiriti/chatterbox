package com.chatterbox.test;

import com.chatterbox.crypto.Curve25519;

import junit.framework.TestCase;

/**
 * X25519 test vectors for Curve25519, from RFC 7748 sections 5.2 and 6.1.
 * Byte strings are little-endian hex, exactly as printed in the RFC.
 */
public final class X25519Test extends TestCase {

    // ---- RFC 7748 section 5.2: X25519 raw vectors ----

    public void testVector1() {
        byte[] k = hex("a546e36bf0527c9d3b16154b82465edd62144c0ac1fc5a18506a2244ba449ac4");
        byte[] u = hex("e6db6867583030db3594c1a424b15f7c726624ec26b3353b10a903a6d0ab1c4c");
        assertEquals("RFC 7748 vector 1",
                "c3da55379de9c6908e94ea4df28d084f32eccf03491c71f754b4075577a28552",
                toHex(Curve25519.scalarMult(k, u)));
    }

    public void testVector2() {
        byte[] k = hex("4b66e9d4d1b4673c5ad22691957d6af5c11b6421e0ea01d42ca4169e7918ba0d");
        byte[] u = hex("e5210f12786811d3f4b7959d0538ae2c31dbe7106fc03c3efc4cd549c715a493");
        assertEquals("RFC 7748 vector 2",
                "95cbde9476e8907d7aade45cb4b873f88b595a68799fa152e6f8f7647aac7957",
                toHex(Curve25519.scalarMult(k, u)));
    }

    public void testIterated1() {
        byte[] base = hex("0900000000000000000000000000000000000000000000000000000000000000");
        assertEquals("RFC 7748, 1 iteration",
                "422c8e7a6227d7bca1350b3e2bb7279f7897b87bb6854b783c60e80311ae3079",
                toHex(Curve25519.scalarMult(base, base)));
    }

    public void testIterated1000() {
        byte[] k = hex("0900000000000000000000000000000000000000000000000000000000000000");
        byte[] u = k;
        for (int i = 0; i < 1000; i++) {
            byte[] kr = Curve25519.scalarMult(k, u);
            u = k;
            k = kr;
        }
        assertEquals("RFC 7748, 1000 iterations",
                "684cf59ba83309552800ef566f2f4d3c1c3887c49360e3875f2eb94d99532c51",
                toHex(k));
    }

    // ---- RFC 7748 section 6.1: Diffie-Hellman ----

    public void testDiffieHellmanVector() {
        byte[] aPriv = hex("77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a");
        byte[] bPriv = hex("5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb");
        byte[] aPub = Curve25519.derivePublicKey(aPriv);
        byte[] bPub = Curve25519.derivePublicKey(bPriv);
        assertEquals("Alice public key",
                "8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a",
                toHex(aPub));
        assertEquals("Bob public key",
                "de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f",
                toHex(bPub));
        String shared = "4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742";
        assertEquals("Alice's shared secret", shared, toHex(Curve25519.keyAgreement(aPriv, bPub)));
        assertEquals("Bob's shared secret", shared, toHex(Curve25519.keyAgreement(bPriv, aPub)));
    }

    public void testAgreementSymmetry() {
        for (int i = 0; i < 5; i++) {
            byte[] a = Curve25519.generatePrivateKey();
            byte[] b = Curve25519.generatePrivateKey();
            byte[] kab = Curve25519.keyAgreement(a, Curve25519.derivePublicKey(b));
            byte[] kba = Curve25519.keyAgreement(b, Curve25519.derivePublicKey(a));
            assertEquals("Generated DH agreement must agree (#" + i + ")", toHex(kab), toHex(kba));
        }
    }

    // ---- RFC 7748 input handling requirements ----

    public void testNonCanonicalAndMaskedU() {
        byte[] k = hex("a546e36bf0527c9d3b16154b82465edd62144c0ac1fc5a18506a2244ba449ac4");
        byte[] uOne = new byte[32];
        uOne[0] = 1;

        // u = p + 1 must be accepted and reduced to 1 (non-canonical values).
        byte[] uPPlusOne = new byte[32];
        uPPlusOne[0] = (byte) 0xEE;
        for (int i = 1; i < 31; i++) uPPlusOne[i] = (byte) 0xFF;
        uPPlusOne[31] = 0x7F;
        assertEquals("u = p + 1 must reduce to u = 1",
                toHex(Curve25519.scalarMult(k, uOne)),
                toHex(Curve25519.scalarMult(k, uPPlusOne)));

        // Bit 255 of u must be masked.
        byte[] uHighBit = new byte[32];
        uHighBit[0] = 1;
        uHighBit[31] |= (byte) 0x80;
        assertEquals("bit 255 of u must be masked",
                toHex(Curve25519.scalarMult(k, uOne)),
                toHex(Curve25519.scalarMult(k, uHighBit)));
    }

    public void testRejectsBadLengths() {
        try {
            Curve25519.scalarMult(new byte[31], new byte[32]);
            fail("31-byte scalar must be rejected");
        } catch (IllegalArgumentException expected) { }
        try {
            Curve25519.scalarMult(new byte[32], new byte[33]);
            fail("33-byte point must be rejected");
        } catch (IllegalArgumentException expected) { }
        try {
            Curve25519.scalarMult(null, new byte[32]);
            fail("null scalar must be rejected");
        } catch (IllegalArgumentException expected) { }
    }

    public void testGeneratedKeysAreClamped() {
        byte[] k = Curve25519.generatePrivateKey();
        assertEquals("low 3 bits cleared", 0, k[0] & 0x07);
        assertEquals("bit 255 cleared", 0, k[31] & 0x80);
        assertTrue("bit 254 set", (k[31] & 0x40) != 0);
    }

    // ---- Helpers ----

    private static byte[] hex(String s) {
        byte[] b = new byte[s.length() / 2];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        }
        return b;
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
}
