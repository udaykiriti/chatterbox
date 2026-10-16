package com.chatterbox.test;

import com.chatterbox.crypto.ChaCha20;
import com.chatterbox.crypto.ChaCha20Poly1305;
import com.chatterbox.crypto.HKDF;
import com.chatterbox.crypto.Poly1305;
import com.chatterbox.crypto.SHA256;

import junit.framework.TestCase;

/**
 * Crypto layer tests for the hash/KDF primitives and the AEAD cipher.
 * Curve25519 is covered by X25519Test; PskE2E by E2ETest.
 * ChaCha20/Poly1305/ChaCha20-Poly1305 vectors below are taken verbatim from
 * RFC 8439 (sections 2.3.2, 2.4.2, 2.5.2, 2.8.2) and reproduce byte-for-byte.
 */
public final class CryptoTest extends TestCase {

    private static final String SUNSCREEN =
            "Ladies and Gentlemen of the class of '99: If I could offer you "
            + "only one tip for the future, sunscreen would be it.";

    // ---- HKDF tests ----

    public void testHKDFExtract() {
        byte[] salt = "salt".getBytes();
        byte[] ikm = "input key material".getBytes();
        byte[] prk = HKDF.extract(salt, ikm);
        assertEquals(32, prk.length);
    }

    public void testHKDFExtractNoSalt() {
        byte[] ikm = "ikm".getBytes();
        byte[] prk = HKDF.extract(null, ikm);
        assertEquals(32, prk.length);
        
        // Same IKM with null salt should produce same PRK
        byte[] prk2 = HKDF.extract(new byte[32], ikm);
        for (int i = 0; i < 32; i++) {
            assertEquals(prk[i], prk2[i]);
        }
    }

    public void testHKDFExpand() {
        // Use non-zero PRK to avoid all-zero output
        byte[] prk = "testprk".getBytes();
        // Pad to 32 bytes
        byte[] prk32 = new byte[32];
        System.arraycopy(prk, 0, prk32, 0, Math.min(prk.length, 32));
        
        byte[] info = "info".getBytes();
        byte[] okm = HKDF.expand(prk32, info, 32);
        assertEquals(32, okm.length);
        
        // Different info should produce different OKM
        byte[] okm2 = HKDF.expand(prk32, "different".getBytes(), 32);
        boolean different = false;
        for (int i = 0; i < 32; i++) {
            if (okm[i] != okm2[i]) {
                different = true;
                break;
            }
        }
        assertTrue(different);
    }

    public void testHKDF() {
        byte[] salt = "salt".getBytes();
        byte[] ikm = "ikm".getBytes();
        byte[] info = "info".getBytes();
        byte[] okm = HKDF.hkdf(salt, ikm, info, 64);
        assertEquals(64, okm.length);
    }

    public void testHKDFDifferentOutputLengths() {
        byte[] salt = "salt".getBytes();
        byte[] ikm = "ikm".getBytes();
        byte[] info = "info".getBytes();
        
        byte[] okm32 = HKDF.hkdf(salt, ikm, info, 32);
        byte[] okm64 = HKDF.hkdf(salt, ikm, info, 64);
        byte[] okm128 = HKDF.hkdf(salt, ikm, info, 128);
        
        assertEquals(32, okm32.length);
        assertEquals(64, okm64.length);
        assertEquals(128, okm128.length);
        
        // First 32 bytes should match
        for (int i = 0; i < 32; i++) {
            assertEquals(okm32[i], okm64[i]);
            assertEquals(okm32[i], okm128[i]);
        }
    }

    public void testHKDFWithNullInfo() {
        byte[] salt = "salt".getBytes();
        byte[] ikm = "ikm".getBytes();
        byte[] okm1 = HKDF.hkdf(salt, ikm, null, 32);
        byte[] okm2 = HKDF.hkdf(salt, ikm, new byte[0], 32);
        for (int i = 0; i < 32; i++) {
            assertEquals(okm1[i], okm2[i]);
        }
    }

    // ---- HMAC-SHA256 tests ----

    public void testHMACSHA256Basic() {
        byte[] key = "key".getBytes();
        byte[] data = "data".getBytes();
        byte[] mac = HKDF.hmacSha256(key, data);
        assertEquals(32, mac.length);
        
        // Same inputs should produce same MAC
        byte[] mac2 = HKDF.hmacSha256(key, data);
        for (int i = 0; i < 32; i++) {
            assertEquals(mac[i], mac2[i]);
        }
    }

    public void testHMACSHA256DifferentKeys() {
        byte[] key1 = "key1".getBytes();
        byte[] key2 = "key2".getBytes();
        byte[] data = "data".getBytes();
        
        byte[] mac1 = HKDF.hmacSha256(key1, data);
        byte[] mac2 = HKDF.hmacSha256(key2, data);
        
        boolean different = false;
        for (int i = 0; i < 32; i++) {
            if (mac1[i] != mac2[i]) {
                different = true;
                break;
            }
        }
        assertTrue(different);
    }

