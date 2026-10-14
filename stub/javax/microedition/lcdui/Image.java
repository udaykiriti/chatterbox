package javax.microedition.lcdui;

public class Image {
    protected Image() {}
    public static Image createImage(int width, int height) { return new Image(); }
    public int getWidth() { return 0; }
    public int getHeight() { return 0; }
    public static Image createImageFromStream(java.io.InputStream s)
            throws java.io.IOException { return new Image(); }
}
