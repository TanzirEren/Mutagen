package com.mutagen.lab.core;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tiny binary-XML (AXML) + resources.arsc string-pool toolkit.
 * Works directly on bytes: no apktool needed on the device.
 */
public final class Axml {
    private Axml() {}

    public static final int T_POOL = 0x0001, T_RESMAP = 0x0180;
    public static final int T_START_NS = 0x0100, T_END_NS = 0x0101, T_START = 0x0102, T_END = 0x0103;
    public static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final Charset U8 = Charset.forName("UTF-8");
    private static final Charset U16 = Charset.forName("UTF-16LE");

    // ------------------------------------------------------------------ bytes
    static int u16(byte[] b, int o) { return (b[o] & 0xff) | ((b[o + 1] & 0xff) << 8); }
    static int i32(byte[] b, int o) {
        return (b[o] & 0xff) | ((b[o + 1] & 0xff) << 8) | ((b[o + 2] & 0xff) << 16) | ((b[o + 3] & 0xff) << 24);
    }
    static void p16(byte[] b, int o, int v) { b[o] = (byte) v; b[o + 1] = (byte) (v >> 8); }
    static void p32(byte[] b, int o, int v) {
        b[o] = (byte) v; b[o + 1] = (byte) (v >> 8); b[o + 2] = (byte) (v >> 16); b[o + 3] = (byte) (v >> 24);
    }

    // ------------------------------------------------------------------ pool
    public static final class Pool {
        public boolean utf8;
        public int flags;
        public List<String> strings = new ArrayList<>();
        public int styleCount;
        public int[] styleOffsets = new int[0];
        public byte[] styles = new byte[0];
        public int chunkSize;
    }

    public static Pool parsePool(byte[] d, int off) {
        int hdr = u16(d, off + 2);
        Pool p = new Pool();
        p.chunkSize = i32(d, off + 4);
        int sc = i32(d, off + 8);
        int stc = i32(d, off + 12);
        p.flags = i32(d, off + 16);
        int sStart = i32(d, off + 20);
        int stStart = i32(d, off + 24);
        p.utf8 = (p.flags & 0x100) != 0;
        int tab = off + hdr;
        int base = off + sStart;
        for (int i = 0; i < sc; i++) {
            int so = i32(d, tab + i * 4);
            p.strings.add(readStr(d, base + so, p.utf8));
        }
        p.styleCount = stc;
        p.styleOffsets = new int[stc];
        for (int i = 0; i < stc; i++) p.styleOffsets[i] = i32(d, tab + sc * 4 + i * 4);
        if (stc > 0) p.styles = Arrays.copyOfRange(d, off + stStart, off + p.chunkSize);
        return p;
    }

    private static String readStr(byte[] d, int o, boolean utf8) {
        try {
            if (utf8) {
                int l = d[o] & 0xff; int p = o + 1;
                if ((l & 0x80) != 0) { p++; }
                int bl = d[p] & 0xff; p++;
                if ((bl & 0x80) != 0) { bl = ((bl & 0x7f) << 8) | (d[p] & 0xff); p++; }
                return new String(d, p, bl, U8);
            } else {
                int len = u16(d, o); int p = o + 2;
                if ((len & 0x8000) != 0) { len = ((len & 0x7fff) << 16) | u16(d, p); p += 2; }
                return new String(d, p, len * 2, U16);
            }
        } catch (Throwable t) {
            return "";
        }
    }

    private static void writeStr(ByteArrayOutputStream o, String s, boolean utf8) {
        if (utf8) {
            byte[] b = s.getBytes(U8);
            int cl = s.length();
            if (cl > 0x7f) { o.write(((cl >> 8) & 0x7f) | 0x80); o.write(cl & 0xff); } else o.write(cl);
            if (b.length > 0x7f) { o.write(((b.length >> 8) & 0x7f) | 0x80); o.write(b.length & 0xff); } else o.write(b.length);
            o.write(b, 0, b.length);
            o.write(0);
        } else {
            int cl = s.length();
            if (cl > 0x7fff) {
                int hi = ((cl >> 16) & 0x7fff) | 0x8000;
                o.write(hi & 0xff); o.write(hi >> 8);
                o.write(cl & 0xff); o.write((cl >> 8) & 0xff);
            } else { o.write(cl & 0xff); o.write(cl >> 8); }
            byte[] b = s.getBytes(U16);
            o.write(b, 0, b.length);
            o.write(0); o.write(0);
        }
    }

