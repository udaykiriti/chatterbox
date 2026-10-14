package javax.microedition.lcdui;

import javax.microedition.media.MediaException;

public class Alert extends Displayable {
    public static final int ALERT_TIMEOUT = -2;
    public static final int FOREVER = -1;

    public Alert(String title) {}
    public Alert(String title, String alertText, Image image, int timeoutType) {}
    public Alert(String title, String alertText, Image image, int timeoutType, Sound sound)
            throws MediaException {}
    public void setTimeout(int time) {}
    public int getTimeout() { return FOREVER; }
    public void setType(int type) {}
    public void setString(String str) {}
    public String getString() { return ""; }
    public void setTitle(String title) {}
    public String getTitle() { return ""; }
}
