package com.chatterbox.crypto;

import java.util.Vector;

/**
 * Double Ratchet implementation for end-to-end encryption.
 */
public final class DoubleRatchet {

    private DoubleRatchet() {}

    public static final int MAX_SKIP = 1000;

    public static final class Session {
        public byte[] rootKey;
        public byte[] dhPrivate;
        public byte[] dhPublic;
        public byte[] theirDhPublic;
        public byte[] sendingChainKey;
        public byte[] receivingChainKey;
        public int Ns = 0;
        public int Nr = 0;
        public int PN = 0;
        public Vector skippedMessageKeys = new Vector();
        public byte[] ad;
    }

    public static final class MessageKeys {
        public final byte[] cipherKey;
        public final int messageNumber;
        
        MessageKeys(byte[] cipherKey, int msgNum) {
            this.cipherKey = cipherKey;
            this.messageNumber = msgNum;
        }
    }

    public static final class SkippedKey {
        public final byte[] theirDhPublic;
        public final int messageNumber;
        public final byte[] cipherKey;
        
        SkippedKey(byte[] dhPub, int msgNum, byte[] ck) {
            this.theirDhPublic = dhPub;
            this.messageNumber = msgNum;
            this.cipherKey = ck;
        }
    }

    public static Session initializeAlice(byte[] sharedSecret, 
                                           byte[] alicePrivate, 
                                           byte[] bobPublic,
                                           byte[] ad) {
        Session s = new Session();
        s.ad = ad;
        s.dhPrivate = alicePrivate;
        s.theirDhPublic = bobPublic;

        byte[] dhOut = Curve25519.keyAgreement(alicePrivate, bobPublic);
        byte[] rk_ck = HKDF.hkdf(sharedSecret, dhOut, null, 64);
        s.rootKey = new byte[32];
        System.arraycopy(rk_ck, 0, s.rootKey, 0, 32);
        s.sendingChainKey = new byte[32];
        System.arraycopy(rk_ck, 32, s.sendingChainKey, 0, 32);
        s.receivingChainKey = null;
        return s;
    }

    public static Session initializeBob(byte[] sharedSecret,
                                         byte[] bobPrivate,
                                         byte[] alicePublic,
                                         byte[] ad) {
        Session s = new Session();
        s.ad = ad;
        s.dhPrivate = bobPrivate;
        s.theirDhPublic = alicePublic;

        byte[] dhOut = Curve25519.keyAgreement(bobPrivate, alicePublic);
        byte[] rk_ck = HKDF.hkdf(sharedSecret, dhOut, null, 64);
        s.rootKey = new byte[32];
        System.arraycopy(rk_ck, 0, s.rootKey, 0, 32);
        s.receivingChainKey = new byte[32];
        System.arraycopy(rk_ck, 32, s.receivingChainKey, 0, 32);
        s.sendingChainKey = null;
        return s;
    }

    public static byte[] encrypt(Session session, byte[] plaintext, byte[] ad) {
        if (session.sendingChainKey == null) {
            ratchetEncrypt(session);
        }

        MessageKeys mk = chainKeyToMessageKeys(session.sendingChainKey);
        session.sendingChainKey = nextChainKey(session.sendingChainKey);
        session.Ns++;

        byte[] nonce = buildNonce(session.Ns);
        // ChaCha20Poly1305 encrypts plaintext + 16 byte tag
        byte[] ciphertextWithTag = new byte[plaintext.length + 16];
        int ctLen = ChaCha20Poly1305.encrypt(mk.cipherKey, nonce, 
                combineAd(session.ad, ad), plaintext, ciphertextWithTag);
        
        byte[] result = new byte[4 + ctLen];
        write32(result, 0, session.Ns);
        System.arraycopy(ciphertextWithTag, 0, result, 4, ctLen);
        return result;
    }

    public static byte[] decrypt(Session session, byte[] ciphertextWithHeader, byte[] ad) {
        if (ciphertextWithHeader == null || ciphertextWithHeader.length < 4 + 16) {
            return null;
        }

        int msgNum = read32(ciphertextWithHeader, 0);
        int ctOffset = 4;
        int ctLen = ciphertextWithHeader.length - ctOffset;

        MessageKeys mk = trySkippedKey(session, msgNum);
        
        if (mk == null) {
            if (session.receivingChainKey == null || msgNum < session.Nr) {
                if (!skipMessageKeys(session, msgNum)) return null;
                mk = trySkippedKey(session, msgNum);
                if (mk == null) return null;
            } else if (msgNum > session.Nr) {
                if (!skipMessageKeys(session, msgNum)) return null;
                mk = trySkippedKey(session, msgNum);
                if (mk == null) return null;
            } else {
                mk = chainKeyToMessageKeys(session.receivingChainKey);
                session.receivingChainKey = nextChainKey(session.receivingChainKey);
                session.Nr++;
            }
        }

        if (mk == null) return null;

        byte[] nonce = buildNonce(msgNum);
        byte[] combinedAd = combineAd(session.ad, ad);
        byte[] plaintext = new byte[ciphertextWithHeader.length - ctOffset - 16];
        
        int ptLen = ChaCha20Poly1305.decrypt(mk.cipherKey, nonce, 
                combineAd(session.ad, ad), 
                ciphertextWithHeader, ctOffset, ciphertextWithHeader.length - ctOffset,
                plaintext);
        
        if (ptLen < 0) return null;

        byte[] result = new byte[ptLen];
        System.arraycopy(plaintext, 0, result, 0, ptLen);
        return result;
    }