    public static byte[] buildPool(Pool p) {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        int n = p.strings.size();
        int[] offs = new int[n];
        for (int i = 0; i < n; i++) { offs[i] = data.size(); writeStr(data, p.strings.get(i), p.utf8); }
        while (data.size() % 4 != 0) data.write(0);
        int hdr = 28;
        int strStart = hdr + n * 4 + p.styleCount * 4;
        int stylesStart = p.styleCount > 0 ? strStart + data.size() : 0;
        int total = strStart + data.size() + p.styles.length;
        byte[] out = new byte[total];
        p16(out, 0, T_POOL); p16(out, 2, hdr); p32(out, 4, total);
        p32(out, 8, n); p32(out, 12, p.styleCount);
        p32(out, 16, p.flags & ~0x1);
        p32(out, 20, strStart); p32(out, 24, stylesStart);
        for (int i = 0; i < n; i++) p32(out, hdr + i * 4, offs[i]);
        for (int i = 0; i < p.styleCount; i++) p32(out, hdr + n * 4 + i * 4, p.styleOffsets[i]);
        byte[] db = data.toByteArray();
        System.arraycopy(db, 0, out, strStart, db.length);
        System.arraycopy(p.styles, 0, out, strStart + db.length, p.styles.length);
        return out;
    }

    private static byte[] replacePool(byte[] d, int off, Pool p) {
        int oldSize = i32(d, off + 4);
        byte[] np = buildPool(p);
        byte[] out = new byte[d.length - oldSize + np.length];
        System.arraycopy(d, 0, out, 0, off);
        System.arraycopy(np, 0, out, off, np.length);
        System.arraycopy(d, off + oldSize, out, off + np.length, d.length - off - oldSize);
        p32(out, 4, out.length);
        return out;
    }

    public interface Editor { String edit(int index, String value); }

    public static List<String> poolStrings(byte[] d) {
        return parsePool(d, u16(d, 2)).strings;
    }

    /** Edit strings of the global/first pool. Return null from the editor to leave a string alone. */
    public static byte[] editPool(byte[] d, Editor ed) {
        int off = u16(d, 2);
        Pool p = parsePool(d, off);
        boolean changed = false;
        for (int i = 0; i < p.strings.size(); i++) {
            String old = p.strings.get(i);
            String r = ed.edit(i, old);
            if (r != null && !r.equals(old)) { p.strings.set(i, r); changed = true; }
        }
        return changed ? replacePool(d, off, p) : d;
    }

    public static byte[] setString(byte[] d, int index, String value) {
        final int idx = index; final String v = value;
        return editPool(d, new Editor() { public String edit(int i, String s) { return i == idx ? v : null; } });
    }

    public static byte[] replaceExact(byte[] d, final String from, final String to) {
        return editPool(d, new Editor() { public String edit(int i, String s) { return s.equals(from) ? to : null; } });
    }

    public static byte[] replaceSubstring(byte[] d, final String from, final String to) {
        return editPool(d, new Editor() { public String edit(int i, String s) { return s.contains(from) ? s.replace(from, to) : null; } });
    }

    // ------------------------------------------------------------------ walker
    public static final class Attr {
        public String ns, name, text;
        public int type, data, rawIdx = -1;
        public boolean isString() { return type == 3; }
    }

    public interface Visitor {
        void start(String element, List<Attr> attrs, int depth);
        void end(String element, int depth);
    }

    private static String valueText(Pool p, int raw, int type, int data) {
        switch (type) {
            case 3: { int i = raw != -1 ? raw : data; return i >= 0 && i < p.strings.size() ? p.strings.get(i) : ""; }
            case 0x12: return data != 0 ? "true" : "false";
            case 0x10: return String.valueOf(data);
            case 0x11: return String.format("0x%x", data);
            case 0x01: return String.format("@0x%08x", data);
            case 0x02: return String.format("?0x%08x", data);
            case 0x04: return String.valueOf(Float.intBitsToFloat(data));
            case 0x1c: case 0x1d: case 0x1e: case 0x1f: return String.format("#%08x", data);
            default: return String.format("0x%08x", data);
        }
    }

