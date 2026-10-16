---
id: performance
title: Performance
triggers: [performance, optimize, slow, memory, heap, gc, lag, speed, efficient]
packages: []
complexity: hard
---

# Performance Skill

Java ME optimization patterns for constrained devices.

## When to Use

- App feels sluggish
- Memory usage too high
- GC pauses causing UI lag
- Battery drain concerns
- Optimizing before release

## Performance Targets

| Metric | Target | Measurement |
|--------|--------|-------------|
| App startup | < 3 seconds | JAD launch to server list |
| Message display | < 100ms | Receive to render |
| Memory usage | < 1 MB heap | WTK memory monitor |
| JAR size | < 300 KB | File size |
| Battery | Minimal drain | Hours of use |

## Optimization Patterns

### 1. Object Pooling

Reuse objects instead of creating new ones:

```java
// WRONG: Creating new objects in loop
for (int i = 0; i < messages.length; i++) {
    Message msg = new Message();  // GC pressure!
    msg.setText(messages[i]);
    display(msg);
}

// RIGHT: Reuse objects
Message msg = new Message();
for (int i = 0; i < messages.length; i++) {
    msg.setText(messages[i]);  // Reuse
    display(msg);
}
```

### 2. String Building

```java
// WRONG: String concatenation in loop
String result = "";
for (int i = 0; i < parts.length; i++) {
    result += parts[i];  // Creates new String each time!
}

// RIGHT: Use StringBuffer
StringBuffer sb = new StringBuffer();
for (int i = 0; i < parts.length; i++) {
    sb.append(parts[i]);
}
String result = sb.toString();
```

### 3. Array vs Vector

```java
// WRONG: Vector (synchronized, slower)
Vector list = new Vector();
list.addElement(item);

// RIGHT: Array (faster, but fixed size)
Item[] list = new Item[100];
list[count++] = item;

// RIGHT: If dynamic needed, use custom growable array
private Item[] items = new Item[10];
private int count;

public void add(Item item) {
    if (count >= items.length) {
        Item[] newItems = new Item[items.length * 2];
        System.arraycopy(items, 0, newItems, 0, items.length);
        items = newItems;
    }
    items[count++] = item;
}
```

### 4. Avoid Object Creation in paint()

```java
// WRONG: Creating objects in paint()
protected void paint(Graphics g) {
    Font font = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
    String label = "Messages: " + count;  // New String every frame!
    g.setFont(font);
    g.drawString(label, 0, 0, Graphics.TOP | Graphics.LEFT);
}

// RIGHT: Cache objects
private Font font;
private StringItem label;

public MyScreen() {
    font = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
    label = new StringItem("Messages", "0");
    append(label);
}

protected void paint(Graphics g) {
    g.setFont(font);
    label.setText("Messages: " + count);  // Reuse
}
```

### 5. RMS Write Batching

```java
// WRONG: Writing to RMS for every message
public void onMessage(Message msg) {
    LogStore.append(channel, nick, text);  // Disk I/O every message!
}

// RIGHT: Batch writes
private Vector pending = new Vector();

public void onMessage(Message msg) {
    pending.addElement(msg);
    if (pending.size() >= 10) {
        flush();
    }
}

public void flush() {
    for (int i = 0; i < pending.size(); i++) {
        Message msg = (Message) pending.elementAt(i);
        LogStore.append(msg.channel, msg.nick, msg.text);
    }
    pending.removeAllElements();
}
```

### 6. Lazy Loading

```java
// WRONG: Loading all data at startup
public MyScreen() {
    // Loads 1000 items at once!
    for (int i = 0; i < 1000; i++) {
        append(loadItem(i));
    }
}

// RIGHT: Load on demand
public MyScreen() {
    // Load first 20 items
    for (int i = 0; i < 20; i++) {
        append(loadItem(i));
    }
    // Load more when user scrolls
}

public void scrollDown() {
    if (needsMoreItems()) {
        append(loadNextItem());
    }
}
```

### 7. Image Optimization

```java
// WRONG: Loading large images
Image large = Image.createImage("/large.png");  // 50KB!

// RIGHT: Use appropriately sized images
Image small = Image.createImage("/small.png");  // 5KB

// RIGHT: Reuse images
private static Image icon;

public static Image getIcon() {
    if (icon == null) {
        icon = Image.createImage("/icon.png");
    }
    return icon;
}
```

## Memory Management

### Heap Usage

| Component | Typical | Target |
|-----------|---------|--------|
| App code | 50-100 KB | Minimize with ProGuard |
| UI objects | 50-100 KB | Reuse, don't cache |
| Messages | 100-200 KB | Limit ring buffer |
| Images | 50-100 KB | Use small images |
| **Total** | **250-500 KB** | **< 1 MB** |

### Garbage Collection

```java
// WRONG: Creating garbage
String temp = new String("temp");
temp = temp.trim();  // Another object!
temp = temp.toUpperCase();  // Another object!

// RIGHT: Minimize garbage
String temp = "temp";
// Use temp directly
```

## Profiling

### WTK Memory Monitor

```bash
# Run with memory monitoring
emulator -Xheapsize:2M -Xdescriptor:dist/chatterbox.jad
# View: Menu → Memory → Used
```

### Manual Timing

```java
long start = System.currentTimeMillis();
// Code to measure
long elapsed = System.currentTimeMillis() - start;
System.out.println("Operation took: " + elapsed + "ms");
```

## Anti-Patterns

```java
// WRONG: Synchronized when not needed
public synchronized void update() {  // Unnecessary lock!
    count++;
}

// RIGHT: No synchronization for single-threaded
public void update() {
    count++;
}
```

```java
// WRONG: Creating objects in event handler
public void onMessage(Message msg) {
    Message copy = new Message(msg);  // Unnecessary copy!
    process(copy);
}

// RIGHT: Use directly
public void onMessage(Message msg) {
    process(msg);
}
```

## Optimization Checklist

- [ ] No object creation in paint()
- [ ] No String concatenation in loops
- [ ] Arrays preferred over Vector
- [ ] RMS writes batched
- [ ] Images appropriately sized
- [ ] Lazy loading for large lists
- [ ] Object pooling for frequent allocations
- [ ] No unnecessary synchronization
- [ ] Memory usage < 1 MB
- [ ] Startup time < 3 seconds
