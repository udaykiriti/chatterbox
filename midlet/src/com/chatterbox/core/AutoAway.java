package com.chatterbox.core;

/**
 * Auto-away manager. Detects user inactivity and sets/removes away status.
 *
 * On MIDP, we detect idle by tracking key presses in the ChatCanvas.
 * The MIDlet calls markActivity() on every key event.
 */
public final class AutoAway {

    public interface AwayListener {
        void onAutoAway(String message);
        void onAutoBack();
    }

    private final Session session;
    private final AwayListener listener;
    private final long awayDelayMs;
    private String awayMessage = "Auto-away (idle)";

    private long lastActivity = System.currentTimeMillis();
    private boolean away = false;
    private Timer timer;

    public AutoAway(Session session, AwayListener listener) {
        this(session, listener, 5 * 60 * 1000); // 5 min default
    }

    public AutoAway(Session session, AwayListener listener, long delayMs) {
        this.session = session;
        this.listener = listener;
        this.awayDelayMs = delayMs;
    }

    public void setEnabled(boolean enabled) {
        if (!enabled && timer != null) {
            timer.cancel();
            timer = null;
        } else if (enabled && timer == null) {
            startTimer();
        }
    }

    public void setAwayMessage(String msg) {
        this.awayMessage = msg == null ? "Auto-away (idle)" : msg;
    }

    /** Call on every user key press / activity. */
    public synchronized void markActivity() {
        lastActivity = System.currentTimeMillis();
        if (away) {
            // User came back
            away = false;
            session.getSender().send("AWAY\r\n");
            if (listener != null) {
                listener.onAutoBack();
            }
        }
    }

    private void startTimer() {
        timer = new Timer();
        timer.schedule(new TimerTask() {
            public void run() {
                checkIdle();
            }
        }, awayDelayMs, 30000); // check every 30s after initial delay
    }

    private synchronized void checkIdle() {
        if (away) {
            return; // already away
        }
        long idle = System.currentTimeMillis() - lastActivity;
        if (idle >= awayDelayMs) {
            away = true;
            session.getSender().send("AWAY :" + awayMessage + "\r\n");
            if (listener != null) {
                listener.onAutoAway(awayMessage);
            }
        }
    }

    public boolean isAway() {
        return away;
    }

    public void shutdown() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    // Minimal Timer/TimerTask for CLDC (since java.util.Timer may not exist)
    private static class Timer {
        private Thread thread;
        private boolean running;
        private long period;
        private TimerTask task;

        public void schedule(TimerTask task, long delay, long period) {
            this.task = task;
            this.period = period;
            running = true;
            thread = new Thread(new Runnable() {
                public void run() {
                    try {
                        Thread.sleep(delay);
                        while (running) {
                            task.run();
                            Thread.sleep(period);
                        }
                    } catch (InterruptedException e) {
                        // shutdown
                    }
                }
            });
            thread.start();
        }

        public void cancel() {
            running = false;
            if (thread != null) {
                thread.interrupt();
            }
        }
    }

    private interface TimerTask {
        void run();
    }
}