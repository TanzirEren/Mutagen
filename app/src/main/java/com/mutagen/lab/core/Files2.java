package com.mutagen.lab.core;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public final class Files2 {
    private Files2() {}

    public static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] b = new byte[16384];
        int n;
        while ((n = in.read(b)) > 0) o.write(b, 0, n);
        in.close();
        return o.toByteArray();
    }
    public static byte[] readBytes(File f) {
        try { return readAll(new FileInputStream(f)); } catch (IOException e) { return null; }
    }
    public static String readText(File f) {
        byte[] b = readBytes(f);
        try { return b == null ? "" : new String(b, "UTF-8"); } catch (Exception e) { return ""; }
    }
    public static List<String> readLines(File f) {
        List<String> l = new ArrayList<>();
        for (String s : readText(f).split("\n")) l.add(s.replace("\r", ""));
        return l;
    }
    public static void writeBytes(File f, byte[] data) {
        try {
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            FileOutputStream o = new FileOutputStream(f);
            o.write(data);
            o.close();
        } catch (IOException e) { throw new RuntimeException(e); }
    }
    public static void writeText(File f, String s) {
        try { writeBytes(f, s.getBytes("UTF-8")); } catch (Exception e) { throw new RuntimeException(e); }
    }
    public static void copy(InputStream in, File dst) throws IOException {
        if (dst.getParentFile() != null) dst.getParentFile().mkdirs();
        FileOutputStream o = new FileOutputStream(dst);
        byte[] b = new byte[32768];
        int n;
        while ((n = in.read(b)) > 0) o.write(b, 0, n);
        o.close();
        in.close();
    }
    public static void deleteRecursive(File f) {
        File[] c = f.listFiles();
        if (c != null) for (File x : c) deleteRecursive(x);
        f.delete();
    }
    public static String human(long n) {
        if (n < 1024) return n + " B";
        if (n < 1024 * 1024) return String.format("%.1f KB", n / 1024.0);
        return String.format("%.2f MB", n / 1048576.0);
    }
}
