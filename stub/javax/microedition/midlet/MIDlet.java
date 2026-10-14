package javax.microedition.midlet;

/** Compile-time stub of the CLDC/MIDP MIDlet API. See stub/README. */
public abstract class MIDlet {
    protected MIDlet() {}
    protected abstract void startApp() throws MIDletStateChangeException;
    protected abstract void pauseApp();
    protected abstract void destroyApp(boolean unconditional);
    public final void notifyDestroyed() {}
    public boolean platformRequest(String url) { return false; }
    public final void notifyPaused() {}
    public final String getAppProperty(String key) { return null; }
    public final void resumeRequest() {}
}
