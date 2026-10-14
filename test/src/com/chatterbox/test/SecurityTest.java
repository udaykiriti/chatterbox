package com.chatterbox.test;

import com.chatterbox.proto.IrcSanitizer;
import com.chatterbox.rms.PasswordObfuscator;

import junit.framework.TestCase;

/**
 * Security utility tests. JUnit 3 style, runs on any desktop JDK.
 *
 * Tests IRC protocol injection prevention and password obfuscation.
 */
public final class SecurityTest extends TestCase {

    // ------------------------------------------------------------------
    // IrcSanitizer tests
    // ------------------------------------------------------------------

    public void testSanitizeParamBasic() {
        assertEquals("normal", IrcSanitizer.sanitizeParam("normal"));
        assertEquals("with_spaces", IrcSanitizer.sanitizeParam("with spaces"));
        assertEquals("with_colon", IrcSanitizer.sanitizeParam("with:colon"));
        assertEquals("with_backslash", IrcSanitizer.sanitizeParam("with\\backslash"));
    }

    public void testSanitizeParamControlChars() {
        assertEquals("with__crlf", IrcSanitizer.sanitizeParam("with\r\ncrlf"));
        assertEquals("ctrlchar", IrcSanitizer.sanitizeParam("ctrl\000char"));
        assertEquals("ctrlchar", IrcSanitizer.sanitizeParam("ctrl\001char"));
    }

    public void testSanitizeParamLengthLimit() {
        String longInput = "a";
        for (int i = 0; i < 300; i++) {
            longInput += "a";
        }
        String result = IrcSanitizer.sanitizeParam(longInput);
        assertEquals(200, result.length());
    }

    public void testSanitizeParamNullAndEmpty() {
        assertEquals("", IrcSanitizer.sanitizeParam(null));
        assertEquals("", IrcSanitizer.sanitizeParam(""));
    }

    public void testSanitizeTrailingBasic() {
        assertEquals("normal message", IrcSanitizer.sanitizeTrailing("normal message"));
        assertEquals("with  crlf", IrcSanitizer.sanitizeTrailing("with\r\ncrlf"));
        assertEquals("ctrlchar", IrcSanitizer.sanitizeTrailing("ctrl\000char"));
    }

    public void testSanitizeTrailingLengthLimit() {
        String longInput = "a";
        for (int i = 0; i < 500; i++) {
            longInput += "a";
        }
        String result = IrcSanitizer.sanitizeTrailing(longInput);
        assertEquals(400, result.length());
    }

    public void testSanitizeTrailingAllowsSpaces() {
        // Trailing can have spaces (unlike middle params)
        assertEquals("hello world", IrcSanitizer.sanitizeTrailing("hello world"));
        assertEquals("with:colon", IrcSanitizer.sanitizeTrailing("with:colon"));
    }

    public void testSanitizeChannelBasic() {
        assertEquals("#channel", IrcSanitizer.sanitizeChannel("#channel"));
        assertEquals("#channel", IrcSanitizer.sanitizeChannel("channel"));
        assertEquals("#channelkey", IrcSanitizer.sanitizeChannel("#channel,key"));
    }

    public void testSanitizeChannelInvalidChars() {
        // The sanitizer removes spaces, colons, commas from channel names
        assertEquals("#channel", IrcSanitizer.sanitizeChannel("#ch:an nel"));
        assertEquals("#chan&nel", IrcSanitizer.sanitizeChannel("#ch,an&nel"));
    }

    public void testSanitizeChannelLengthLimit() {
        String longInput = "#";
        for (int i = 0; i < 300; i++) {
            longInput += "a";
        }
        String result = IrcSanitizer.sanitizeChannel(longInput);
        assertEquals(200, result.length());
    }

    public void testSanitizeNickBasic() {
        assertEquals("nick", IrcSanitizer.sanitizeNick("nick"));
        assertEquals("nick", IrcSanitizer.sanitizeNick("123nick"));
        assertEquals("nick-with-dash", IrcSanitizer.sanitizeNick("nick-with-dash"));
    }

    public void testSanitizeNickInvalidChars() {
        // @ and ! are not valid nick chars (they're prefix modes)
        assertEquals("nickhost", IrcSanitizer.sanitizeNick("nick@host"));
        assertEquals("nickuser", IrcSanitizer.sanitizeNick("nick!user"));
        // Space is not a valid nick char, gets removed
        assertEquals("nickspace", IrcSanitizer.sanitizeNick("nick space"));
    }

    public void testSanitizeNickValidSpecialChars() {
        // These are valid IRC nick chars
        assertEquals("nick[", IrcSanitizer.sanitizeNick("nick["));
        assertEquals("nick]", IrcSanitizer.sanitizeNick("nick]"));
        assertEquals("nick^", IrcSanitizer.sanitizeNick("nick^"));
        assertEquals("nick{", IrcSanitizer.sanitizeNick("nick{"));
        assertEquals("nick}", IrcSanitizer.sanitizeNick("nick}"));
        assertEquals("nick|", IrcSanitizer.sanitizeNick("nick|"));
        assertEquals("nick`", IrcSanitizer.sanitizeNick("nick`"));
        assertEquals("nick\\", IrcSanitizer.sanitizeNick("nick\\"));
    }

