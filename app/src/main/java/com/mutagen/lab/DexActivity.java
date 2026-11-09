package com.mutagen.lab;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.mutagen.lab.core.DexInfo;
import com.mutagen.lab.core.Files2;
import com.mutagen.lab.core.Importer;
import com.mutagen.lab.core.Project;
import com.mutagen.lab.core.Refactor;

import java.io.File;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class DexActivity extends BaseActivity {
    private TextView body;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_text);
        loadProject();
        header("Dex tools", "Inspect and reshape classes");
        body = (TextView) findViewById(R.id.body);
        btn(R.id.btn1, "Add class", new Runnable() { public void run() { addClass(); } });
        btn(R.id.btn2, "Delete class", new Runnable() { public void run() { delClass(); } });
        btn(R.id.btn3, "Rename class", new Runnable() { public void run() { renClass(); } });
        btn(R.id.btn4, "Re-decompile", new Runnable() { public void run() { redecompile(); } });
        btn(R.id.btn5, "Refresh", new Runnable() { public void run() { show(); } });
        findViewById(R.id.btn6).setVisibility(View.GONE);
        show();
    }

    private void btn(int id, String t, final Runnable r) {
        Button b = (Button) findViewById(id); b.setText(t); Ui.pressable(b);
        b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.run(); } });
    }

    private void show() {
        StringBuilder sb = new StringBuilder("ORIGINAL DEX FILES\n\n");
        List<DexInfo> l = DexInfo.of(project);
        for (DexInfo d : l) sb.append(d.summary()).append("\n   size ").append(Files2.human(d.size)).append("\n\n");
        sb.append("WORKSPACE\n\n");
        for (File d : project.smaliDirs()) sb.append(d.getName()).append("  →  ").append(Project.dexNameFor(d)).append('\n');
        sb.append("\nSmali classes now: ").append(project.smaliFiles().size());
        sb.append("\n\nEvery build re-assembles all smali folders into fresh classes*.dex files.");
        body.setText(sb.toString());
    }

    private void addClass() {
        Ui.prompt(this, "Add class", "com.example.Helper", "", new Ui.Cb<String>() {
            public void on(String name) {
                name = name.trim(); if (name.isEmpty()) return;
                File f = new File(project.smaliDirs().get(0), name.replace('.', '/') + ".smali");
                if (f.exists()) { toast("Exists"); return; }
                Files2.writeText(f, ".class public L" + name.replace('.', '/') + ";\n.super Ljava/lang/Object;\n\n.method public constructor <init>()V\n    .locals 0\n    invoke-direct {p0}, Ljava/lang/Object;-><init>()V\n    return-void\n.end method\n");
                toast("Class added"); show();
            }
        });
    }

    private void delClass() {
        Ui.prompt(this, "Delete class", "com.example.Helper", "", new Ui.Cb<String>() {
            public void on(String name) {
                File f = project.findSmaliForClass(name.trim());
                if (f == null) { toast("Not found"); return; }
                f.delete(); toast("Deleted"); show();
            }
        });
    }

    private void renClass() {
        Ui.prompt2(this, "Rename class", "old (com.a.B)", "", "new (com.x.Y)", "", new Ui.Cb2() {
            public void on(final String a, final String b2) {
                bg(new Runnable() { public void run() {
                    try {
                        Refactor.renameClass(project, a.trim(), b2.trim(), new com.mutagen.lab.core.Log() { public void i(String s) {} });
                        ui(new Runnable() { public void run() { toast("Renamed"); show(); } });
                    } catch (Throwable t) { showError(t); }
                }});
            }
        });
    }

    private void redecompile() {
        Ui.confirm(this, "Re-decompile?", "Throws away ALL smali edits and injections and decompiles the original APK again.", new Runnable() { public void run() {
            body.setText("Re-decompiling...");
            bg(new Runnable() { public void run() {
                try {
                    for (File d : project.smaliDirs()) Files2.deleteRecursive(d);
                    SmaliCleanup.clear(project);
                    ZipFile z = new ZipFile(project.apk());
                    java.util.Enumeration<? extends ZipEntry> en = z.entries();
                    while (en.hasMoreElements()) {
                        ZipEntry e = en.nextElement();
                        String nm = e.getName();
                        if (nm.matches("classes\\d*\\.dex")) {
                            File tmp = new File(getCacheDir(), nm);
                            Files2.copy(z.getInputStream(e), tmp);
                            Importer.disassemble(tmp, new File(project.dir, nm.equals("classes.dex") ? "smali" : "smali_" + nm.replace(".dex", "")));
                            tmp.delete();
                        }
                    }
                    z.close();
                    ui(new Runnable() { public void run() { toast("Fresh decompile ready"); show(); } });
                } catch (Throwable t) { showError(t); }
            }});
        }});
    }
}
