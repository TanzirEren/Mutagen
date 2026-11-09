package com.mutagen.lab.core;

/** Sets optional library fields without hard-failing if a field is missing in a given smali version. */
final class Reflect {
    private Reflect() {}
    static void set(Object o, String field, Object value) {
        try {
            java.lang.reflect.Field f = o.getClass().getField(field);
            f.set(o, value);
        } catch (Throwable ignored) { }
    }
}
