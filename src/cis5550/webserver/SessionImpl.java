package cis5550.webserver;

import java.util.Map;
import java.util.HashMap;

public class SessionImpl implements Session {
    private String id;
    private long creationTime;
    private long lastAccessedTime;
    private int maxActiveInterval = 300;
    private Map<String, Object> attributes = new HashMap<>();
    private boolean isInvalidated;

    public SessionImpl(String id) {
        this.id = id;
        this.creationTime = System.currentTimeMillis();
        this.lastAccessedTime = System.currentTimeMillis();
    }

    // Returns the session ID (the value of the SessionID cookie) that this session
    // is associated with
    @Override
    public String id() {
        return this.id;
    }

    // The methods below return the time this session was created, and the time time
    // this session was
    // last accessed. The return values should be in the same format as the return
    // value of
    // System.currentTimeMillis().
    @Override
    public long creationTime() {
        return creationTime;
    }

    @Override
    public boolean setLastAccessedTime(long time) {
        if (time - this.lastAccessedTime > maxActiveInterval * 1000) {
            return false;
        }
        this.lastAccessedTime = time;
        return true;
    }

    @Override
    public long lastAccessedTime() {
        return lastAccessedTime;
    }

    // Set the maximum time, in seconds, this session can be active without being
    // accessed.
    @Override
    public void maxActiveInterval(int seconds) {
        this.maxActiveInterval = seconds;
    }

    public int getMaxActiveInterval() {
        return this.maxActiveInterval;
    }

    // Invalidates the session. You do not need to delete the cookie on the client
    // when this method
    // is called; it is sufficient if the session object is removed from the server.
    public void invalidate() {
        this.isInvalidated = true;
    }

    @Override
    public boolean isValid() {
        return !this.isInvalidated;
    }

    // The methods below look up the value for a given key, and associate a key with
    // a new value,
    // respectively.
    public Object attribute(String name) {
        return attributes.get(name);
    }

    public void attribute(String name, Object value) {
        attributes.put(name, value);
    }
}
