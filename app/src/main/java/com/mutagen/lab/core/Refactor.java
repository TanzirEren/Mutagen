package com.mutagen.lab.core;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;

/** Class / package / app-name / manifest refactors across smali + binary XML + arsc. */
public final class Refactor {
    private Refactor() {}

    public static final String MANIFEST = "AndroidManifest.xml";

    // ---------------------------------------------------------------- binary xml helpers
    /** Applies an editor to AndroidManifest.xml, all res/**.xml and resources.arsc. */
    public static int editAllBinary(Project p, Axml.Editor ed, boolean includeArsc) {
        int changed = 0;
        List<String> names = new ArrayList<>();
        names.add(MANIFEST);
        for (ZipEntry e : p.originalEntries()) {
            String n = e.getName();
            if (n.startsWith("res/") && n.endsWith(".xml")) names.add(n);
        }
        for (String n : p.overlayEntries()) if (n.startsWith("res/") && n.endsWith(".xml") && !names.contains(n)) names.add(n);
        if (includeArsc) names.add("resources.arsc");
        for (String n : names) {
            if (p.deleted().contains(n)) continue;
            byte[] d = p.entry(n);
            if (d == null || d.length < 16) continue;
            try {
                byte[] r = Axml.editPool(d, ed);
                if (r != d) { p.putOverlay(n, r); changed++; }
            } catch (Throwable ignored) { }
        }
        return changed;
    }

    // ---------------------------------------------------------------- smali text helpers
    public static int replaceInSmali(Project p, String from, String to, boolean regex, Log log) {
        int files = 0;
        for (File f : p.smaliFiles()) {
            String s = Files2.readText(f);
            String r = regex ? s.replaceAll(from, to) : s.replace(from, to);
            if (!r.equals(s)) { Files2.writeText(f, r); files++; }
        }
        if (log != null) log.i("Replaced in " + files + " smali file(s)");
        return files;
    }

    // ---------------------------------------------------------------- class rename
    public static String renameClass(Project p, String oldDotted, String newDotted, Log log) throws Exception {
        File f = p.findSmaliForClass(oldDotted);
        if (f == null) throw new Exception("Class not found: " + oldDotted);
        String oldPath = oldDotted.replace('.', '/');
        String newPath = newDotted.replace('.', '/');
        File root = null;
        for (File d : p.smaliDirs()) if (f.getAbsolutePath().startsWith(d.getAbsolutePath() + "/")) root = d;
        // move main + inner classes
        File oldDir = f.getParentFile();
        String oldSimple = oldPath.substring(oldPath.lastIndexOf('/') + 1);
        String newSimple = newPath.substring(newPath.lastIndexOf('/') + 1);
        File newFile = new File(root, newPath + ".smali");
        newFile.getParentFile().mkdirs();
        File[] sibs = oldDir.listFiles();
        if (sibs != null) for (File s : sibs) {
            String n = s.getName();
            if (n.equals(oldSimple + ".smali") || (n.startsWith(oldSimple + "$") && n.endsWith(".smali"))) {
                String target = newSimple + n.substring(oldSimple.length());
                File dst = new File(newFile.getParentFile(), target);
                if (!s.renameTo(dst)) throw new Exception("Cannot move " + n);
            }
        }
        prune(oldDir);
        replaceInSmali(p, "L" + oldPath + ";", "L" + newPath + ";", false, null);
        replaceInSmali(p, "L" + oldPath + "$", "L" + newPath + "$", false, null);
        replaceInSmali(p, "\"" + oldDotted + "\"", "\"" + newDotted + "\"", false, null);
        final String od = oldDotted, nd = newDotted;
        final String oldRel = oldDotted.substring(oldDotted.lastIndexOf('.'));
        final boolean samePkg = oldDotted.substring(0, Math.max(0, oldDotted.lastIndexOf('.')))
                .equals(newDotted.substring(0, Math.max(0, newDotted.lastIndexOf('.'))));
        final String newRel = newDotted.substring(newDotted.lastIndexOf('.'));
        int n = editAllBinary(p, new Axml.Editor() {
            public String edit(int i, String s) {
                if (s.equals(od)) return nd;
                if (samePkg && s.equals(oldRel)) return newRel;
                return null;
            }
        }, false);
        log.i("Renamed " + oldDotted + " -> " + newDotted + " (" + n + " binary xml updated)");
        return newDotted;
    }