    public static void walk(byte[] d, Visitor v) {
        int off = u16(d, 2);
        Pool p = parsePool(d, off);
        int pos = off + p.chunkSize;
        java.util.Map<Integer, String> nsPrefix = new java.util.HashMap<>();
        int depth = 0;
        while (pos + 8 <= d.length) {
            int type = u16(d, pos);
            int size = i32(d, pos + 4);
            if (size <= 0) break;
            if (type == T_START_NS) {
                nsPrefix.put(i32(d, pos + 20), p.strings.get(Math.max(0, i32(d, pos + 16)))); // uri idx -> prefix
            } else if (type == T_START) {
                int nameIdx = i32(d, pos + 20);
                int attrStart = u16(d, pos + 24);
                int attrSize = u16(d, pos + 26);
                int cnt = u16(d, pos + 28);
                List<Attr> attrs = new ArrayList<>();
                for (int i = 0; i < cnt; i++) {
                    int a = pos + 16 + attrStart + i * attrSize;
                    Attr at = new Attr();
                    int ns = i32(d, a);
                    int nm = i32(d, a + 4);
                    int raw = i32(d, a + 8);
                    at.type = d[a + 15] & 0xff;
                    at.data = i32(d, a + 16);
                    at.rawIdx = at.type == 3 ? (raw != -1 ? raw : at.data) : -1;
                    at.ns = ns >= 0 && ns < p.strings.size() ? p.strings.get(ns) : null;
                    at.name = nm >= 0 && nm < p.strings.size() ? p.strings.get(nm) : "?";
                    at.text = valueText(p, raw, at.type, at.data);
                    attrs.add(at);
                }
                v.start(nameIdx >= 0 && nameIdx < p.strings.size() ? p.strings.get(nameIdx) : "?", attrs, depth);
                depth++;
            } else if (type == T_END) {
                depth--;
                int nameIdx = i32(d, pos + 20);
                v.end(nameIdx >= 0 && nameIdx < p.strings.size() ? p.strings.get(nameIdx) : "?", depth);
            }
            pos += size;
        }
    }

    public static String dump(byte[] d) {
        final StringBuilder sb = new StringBuilder();
        final boolean[] first = {true};
        final Pool pool = parsePool(d, u16(d, 2));
        walk(d, new Visitor() {
            public void start(String el, List<Attr> attrs, int depth) {
                indent(sb, depth);
                sb.append('<').append(el);
                if (first[0]) { sb.append("\n"); indent(sb, depth + 1); sb.append("xmlns:android=\"" + ANDROID_NS + "\""); first[0] = false; }
                for (Attr a : attrs) {
                    sb.append('\n'); indent(sb, depth + 1);
                    if (ANDROID_NS.equals(a.ns)) sb.append("android:");
                    sb.append(a.name).append("=\"").append(a.text).append('"');
                }
                sb.append(">\n");
            }
            public void end(String el, int depth) {
                indent(sb, depth); sb.append("</").append(el).append(">\n");
            }
        });
        return sb.toString();
    }

    private static void indent(StringBuilder sb, int n) { for (int i = 0; i < n; i++) sb.append("    "); }

    // ------------------------------------------------------------------ manifest info
    public static final class Info {
        public String pkg = "", versionName = "";
        public int versionCode, minSdk, targetSdk;
        public List<String> activities = new ArrayList<>();
        public String launcher;
        public List<String> permissions = new ArrayList<>();
        public Set<Integer> authorityIdx = new HashSet<>();
        public Set<Integer> permissionIdx = new HashSet<>();
    }

