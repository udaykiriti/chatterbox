package com.chatterbox.rms;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Vector;

import com.chatterbox.core.Str;
import com.chatterbox.net.ConnectionConfig;

/**
 * Persisted profile list plus global preferences.
 *
 * Record layout in store "chatterbox.cfg":
 *   id 0            magic + version
 *   id 1            global prefs (theme, alerts, timestamps, urls, logging)
 *   id 2            profile index
 *   id 1000 + i     profile i
 *
 * Assumptions:
 *  - The profile list is small (< 20). We read and rewrite it as a single blob
 *    on every change; simpler and safer than incremental updates.
 *  - Passwords are obfuscated in RMS using PasswordObfuscator (XOR with device key).
 *    This is NOT encryption - it prevents casual reading but not a determined attacker.
 *    CLDC 1.1 does not provide crypto APIs.
 */
public final class ProfileStore {

    public static final String STORE = "chatterbox.cfg";
    public static final long REC_MAGIC = 0;
    public static final long REC_PREFS = 1;
    public static final long REC_PROFILES = 2;
    public static final long PROFILE_BASE = 1000L;

    private static final int MAGIC = 0x43423100; // "CB1\0"
    private static final int VERSION = 1;

    private final RmsStore store;

    // global preferences
    private int theme = 0;            // 0 dark, 1 light
    private boolean alertVibrate = true;
    private boolean alertBeep = true;
    private boolean showTimestamps = true;
    private boolean detectUrls = true;
    private boolean logToRms = false;
    private String gatewayHost = "";
    private int gatewayPort = 0;
    private String gatewayPassword = "";
    private boolean preferTls = true;
    private long floodDelayMs = 700;
    private int floodBurst = 4;

    private boolean requireTls = false;

    private boolean dirty;

    public ProfileStore() {
        store = new RmsStore(STORE);
        loadPrefs();
    }

    // ---- global prefs ----

    public int getTheme() {
        return theme;
    }

    public void setTheme(int t) {
        theme = (t == 1) ? 1 : 0;
        dirty = true;
    }

    public boolean isAlertVibrate() {
        return alertVibrate;
    }

    public void setAlertVibrate(boolean b) {
        alertVibrate = b;
        dirty = true;
    }

    public boolean isAlertBeep() {
        return alertBeep;
    }

    public void setAlertBeep(boolean b) {
        alertBeep = b;
        dirty = true;
    }

    public boolean isShowTimestamps() {
        return showTimestamps;
    }

    public void setShowTimestamps(boolean b) {
        showTimestamps = b;
        dirty = true;
    }

    public boolean isDetectUrls() {
        return detectUrls;
    }

    public void setDetectUrls(boolean b) {
        detectUrls = b;
        dirty = true;
    }

    public boolean isLogToRms() {
        return logToRms;
    }

    public void setLogToRms(boolean b) {
        logToRms = b;
        dirty = true;
    }

    public String getGatewayHost() {
        return gatewayHost;
    }

    public int getGatewayPort() {
        return gatewayPort;
    }

    public String getGatewayPassword() {
        return gatewayPassword;
    }

    public void setGatewayPassword(String pw) {
        gatewayPassword = pw == null ? "" : pw;
        dirty = true;
    }

    /** Get the gateway password, deobfuscated for use. */
    public String getGatewayPasswordDeobf() {
        return PasswordObfuscator.deobfuscate(gatewayPassword);
    }

    /** Set the gateway password, storing it obfuscated. */
    public void setGatewayPasswordObf(String pw) {
        gatewayPassword = PasswordObfuscator.obfuscate(pw);
        dirty = true;
    }

    public boolean isPreferTls() {
        return preferTls;
    }

    public void setPreferTls(boolean b) {
        preferTls = b;
        dirty = true;
    }

    public boolean isRequireTls() {
        return requireTls;
    }

    public void setRequireTls(boolean b) {
        requireTls = b;
        dirty = true;
    }

    public void setAlerts(boolean vibrate, boolean beep) {
        alertVibrate = vibrate;
        alertBeep = beep;
        dirty = true;

    }

    public void setGateway(String host, int port, String password) {
        gatewayHost = host == null ? "" : host.trim();
        gatewayPort = port;
        setGatewayPasswordObf(password);
    }

    public void setFlood(long delayMs, int burst) {
        floodDelayMs = delayMs < 100 ? 100 : delayMs;
        floodBurst = burst < 1 ? 1 : burst;
        dirty = true;
    }

