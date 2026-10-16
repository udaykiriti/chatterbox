package javax.microedition.lcdui;

/** JSR 118 javax.microedition.lcdui.Alert (compile stub). */
public class Alert extends Displayable {
    public static final int ALERT_TIMEOUT = -2;

    public Alert(String title) {}

    public Alert(String title, String alertText, Image image, int timeout) {}

    public void setTimeout(int time) {}
    public int getTimeout() { return ALERT_TIMEOUT; }
    public void setString(String str) {}
    public String getString() { return ""; }
}
