package com.events;

public class JextractResult {
    public interface Callback {
        void apply(int status);
    }
    public static void register_cb(java.lang.foreign.MemorySegment cb) {}
}
