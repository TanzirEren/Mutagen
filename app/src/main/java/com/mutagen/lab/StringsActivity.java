package com.mutagen.lab;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.mutagen.lab.core.Axml;
import com.mutagen.lab.core.Files2;
import com.mutagen.lab.core.Refactor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringsActivity extends BaseActivity {
    private static final Pattern CONST = Pattern.compile("^(\\s*const-string(?:/jumbo)?\\s+[vp]\\d+,\\s*\")(.*)(\"\\s*)$");
    private Ui.RowAdapter adapter;
    private final List<Ui.Row> all = new ArrayList<>();
    private EditText search;
    private TextView status;
    private int mode = 0; // 0 resources.arsc, 1 smali const-string

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_list);
        loadProject();
        header("Strings", "Edit app text in resources and code");
        search = (EditText) findViewById(R.id.search);
        status = (TextView) findViewById(R.id.status);
        ListView list = (ListView) findViewById(R.id.list);
        adapter = new Ui.RowAdapter(this);
        list.setAdapter(adapter);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence c, int a, int x, int d) {}
            public void onTextChanged(CharSequence c, int a, int x, int d) { filter(); }
            public void afterTextChanged(Editable e) {}
        });
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) { edit(adapter.rows.get(pos)); }
        });
        Button a = (Button) findViewById(R.id.btnA), c2 = (Button) findViewById(R.id.btnB), c3 = (Button) findViewById(R.id.btnC);
        a.setText("Resources"); c2.setText("Code strings"); c3.setText("Replace all"); c3.setVisibility(View.VISIBLE);
        Ui.pressable(a); Ui.pressable(c2); Ui.pressable(c3);
        a.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { mode = 0; load(); } });
        c2.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { mode = 1; load(); } });
        c3.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { replaceAll(); } });
        load();
    }

    private void load() {
        status.setText("Loading...");
        search.setHint(mode == 0 ? "Search resources.arsc strings" : "Search const-string values in smali");
        bg(new Runnable() { public void run() {
            final List<Ui.Row> rows = new ArrayList<>();
            try {
                if (mode == 0) {
                    byte[] arsc = project.entry("resources.arsc");
                    if (arsc == null) { ui(new Runnable() { public void run() { status.setText("This APK has no resources.arsc"); adapter.set(new ArrayList<Ui.Row>()); } }); return; }
                    List<String> s = Axml.poolStrings(arsc);
                    for (int i = 0; i < s.size(); i++) {
                        String v = s.get(i);
                        if (v.length() == 0 || v.startsWith("res/")) continue;
                        rows.add(new Ui.Row("#" + i, v.replace('\n', ' '), "", "", Integer.valueOf(i)));
                    }
                } else {
                    outer:
                    for (File f : project.smaliFiles()) {
                        List<String> lines = Files2.readLines(f);
                        for (int i = 0; i < lines.size(); i++) {
                            Matcher m = CONST.matcher(lines.get(i));
                            if (m.matches() && m.group(2).length() > 0) {
                                rows.add(new Ui.Row("STR", m.group(2), project.relativeSmali(f).replace(".smali", "") + ":" + (i + 1), "", new Object[]{f, i}));
                                if (rows.size() >= 6000) break outer;
                            }
                        }
                    }
                }
            } catch (Throwable t) { showError(t); }
            ui(new Runnable() { public void run() { all.clear(); all.addAll(rows); filter(); } });
        }});
    }

    private void filter() {
        String q = search.getText().toString().toLowerCase();
        List<Ui.Row> out = new ArrayList<>();
        for (Ui.Row r : all) {
            if (q.isEmpty() || r.t1.toLowerCase().contains(q) || r.t2.toLowerCase().contains(q)) { out.add(r); if (out.size() >= 1500) break; }
        }
        adapter.set(out);
        status.setText(out.size() + " shown of " + all.size() + (mode == 1 ? "  ·  raw smali text, keep \\n \\\" escapes" : ""));
    }

    private void edit(final Ui.Row r) {
        if (mode == 0) {
            final int idx = (Integer) r.tag;
            Ui.prompt(this, "Edit resource string #" + idx, "value", r.t1, new Ui.Cb<String>() {
                public void on(String v) {
                    byte[] arsc = project.entry("resources.arsc");
                    project.putOverlay("resources.arsc", Axml.setString(arsc, idx, v));
                    load(); toast("Saved");
                }
            });
        } else {
            final Object[] t = (Object[]) r.tag;
            Ui.prompt(this, "Edit code string", "value", r.t1, new Ui.Cb<String>() {
                public void on(String v) {
                    File f = (File) t[0]; int i = (Integer) t[1];
                    List<String> lines = Files2.readLines(f);
                    Matcher m = CONST.matcher(lines.get(i));
                    if (!m.matches()) { toast("Line changed, reload"); return; }
                    lines.set(i, m.group(1) + v + m.group(3));
                    StringBuilder sb = new StringBuilder();
                    for (int k = 0; k < lines.size(); k++) { sb.append(lines.get(k)); if (k < lines.size() - 1) sb.append('\n'); }
                    Files2.writeText(f, sb.toString());
                    load(); toast("Saved");
                }
            });
        }
    }

    private void replaceAll() {
        Ui.prompt2(this, "Replace in strings (resources + code)", "find", "", "replace with", "", new Ui.Cb2() {
            public void on(final String a, final String b2) {
                if (a.isEmpty()) return;
                bg(new Runnable() { public void run() {
                    byte[] arsc = project.entry("resources.arsc");
                    if (arsc != null) project.putOverlay("resources.arsc", Axml.replaceSubstring(arsc, a, b2));
                    int files = 0;
                    for (File f : project.smaliFiles()) {
                        List<String> lines = Files2.readLines(f);
                        boolean ch = false;
                        for (int i = 0; i < lines.size(); i++) {
                            Matcher m = CONST.matcher(lines.get(i));
                            if (m.matches() && m.group(2).contains(a)) { lines.set(i, m.group(1) + m.group(2).replace(a, b2) + m.group(3)); ch = true; }
                        }
                        if (ch) {
                            StringBuilder sb = new StringBuilder();
                            for (int k = 0; k < lines.size(); k++) { sb.append(lines.get(k)); if (k < lines.size() - 1) sb.append('\n'); }
                            Files2.writeText(f, sb.toString()); files++;
                        }
                    }
                    final int n = files;
                    ui(new Runnable() { public void run() { toast("Resources + " + n + " smali file(s) updated"); load(); } });
                }});
            }
        });
    }
}
