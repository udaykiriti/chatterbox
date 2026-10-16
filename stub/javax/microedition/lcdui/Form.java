package javax.microedition.lcdui;

/**
 * Compile-time stub of the MIDP 2.0 Form API, matching the platform
 * signatures exactly (JSR 118). See stub/README.
 */
public class Form extends Displayable {
    public Form(String title) {}
    public Form(String title, Item[] items) {}
    public int append(Item item) { return 0; }
    public int append(String str) { return 0; }
    public int append(Image img) { return 0; }
    public void delete(int index) {}
    public void deleteAll() {}
    public void insert(int index, Item item) {}
    public void set(int index, Item item) {}
    public int size() { return 0; }
    public Item get(int index) { return null; }
}