    public void testSanitizeNickLengthLimit() {
        String longInput = "n";
        for (int i = 0; i < 50; i++) {
            longInput += "i";
        }
        String result = IrcSanitizer.sanitizeNick(longInput);
        assertEquals(30, result.length());
    }

    public void testSanitizeMessageBasic() {
        assertEquals("hello world", IrcSanitizer.sanitizeMessage("hello world"));
        assertEquals("line1  line2", IrcSanitizer.sanitizeMessage("line1\r\nline2"));
        assertEquals("ctrlchar", IrcSanitizer.sanitizeMessage("ctrl\001char"));
    }

    public void testSanitizeMessageAllowsTabs() {
        assertEquals("hello\tworld", IrcSanitizer.sanitizeMessage("hello\tworld"));
    }

    public void testSanitizeMessageLengthLimit() {
        String longInput = "a";
        for (int i = 0; i < 500; i++) {
            longInput += "a";
        }
        String result = IrcSanitizer.sanitizeMessage(longInput);
        assertEquals(400, result.length());
    }

    public void testSanitizeCtcpBasic() {
        assertEquals("ACTION waves", IrcSanitizer.sanitizeCtcp("ACTION waves"));
        assertEquals("VERSION", IrcSanitizer.sanitizeCtcp("VERSION"));
    }

    public void testSanitizeCtcpStripsDelimiters() {
        assertEquals("ACTION test", IrcSanitizer.sanitizeCtcp("\001ACTION test\001"));
        assertEquals("ACTION test", IrcSanitizer.sanitizeCtcp("\002ACTION test\002"));
        // Delimiters are removed without space replacement
        assertEquals("nodelimiters", IrcSanitizer.sanitizeCtcp("no\001delimiters\002"));
    }

    public void testSanitizeCtcpStripsCrlf() {
        // CRLF is removed (not replaced with space)
        assertEquals("line1line2", IrcSanitizer.sanitizeCtcp("line1\r\nline2"));
    }

    public void testSanitizeCtcpLengthLimit() {
        String longInput = "a";
        for (int i = 0; i < 300; i++) {
            longInput += "a";
        }
        String result = IrcSanitizer.sanitizeCtcp(longInput);
        assertEquals(200, result.length());
    }

    // ------------------------------------------------------------------
    // PasswordObfuscator tests
    // ------------------------------------------------------------------

    public void testObfuscateDeobfuscateRoundTrip() {
        String[] passwords = {
            "simple",
            "complex-password-123!@#",
            "",
            "a",
            "unicode: caf\u00E9 \uD83C\uDF89"  // cafe emoji
        };
        for (String pw : passwords) {
            String obf = PasswordObfuscator.obfuscate(pw);
            String deobf = PasswordObfuscator.deobfuscate(obf);
            assertEquals("Failed for: " + pw, pw, deobf);
        }
    }

    public void testObfuscateEmptyReturnsEmpty() {
        assertEquals("", PasswordObfuscator.obfuscate(""));
        assertEquals("", PasswordObfuscator.obfuscate(null));
    }

    public void testDeobfuscateEmptyReturnsEmpty() {
        assertEquals("", PasswordObfuscator.deobfuscate(""));
        assertEquals("", PasswordObfuscator.deobfuscate(null));
    }

    public void testObfuscatedHasPrefix() {
        String obf = PasswordObfuscator.obfuscate("test");
        assertTrue(obf.startsWith("OBF:"));
    }

    public void testIsObfuscatedDetection() {
        assertTrue(PasswordObfuscator.isObfuscated("OBF:abc123"));
        assertFalse(PasswordObfuscator.isObfuscated("plaintext"));
        assertFalse(PasswordObfuscator.isObfuscated(""));
        assertFalse(PasswordObfuscator.isObfuscated(null));
    }

    public void testLegacyPlaintextMigration() {
        // Passwords without OBF: prefix are treated as legacy plaintext
        String legacy = "plaintext-password";
        String deobf = PasswordObfuscator.deobfuscate(legacy);
        assertEquals(legacy, deobf);
    }

    public void testCorruptedObfuscationReturnsEmpty() {
        // Corrupted hex
        String corrupted = "OBF:notvalidhex";
        assertEquals("", PasswordObfuscator.deobfuscate(corrupted));

        // Odd-length hex
        String oddHex = "OBF:abc";
        assertEquals("", PasswordObfuscator.deobfuscate(oddHex));

        // Valid hex but wrong key (simulated by corrupting the data)
        String validObf = PasswordObfuscator.obfuscate("test");
        String corruptedData = "OBF:" + validObf.substring(4, 6) + "00" + validObf.substring(8);
        // This should still deobfuscate to something (may be garbage), but not crash
        String result = PasswordObfuscator.deobfuscate(corruptedData);
        // Just verify it doesn't throw
        assertNotNull(result);
    }

    public void testLongPassword() {
        String longPw = "";
        for (int i = 0; i < 200; i++) {
            longPw += "a";
        }
        String obf = PasswordObfuscator.obfuscate(longPw);
        String deobf = PasswordObfuscator.deobfuscate(obf);
        assertEquals(longPw, deobf);
    }
}