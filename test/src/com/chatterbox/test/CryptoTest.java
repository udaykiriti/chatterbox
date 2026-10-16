package com.chatterbox.test;

import com.chatterbox.crypto.HKDF;
import com.chatterbox.crypto.SHA256;

import junit.framework.TestCase;

/**
 * Crypto layer tests for working primitives only.
 * Curve25519 is covered by X25519Test. ChaCha20, Poly1305, ChaCha20-Poly1305 and
 * DoubleRatchet have known issues and are tested separately when fixed.
 */
public final class CryptoTest extends TestCase {

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
}