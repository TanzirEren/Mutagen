package com.mutagen.lab.core;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** One decompiled app workspace: original.apk + smali dirs + overlay of edited resources. */
public final class Project {
    public final File dir;
    public final String name;

    public Project(File dir) { this.dir = dir; this.name = dir.getName(); }

    // ---- locations
    public static File root(Context c) {
        File base = c.getExternalFilesDir(null);
        if (base == null) base = c.getFilesDir();
        File r = new File(base, "projects");
        r.mkdirs();
        return r;
    }

    public static List<Project> all(Context c) {
        File[] fs = root(c).listFiles();
        List<Project> l = new ArrayList<>();
        if (fs != null) for (File f : fs) if (f.isDirectory() && new File(f, "original.apk").exists()) l.add(new Project(f));
        Collections.sort(l, new Comparator<Project>() {
            public int compare(Project a, Project b) { return Long.compare(b.dir.lastModified(), a.dir.lastModified()); }
        });
        return l;
    }

    public File apk() { return new File(dir, "original.apk"); }
    public File overlay() { File f = new File(dir, "overlay"); f.mkdirs(); return f; }
    public File outDir() { File f = new File(dir, "out"); f.mkdirs(); return f; }
    public File metaFile() { return new File(dir, "meta.properties"); }

    public Properties meta() {
        Properties p = new Properties();
        try { FileInputStream in = new FileInputStream(metaFile()); p.load(in); in.close(); } catch (Exception ignored) {}
        return p;
    }
    public void saveMeta(Properties p) {
        try { FileOutputStream o = new FileOutputStream(metaFile()); p.store(o, "mutagen"); o.close(); } catch (Exception ignored) {}
    }

    public List<File> smaliDirs() {
        File[] fs = dir.listFiles();
        List<File> l = new ArrayList<>();
        if (fs != null) for (File f : fs) if (f.isDirectory() && (f.getName().equals("smali") || f.getName().startsWith("smali_classes"))) l.add(f);
        Collections.sort(l, new Comparator<File>() {
            public int compare(File a, File b) { return Integer.compare(idx(a), idx(b)); }
            int idx(File f) {
                String n = f.getName();
                if (n.equals("smali")) return 1;
                try { return Integer.parseInt(n.substring("smali_classes".length())); } catch (Exception e) { return 99; }
            }
        });
        return l;
    }

    public static String dexNameFor(File smaliDir) {
        String n = smaliDir.getName();
        if (n.equals("smali")) return "classes.dex";
        return "classes" + n.substring("smali_classes".length()) + ".dex";
    }

    // ---- smali files
    public List<File> smaliFiles() {
        List<File> out = new ArrayList<>();
        for (File d : smaliDirs()) walk(d, out);
        return out;
    }
    private static void walk(File d, List<File> out) {
        File[] fs = d.listFiles();
        if (fs == null) return;
        Arrays.sort(fs);
        for (File f : fs) { if (f.isDirectory()) walk(f, out); else if (f.getName().endsWith(".smali")) out.add(f); }
    }
    public String relativeSmali(File f) {
        for (File d : smaliDirs()) {
            String p = d.getAbsolutePath() + "/";
            if (f.getAbsolutePath().startsWith(p)) return f.getAbsolutePath().substring(p.length());
        }
        return f.getName();
    }
    public File findSmaliForClass(String dotted) {
        String rel = dotted.replace('.', '/') + ".smali";
        for (File d : smaliDirs()) { File f = new File(d, rel); if (f.exists()) return f; }
        return null;
    }

    // ---- overlay + original entries
    public File overlayFile(String entry) { return new File(overlay(), entry); }

    public Set<String> deleted() {
        Set<String> s = new LinkedHashSet<>();
        File f = new File(dir, "deleted.txt");
        if (f.exists()) for (String l : Files2.readLines(f)) if (!l.isEmpty()) s.add(l);
        return s;
    }
    private void saveDeleted(Set<String> s) { Files2.writeText(new File(dir, "deleted.txt"), join(s)); }
    private static String join(Set<String> s) { StringBuilder b = new StringBuilder(); for (String x : s) b.append(x).append('\n'); return b.toString(); }

    public void deleteEntry(String entry) {
        Set<String> s = deleted(); s.add(entry); saveDeleted(s);
        overlayFile(entry).delete();
    }
    public void restoreEntry(String entry) {
        Set<String> s = deleted(); s.remove(entry); saveDeleted(s);
        overlayFile(entry).delete();
    }
    public void putOverlay(String entry, byte[] data) {
        Set<String> s = deleted(); if (s.remove(entry)) saveDeleted(s);
        File f = overlayFile(entry);
        f.getParentFile().mkdirs();
        Files2.writeBytes(f, data);
    }
    public boolean hasOverlay(String entry) { return overlayFile(entry).exists(); }

    public List<String> overlayEntries() {
        List<String> l = new ArrayList<>();
        collect(overlay(), overlay().getAbsolutePath().length() + 1, l);
        return l;
    }
    private void collect(File d, int cut, List<String> out) {
        File[] fs = d.listFiles();
        if (fs == null) return;
        for (File f : fs) { if (f.isDirectory()) collect(f, cut, out); else out.add(f.getAbsolutePath().substring(cut)); }
    }

    public byte[] originalEntry(String name) {
        try {
            ZipFile z = new ZipFile(apk());
            try {
                ZipEntry e = z.getEntry(name);
                if (e == null) return null;
                return Files2.readAll(z.getInputStream(e));
            } finally { z.close(); }
        } catch (IOException e) { return null; }
    }

    /** Current content: overlay if edited, else original. */
    public byte[] entry(String name) {
        File o = overlayFile(name);
        if (o.exists()) return Files2.readBytes(o);
        if (deleted().contains(name)) return null;
        return originalEntry(name);
    }

    public List<ZipEntry> originalEntries() {
        List<ZipEntry> l = new ArrayList<>();
        try {
            ZipFile z = new ZipFile(apk());
            try {
                java.util.Enumeration<? extends ZipEntry> en = z.entries();
                while (en.hasMoreElements()) { ZipEntry e = en.nextElement(); if (!e.isDirectory()) l.add(e); }
            } finally { z.close(); }
        } catch (IOException ignored) {}
        return l;
    }

    public void deleteProject() { Files2.deleteRecursive(dir); }
}
