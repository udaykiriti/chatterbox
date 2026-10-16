package com.chatterbox.crypto;

/**
 * X25519 scalar multiplication (RFC 7748) for CLDC 1.1 / MIDP 2.0.
 *
 * <p>Field elements are held as 16 little-endian 16-bit limbs in int[16] and are
 * kept fully reduced modulo p = 2^255 - 19 after every operation. This avoids
 * java.math.BigInteger, which is not part of CLDC.
 *
 * <p>Byte arrays are little-endian, matching RFC 7748. The most significant
 * bit of the input u-coordinate is masked, and non-canonical u values
 * (>= p) are accepted and reduced, as the RFC requires.
 */
public final class Curve25519 {

    public static final int SCALAR_BYTES = 32;
    public static final int POINT_BYTES = 32;

    /** (486662 - 2) / 4, the a24 constant of the Montgomery ladder. */
    private static final int[] A24 = constant(121665);

    private Curve25519() {}

    /** Computes X25519(k, u): scalar multiplication on the Montgomery u-line. */
    public static byte[] scalarMult(byte[] k, byte[] u) {
        if (k == null || k.length != SCALAR_BYTES) throw new IllegalArgumentException("Scalar must be 32 bytes");
        if (u == null || u.length != POINT_BYTES) throw new IllegalArgumentException("Point must be 32 bytes");

        byte[] scalar = new byte[32];
        System.arraycopy(k, 0, scalar, 0, 32);
        clampScalar(scalar);

        int[] x1 = toLimbs(u);
        int[] x2 = new int[16]; x2[0] = 1;
        int[] z2 = new int[16];
        int[] x3 = toLimbs(u);
        int[] z3 = new int[16]; z3[0] = 1;

        int swap = 0;
        for (int t = 254; t >= 0; t--) {
            int kt = (scalar[t >> 3] >> (t & 7)) & 1;
            swap ^= kt;
            cswap(swap, x2, x3);
            cswap(swap, z2, z3);
            swap = kt;

            int[] A = add(x2, z2);
            int[] AA = sqr(A);
            int[] B = sub(x2, z2);
            int[] BB = sqr(B);
            int[] E = sub(AA, BB);
            int[] C = add(x3, z3);
            int[] D = sub(x3, z3);
            int[] DA = mul(D, A);
            int[] CB = mul(C, B);
            x3 = sqr(add(DA, CB));
            z3 = mul(x1, sqr(sub(DA, CB)));
            x2 = mul(AA, BB);
            z2 = mul(E, add(AA, mul(E, A24)));
        }
        cswap(swap, x2, x3);
        cswap(swap, z2, z3);
        return fromLimbs(mul(x2, invert(z2)));
    }

    /**
     * Generates a random private scalar. CLDC 1.1 provides no crypto-strength
     * RNG, so this seeds java.util.Random from the clock and object identity;
     * prefer importing keys generated on a stronger source when possible.
     */
    public static byte[] generatePrivateKey() {
        byte[] key = new byte[32];
        java.util.Random rng = new java.util.Random(
                System.currentTimeMillis() ^ (((long) new Object().hashCode()) << 20));
        for (int i = 0; i < 32; i++) key[i] = (byte) rng.nextInt();
        clampScalar(key);
        return key;
    }

    public static byte[] derivePublicKey(byte[] privateKey) {
        return scalarMult(privateKey, basePoint());
    }

    public static byte[] keyAgreement(byte[] myPrivateKey, byte[] theirPublicKey) {
        return scalarMult(myPrivateKey, theirPublicKey);
    }

    public static byte[] basePoint() {
        byte[] bp = new byte[32];
        bp[0] = 9;
        return bp;
    }

    // ---- Encoding ----

    private static void clampScalar(byte[] k) {
        k[0] &= 0xF8;   // clear the 3 low bits
        k[31] &= 0x7F;  // clear bit 255
        k[31] |= 0x40;  // set bit 254
    }

    private static int[] toLimbs(byte[] b) {
        int[] x = new int[16];
        for (int i = 0; i < 16; i++) x[i] = (b[2*i] & 0xFF) | ((b[2*i + 1] & 0xFF) << 8);
        x[15] &= 0x7FFF;  // RFC 7748: mask the most significant bit
        return x;
    }

    private static byte[] fromLimbs(int[] x) {
        byte[] b = new byte[32];
        for (int i = 0; i < 16; i++) {
            b[2*i] = (byte) x[i];
            b[2*i + 1] = (byte) (x[i] >> 8);
        }
        return b;
    }

    private static int[] constant(int v) {
        int[] x = new int[16];
        x[0] = v;
        return x;
    }

    // ---- Field arithmetic mod 2^255 - 19 ----

    private static int[] add(int[] a, int[] b) {
        long[] t = new long[16];
        long carry = 0;
        for (int i = 0; i < 16; i++) {
            long v = a[i] + b[i] + carry;
            t[i] = v & 0xFFFF;
            carry = v >>> 16;
        }
        return canon(normalize(t));
    }