    public ConnectionConfig toConfig() {
        ConnectionConfig c = new ConnectionConfig();
        c.gatewayHost = gatewayHost;
        c.gatewayPort = gatewayPort;
        c.gatewayPassword = getGatewayPasswordDeobf();
        c.preferTls = preferTls;
        c.requireTls = requireTls;
        c.floodDelayMs = floodDelayMs;
        c.floodBurst = floodBurst;
        return c;
    }

    private void loadPrefs() {
        byte[] data = store.read(REC_PREFS);
        if (data == null) {
            return;
        }
        try {
            java.io.DataInputStream in = RmsStore.in(data);
            theme = in.readInt();
            alertVibrate = in.readBoolean();
            alertBeep = in.readBoolean();
            showTimestamps = in.readBoolean();
            detectUrls = in.readBoolean();
            logToRms = in.readBoolean();
            gatewayHost = RmsStore.readString(in);
            gatewayPort = in.readInt();
            gatewayPassword = RmsStore.readString(in);
            preferTls = in.readBoolean();
            // requireTls field added later; default to false for old records
            try {
                requireTls = in.readBoolean();
            } catch (IOException eof) {
                requireTls = false;
            }
            floodDelayMs = in.readInt();
            floodBurst = in.readInt();
            in.close();
        } catch (Throwable t) {
            // Corrupt prefs: keep the defaults rather than refusing to start.
        }
    }

    /** @return true when the prefs were written. */
    public boolean savePrefs() {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(256);
        try {
            DataOutputStream out = RmsStore.out(bos);
            out.writeInt(theme);
            out.writeBoolean(alertVibrate);
            out.writeBoolean(alertBeep);
            out.writeBoolean(showTimestamps);
            out.writeBoolean(detectUrls);
            out.writeBoolean(logToRms);
            RmsStore.writeString(out, gatewayHost);
            out.writeInt(gatewayPort);
            RmsStore.writeString(out, gatewayPassword);
            out.writeBoolean(preferTls);
            out.writeBoolean(requireTls);
            out.writeLong(floodDelayMs);
            out.writeInt(floodBurst);
            out.flush();
            out.close();
            dirty = false;
            return store.write(REC_PREFS, bos.toByteArray());
        } catch (IOException e) {
            return false;
        }
    }

    // ---- profiles ----

    public boolean isDirty() {
        return dirty;
    }

    /** @return Vector of Profile in insertion order. Never null. */
    public Vector loadProfiles() {
        Vector v = new Vector();
        byte[] data = store.read(REC_PROFILES);
        if (data != null) {
            try {
                java.io.DataInputStream in = RmsStore.in(data);
                int n = in.readInt();
                if (n < 0 || n > 64) {
                    n = 0;
                }
                for (int i = 0; i < n; i++) {
                    long id = in.readLong();
                    Profile p = Profile.decode(in);
                    if (p != null) {
                        p.setId(id);
                        v.addElement(p);
                    }
                }
                in.close();
            } catch (Throwable t) {
                // Fall through to per-record recovery below.
            }
        }
        if (v.size() == 0) {
            recoverPerRecord(v);
        }
        return v;
    }

    private void recoverPerRecord(Vector into) {
        long[] ids = store.ids();
        for (int i = 0; i < ids.length; i++) {
            long id = ids[i];
            if (id < PROFILE_BASE) {
                continue;
            }
            byte[] data = store.read(id);
            if (data == null) {
                continue;
            }
            try {
                java.io.DataInputStream in = RmsStore.in(data);
                Profile p = Profile.decode(in);
                if (p != null) {
                    p.setId(id);
                    into.addElement(p);
                }
                in.close();
            } catch (Throwable t) {
                // Skip the damaged profile, keep the rest.
            }
        }
    }