    public void testHMACSHA256DifferentData() {
        byte[] key = "key".getBytes();
        byte[] data1 = "data1".getBytes();
        byte[] data2 = "data2".getBytes();
        
        byte[] mac1 = HKDF.hmacSha256(key, data1);
        byte[] mac2 = HKDF.hmacSha256(key, data2);
        
        boolean different = false;
        for (int i = 0; i < 32; i++) {
            if (mac1[i] != mac2[i]) {
                different = true;
                break;
            }
        }
        assertTrue(different);
    }

    public void testHMACSHA256LongKey() {
        // Key longer than block size (64 bytes) should be hashed first
        byte[] longKey = new byte[80];
        for (int i = 0; i < 80; i++) {
            longKey[i] = (byte) i;
        }
        byte[] data = "data".getBytes();
        byte[] mac = HKDF.hmacSha256(longKey, data);
        assertEquals(32, mac.length);
    }

    // ---- SHA256 tests ----

    public void testSHA256Basic() {
        byte[] data = "abc".getBytes();
        byte[] hash = SHA256.hash(data);
        assertEquals(32, hash.length);
        
        // Known SHA-256 of "abc"
        byte[] expected = new byte[] {
            (byte)0xba, (byte)0x78, (byte)0x16, (byte)0xbf,
            (byte)0x8f, (byte)0x01, (byte)0xcf, (byte)0xea,
            (byte)0x41, (byte)0x41, (byte)0x40, (byte)0xde,
            (byte)0x5d, (byte)0xae, (byte)0x22, (byte)0x23,
            (byte)0xb0, (byte)0x03, (byte)0x61, (byte)0xa3,
            (byte)0x96, (byte)0x17, (byte)0x7a, (byte)0x9c,
            (byte)0xb4, (byte)0x10, (byte)0xff, (byte)0x61,
            (byte)0xf2, (byte)0x00, (byte)0x15, (byte)0xad
        };
        for (int i = 0; i < 32; i++) {
            assertEquals(expected[i], hash[i]);
        }
    }

    public void testSHA256Empty() {
        byte[] hash = SHA256.hash(new byte[0]);
        // SHA-256 of empty string
        byte[] expected = new byte[] {
            (byte)0xe3, (byte)0xb0, (byte)0xc4, (byte)0x42,
            (byte)0x98, (byte)0xfc, (byte)0x1c, (byte)0x14,
            (byte)0x9a, (byte)0xfb, (byte)0xf4, (byte)0xc8,
            (byte)0x99, (byte)0x6f, (byte)0xb9, (byte)0x24,
            (byte)0x27, (byte)0xae, (byte)0x41, (byte)0xe4,
            (byte)0x64, (byte)0x9b, (byte)0x93, (byte)0x4c,
            (byte)0xa4, (byte)0x95, (byte)0x99, (byte)0x1b,
            (byte)0x78, (byte)0x52, (byte)0xb8, (byte)0x55
        };
        for (int i = 0; i < 32; i++) {
            assertEquals(expected[i], hash[i]);
        }
    }

