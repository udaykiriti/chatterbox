package com.chatterbox.core;

/**
 * Fixed-capacity ring buffer of {@link Message}. One per channel and one for
 * each private-message tab.
 *
 * Assumptions:
 *  - Capacity is fixed at construction (200 for chat history per the spec).
 *  - All methods are synchronized; the reader thread writes while the MIDlet
 *    thread reads.
 *  - Dropping the oldest line is silent. An unread counter tells the user
 *    activity happened even if it scrolled past.
 *  - We also track a monotonically increasing sequence number so the view can
 *    tell "am I looking at the newest message?" without comparing indices,
 *    which is impossible once the ring wraps.
 */
public final class MessageRing {

    public static final int DEFAULT_CAPACITY = 200;

    private Message[] items;
    private int head;      // index of the oldest item
    private int size;
    private long seq = 0;  // total ever added

    public MessageRing() {
        this(DEFAULT_CAPACITY);
    }

    public MessageRing(int capacity) {
        if (capacity < 1) {
            capacity = 1;
        }
        items = new Message[capacity];
    }

    public synchronized void add(Message m) {
        if (m == null) {
            return;
        }
        if (size == items.length) {
            // Full: overwrite the oldest, advance head.
            items[head] = m;
            head = (head + 1) % items.length;
        } else {
            items[(head + size) % items.length] = m;
            size++;
        }
        seq++;
    }

    public synchronized Message get(int i) {
        if (i < 0 || i >= size) {
            return null;
        }
        return items[(head + i) % items.length];
    }

    public synchronized int size() {
        return size;
    }

    public synchronized int capacity() {
        return items.length;
    }

    public synchronized long sequence() {
        return seq;
    }

    public synchronized void clear() {
        for (int i = 0; i < items.length; i++) {
            items[i] = null;
        }
        head = 0;
        size = 0;
        // seq intentionally not reset: the view uses it as a change token.
    }

    /**
     * Copy to an array, oldest first. Used by the renderer so it can work
     * outside the lock.
     */
    public synchronized Message[] snapshot() {
        Message[] out = new Message[size];
        for (int i = 0; i < size; i++) {
            out[i] = items[(head + i) % items.length];
        }
        return out;
    }

    /**
     * Compact the ring into 'capacity' or fewer messages, keeping the most
     * recent ones. Used by the RMS log writer.
     */
    public synchronized void dumpTo(StringBuffer sb, int maxChars) {
        int total = 0;
        for (int i = size - 1; i >= 0; i--) {
            Message m = get(i);
            if (m == null) {
                continue;
            }
            String line = "[" + m.timeStamp() + "] " + m.nick + " " + m.text + "\n";
            if (total + line.length() > maxChars && total > 0) {
                break;
            }
            sb.insert(0, line);
            total += line.length();
        }
    }
}