    /**
     * Replace the stored profile list.
     * @return true on success.
     */
    public boolean saveProfiles(Vector profiles) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(1024);
        try {
            DataOutputStream out = RmsStore.out(bos);
            out.writeInt(profiles.size());
            for (int i = 0; i < profiles.size(); i++) {
                Profile p = (Profile) profiles.elementAt(i);
                out.writeLong(p.getId());
                p.encode(out);
            }
            out.flush();
            out.close();
            dirty = false;
            return store.write(REC_PROFILES, bos.toByteArray());
        } catch (IOException e) {
            return false;
        }
    }

    public void deleteProfile(long id) {
        store.remove(id);
    }

    /** Number of profile slots in use. */
    public int count() {
        return loadProfiles().size();
    }

    public static Profile defaultProfile() {
        Profile p = new Profile();
        p.setName("Gateway");
        p.setHost("irc.libera.chat");
        p.setPort(6697);
        p.setSecure(true);
        p.setNick("chatterbox");
        p.setAltNick("chatterbox_");
        p.setChannels("#chatterbox");
        return p;
    }

    /** Convenience: join a Vector of profile names. */
    public static String names(Vector profiles) {
        Vector names = new Vector();
        for (int i = 0; i < profiles.size(); i++) {
            names.addElement(((Profile) profiles.elementAt(i)).getName());
        }
        return Str.join(names, ", ");
    }

    // ---- Export/Import ----

    /** Export all profiles to a byte array (UTF-8 pipe-separated format). */
    public byte[] exportProfiles(Vector profiles) {
        StringBuffer sb = new StringBuffer(2048);
        sb.append("CHATTERBOX-PROFILES-1");
        sb.append('\n');
        sb.append(profiles.size());
        sb.append('\n');
        for (int i = 0; i < profiles.size(); i++) {
            Profile p = (Profile) profiles.elementAt(i);
            sb.append(escape(p.getName())); sb.append('|');
            sb.append(escape(p.getHost())); sb.append('|');
            sb.append(p.getPort()); sb.append('|');
            sb.append(p.isSecure() ? "1" : "0"); sb.append('|');
            sb.append(escape(p.getNick())); sb.append('|');
            sb.append(escape(p.getAltNick())); sb.append('|');
            sb.append(escape(p.getPassword())); sb.append('|');
            sb.append(escape(p.getRealName())); sb.append('|');
            sb.append(escape(p.getChannels())); sb.append('|');
            sb.append(p.isEnabled() ? "1" : "0"); sb.append('\n');
        }
        return com.chatterbox.proto.Utf8.encode(sb.toString());
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        StringBuffer sb = new StringBuffer(s.length() + 4);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '|' || c == '\\' || c == '\n' || c == '\r') {
                sb.append('\\');
                if (c == '\n') {
                    sb.append('n');
                } else if (c == '\r') {
                    sb.append('r');
                } else {
                    sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String unescape(String s) {
        if (s == null) {
            return "";
        }
        StringBuffer sb = new StringBuffer(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(i + 1);
                if (n == 'n') {
                    sb.append('\n');
                    i++;
                } else if (n == 'r') {
                    sb.append('\r');
                    i++;
                } else {
                    sb.append(n);
                    i++;
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Import profiles from exported byte array. Returns number imported. */
    public int importProfiles(byte[] data, java.util.Vector into) {
        if (data == null) {
            return 0;
        }
        String s = com.chatterbox.proto.Utf8.decode(data);
        String[] lines = splitLines(s);
        if (lines.length < 2 || !lines[0].equals("CHATTERBOX-PROFILES-1")) {
            return 0;
        }
        int count = 0;
        for (int i = 1; i < lines.length; i++) {
            String[] parts = splitPipe(lines[i]);
            if (parts.length >= 10) {
                Profile p = new Profile();
                p.setName(unescape(parts[0]));
                p.setHost(unescape(parts[1]));
                p.setPort(Str.parseInt(parts[2], 6667));
                p.setSecure("1".equals(parts[3]));
                p.setNick(unescape(parts[4]));
                p.setAltNick(unescape(parts[5]));
                // Password is imported as plaintext but will be stored obfuscated
                String plainPassword = unescape(parts[6]);
                p.setPassword(plainPassword);
                p.setRealName(unescape(parts[7]));
                p.setChannels(unescape(parts[8]));
                p.setEnabled("1".equals(parts[9]));
                if (p.validate().length() == 0) {
                    into.addElement(p);
                    count++;
                }
            }
        }
        return count;
    }

    private static String[] splitLines(String s) {
        java.util.Vector v = new java.util.Vector();
        int start = 0;
        for (int i = 0; i <= s.length(); i++) {
            if (i == s.length() || s.charAt(i) == '\n') {
                v.addElement(s.substring(start, i));
                start = i + 1;
            }
        }
        String[] out = new String[v.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = (String) v.elementAt(i);
        }
        return out;
    }

    private static String[] splitPipe(String s) {
        java.util.Vector v = new java.util.Vector();
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '|' && (i == 0 || s.charAt(i - 1) != '\\')) {
                v.addElement(s.substring(start, i));
                start = i + 1;
            }
        }
        v.addElement(s.substring(start));
        String[] out = new String[v.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = (String) v.elementAt(i);
        }
        return out;
    }
}