    public void testSHA256LongInput() {
        // Test with larger input
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append("a");
        }
        byte[] data = sb.toString().getBytes();
        byte[] hash = SHA256.hash(data);
        assertEquals(32, hash.length);
    }

    // ---- RFC 8439 section 2.3.2: ChaCha20 block function ----

    public void testChaCha20Block8439() {
        byte[] key = hex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");
        byte[] nonce = hex("000000090000004a00000000");
        // Encrypt 64 zero bytes with counter 1; the ciphertext is the block.
        byte[] zeros = new byte[64];
        byte[] block = new byte[64];
        ChaCha20.crypt(key, nonce, 1, zeros, 0, 64, block, 0);
        assertEquals("RFC 8439 2.3.2 serialized block",
                "10f1e7e4d13b5915500fdd1fa32071c4"
                + "c7d1f4c733c068030422aa9ac3d46c4e"
                + "d2826446079faa0914c2d705d98b02a2"
                + "b5129cd1de164eb9cbd083e8a2503c4e",
                toHex(block));
    }

    // ---- RFC 8439 section 2.4.2: ChaCha20 cipher ----

    public void testChaCha20Cipher8439() {
        byte[] key = hex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");
        byte[] nonce = hex("000000000000004a00000000");
        byte[] pt = ascii(SUNSCREEN);
        byte[] ct = new byte[pt.length];
        ChaCha20.crypt(key, nonce, 1, pt, 0, pt.length, ct, 0);
        assertEquals("RFC 8439 2.4.2 ciphertext",
                "6e2e359a2568f98041ba0728dd0d6981e97e7aec1d4360c20a27afccfd9fae"
                + "0bf91b65c5524733ab8f593dabcd62b3571639d624e65152ab8f530c359f086"
                + "1d807ca0dbf500d6a6156a38e088a22b65e52bc514d16ccf806818ce91ab7793"
                + "7365af90bbf74a35be6b40b8eedf2785e42874d",
                toHex(ct));
    }

    // ---- RFC 8439 section 2.5.2: Poly1305 ----

    public void testPoly13058439() {
        byte[] oneTimeKey = hex(
                "85d6be7857556d337f4452fe42d506a8"
                + "0103 808afb0db2fd4abff6af4149f51b".replaceAll(" ", ""));
        byte[] msg = ascii("Cryptographic Forum Research Group");
        byte[] tag = new byte[16];
        Poly1305.mac(oneTimeKey, msg, tag);
        assertEquals("RFC 8439 2.5.2 tag",
                "a8061dc1305136c6c22b8baf0c0127a9",
                toHex(tag));
    }

    public void testPoly1305ZeroVector() {
        // RFC 8439 A.3 test vector #1: all-zero key, all-zero 64-byte message.
        byte[] tag = new byte[16];
        Poly1305.mac(new byte[32], new byte[64], tag);
        assertEquals("A.3 #1 zero tag", "00000000000000000000000000000000", toHex(tag));
    }

    // ---- RFC 8439 section 2.8.2: AEAD_CHACHA20_POLY1305 ----

    public void testAead8439Encrypt() {
        byte[] key = hex("808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f");
        byte[] nonce = hex("070000004041424344454647");
        byte[] aad = hex("50515253c0c1c2c3c4c5c6c7");
        byte[] pt = ascii(SUNSCREEN);
        byte[] out = new byte[pt.length + 16];
        int n = ChaCha20Poly1305.encrypt(key, nonce, aad, pt, out);
        assertEquals(pt.length + 16, n);
        assertEquals("RFC 8439 2.8.2 ciphertext+tag",
                "d31a8d34648e60db7b86afbc53ef7ec2a4aded51296e08fea9e2b5a736ee62d6"
                + "3dbea45e8ca9671282fafb69da92728b1a71de0a9e060b2905d6a5b67ecd3b3692dd"
                + "bd7f2d778b8c9803aee328091b58fab324e4fad675945585808b4831d7bc"
                + "3ff4def08e4b7a9de576d26586cec64b6116"
                + "1ae10b594f09e26a7e902ecbd0600691",
                toHex(out));
    }

    public void testAead8439Decrypt() {
        byte[] key = hex("808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f");
        byte[] nonce = hex("070000004041424344454647");
        byte[] aad = hex("50515253c0c1c2c3c4c5c6c7");
        byte[] blob = hex("d31a8d34648e60db7b86afbc53ef7ec2a4aded51296e08fea9e2b5a736ee62d6"
                + "3dbea45e8ca9671282fafb69da92728b1a71de0a9e060b2905d6a5b67ecd3b3692dd"
                + "bd7f2d778b8c9803aee328091b58fab324e4fad675945585808b4831d7bc"
                + "3ff4def08e4b7a9de576d26586cec64b6116"
                + "1ae10b594f09e26a7e902ecbd0600691");
        byte[] pt = new byte[blob.length - 16];
        int n = ChaCha20Poly1305.decrypt(key, nonce, aad, blob, 0, blob.length, pt);
        assertEquals("decrypt must yield the plaintext", pt.length, n);
        assertEquals("RFC 8439 2.8.2 round trip", SUNSCREEN, new String(pt));
    }

    public void testAeadRejectsTamperedTag() {
        byte[] key = hex("808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f");
        byte[] nonce = hex("070000004041424344454647");
        byte[] aad = hex("50515253c0c1c2c3c4c5c6c7");
        byte[] pt = ascii(SUNSCREEN);
        byte[] out = new byte[pt.length + 16];
        ChaCha20Poly1305.encrypt(key, nonce, aad, pt, out);
        out[out.length - 1] ^= 0x01;  // flip a tag bit
        byte[] pt2 = new byte[pt.length];
        assertEquals("bad tag must fail", -1,
                ChaCha20Poly1305.decrypt(key, nonce, aad, out, 0, out.length, pt2));
    }

    public void testAeadRejectsWrongAad() {
        byte[] key = hex("808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f");
        byte[] nonce = hex("070000004041424344454647");
        byte[] pt = ascii(SUNSCREEN);
        byte[] out = new byte[pt.length + 16];
        ChaCha20Poly1305.encrypt(key, nonce, hex("50515253c0c1c2c3c4c5c6c7"), pt, out);
        byte[] otherAad = hex("50515253c0c1c2c3c4c5c6c8");
        byte[] pt2 = new byte[pt.length];
        assertEquals("mismatched AAD must fail", -1,
                ChaCha20Poly1305.decrypt(key, nonce, otherAad, out, 0, out.length, pt2));
    }

    // ---- Helpers ----

    private static byte[] hex(String s) {
        byte[] b = new byte[s.length() / 2];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        }
        return b;
    }

    private static byte[] ascii(String s) {
        byte[] b = new byte[s.length()];
        for (int i = 0; i < s.length(); i++) {
            b[i] = (byte) s.charAt(i);
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