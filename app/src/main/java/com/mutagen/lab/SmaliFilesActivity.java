package com.mutagen.lab;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.mutagen.lab.core.Files2;
import com.mutagen.lab.core.Refactor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SmaliFilesActivity extends BaseActivity {
    private Ui.RowAdapter adapter;
    private final List<Ui.Row> all = new ArrayList<>();
    private EditText search;
    private TextView status;
    private boolean resultsMode = false;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_list);
        loadProject();
        header("Smali code", "Browse · edit · search · rename");
        search = (EditText) findViewById(R.id.search);
        status = (TextView) findViewById(R.id.status);
        ListView list = (ListView) findViewById(R.id.list);
        adapter = new Ui.RowAdapter(this);
        list.setAdapter(adapter);
        search.setHint("Filter classes (e.g. MainActivity)");
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence c, int a, int x, int d) {}
            public void onTextChanged(CharSequence c, int a, int x, int d) { if (!resultsMode) applyFilter(); }
            public void afterTextChanged(Editable e) {}
        });
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                Object[] t = (Object[]) adapter.rows.get(pos).tag;
                Intent i = new Intent(SmaliFilesActivity.this, EditorActivity.class);
                i.putExtra("p", project.name);
                i.putExtra("f", ((File) t[0]).getAbsolutePath());
                i.putExtra("line", (Integer) t[1]);
                startActivity(i);
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                final File f = (File) ((Object[]) adapter.rows.get(pos).tag)[0];
                Ui.choose(SmaliFilesActivity.this, f.getName(), new String[]{"Rename / move class", "Delete class"}, new Ui.Cb<Integer>() {
                    public void on(Integer i) { if (i == 0) renameClass(f); else deleteClass(f); }
                });
                return true;
            }
        });
        Button a = (Button) findViewById(R.id.btnA), c2 = (Button) findViewById(R.id.btnB), c3 = (Button) findViewById(R.id.btnC);
        a.setText("Search code"); c2.setText("Replace all"); c3.setText("New class"); c3.setVisibility(View.VISIBLE);
        Ui.pressable(a); Ui.pressable(c2); Ui.pressable(c3);
        a.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { grep(); } });
        c2.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { replaceAll(); } });
        c3.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { newClass(); } });
    }

    @Override protected void onResume() { super.onResume(); if (!resultsMode) load(); }

    @Override public void onBackPressed() {
        if (resultsMode) { resultsMode = false; search.setText(""); load(); } else super.onBackPressed();
    }

    private void load() {
        status.setText("Scanning smali...");
        bg(new Runnable() { public void run() {
            final List<Ui.Row> rows = new ArrayList<>();
            for (File f : project.smaliFiles()) {
                String rel = project.relativeSmali(f);
                String cls = rel.substring(0, rel.length() - 6).replace('/', '.');
                rows.add(new Ui.Row("S", cls, f.getParentFile().getParentFile() == null ? "" : f.getAbsolutePath().contains("smali_classes") ? "dex " + dexOf(f) : "classes.dex", Files2.human(f.length()), new Object[]{f, 0}));
            }
            ui(new Runnable() { public void run() { all.clear(); all.addAll(rows); applyFilter(); } });
        }});
    }

    private String dexOf(File f) {
        for (File d : project.smaliDirs()) if (f.getAbsolutePath().startsWith(d.getAbsolutePath() + "/")) return com.mutagen.lab.core.Project.dexNameFor(d);
        return "";
    }

    private void applyFilter() {
        String q = search.getText().toString().toLowerCase();
        List<Ui.Row> out = new ArrayList<>();
        for (Ui.Row r : all) if (q.isEmpty() || r.t1.toLowerCase().contains(q)) out.add(r);
        adapter.set(out);
        status.setText(out.size() + " of " + all.size() + " classes");
    }

    private void grep() {
        Ui.prompt(this, "Search in all smali", "text to find", "", new Ui.Cb<String>() {
            public void on(final String q) {
                if (q.isEmpty()) return;
                status.setText("Searching...");
                bg(new Runnable() { public void run() {
                    final List<Ui.Row> rows = new ArrayList<>();
                    String ql = q.toLowerCase();
                    outer:
                    for (File f : project.smaliFiles()) {
                        List<String> lines = Files2.readLines(f);
                        for (int i = 0; i < lines.size(); i++) {
                            if (lines.get(i).toLowerCase().contains(ql)) {
                                String rel = project.relativeSmali(f);
                                rows.add(new Ui.Row("L" + (i + 1), rel.replace(".smali", ""), lines.get(i).trim(), "", new Object[]{f, i + 1}));
                                if (rows.size() >= 400) break outer;
                            }
                        }
                    }
                    ui(new Runnable() { public void run() {
                        resultsMode = true; adapter.set(rows);
                        status.setText(rows.size() + (rows.size() >= 400 ? "+" : "") + " matches for \"" + q + "\"  ·  back to return");
                    }});
                }});
            }
        });
    }

    private void replaceAll() {
        Ui.prompt2(this, "Replace in ALL smali files", "find (plain text)", "", "replace with", "", new Ui.Cb2() {
            public void on(final String a, final String b2) {
                if (a.isEmpty()) return;
                Ui.confirm(SmaliFilesActivity.this, "Replace everywhere?", "\"" + a + "\" → \"" + b2 + "\"\nThis edits every smali file.", new Runnable() { public void run() {
                    bg(new Runnable() { public void run() {
                        final int n = Refactor.replaceInSmali(project, a, b2, false, null);
                        ui(new Runnable() { public void run() { toast("Changed " + n + " file(s)"); } });
                    }});
                }});
            }
        });
    }

    private void newClass() {
        Ui.prompt(this, "New class", "com.example.Helper", "", new Ui.Cb<String>() {
            public void on(String name) {
                name = name.trim();
                if (name.isEmpty()) return;
                File root = project.smaliDirs().get(0);
                File f = new File(root, name.replace('.', '/') + ".smali");
                if (f.exists()) { toast("Already exists"); return; }
                Files2.writeText(f, ".class public L" + name.replace('.', '/') + ";\n.super Ljava/lang/Object;\n\n"
                        + ".method public constructor <init>()V\n    .locals 0\n    invoke-direct {p0}, Ljava/lang/Object;-><init>()V\n    return-void\n.end method\n");
                toast("Created"); load();
            }
        });
    }

    private void deleteClass(final File f) {
        Ui.confirm(this, "Delete class?", f.getName(), new Runnable() { public void run() { f.delete(); toast("Deleted"); load(); } });
    }

    private void renameClass(File f) {
        final String rel = project.relativeSmali(f);
        final String dotted = rel.substring(0, rel.length() - 6).replace('/', '.');
        Ui.prompt(this, "Rename / move class", "new full name", dotted, new Ui.Cb<String>() {
            public void on(final String nn) {
                if (nn.trim().isEmpty() || nn.equals(dotted)) return;
                bg(new Runnable() { public void run() {
                    try {
                        Refactor.renameClass(project, dotted, nn.trim(), new com.mutagen.lab.core.Log() { public void i(String s) {} });
                        ui(new Runnable() { public void run() { toast("Renamed"); load(); } });
                    } catch (Throwable t) { showError(t); }
                }});
            }
        });
    }
}
