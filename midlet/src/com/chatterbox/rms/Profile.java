package com.chatterbox.rms;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import com.chatterbox.core.Str;

/**
 * One server profile: how to connect, who to be, what to join.
 *
 * Version-tagged fields are appended, never reordered, so a profile written by
 * an older build still decodes. The decoder tolerates EOF at any field.
 */
public final class Profile {

    private long id;
    private String name = "profile";
    private String host = "";
    private int port = 6697;
    private boolean secure = true;
    private String nick = "";
    private String altNick = "";
    private String password = "";
    private String realName = "";
    private String channels = "";
    private boolean enabled = true;
    private boolean e2eOn;
    private String e2eKey = "";

    public Profile() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String n) {
        this.name = Str.trim(n);
    }

    public String getHost() {
        return host;
    }

    public void setHost(String h) {
        this.host = Str.trim(h);
    }

    public int getPort() {
        return port;
    }

    public void setPort(int p) {
        this.port = (p > 0 && p < 65536) ? p : 6667;
    }

    public boolean isSecure() {
        return secure;
    }

    public void setSecure(boolean s) {
        this.secure = s;
    }

    public String getNick() {
        return nick;
    }

    public void setNick(String n) {
        this.nick = Str.trim(n);
    }

    public String getAltNick() {
        return altNick;
    }

    public void setAltNick(String n) {
        this.altNick = Str.trim(n);
    }

    public String getPassword() {
        return password;
    }

    /** Get the password for use in connections (plaintext). */
    public String getPasswordPlain() {
        return password; // Already deobfuscated by decode()
    }

    public void setPassword(String p) {
        this.password = p == null ? "" : p;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String r) {
        this.realName = r == null ? "" : r;
    }

    public String getChannels() {
        return channels;
    }

    public void setChannels(String c) {
        this.channels = c == null ? "" : c.trim();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean e) {
        this.enabled = e;
    }

    /** Pre-shared-key end-to-end encryption for this profile. */
    public boolean isE2eOn() {
        return e2eOn;
    }

    public void setE2eOn(boolean on) {
        this.e2eOn = on;
    }

    /** E2E passphrase; shared out-of-band with every chat partner. */
    public String getE2eKey() {
        return e2eKey;
    }

    public void setE2eKey(String k) {
        this.e2eKey = k == null ? "" : k;
    }

    /** Stable key for the connection, also used in thread names. */
    public String serverId() {
        return host + ":" + port;
    }

    /** Validate and normalise. @return an error string, or "" when valid. */
    public String validate() {
        if (host.length() == 0) {
            return "Server address is required.";
        }
        for (int i = 0; i < host.length(); i++) {
            char c = host.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '.' || c == '-' || c == ':';
            if (!ok) {
                return "Server address has an invalid character: " + c;
            }
        }
        if (port < 1 || port > 65535) {
            return "Port must be between 1 and 65535.";
        }
        if (nick.length() == 0) {
            return "Nickname is required.";
        }
        if (nick.length() > 30) {
            return "Nickname must be 30 characters or fewer.";
        }
        for (int i = 0; i < nick.length(); i++) {
            char c = nick.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '[' || c == ']'
                    || c == '\\' || c == '`' || c == '^' || c == '{' || c == '}';
            if (!ok) {
                return "Nickname has an invalid character: " + c;
            }
        }
        if (nick.charAt(0) >= '0' && nick.charAt(0) <= '9') {
            return "Nickname cannot start with a digit.";
        }
        return "";
    }

    public void encode(DataOutputStream out) throws IOException {
        RmsStore.writeString(out, name);
        RmsStore.writeString(out, host);
        out.writeInt(port);
        out.writeBoolean(secure);
        RmsStore.writeString(out, nick);
        RmsStore.writeString(out, altNick);
        // Store password obfuscated in RMS
        RmsStore.writeString(out, PasswordObfuscator.obfuscate(password));
        RmsStore.writeString(out, realName);
        RmsStore.writeString(out, channels);
        out.writeBoolean(enabled);
        // E2E settings (appended in v1.3; decode tolerates records without them)
        RmsStore.writeString(out, PasswordObfuscator.obfuscate(e2eKey));
        out.writeBoolean(e2eOn);
    }

    /**
     * @return the decoded profile, or null when the stream is unusable.
     */
    public static Profile decode(DataInputStream in) {
        try {
            Profile p = new Profile();
            p.name = RmsStore.readString(in);
            p.host = RmsStore.readString(in);
            p.port = in.readInt();
            p.secure = in.readBoolean();
            p.nick = RmsStore.readString(in);
            p.altNick = RmsStore.readString(in);
            // Password is stored obfuscated in RMS; deobfuscate on read
            p.password = PasswordObfuscator.deobfuscate(RmsStore.readString(in));
            p.realName = RmsStore.readString(in);
            p.channels = RmsStore.readString(in);
            try {
                p.enabled = in.readBoolean();
            } catch (IOException eof) {
                p.enabled = true; // older record without the field
            }
            try {
                p.e2eKey = PasswordObfuscator.deobfuscate(RmsStore.readString(in));
                p.e2eOn = in.readBoolean();
            } catch (IOException eof) {
                p.e2eKey = ""; // older record without E2E fields
                p.e2eOn = false;
            }
            if (p.host.length() == 0) {
                return null;
            }
            return p;
        } catch (Throwable t) {
            return null;
        }
    }

    public String toString() {
        return name + " (" + host + ":" + port + (secure ? " ssl" : "") + ")";
    }
}
