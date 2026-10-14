package com.chatterbox.crypto;

public final class Curve25519 {
    public static final int SCALAR_BYTES = 32;
    public static final int POINT_BYTES = 32;

    private Curve25519() {}

    public static byte[] scalarMult(byte[] k, byte[] u) {
        if (k == null || k.length != SCALAR_BYTES) throw new IllegalArgumentException("Scalar must be 32 bytes");
        if (u == null || u.length != POINT_BYTES) throw new IllegalArgumentException("Point must be 32 bytes");
        byte[] scalar = k.clone(); clampScalar(scalar);
        byte[] x1 = u.clone(); byte[] x2 = new byte[32]; x2[0] = 1; byte[] z2 = new byte[32]; byte[] x3 = u.clone(); byte[] z3 = new byte[32]; z3[0] = 1; byte[] scalarCopy = scalar;
        for (int i = 255; i >= 0; i--) {
            int bit = (scalarCopy[i >> 3] >> (i & 7)) & 1;
            if (bit == 1) { cswap(x2, x3); cswap(z2, z3); }
            ladderStep(x1, x2, z2, x3, z3);
            if (bit == 1) { cswap(x2, x3); cswap(z2, z3); }
        }
        byte[] z2inv = invert(z2); return mul(x2, z2inv);
    }
    public static byte[] generatePrivateKey() { byte[] key = new byte[32]; java.security.SecureRandom rand = new java.security.SecureRandom(); rand.nextBytes(key); clampScalar(key); return key; }
    public static byte[] derivePublicKey(byte[] privateKey) { byte[] basePoint = new byte[32]; basePoint[0] = 9; return scalarMult(privateKey, basePoint); }
    public static byte[] keyAgreement(byte[] myPrivateKey, byte[] theirPublicKey) { return scalarMult(myPrivateKey, theirPublicKey); }
    private static void clampScalar(byte[] k) { k[0] &= 0xF8; k[31] &= 0x7F; k[31] |= 0x40; }
    private static void cswap(byte[] a, byte[] b) { for (int i = 0; i < 32; i++) { byte t = a[i]; a[i] = b[i]; b[i] = t; } }
    private static void ladderStep(byte[] x1, byte[] x2, byte[] z2, byte[] x3, byte[] z3) {
        byte[] A = add(x2, z2); byte[] AA = sqr(A); byte[] B = sub(x2, z2); byte[] BB = sqr(B); byte[] E1 = sub(AA, BB);
        byte[] C = add(x3, z3); byte[] D = sub(x3, z3); byte[] DA = mul(D, A); byte[] CB = mul(C, B);
        byte[] x3_new = sqr(add(mul(D, A), mul(C, B))); byte[] z3_new = sqr(sub(mul(D, A), mul(C, B))); mul(x1, z3_new, z3);
        byte[] x2_new = mul(AA, BB); byte[] E = sub(AA, BB); mulSmall(E, 121666); byte[] z2_new = mul(E, add(AA, mul(E, BB)));
        System.arraycopy(x3_new, 0, x3, 0, 32); System.arraycopy(z3_new, 0, z3, 0, 32); System.arraycopy(x2_new, 0, x2, 0, 32); System.arraycopy(z2_new, 0, z2, 0, 32);
    }
    private static byte[] add(byte[] a, byte[] b) { byte[] out = new byte[32]; int carry = 0; for (int i = 0; i < 32; i++) { int sum = (a[i] & 0xFF) + (b[i] & 0xFF) + (carry & 0xFF); out[i] = (byte)(sum & 0xFF); carry = sum >> 8; } return out; }
    private static byte[] sub(byte[] a, byte[] b) { byte[] out = new byte[32]; int borrow = 0; for (int i = 0; i < 32; i++) { int diff = (a[i] & 0xFF) - (b[i] & 0xFF) - borrow; if (diff < 0) { diff += 256; borrow = 1; } else borrow = 0; out[i] = (byte)diff; } return out; }
    private static byte[] mul(byte[] a, byte[] b) {
        int[] a16 = to16(a); int[] b16 = to16(b); int[] prod = new int[31];
        for (int i = 0; i < 16; i++) { long carry = 0; for (int j = 0; j < 16; j++) { carry += (long)(a16[i] & 0xFFFF) * (b16[j] & 0xFFFF) + prod[i+j]; prod[i+j] = (int)(carry & 0xFFFF); carry >>= 16; } prod[i+16] = (int)carry; }
        for (int i = 30; i >= 16; i--) { long v = (long)prod[i] * 38L; prod[i - 16] += (int)(v & 0xFFFF); if (i - 15 < 31) prod[i - 15] += (int)(v >> 16); }
        int carry = 0; int[] out16 = new int[16]; for (int i = 0; i < 16; i++) { int v = prod[i] + carry; out16[i] = v & 0xFFFF; carry = v >> 16; }
        if (carry != 0) { long v = (long)carry * 38L; out16[0] += (int)(v & 0xFFFF); out16[1] += (int)(v >> 16); int c = 0; for (int i = 0; i < 16; i++) { int v2 = out16[i] + c; out16[i] = v2 & 0xFFFF; c = v2 >> 16; } if (c != 0) { long v = (long)c * 38L; out16[0] += (int)(v & 0xFFFF); out16[1] += (int)(v >> 16); } }
        byte[] out = new byte[32]; for (int i = 0; i < 16; i++) { out[2*i] = (byte)(out16[i] & 0xFF); out[2*i + 1] = (byte)((out16[i] >> 8) & 0xFF); } out[31] &= 0x7F; return out; }
    }
    private static byte[] sqr(byte[] a) { return mul(a, a); }
    private static void mulSmall(byte[] a, int k) { long carry = 0; for (int i = 0; i < 16; i++) { int limb = (a[2*i] & 0xFF) | ((a[2*i+1] & 0xFF) << 8); carry += (long)limb * k; a[2*i] = (byte)(carry & 0xFF); a[2*i + 1] = (byte)((carry >> 8) & 0xFF); carry >>= 16; } long v = carry * 38L; a[0] += (int)(v & 0xFF); a[1] += (int)(v >> 16); int carry2 = 0; for (int i = 0; i < 16; i++) { int v = (a[2*i] & 0xFF) | ((a[2*i+1] & 0xFF) << 8); v += carry2; a[2*i] = (byte)(v & 0xFF); a[2*i+1] = (byte)((v >> 8) & 0xFF); carry2 = v >> 16; } a[31] &= 0x7F; }
    private static byte[] invert(byte[] a) {
        int[] x = to16(a);
        int[] inv = modInvert(x);
        return from16(inv);
    }

    private static int[] modInvert(int[] a) {
        int[] u = a.clone();
        int[] v = new int[16];
        v[0] = 0xFFED;
        v[15] = 0x7FFF;
        for (int i = 1; i < 15; i++) v[i] = 0xFFFF;
        int[] x1 = new int[16]; x1[0] = 1;
        int[] x2 = new int[16];
        while (deg(u) >= 0 && deg(v) >= 0) {
            int du = deg(u), dv = deg(v);
            if (du >= dv) {
                int s = du - dv;
                subShift(u, v, s);
                subShift(x1, x2, s);
            } else {
                int s = dv - du;
                subShift(v, u, s);
                subShift(x2, x1, s);
            }
        }
        int[] res = deg(u) >= 0 ? x1 : x2;
        normalize(res);
        return res;
    }

    private static int deg(int[] a) {
        for (int i = 15; i >= 0; i--) if (a[i] != 0) return i;
        return -1;
    }

    private static void subShift(int[] a, int[] b, int s) {
        int c = 0;
        for (int i = 0; i < 16; i++) {
            int bi = i >= s ? b[i - s] : 0;
            int d = a[i] - bi - c;
            if (d < 0) { d += 0x10000; c = 1; } else c = 0;
            a[i] = d & 0xFFFF;
        }
        if (c != 0) {
            int[] n = new int[16];
            n[0] = 0xFFED; n[15] = 0x7FFF;
            for (int i = 1; i < 15; i++) n[i] = 0xFFFF;
            addMod(a, n, 0);
        }
    }

    private static void addMod(int[] a, int[] b, int s) {
        long c = 0;
        for (int i = 0; i < 16; i++) {
            int bi = i >= s ? b[i - s] : 0;
            long sum = (a[i] & 0xFFFFL) + (bi & 0xFFFFL) + c;
            a[i] = (int)(sum & 0xFFFF);
            c = sum >> 16;
        }
        if (c != 0) {
            long v = c * 38L;
            a[0] += (int)(v & 0xFFFF);
            a[1] += (int)(v >> 16);
            for (int i = 0; i < 15; i++) {
                if (a[i] >= 0x10000) { a[i+1] += a[i] >> 16; a[i] &= 0xFFFF; }
            }
            a[15] &= 0x7FFF;
        }
    }

    private static void normalize(int[] a) {
        for (int i = 0; i < 15; i++) {
            if (a[i] >= 0x10000) { a[i+1] += a[i] >> 16; a[i] &= 0xFFFF; }
        }
        a[15] &= 0x7FFF;
        int[] p = new int[16];
        p[0] = 0xFFED; p[15] = 0x7FFF;
        for (int i = 1; i < 15; i++) p[i] = 0xFFFF;
        if (compare(a, p) >= 0) subMod(a, p);
    }

    private static int compare(int[] a, int[] b) {
        for (int i = 15; i >= 0; i--) if (a[i] != b[i]) return a[i] - b[i];
        return 0;
    }

    private static void subMod(int[] a, int[] b) {
        int c = 0;
        for (int i = 0; i < 16; i++) {
            int d = a[i] - b[i] - c;
            if (d < 0) { d += 0x10000; c = 1; } else c = 0;
            a[i] = d;
        }
    }

    private static byte[] from16(int[] a) {
        byte[] out = new byte[32];
        for (int i = 0; i < 16; i++) {
            out[2*i] = (byte)(a[i] & 0xFF);
            out[2*i+1] = (byte)((a[i] >> 8) & 0xFF);
        }
        return out;
    }

    private static int[] to16(byte[] b) { int[] out = new int[16]; for (int i = 0; i < 16; i++) out[i] = (b[2*i] & 0xFF) | ((b[2*i+1] & 0xFF) << 8); return out; }
    private static void clampScalar(byte[] k) { k[0] &= 0xF8; k[31] &= 0x7F; k[31] |= 0x40; }
    private static void cswap(byte[] a, byte[] b) { for (int i = 0; i < 32; i++) { byte t = a[i]; a[i] = b[i]; b[i] = t; } }
    public static byte[] basePoint() { byte[] bp = new byte[32]; bp[0] = 9; return bp; }
}