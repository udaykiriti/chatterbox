package com.chatterbox.ui;

import javax.microedition.midlet.MIDlet;
import javax.microedition.io.Connector;

/**
 * URL handler - opens URLs in the platform browser if supported.
 *
 * On MIDP 2.0, platformRequest("http://...") launches the browser.
 * On some Nokia S60 devices, this works for http/https.
 */
public final class UrlHandler {

    private final MIDlet midlet;

    public UrlHandler(MIDlet midlet) {
        this.midlet = midlet;
    }

    /** Try to open a URL in the system browser. */
    public boolean open(String url) {
        if (url == null || url.length() == 0) {
            return false;
        }
        try {
            return midlet.platformRequest(url);
        } catch (SecurityException e) {
            // User denied permission or platform doesn't support it
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /** Try common URL schemes. */
    public boolean openHttp(String url) {
        if (url == null) {
            return false;
        }
        String u = url.trim();
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            u = "http://" + u;
        }
        return open(u);
    }
}