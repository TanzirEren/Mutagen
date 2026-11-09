package com.mutagen.lab.core;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Reads counts straight from the DEX headers (no library needed). */
public final class DexInfo {
    public String name;
    public int strings, types, protos, fields, methods, classes;
    public long size;

    static int le(byte[] b, int o) {
        return (b[o] & 0xff) | ((b[o + 1] & 0xff) << 8) | ((b[o + 2] & 0xff) << 16) | ((b[o + 3] & 0xff) << 24);
    }

    public static List<DexInfo> of(Project p) {
        List<DexInfo> out = new ArrayList<>();
        try {
            ZipFile z = new ZipFile(p.apk());
            try {
                java.util.Enumeration<? extends ZipEntry> en = z.entries();
                while (en.hasMoreElements()) {
                    ZipEntry e = en.nextElement();
                    if (!e.getName().matches("classes\\d*\\.dex")) continue;
                    byte[] head = new byte[0x70];
                    java.io.InputStream in = z.getInputStream(e);
                    int off = 0, n;
                    while (off < head.length && (n = in.read(head, off, head.length - off)) > 0) off += n;
                    in.close();
                    DexInfo d = new DexInfo();
                    d.name = e.getName(); d.size = e.getSize();
                    d.strings = le(head, 0x38); d.types = le(head, 0x40); d.protos = le(head, 0x48);
                    d.fields = le(head, 0x50); d.methods = le(head, 0x58); d.classes = le(head, 0x60);
                    out.add(d);
                }
            } finally { z.close(); }
        } catch (Exception ignored) {}
        return out;
    }

    public String summary() {
        return name + "  ·  " + classes + " classes · " + methods + " methods · " + fields + " fields · " + strings + " strings"
                + (methods > 60000 ? "  ⚠ near 64K limit" : "");
    }
}