    public static Info info(byte[] d) {
        final Info in = new Info();
        final String[] curAct = {null};
        final boolean[] inFilter = {false};
        final boolean[] hasMain = {false};
        walk(d, new Visitor() {
            public void start(String el, List<Attr> attrs, int depth) {
                if (el.equals("manifest")) {
                    for (Attr a : attrs) {
                        if (a.name.equals("package")) in.pkg = a.text;
                        else if (a.name.equals("versionName")) in.versionName = a.text;
                        else if (a.name.equals("versionCode")) try { in.versionCode = a.data; } catch (Throwable ignored) {}
                    }
                } else if (el.equals("uses-sdk")) {
                    for (Attr a : attrs) {
                        if (a.name.equals("minSdkVersion")) in.minSdk = a.data;
                        if (a.name.equals("targetSdkVersion")) in.targetSdk = a.data;
                    }
                } else if (el.equals("uses-permission") || el.equals("permission")) {
                    for (Attr a : attrs) if (a.name.equals("name")) {
                        in.permissions.add(a.text);
                        if (el.equals("permission") && a.rawIdx >= 0) in.permissionIdx.add(a.rawIdx);
                    }
                } else if (el.equals("activity") || el.equals("activity-alias")) {
                    for (Attr a : attrs) if (a.name.equals("name")) {
                        curAct[0] = a.text; hasMain[0] = false;
                        in.activities.add(abs(in.pkg, a.text));
                    }
                } else if (el.equals("action")) {
                    for (Attr a : attrs) if (a.name.equals("name") && a.text.equals("android.intent.action.MAIN") && curAct[0] != null)
                        hasMain[0] = true;
                } else if (el.equals("category")) {
                    for (Attr a : attrs) if (a.name.equals("name") && a.text.equals("android.intent.category.LAUNCHER") && curAct[0] != null && hasMain[0] && in.launcher == null)
                        in.launcher = abs(in.pkg, curAct[0]);
                } else if (el.equals("provider")) {
                    for (Attr a : attrs) if (a.name.equals("authorities") && a.rawIdx >= 0) in.authorityIdx.add(a.rawIdx);
                }
                if (!el.equals("action") && !el.equals("category") && !el.equals("intent-filter") && !el.equals("data")
                        && !el.equals("meta-data") && !el.equals("activity") && !el.equals("activity-alias")) { /* keep */ }
            }
            public void end(String el, int depth) {
                if (el.equals("activity") || el.equals("activity-alias")) curAct[0] = null;
            }
        });
        return in;
    }

    public static String abs(String pkg, String name) {
        if (name.startsWith(".")) return pkg + name;
        if (!name.contains(".")) return pkg + "." + name;
        return name;
    }

    // ------------------------------------------------------------------ structural edits
    private static int firstElementPos(byte[] d) {
        int off = u16(d, 2);
        return off + i32(d, off + 4);
    }

    public static byte[] setIntAttr(byte[] d, String element, String attr, int value) {
        byte[] out = d.clone();
        Pool p = parsePool(out, u16(out, 2));
        int pos = firstElementPos(out);
        while (pos + 8 <= out.length) {
            int type = u16(out, pos); int size = i32(out, pos + 4);
            if (size <= 0) break;
            if (type == T_START) {
                String el = p.strings.get(i32(out, pos + 20));
                if (el.equals(element)) {
                    int attrStart = u16(out, pos + 24), attrSize = u16(out, pos + 26), cnt = u16(out, pos + 28);
                    for (int i = 0; i < cnt; i++) {
                        int a = pos + 16 + attrStart + i * attrSize;
                        String nm = p.strings.get(i32(out, a + 4));
                        int t = out[a + 15] & 0xff;
                        if (nm.equals(attr) && (t == 0x10 || t == 0x11 || t == 0x12)) {
                            p32(out, a + 16, t == 0x12 ? (value != 0 ? -1 : 0) : value);
                            p32(out, a + 8, -1);
                        }
                    }
                }
            }
            pos += size;
        }
        return out;
    }

    private static int indexOfResId(byte[] d, int resId) {
        int off = u16(d, 2);
        int pos = off + i32(d, off + 4);
        while (pos + 8 <= d.length) {
            int type = u16(d, pos); int size = i32(d, pos + 4);
            if (size <= 0) break;
            if (type == T_RESMAP) {
                int n = (size - 8) / 4;
                for (int i = 0; i < n; i++) if (i32(d, pos + 8 + i * 4) == resId) return i;
            }
            pos += size;
        }
        return -1;
    }