    private static int[] sub(int[] a, int[] b) {
        long[] t = new long[16];
        long borrow = 0;
        for (int i = 0; i < 16; i++) {
            long d = a[i] - b[i] - borrow;
            if (d < 0) { d += 0x10000; borrow = 1; } else borrow = 0;
            t[i] = d;
        }
        if (borrow != 0) {
            // a < b: t holds a - b + 2^256; subtract 2^255 + 19 to get a - b + p.
            long br = 0;
            for (int i = 0; i < 16; i++) {
                long s = (i == 0) ? 19 : (i == 15) ? 0x8000 : 0;
                long d = t[i] - s - br;
                if (d < 0) { d += 0x10000; br = 1; } else br = 0;
                t[i] = d;
            }
        }
        int[] r = new int[16];
        for (int i = 0; i < 16; i++) r[i] = (int) t[i];
        return r;
    }

    private static int[] mul(int[] a, int[] b) {
        long[] t = new long[31];
        for (int i = 0; i < 16; i++) {
            long ai = a[i];
            if (ai == 0) continue;
            for (int j = 0; j < 16; j++) t[i + j] += ai * b[j];
        }
        // Words 16..30 have weight 2^(16k); since 2^256 = 38 (mod p) each folds
        // into words k-16 (low half) and k-15 (high half).
        for (int k = 30; k >= 16; k--) {
            long v = t[k];
            if (v != 0) {
                t[k - 16] += (v & 0xFFFF) * 38;
                t[k - 15] += (v >>> 16) * 38;
            }
        }
        return canon(normalize(t));
    }

    private static int[] sqr(int[] a) {
        return mul(a, a);
    }

    /** Carry-propagates 16 long limbs into 16-bit digits, folding 2^256 = 38 back in. */
    private static int[] normalize(long[] t) {
        long carry = 0;
        for (int i = 0; i < 16; i++) {
            long v = t[i] + carry;
            t[i] = v & 0xFFFF;
            carry = v >>> 16;
        }
        while (carry != 0) {
            long v = t[0] + carry * 38;
            t[0] = v & 0xFFFF;
            carry = v >>> 16;
            for (int i = 1; i < 16 && carry != 0; i++) {
                v = t[i] + carry;
                t[i] = v & 0xFFFF;
                carry = v >>> 16;
            }
        }
        int[] out = new int[16];
        for (int i = 0; i < 16; i++) out[i] = (int) t[i];
        return out;
    }

    /** Fully reduces a 16-digit value below p (at most two subtractions needed). */
    private static int[] canon(int[] a) {
        return trySubP(trySubP(a));
    }

    private static int[] trySubP(int[] a) {
        long borrow = 0;
        int[] r = new int[16];
        for (int i = 0; i < 16; i++) {
            int pl = (i == 0) ? 0xFFED : (i == 15) ? 0x7FFF : 0xFFFF;
            long d = a[i] - pl - borrow;
            if (d < 0) { d += 0x10000; borrow = 1; } else borrow = 0;
            r[i] = (int) d;
        }
        return (borrow == 0) ? r : a;
    }

    /** Branchless conditional swap of two limbs arrays. */
    private static void cswap(int bit, int[] a, int[] b) {
        int mask = 0 - bit;  // 0 or -1
        for (int i = 0; i < 16; i++) {
            int t = mask & (a[i] ^ b[i]);
            a[i] ^= t;
            b[i] ^= t;
        }
    }

    /** Computes z^(p - 2) = z^-1 in GF(p) with the standard addition chain. */
    private static int[] invert(int[] z) {
        int[] t0 = sqr(z);                 // z^2
        int[] t1 = sqr(t0);                // z^4
        t1 = sqr(t1);                      // z^8
        t1 = mul(z, t1);                   // z^9
        t0 = mul(t0, t1);                  // z^11
        int[] t2 = sqr(t0);                // z^22
        t1 = mul(t1, t2);                  // z^(2^5 - 1)
        t2 = sqr(t1);
        for (int i = 1; i < 5; i++) t2 = sqr(t2);
        t1 = mul(t2, t1);                  // z^(2^10 - 1)
        t2 = sqr(t1);
        for (int i = 1; i < 10; i++) t2 = sqr(t2);
        t2 = mul(t2, t1);                  // z^(2^20 - 1)
        int[] t3 = sqr(t2);
        for (int i = 1; i < 20; i++) t3 = sqr(t3);
        t2 = mul(t3, t2);                  // z^(2^40 - 1)
        t2 = sqr(t2);
        for (int i = 1; i < 10; i++) t2 = sqr(t2);
        t1 = mul(t2, t1);                  // z^(2^50 - 1)
        t2 = sqr(t1);
        for (int i = 1; i < 50; i++) t2 = sqr(t2);
        t2 = mul(t2, t1);                  // z^(2^100 - 1)
        t3 = sqr(t2);
        for (int i = 1; i < 100; i++) t3 = sqr(t3);
        t2 = mul(t3, t2);                  // z^(2^200 - 1)
        t2 = sqr(t2);
        for (int i = 1; i < 50; i++) t2 = sqr(t2);
        t1 = mul(t2, t1);                  // z^(2^250 - 1)
        t1 = sqr(t1);
        for (int i = 1; i < 5; i++) t1 = sqr(t1);
        return mul(t1, t0);                // z^(2^255 - 21) = z^(p - 2)
    }
}
