package com.mutagen.lab.core;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/** Minimal ZIP writer with zipalign-style alignment for stored entries. */
public final class ZipWriter implements Closeable {
    private final OutputStream out;
    private long pos = 0;
    private final List<Object[]> central = new ArrayList<>();

    public ZipWriter(OutputStream out) { this.out = out; }

    private void w(byte[] b) throws IOException { out.write(b); pos += b.length; }
    private static void le16(ByteArrayOutputStream o, int v) { o.write(v & 0xff); o.write((v >> 8) & 0xff); }
    private static void le32(ByteArrayOutputStream o, long v) {
        o.write((int) (v & 0xff)); o.write((int) ((v >> 8) & 0xff)); o.write((int) ((v >> 16) & 0xff)); o.write((int) ((v >> 24) & 0xff));
    }

    public void add(String name, byte[] data, boolean stored) throws IOException {
        int align = 1;
        if (stored) align = name.endsWith(".so") ? 4096 : 4;
        CRC32 crc = new CRC32();
        crc.update(data, 0, data.length);
        byte[] body = data;
        int method = 0;
        if (!stored) {
            Deflater df = new Deflater(6, true);
            df.setInput(data);
            df.finish();
            ByteArrayOutputStream bo = new ByteArrayOutputStream(Math.max(64, data.length / 2));
            byte[] buf = new byte[16384];
            while (!df.finished()) { int n = df.deflate(buf); bo.write(buf, 0, n); }
            df.end();
            body = bo.toByteArray();
            method = 8;
            if (body.length >= data.length) { body = data; method = 0; align = name.endsWith(".so") ? 4096 : 4; }
        }
        byte[] nm = name.getBytes("UTF-8");
        int pad = 0;
        if (method == 0 && align > 1) {
            long dataStart = pos + 30 + nm.length;
            pad = (int) ((align - (dataStart % align)) % align);
        }
        int flags = 0x0800;
        long offset = pos;
        ByteArrayOutputStream h = new ByteArrayOutputStream();
        le32(h, 0x04034b50L); le16(h, 20); le16(h, flags); le16(h, method);
        le16(h, 0); le16(h, 0x21); // fixed DOS time/date (1980-01-01)
        le32(h, crc.getValue()); le32(h, body.length); le32(h, data.length);
        le16(h, nm.length); le16(h, pad);
        h.write(nm, 0, nm.length);
        for (int i = 0; i < pad; i++) h.write(0);
        w(h.toByteArray());
        w(body);
        central.add(new Object[]{nm, method, crc.getValue(), (long) body.length, (long) data.length, offset});
    }

    @Override
    public void close() throws IOException {
        long cdStart = pos;
        for (Object[] e : central) {
            byte[] nm = (byte[]) e[0];
            ByteArrayOutputStream h = new ByteArrayOutputStream();
            le32(h, 0x02014b50L); le16(h, 20); le16(h, 20); le16(h, 0x0800); le16(h, (Integer) e[1]);
            le16(h, 0); le16(h, 0x21);
            le32(h, (Long) e[2]); le32(h, (Long) e[3]); le32(h, (Long) e[4]);
            le16(h, nm.length); le16(h, 0); le16(h, 0); le16(h, 0); le16(h, 0); le32(h, 0); le32(h, (Long) e[5]);
            h.write(nm, 0, nm.length);
            w(h.toByteArray());
        }
        long cdSize = pos - cdStart;
        ByteArrayOutputStream h = new ByteArrayOutputStream();
        le32(h, 0x06054b50L); le16(h, 0); le16(h, 0); le16(h, central.size()); le16(h, central.size());
        le32(h, cdSize); le32(h, cdStart); le16(h, 0);
        w(h.toByteArray());
        out.flush();
        out.close();
    }
}
