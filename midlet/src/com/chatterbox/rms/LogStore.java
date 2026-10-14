package com.chatterbox.rms;

import java.util.Vector;

import com.chatterbox.core.ChannelState;
import com.chatterbox.core.Message;
import com.chatterbox.core.ServerState;
import com.chatterbox.core.Str;

/**
 * Optional local scrollback log, appended to RMS.
 *
 * Design constraints on a Series 40 / Symbian handset:
 *  - RMS is slow. We therefore buffer in RAM and flush only when the buffer
 *    reaches FLUSH_AT bytes, on window change, and on midlet exit. Writing every
 *    line individually would stall the UI thread for hundreds of milliseconds.
 *  - One store per server keeps any single store small enough to open fast.
 *  - The log is a strict ring of N records; record i holds one chunk of lines.
 *
 * All methods are best-effort: if RMS is full or missing, logging silently
 * disables itself rather than surfacing errors to the user.
 */
public final class LogStore {

    private static final int CHUNK_RECORDS = 32;
    private static final int CHUNK_BYTES = 1800;
    private static final int FLUSH_AT = 1400;

    private final String storeName;
    private final RmsStore store;
    private final StringBuffer buffer = new StringBuffer(FLUSH_AT + 512);
    private boolean enabled;
    private boolean broken;
    /** Round-robin write cursor for the CHUNK_RECORDS log slots. */
    private int slot;

    public LogStore(String serverId) {
        String safe = sanitize(serverId);
        storeName = "chatterbox.log." + safe;
        store = new RmsStore(storeName);
    }

    /** Keep the store name legal on all firmwares. */
    private static String sanitize(String s) {
        StringBuffer sb = new StringBuffer(s.length());
        for (int i = 0; i < s.length() && sb.length() < 20; i++) {
            char c = s.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '.' || c == '-';
            sb.append(ok ? c : '_');
        }
        if (sb.length() == 0) {
            sb.append('x');
        }
        return sb.toString();
    }

    public void setEnabled(boolean e) {
        this.enabled = e;
        if (!e) {
            flush();
        }
    }

    public boolean isEnabled() {
        return enabled && !broken;
    }

    public String storeName() {
        return storeName;
    }

    /** Queue one window's newest messages. Never throws. */
    public synchronized void log(ServerState server, ChannelState window, Message m) {
        if (!isEnabled()) {
            return;
        }
        try {
            buffer.append('[');
            buffer.append(m.timeStamp());
            buffer.append("] <");
            buffer.append(window.getName());
            buffer.append("> ");
            if (m.nick.length() > 0) {
                buffer.append(m.nick);
                buffer.append(": ");
            }
            buffer.append(m.text);
            buffer.append('\n');
            if (buffer.length() >= FLUSH_AT) {
                flushLocked();
            }
        } catch (Throwable t) {
            broken = true;
        }
    }

    /** Persist the pending chunk. Safe to call from any thread. */
    public synchronized void flush() {
        if (broken || buffer.length() == 0) {
            return;
        }
        try {
            flushLocked();
        } catch (Throwable t) {
            broken = true;
        }
    }

    private void flushLocked() {
        if (buffer.length() == 0) {
            return;
        }
        byte[] bytes = com.chatterbox.proto.Utf8.encode(buffer.toString());
        buffer.setLength(0);
        int written = 0;
        while (written < bytes.length) {
            int n = Math.min(CHUNK_BYTES, bytes.length - written);
            byte[] chunk = new byte[n];
            System.arraycopy(bytes, written, chunk, 0, n);
            written += n;
            // Round-robin over a fixed slot count. After the first full lap the
            // slot we land on is by definition the oldest chunk, so the log is
            // a bounded ring without needing to scan for a minimum id.
            boolean ok = store.write(RmsStoreIds.BASE + slot, chunk);
            slot = (slot + 1) % CHUNK_RECORDS;
            if (!ok) {
                throw new IllegalStateException("rms write failed");
            }
        }
    }

    /** Read the whole log back, in slot order so chunks stay chronological. */
    public synchronized String readAll(int maxChars) {
        StringBuffer sb = new StringBuffer(maxChars);
        for (int i = 0; i < CHUNK_RECORDS; i++) {
            byte[] d = store.read(RmsStoreIds.BASE + i);
            if (d == null) {
                continue;
            }
            sb.append(com.chatterbox.proto.Utf8.decode(d));
            if (sb.length() >= maxChars) {
                break;
            }
        }
        return sb.length() > maxChars ? sb.substring(0, maxChars) : sb.toString();
    }

    public synchronized void clear() {
        buffer.setLength(0);
        store.delete();
    }

    /** Approximate on-device size, for the settings screen. */
    public synchronized int sizeHint() {
        long[] ids = store.ids();
        return ids.length * CHUNK_BYTES;
    }

    /** Delete every chatterbox store. Offered on the settings screen. */
    public static void purgeAll() {
        RmsStore probe = new RmsStore("chatterbox.cfg");
        String[] names = probe.list();
        for (int i = 0; i < names.length; i++) {
            String n = names[i];
            if (n.startsWith("chatterbox.log.") || n.equals("chatterbox.log")) {
                new RmsStore(n).delete();
            }
        }
    }

    /** Format a vector of channel names for the log header. */
    public static String header(ServerState s) {
        Vector v = s.channelWindows();
        Vector names = new Vector();
        for (int i = 0; i < v.size(); i++) {
            names.addElement(((ChannelState) v.elementAt(i)).getName());
        }
        return "-- log for " + s.getHost() + " " + Str.join(names, ",") + " --";
    }

    /** Log record ids live above this so prefs/profiles never collide. */
    static final class RmsStoreIds {
        static final long BASE = 500L;
        private RmsStoreIds() {
        }
    }
}
