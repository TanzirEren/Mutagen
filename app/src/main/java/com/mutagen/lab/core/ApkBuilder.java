package com.mutagen.lab.core;

import android.content.Context;

import org.jf.smali.Smali;
import org.jf.smali.SmaliOptions;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Rebuilds smali -> dex, repacks the APK with overlay edits, zip-aligns and signs. */
public final class ApkBuilder {
    private ApkBuilder() {}

    public static File build(Context c, Project pr, Log log) throws Exception {
        File work = new File(c.getCacheDir(), "build");
        Files2.deleteRecursive(work);
        work.mkdirs();

        // 1) smali -> dex
        Set<String> rebuilt = new HashSet<>();
        File dexOut = new File(work, "dex");
        dexOut.mkdirs();
        for (File sd : pr.smaliDirs()) {
            String dn = Project.dexNameFor(sd);
            log.i("Assembling " + sd.getName() + " -> " + dn);
            assemble(sd, new File(dexOut, dn));
            rebuilt.add(dn);
        }

        // 2) repack
        File unsigned = new File(work, "unsigned.apk");
        Set<String> deleted = pr.deleted();
        Set<String> written = new HashSet<>();
        ZipWriter zw = new ZipWriter(new FileOutputStream(unsigned));
        ZipFile z = new ZipFile(pr.apk());
        int count = 0;
        try {
            java.util.Enumeration<? extends ZipEntry> en = z.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                String n = e.getName();
                if (e.isDirectory()) continue;
                if (isSignatureFile(n)) continue;
                if (deleted.contains(n)) { log.i("  - removed " + n); continue; }
                byte[] data;
                boolean stored = e.getMethod() == ZipEntry.STORED;
                if (rebuilt.contains(n)) {
                    data = Files2.readBytes(new File(dexOut, n));
                } else if (pr.hasOverlay(n)) {
                    data = Files2.readBytes(pr.overlayFile(n));
                    log.i("  ~ replaced " + n);
                } else {
                    data = Files2.readAll(z.getInputStream(e));
                }
                if (n.equals("resources.arsc")) stored = true;
                zw.add(n, data, stored);
                written.add(n);
                count++;
            }
            // dex files that did not exist originally (e.g. a new smali_classesN folder)
            for (String dn : rebuilt) if (!written.contains(dn)) {
                zw.add(dn, Files2.readBytes(new File(dexOut, dn)), false); written.add(dn); count++;
                log.i("  + added " + dn);
            }
            for (String n : pr.overlayEntries()) if (!written.contains(n) && !deleted.contains(n)) {
                zw.add(n, Files2.readBytes(pr.overlayFile(n)), n.equals("resources.arsc"));
                written.add(n); count++;
                log.i("  + added " + n);
            }
        } finally { z.close(); }
        zw.close();
        log.i("Packed " + count + " entries (" + Files2.human(unsigned.length()) + ")");

        // 3) sign
        String base = pr.meta().getProperty("package", pr.name);
        File out = new File(pr.outDir(), base + "-mutagen.apk");
        if (out.exists()) out.delete();
        log.i("Signing (v1 + v2 + v3) ...");
        Signer.sign(c, unsigned, out, 21);
        Files2.deleteRecursive(work);
        log.i("Signed: " + out.getName() + " (" + Files2.human(out.length()) + ")");
        return out;
    }

    static boolean isSignatureFile(String n) {
        if (!n.startsWith("META-INF/")) return false;
        String u = n.toUpperCase();
        return u.equals("META-INF/MANIFEST.MF") || u.endsWith(".SF") || u.endsWith(".RSA") || u.endsWith(".DSA") || u.endsWith(".EC");
    }

    static void assemble(File smaliDir, File outDex) throws Exception {
        SmaliOptions o = new SmaliOptions();
        Reflect.set(o, "apiLevel", 30);
        o.outputDexFile = outDex.getAbsolutePath();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        PrintStream old = System.err;
        System.setErr(new PrintStream(err, true));
        boolean ok;
        try {
            ok = Smali.assemble(o, Collections.singletonList(smaliDir.getAbsolutePath()));
        } finally { System.setErr(old); }
        if (!ok) {
            String msg = err.toString("UTF-8");
            if (msg.length() > 3000) msg = msg.substring(0, 3000) + "\n...";
            throw new Exception("Smali error in " + smaliDir.getName() + ":\n" + msg);
        }
    }
}
