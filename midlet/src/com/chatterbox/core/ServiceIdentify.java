package com.chatterbox.core;

import java.util.Vector;

/**
 * Service identification (NickServ, ChanServ, etc.).
 * Automatically identifies to services after registration.
 */
public final class ServiceIdentify {

    private final Session session;
    private final Vector services = new Vector();

    public static final class Service {
        public String name;       // "NickServ", "ChanServ", "AuthServ"
        public String password;   // account password
        public String account;    // account name (optional, defaults to nick)
        public String command;    // "IDENTIFY" or "LOGIN"
        public String method;     // "PRIVMSG", "NOTICE", "QUOTE"
        public boolean enabled;

        public Service(String n) {
            this.name = n == null ? "NickServ" : n;
            this.command = "IDENTIFY";
            this.method = "PRIVMSG";
            this.enabled = true;
        }

        public String buildCommand(String nick) {
            String acct = (account != null && account.length() > 0) ? account : nick;
            String cmd = command + " " + acct + " " + password;
            if ("PRIVMSG".equals(method)) {
                return "PRIVMSG " + name + " :" + cmd;
            } else if ("NOTICE".equals(method)) {
                return "NOTICE " + name + " :" + cmd;
            } else {
                return "QUOTE " + cmd; // raw
            }
        }
    }

    public ServiceIdentify(Session session) {
        this.session = session;
        // Default NickServ
        Service ns = new Service("NickServ");
        services.addElement(ns);
    }

    public void addService(Service s) {
        if (s != null && s.enabled && s.password != null && s.password.length() > 0) {
            // Remove existing with same name
            for (int i = 0; i < services.size(); i++) {
                Service ex = (Service) services.elementAt(i);
                if (ex.name.equalsIgnoreCase(s.name)) {
                    services.removeElementAt(i);
                    break;
                }
            }
            services.addElement(s);
        }
    }

    public void removeService(String name) {
        if (name == null) {
            return;
        }
        for (int i = 0; i < services.size(); i++) {
            Service s = (Service) services.elementAt(i);
            if (s.name.equalsIgnoreCase(name)) {
                services.removeElementAt(i);
                return;
            }
        }
    }

    /** Call after successful registration (001 received). */
    public void identifyAll(String nick) {
        for (int i = 0; i < services.size(); i++) {
            Service s = (Service) services.elementAt(i);
            if (s.enabled && s.password != null && s.password.length() > 0) {
                String cmd = s.buildCommand(nick);
                session.getSender().send(cmd + "\r\n");
            }
        }
    }

    public Vector getServices() {
        Vector v = new Vector(services.size());
        for (int i = 0; i < services.size(); i++) {
            v.addElement(services.elementAt(i));
        }
        return v;
    }

    /** Handle NOTICE from services - e.g. "You are now identified" */
    public boolean handleNotice(String from, String text) {
        if (from == null) {
            return false;
        }
        String f = Str.lower(from);
        for (int i = 0; i < services.size(); i++) {
            Service s = (Service) services.elementAt(i);
            if (Str.lower(s.name).equals(f) && text != null) {
                String t = Str.lower(text);
                if (t.indexOf("identified") >= 0 || t.indexOf("logged in") >= 0
                        || t.indexOf("password accepted") >= 0) {
                    return true;
                }
            }
        }
        return false;
    }
}