    private static void prune(File d) {
        while (d != null && d.isDirectory()) {
            String[] c = d.list();
            if (c != null && c.length == 0) { File parent = d.getParentFile(); d.delete(); d = parent; } else break;
        }
    }

    // ---------------------------------------------------------------- package refactor
    /** Full refactor: moves smali packages and rewrites every reference. */
    public static void renamePackageFull(Project p, String oldPkg, String newPkg, Log log) throws Exception {
        String oldPath = oldPkg.replace('.', '/');
        String newPath = newPkg.replace('.', '/');
        int moved = 0;
        for (File root : p.smaliDirs()) {
            File od = new File(root, oldPath);
            if (od.exists()) {
                File nd = new File(root, newPath);
                nd.getParentFile().mkdirs();
                if (nd.exists()) mergeInto(od, nd); else if (!od.renameTo(nd)) throw new Exception("Cannot move package dir in " + root.getName());
                prune(od.getParentFile());
                moved++;
            }
        }
        log.i("Moved package folders: " + moved);
        replaceInSmali(p, "L" + oldPath + "/", "L" + newPath + "/", false, log);
        replaceInSmali(p, oldPkg + ".", newPkg + ".", false, null);
        replaceInSmali(p, "\"" + oldPkg + "\"", "\"" + newPkg + "\"", false, null);
        final String op = oldPkg, np = newPkg;
        int n = editAllBinary(p, new Axml.Editor() {
            public String edit(int i, String s) {
                if (s.equals(op)) return np;
                if (s.startsWith(op + ".")) return np + s.substring(op.length());
                return null;
            }
        }, false);
        log.i("Binary xml updated: " + n);
    }

    private static void mergeInto(File from, File to) {
        File[] fs = from.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            File t = new File(to, f.getName());
            if (f.isDirectory()) { t.mkdirs(); mergeInto(f, t); } else f.renameTo(t);
        }
    }

    /** Safe applicationId change: only the manifest package + provider authorities + custom permissions. */
    public static void changeApplicationId(Project p, final String newPkg, boolean authorities, Log log) {
        byte[] m = p.entry(MANIFEST);
        final Axml.Info info = Axml.info(m);
        final String oldPkg = info.pkg;
        if (oldPkg.isEmpty()) { log.i("Manifest has no package?"); return; }
        final boolean auth = authorities;
        byte[] r = Axml.editPool(m, new Axml.Editor() {
            public String edit(int i, String s) {
                if (s.equals(oldPkg)) return newPkg;
                // relative class names (".Main") must be pinned to the OLD package so they keep resolving
                if (s.length() > 1 && s.charAt(0) == '.' && Character.isLetter(s.charAt(1)) && s.indexOf(' ') < 0 && s.indexOf('/') < 0) return oldPkg + s;
                if (auth && (info.authorityIdx.contains(i) || info.permissionIdx.contains(i)) && s.startsWith(oldPkg + ".")) return newPkg + s.substring(oldPkg.length());
                return null;
            }
        });
        p.putOverlay(MANIFEST, r);
        log.i("applicationId: " + oldPkg + " -> " + newPkg);
    }

    // ---------------------------------------------------------------- app name
    public static int renameApp(Project p, final String oldLabel, final String newLabel, Log log) {
        int n = editAllBinary(p, new Axml.Editor() {
            public String edit(int i, String s) { return s.equals(oldLabel) ? newLabel : null; }
        }, true);
        log.i("App name '" + oldLabel + "' -> '" + newLabel + "' in " + n + " file(s)");
        return n;
    }
}
