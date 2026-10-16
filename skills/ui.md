---
id: ui
title: UI
triggers: [ui, screen, canvas, lcdui, display, form, list, input, paint, key, navigation]
packages: [ui]
complexity: hard
---

# UI Skill

Work with the LCDUI screens and custom Canvas chat view.

## When to Use

- Adding new screens or UI elements
- Modifying the chat view
- Working with LCDUI components
- Debugging display issues
- Understanding the UI architecture

## Package Overview

All UI classes are in `midlet/src/com/chatterbox/ui/`.

| Class | Type | Responsibility |
|-------|------|---------------|
| `ChatterboxMIDlet` | MIDlet | App entry point, lifecycle, display |
| `ChatCanvas` | Canvas | Custom chat rendering surface |
| `ChatView` | — | Chat view logic (scrolling, rendering) |
| `ServerListScreen` | List | Server selection screen |
| `SettingsScreen` | Form | Settings configuration |
| `NickListScreen` | List | Channel nick list |
| `ChannelListScreen` | List | Channel list screen |
| `QuickConnect` | Form | Quick connect dialog |
| `LogViewer` | Form | Log viewer screen |
| `Notifier` | — | Notification/alert manager |
| `InputLine` | TextField | Message input handling |
| `NickComplete` | — | Tab-completion for nicks |
| `ServiceScreen` | Form | Service/nickserv screen |
| `HighlightScreen` | List | Highlight rules management |
| `IgnoreScreen` | List | Ignore list management |
| `UrlHandler` | — | URL detection and handling |

## Architecture

```
ChatterboxMIDlet (lifecycle)
    │
    ├── ChatCanvas (custom Canvas)
    │   └── ChatView (rendering logic)
    │
    ├── LCDUI Screens (Forms, Lists)
    │   ├── ServerListScreen
    │   ├── SettingsScreen
    │   ├── NickListScreen
    │   └── ...
    │
    └── Notifier (transient alerts)
```

## LCDUI Constraints

- **No custom layouts** — use LCDUI's layout managers
- **Limited widgets** — Form, List, TextField, Alert, Canvas
- **No transparency** — solid colors only
- **Small screens** — 240x320 (S60) or 128x160 (S40)
- **No touch** — key-based navigation only

## Adding a New Screen

### Step 1: Create the screen class

```java
package com.chatterbox.ui;

import javax.microedition.lcdui.*;

public class MyNewScreen extends Form implements CommandListener {

    private final ChatterboxMIDlet midlet;
    private final Command backCommand;

    public MyNewScreen(ChatterboxMIDlet midlet) {
        super("My New Screen");
        this.midlet = midlet;

        // Add items
        append("Hello, world!");

        // Commands
        backCommand = new Command("Back", Command.BACK, 1);
        addCommand(backCommand);
        setCommandListener(this);
    }

    public void commandAction(Command c, Displayable d) {
        if (c == backCommand) {
            midlet.showChat();
        }
    }
}
```

### Step 2: Add navigation from MIDlet

In `ChatterboxMIDlet.java`:
```java
public void showMyNewScreen() {
    Display.getDisplay(this).setCurrent(new MyNewScreen(this));
}
```

### Step 3: Add menu item

In the appropriate screen's menu:
```java
addCommand(new Command("My Screen", Command.SCREEN, 5));
```

## ChatCanvas

The `ChatCanvas` is a custom `Canvas` that renders the chat view:

```java
// Key handling
protected void keyPressed(int keyCode) {
    // Handle navigation, input, etc.
}

// Rendering
protected void paint(Graphics g) {
    // Draw messages, nicks, timestamps
}
```

### Chat View Rendering

- Messages stored in `MessageRing` (circular buffer)
- Rendering: bottom-up (newest at bottom)
- Colors: configurable via `Theme`
- Scrolling: smooth scroll with key input

## Theme Support

Colors and appearance are managed by `core/Theme.java`:

```java
// Get theme colors
int bgColor = Theme.getColor(Theme.BACKGROUND);
int textColor = Theme.getColor(Theme.TEXT);
int nickColor = Theme.getColor(Theme.NICK);
int highlightColor = Theme.getColor(Theme.HIGHLIGHT);
```

## Input Handling

### InputLine

The `InputLine` manages message input:
- Text entry with cursor
- History (up/down arrows)
- Tab completion via `NickComplete`
- Command parsing (messages starting with `/`)

### NickComplete

Tab completion for nicknames:
```java
// In InputLine
String completed = NickComplete.complete(partial, nicks);
```

## Navigation Pattern

All screens follow this pattern:

```java
public class MyScreen extends Form implements CommandListener {
    private final ChatterboxMIDlet midlet;
    private final Command backCommand;

    public MyScreen(ChatterboxMIDlet midlet) {
        super("Title");
        this.midlet = midlet;
        backCommand = new Command("Back", Command.BACK, 1);
        addCommand(backCommand);
        setCommandListener(this);
    }

    public void commandAction(Command c, Displayable d) {
        if (c == backCommand) {
            midlet.showChat();  // Return to chat
        }
    }
}
```

## Anti-Patterns

```java
// WRONG: Allocating objects in paint() (causes GC pressure)
protected void paint(Graphics g) {
    Font font = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
    String label = new String("Status: " + status);  // New object every frame!
    g.drawString(label, 0, 0, Graphics.TOP | Graphics.LEFT);
}

// RIGHT: Reuse objects
private Font font;
private StringItem statusItem;

public MyScreen() {
    font = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
    statusItem = new StringItem("Status", "");
    append(statusItem);
}

protected void paint(Graphics g) {
    g.setFont(font);
    statusItem.setText("Status: " + status);  // Reuse
}
```

```java
// WRONG: Not handling key repeat
protected void keyPressed(int keyCode) {
    scrollDown();  // Scrolls once per key press
}

// RIGHT: Handle key repeat for smooth scrolling
protected void keyRepeated(int keyCode) {
    scrollDown();  // Smooth scroll on hold
}
```

```java
// WRONG: Using hardcoded pixel values
g.drawString("Title", 10, 5, Graphics.TOP | Graphics.LEFT);

// RIGHT: Use screen dimensions
int width = getWidth();
int height = getHeight();
g.drawString("Title", width / 2, 5, Graphics.TOP | Graphics.HCENTER);
```

```java
// WRONG: Not sanitizing user input before display
String message = line.getMessage();
g.drawString(message, 0, y, Graphics.TOP | Graphics.LEFT);

// RIGHT: Sanitize first
String message = IrcSanitizer.sanitize(line.getMessage());
g.drawString(message, 0, y, Graphics.TOP | Graphics.LEFT);
```

## Code Template: Adding a New LCDUI Screen

```java
package com.chatterbox.ui;

import javax.microedition.lcdui.*;

/**
 * MyNewScreen - description of what this screen does.
 */
public class MyNewScreen extends Form implements CommandListener {

    private final ChatterboxMIDlet midlet;
    private final Command backCommand;
    private final Command saveCommand;

    private TextField nameField;
    private ChoiceGroup typeGroup;

    public MyNewScreen(ChatterboxMIDlet midlet) {
        super("My New Screen");
        this.midlet = midlet;

        // Create form items
        nameField = new TextField("Name:", "", 32, TextField.ANY);
        append(nameField);

        typeGroup = new ChoiceGroup("Type:", ChoiceGroup.EXCLUSIVE);
        typeGroup.append("Option 1", null);
        typeGroup.append("Option 2", null);
        append(typeGroup);

        // Commands
        backCommand = new Command("Back", Command.BACK, 1);
        saveCommand = new Command("Save", Command.OK, 2);
        addCommand(backCommand);
        addCommand(saveCommand);
        setCommandListener(this);
    }

    public void commandAction(Command c, Displayable d) {
        if (c == backCommand) {
            midlet.showChat();
        } else if (c == saveCommand) {
            save();
            midlet.showChat();
        }
    }

    private void save() {
        String name = nameField.getString();
        int type = typeGroup.getSelectedIndex();
        // Save logic here
    }
}
```

## Testing UI

- **Unit tests**: Not applicable (UI classes need LCDUI)
- **Emulator testing**: Use WTK or Nokia S60 emulator
- **Manual testing**: Deploy to physical device
- **Stub testing**: UI classes compile against stubs but can't run

## Common Tasks

### Add a menu item
```java
Command myCmd = new Command("My Feature", Command.SCREEN, 3);
addCommand(myCmd);
```

### Show an alert
```java
Alert alert = new Alert("Title", "Message", null, AlertType.INFO);
alert.setTimeout(Alert.FOREVER);
Display.getDisplay(midlet).setCurrent(alert);
```

### Handle key input in Canvas
```java
protected void keyPressed(int keyCode) {
    int gameAction = getGameAction(keyCode);
    switch (gameAction) {
        case Canvas.UP:    // scroll up
        case Canvas.DOWN:  // scroll down
        case Canvas.FIRE:  // select/activate
    }
}
```

## Constraints

- **No garbage collection pressure** — reuse objects, avoid allocations in paint()
- **No floating point** — not available on CLDC 1.1
- **Limited fonts** — use `Font.getDefaultFont()` or system fonts
- **Key events only** — no pointer events on most devices
- **Small heap** — don't cache large images or data