    public static void ratchetEncrypt(Session session) {
        byte[] newPrivate = Curve25519.generatePrivateKey();
        byte[] newPublic = Curve25519.derivePublicKey(newPrivate);
        byte[] dhOut = Curve25519.keyAgreement(newPrivate, session.theirDhPublic);
        
        byte[] rk_ck = HKDF.hkdf(session.rootKey, dhOut, session.ad, 64);
        session.rootKey = new byte[32];
        System.arraycopy(rk_ck, 0, session.rootKey, 0, 32);
        session.sendingChainKey = new byte[32];
        System.arraycopy(rk_ck, 32, session.sendingChainKey, 0, 32);
        
        session.dhPrivate = newPrivate;
        session.dhPublic = newPublic;
        session.Ns = 0;
    }

    public static void ratchetDecrypt(Session session, byte[] newTheirPublic) {
        byte[] dhOut = Curve25519.keyAgreement(session.dhPrivate, newTheirPublic);
        
        byte[] rk_ck = HKDF.hkdf(session.rootKey, dhOut, session.ad, 64);
        session.rootKey = new byte[32];
        System.arraycopy(rk_ck, 0, session.rootKey, 0, 32);
        session.receivingChainKey = new byte[32];
        System.arraycopy(rk_ck, 32, session.receivingChainKey, 0, 32);
        
        session.theirDhPublic = newTheirPublic;
        session.PN = session.Nr;
        session.Nr = 0;
        
        session.dhPrivate = Curve25519.generatePrivateKey();
        session.dhPublic = Curve25519.derivePublicKey(session.dhPrivate);
    }

    private static MessageKeys chainKeyToMessageKeys(byte[] chainKey) {
        byte[] km = HKDF.hmacSha256(chainKey, new byte[]{0x01});
        return new MessageKeys(km, 0);
    }

    private static byte[] nextChainKey(byte[] chainKey) {
        return HKDF.hmacSha256(chainKey, new byte[]{0x02});
    }

    private static boolean skipMessageKeys(Session session, int until) {
        if (session.receivingChainKey == null) return false;
        
        while (session.Nr < until) {
            MessageKeys mk = chainKeyToMessageKeys(session.receivingChainKey);
            session.receivingChainKey = nextChainKey(session.receivingChainKey);
            session.Nr++;
            
            SkippedKey sk = new SkippedKey(
                session.theirDhPublic, session.Nr, mk.cipherKey);
            session.skippedMessageKeys.addElement(sk);
            
            if (session.skippedMessageKeys.size() > MAX_SKIP) {
                session.skippedMessageKeys.removeElementAt(0);
            }
        }
        return true;
    }

    private static MessageKeys trySkippedKey(Session session, int msgNum) {
        for (int i = 0; i < session.skippedMessageKeys.size(); i++) {
            SkippedKey sk = (SkippedKey) session.skippedMessageKeys.elementAt(i);
            if (sk.messageNumber == msgNum && 
                equalBytes(sk.theirDhPublic, session.theirDhPublic)) {
                session.skippedMessageKeys.removeElementAt(i);
                return new MessageKeys(sk.cipherKey, msgNum);
            }
        }
        return null;
    }

    private static boolean equalBytes(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) return false;
        }
        return true;
    }

    private static byte[] buildNonce(int messageNumber) {
        byte[] nonce = new byte[12];
        write32(nonce, 8, messageNumber);
        return nonce;
    }

    private static byte[] combineAd(byte[] ad1, byte[] ad2) {
        if (ad1 == null && ad2 == null) return new byte[0];
        if (ad1 == null) return ad2;
        if (ad2 == null) return ad1;
        byte[] combined = new byte[ad1.length + ad2.length];
        System.arraycopy(ad1, 0, combined, 0, ad1.length);
        System.arraycopy(ad2, 0, combined, ad1.length, ad2.length);
        return combined;
    }

    private static int read32(byte[] buf, int off) {
        return ((buf[off] & 0xFF) << 24) |
               ((buf[off+1] & 0xFF) << 16) |
               ((buf[off+2] & 0xFF) << 8) |
               (buf[off+3] & 0xFF);
    }

    private static void write32(byte[] buf, int off, int val) {
        buf[off] = (byte) ((val >> 24) & 0xFF);
        buf[off+1] = (byte) ((val >> 16) & 0xFF);
        buf[off+2] = (byte) ((val >> 8) & 0xFF);
        buf[off+3] = (byte) (val & 0xFF);
    }
}