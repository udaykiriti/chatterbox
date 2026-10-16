---
id: test-data
title: Test Data
triggers: [test data, fixture, sample, mock data, generator, test vector]
packages: [test]
complexity: easy
---

# Test Data Skill

Helper for creating test fixtures and sample data.

## When to Use

- Writing new tests
- Creating test fixtures
- Generating edge cases
- Testing with realistic data

## IRC Line Samples

### Basic PRIVMSG

```
:user!~user@host PRIVMSG #channel :Hello, world!
```

### Action Message

```
:user!~user@host PRIVMSG #channel :\x01ACTION waves\x01
```

### CTCP Request

```
:user!~user@host PRIVMSG #channel :\x01VERSION\x01
```

### Channel Join

```
:user!~user@host JOIN #channel
```

### Channel Part

```
:user!~user@host PART #channel :Goodbye!
```

### Nick Change

```
:oldnick!~user@host NICK :newnick
```

### Quit

```
:user!~user@host QUIT :Connection reset by peer
```

### Numeric Replies

```
:server 353 nick = #channel :@op +voice normal
:server 366 nick #channel :End of /NAMES list.
:server 433 * nick :Nickname is already in use.
```

### Server PING

```
PING :server12345
```

### Long Message (512 bytes)

```
:user!~user@host PRIVMSG #channel :AAAA... (512 bytes total)
```

### Unicode Message

```
:user!~user@host PRIVMSG #channel :Hello 世界
```

## Message Objects

```java
// Basic message
Message msg1 = new Message("nick", "Hello, world!");

// Channel message
Message msg2 = new Message("#channel", "nick", "Hello!");

// Action message
Message msg3 = new Message("#channel", "nick", "waves", Message.TYPE_ACTION);

// System message
Message msg4 = new Message("Server", "Welcome to IRC", Message.TYPE_SYSTEM);
```

## Profile Data

```java
// Basic profile
Profile profile1 = new Profile();
profile1.nick = "testnick";
profile1.server = "irc.libera.chat";
profile1.port = 6697;
profile1.useTls = true;

// Minimal profile
Profile profile2 = new Profile();
profile2.nick = "testnick";
profile2.server = "irc.example.com";
profile2.port = 6667;
profile2.useTls = false;
```

## Edge Cases

### Empty Strings

```java
String empty = "";
String nullString = null;
String whitespace = "   ";
```

### Very Long Strings

```java
// 1000 character string
String longString = "";
for (int i = 0; i < 1000; i++) {
    longString += "A";
}
```

### Special Characters

```java
String special = "!@#$%^&*()_+-=[]{}|;':\",./<>?";
String control = "\u0000\u0001\u0002\u001F";
String unicode = "世界🌍";
```

### Boundary Values

```java
int intMax = Integer.MAX_VALUE;
int intMin = Integer.MIN_VALUE;
int zero = 0;
int negative = -1;
```

## Test Vector Generators

### Random IRC Line

```java
public static String randomIrcLine() {
    String[] nicks = {"alice", "bob", "charlie"};
    String[] channels = {"#test", "#help", "#random"};
    String[] messages = {"Hello!", "How are you?", "Thanks!"};

    String nick = nicks[(int)(Math.random() * nicks.length)];
    String channel = channels[(int)(Math.random() * channels.length)];
    String message = messages[(int)(Math.random() * messages.length)];

    return ":" + nick + "!~user@host PRIVMSG " + channel + " :" + message;
}
```

### Random Message

```java
public static Message randomMessage() {
    String[] nicks = {"alice", "bob", "charlie"};
    String[] texts = {"Hello!", "Test message", "Another message"};

    String nick = nicks[(int)(Math.random() * nicks.length)];
    String text = texts[(int)(Math.random() * texts.length)];

    return new Message(nick, text);
}
```

## Crypto Test Vectors

### SHA-256

```java
// Test vector 1
byte[] input1 = "abc".getBytes();
byte[] expected1 = hexToBytes(
    "ba7816bf8f01cfea414140de5dae2223" +
    "b00361a396177a9cb410ff61f20015ad");

// Test vector 2
byte[] input2 = "".getBytes();
byte[] expected2 = hexToBytes(
    "e3b0c44298fc1c149afbf4c8996fb924" +
    "27ae41e4649b934ca495991b7852b855");
```

### ChaCha20-Poly1305

```java
// RFC 8439 test vector
byte[] key = hexToBytes(
    "808182838485868788898a8b8c8d8e8f" +
    "909192939495969798999a9b9c9d9e9f");
byte[] nonce = hexToBytes("000000000000000000000000");
byte[] plaintext = "Ladies and Gentlemen of the class of '99: If I could offer you only one tip for the future, sunscreen would be it.".getBytes();
```

## Anti-Patterns

```java
// WRONG: Hardcoded test data that may change
public void testServer() {
    session.connect("irc.my-server.com", 6697);  // May not exist!
}

// RIGHT: Use test fixtures
public void testServer() {
    session.connect(TEST_SERVER, TEST_PORT);  // Defined constant
}
```

```java
// WRONG: Test depends on external state
public void testLiveServer() {
    // Connects to real IRC server!
    session.connect("irc.libera.chat", 6697);
}

// RIGHT: Use mock or local test server
public void testWithMock() {
    session.connect("localhost", 16667);  // Local test server
}
```
