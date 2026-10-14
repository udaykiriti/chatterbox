package javax.microedition.lcdui;

import javax.microedition.midlet.MIDlet;

public class Display {
    protected Display() {}
    public static Display getDisplay(MIDlet m) { return new Display(); }
    public Displayable getCurrent() { return null; }
    public void setCurrent(Displayable d) {}
    public void setCurrent(Alert a, Displayable next) {}
    public void setCurrent(Alert a) {}
    public void callSerially(Runnable r) {}
    public boolean flashBacklight(int ms) { return false; }
    public boolean vibrate(int ms) { return false; }
    public int numColors() { return 0; }
    public boolean isColor() { return true; }
    public int getColorDepth() { return 12; }
    public void setVisible(boolean b) {}
    public void setOrientation(int o) {}
}
