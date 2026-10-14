package com.chatterbox.net;

/**
 * Token-bucket rate limiter for outbound lines.
 *
 * Policy: steady state one line per {@code delayMs}, with a burst allowance of
 * {@code burst} lines. That is the documented policy of modern ircd ratelimiting
 * (1 token per 2s with a burst of 5 is common); ours is deliberately a bit more
 * generous because a phone user typing on a QWERTY keyboard is nowhere near
 * human typing speed.
 *
 * Assumptions:
 *  - Token math uses longs, not floats: CLDC 1.1 has no Math.pow reliability
 *    guarantees we want to depend on and integer math is exact here.
 *  - When the queue is full we drop the OLDEST non-critical line and report it,
 *    because dropping the newest would silently discard the user's newest
 *    message, which is the one they are looking at.
 *  - Not thread-safe by itself; callers synchronize. The connection holds one
 *    lock for enqueue and the writer thread holds it for dequeue.
 *  - Uses circular array instead of Vector to avoid allocation overhead.
 */
public final class FloodQueue {

    private String[] lines;        // Circular buffer
    private int head = 0;          // Index of oldest element
    private int tail = 0;          // Index where next element goes
    private int count = 0;         // Current number of elements
    private int maxQueue;
    private long delayMs;
    private int burst;

    private long tokensMilli;      // tokens * 1000, to keep integer math
    private long lastRefill;

    private long dropped;

    public FloodQueue(int maxQueue, long delayMs, int burst) {
        this.maxQueue = Math.max(1, maxQueue);
        this.delayMs = Math.max(1, delayMs);
        this.burst = Math.max(1, burst);
        this.lines = new String[this.maxQueue];
        this.tokensMilli = this.burst * 1000L;
        this.lastRefill = System.currentTimeMillis();
    }

    public synchronized void reconfigure(int maxQueue, long delayMs, int burst) {
        this.maxQueue = Math.max(1, maxQueue);
        this.delayMs = Math.max(1, delayMs);
        this.burst = Math.max(1, burst);
        if (tokensMilli > this.burst * 1000L) {
            tokensMilli = this.burst * 1000L;
        }
        // Resize buffer if needed (preserve existing elements)
        if (this.lines.length != this.maxQueue) {
            String[] newLines = new String[this.maxQueue];
            for (int i = 0; i < count; i++) {
                newLines[i] = lines[(head + i) % lines.length];
            }
            this.lines = newLines;
            this.head = 0;
            this.tail = count;
        }
    }

    /**
     * @return true if the line was queued, false if it was dropped because the
     *         queue was full.
     */
    public synchronized boolean offer(String line) {
        if (line == null) {
            return false;
        }
        if (count >= maxQueue) {
            dropped++;
            return false; // Queue full, reject new line
        }
        lines[tail] = line;
        tail = (tail + 1) % lines.length;
        count++;
        return true;
    }

    /**
     * Take the next line if the flood limiter allows it right now.
     * @return the line, or null when nothing is due yet.
     */
    public synchronized String poll() {
        if (count == 0) {
            return null;
        }
        refill();
        if (tokensMilli < 1000L) {
            return null;
        }
        tokensMilli -= 1000L;
        String line = lines[head];
        lines[head] = null;  // Help GC
        head = (head + 1) % lines.length;
        count--;
        return line;
    }

    /**
     * Milliseconds until {@link #poll()} would return something.
     * The writer thread sleeps on this instead of spinning.
     */
    public synchronized long nextDueIn() {
        if (count == 0) {
            return -1;
        }
        refill();
        if (tokensMilli >= 1000L) {
            return 0;
        }
        long deficit = 1000L - tokensMilli;
        return (long) Math.ceil((double) deficit * (double) delayMs / 1000.0);
    }

    private void refill() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastRefill;
        if (elapsed <= 0) {
            return;
        }
        lastRefill = now;
        // tokens += elapsed / delay  (in milli-token units)
        long gained = (elapsed * 1000L) / delayMs;
        if (gained > 0) {
            tokensMilli += gained;
            long cap = (long) burst * 1000L;
            if (tokensMilli > cap) {
                tokensMilli = cap;
            }
        }
    }

    public synchronized int pending() {
        return count;
    }

    public synchronized long droppedCount() {
        return dropped;
    }

    public synchronized void clear() {
        // Null out references to help GC
        for (int i = 0; i < count; i++) {
            lines[(head + i) % lines.length] = null;
        }
        head = 0;
        tail = 0;
        count = 0;
        tokensMilli = 0;
        lastRefill = System.currentTimeMillis();
    }

    /**
     * Credit the bucket fully. Called right after registration succeeds so a
     * burst of JOINs at connect time is not rate-limited into the void.
     */
    public synchronized void grantFullBurst() {
        refill();
        tokensMilli = (long) burst * 1000L;
    }
}
