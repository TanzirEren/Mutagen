package com.mutagen.lab.core;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import org.jf.baksmali.Baksmali;
import org.jf.baksmali.BaksmaliOptions;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.iface.DexFile;

import java.io.File;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** APK -> workspace (original copy + baksmali output for every classes*.dex). */
public final class Importer {
    private Importer() {}

    public static Project importApk(Context c, File src, Log log) throws Exception {
        PackageManager pm = c.getPackageManager();
        PackageInfo pi = pm.getPackageArchiveInfo(src.getAbsolutePath(), 0);
        String pkg = pi != null ? pi.packageName : src.getName().replace(".apk", "").replaceAll("[^A-Za-z0-9._-]", "_");
        File root = Project.root(c);
        File dir = new File(root, pkg);
        int n = 2;
        while (dir.exists()) dir = new File(root, pkg + "-" + (n++));
        dir.mkdirs();
        Project pr = new Project(dir);
        log.i("Project: " + dir.getName());
        Files2.copy(new java.io.FileInputStream(src), pr.apk());
        log.i("Copied APK (" + Files2.human(pr.apk().length()) + ")");

        Properties meta = new Properties();
        meta.setProperty("package", pkg);
        if (pi != null) {
            ApplicationInfo ai = pi.applicationInfo;
            ai.sourceDir = pr.apk().getAbsolutePath();
            ai.publicSourceDir = pr.apk().getAbsolutePath();
            meta.setProperty("label", String.valueOf(ai.loadLabel(pm)));
            meta.setProperty("versionName", pi.versionName == null ? "" : pi.versionName);
        }
        pr.saveMeta(meta);

        ZipFile z = new ZipFile(pr.apk());
        try {
            java.util.Enumeration<? extends ZipEntry> en = z.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                String nm = e.getName();
                if (nm.matches("classes\\d*\\.dex")) {
                    File tmp = new File(c.getCacheDir(), nm);
                    Files2.copy(z.getInputStream(e), tmp);
                    String outName = nm.equals("classes.dex") ? "smali" : "smali_" + nm.replace(".dex", "");
                    File out = new File(dir, outName);
                    log.i("Disassembling " + nm + " -> " + outName + " ...");
                    disassemble(tmp, out);
                    tmp.delete();
                    log.i("  done");
                }
            }
        } finally { z.close(); }
        log.i("Decompile finished: " + pr.smaliFiles().size() + " smali classes");
        return pr;
    }

    public static void disassemble(File dex, File outDir) throws Exception {
        DexFile df = DexFileFactory.loadDexFile(dex, Opcodes.forApi(30));
        BaksmaliOptions bo = new BaksmaliOptions();
        Reflect.set(bo, "apiLevel", 30);
        Reflect.set(bo, "localsDirective", true);
        int jobs = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));
        outDir.mkdirs();
        boolean ok = Baksmali.disassembleDexFile(df, outDir, jobs, bo);
        if (!ok) throw new Exception("baksmali reported errors for " + dex.getName());
    }
}