    public static byte[] addPermission(byte[] d, String perm) {
        int off = u16(d, 2);
        Pool p = parsePool(d, off);
        if (info(d).permissions.contains(perm)) return d;
        int nameAttr = indexOfResId(d, 0x01010003);
        int nsIdx = p.strings.indexOf(ANDROID_NS);
        if (nameAttr < 0 || nsIdx < 0) return d;
        int appPos = -1;
        int pos = firstElementPos(d);
        while (pos + 8 <= d.length) {
            int type = u16(d, pos); int size = i32(d, pos + 4);
            if (size <= 0) break;
            if (type == T_START && "application".equals(p.strings.get(i32(d, pos + 20)))) { appPos = pos; break; }
            pos += size;
        }
        if (appPos < 0) return d;
        int oldPoolSize = i32(d, off + 4);
        int elIdx = p.strings.indexOf("uses-permission");
        if (elIdx < 0) { elIdx = p.strings.size(); p.strings.add("uses-permission"); }
        int valIdx = p.strings.indexOf(perm);
        if (valIdx < 0) { valIdx = p.strings.size(); p.strings.add(perm); }
        byte[] np = buildPool(p);
        byte[] st = new byte[56];
        p16(st, 0, T_START); p16(st, 2, 16); p32(st, 4, 56); p32(st, 8, 0); p32(st, 12, -1);
        p32(st, 16, -1); p32(st, 20, elIdx);
        p16(st, 24, 0x14); p16(st, 26, 0x14); p16(st, 28, 1); p16(st, 30, 0); p16(st, 32, 0); p16(st, 34, 0);
        p32(st, 36, nsIdx); p32(st, 40, nameAttr); p32(st, 44, valIdx);
        p16(st, 48, 8); st[50] = 0; st[51] = 3; p32(st, 52, valIdx);
        byte[] en = new byte[24];
        p16(en, 0, T_END); p16(en, 2, 16); p32(en, 4, 24); p32(en, 8, 0); p32(en, 12, -1);
        p32(en, 16, -1); p32(en, 20, elIdx);
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(d, 0, off);
        o.write(np, 0, np.length);
        o.write(d, off + oldPoolSize, appPos - off - oldPoolSize);
        o.write(st, 0, st.length);
        o.write(en, 0, en.length);
        o.write(d, appPos, d.length - appPos);
        byte[] out = o.toByteArray();
        p32(out, 4, out.length);
        return out;
    }

    public static byte[] removePermission(byte[] d, String perm) {
        int off = u16(d, 2);
        Pool p = parsePool(d, off);
        int pos = firstElementPos(d);
        while (pos + 8 <= d.length) {
            int type = u16(d, pos); int size = i32(d, pos + 4);
            if (size <= 0) break;
            if (type == T_START && "uses-permission".equals(p.strings.get(i32(d, pos + 20)))) {
                int attrStart = u16(d, pos + 24), attrSize = u16(d, pos + 26), cnt = u16(d, pos + 28);
                for (int i = 0; i < cnt; i++) {
                    int a = pos + 16 + attrStart + i * attrSize;
                    if ("name".equals(p.strings.get(i32(d, a + 4)))) {
                        int ti = d[a + 15] & 0xff;
                        int raw = i32(d, a + 8);
                        int si = ti == 3 ? (raw != -1 ? raw : i32(d, a + 16)) : -1;
                        if (si >= 0 && perm.equals(p.strings.get(si))) {
                            int next = pos + size;
                            int endSize = 0;
                            if (next + 8 <= d.length && u16(d, next) == T_END) endSize = i32(d, next + 4);
                            byte[] out = new byte[d.length - size - endSize];
                            System.arraycopy(d, 0, out, 0, pos);
                            System.arraycopy(d, pos + size + endSize, out, pos, d.length - pos - size - endSize);
                            p32(out, 4, out.length);
                            return out;
                        }
                    }
                }
            }
            pos += size;
        }
        return d;
    }
}
