package com.mutagen.lab;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.mutagen.lab.core.Axml;
import com.mutagen.lab.core.Files2;
import com.mutagen.lab.core.Refactor;
import com.mutagen.lab.core.SmaliInjector;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class InjectActivity extends BaseActivity {
    private static final String[] TYPES = {"Dialog", "Dialog + link button", "Toast", "Log message", "Open URL", "Raw smali code"};
    private Spinner spType, spTarget;
    private EditText etMethod, etTitle, etMsg, etOk, etUrl, etRaw, etLocals;
    private CheckBox cbCancel;
    private View grpDialog, grpMsg, grpOk, grpUrl, grpRaw;
    private TextView lblMsg, lblUrl, tvLog;
    private final List<String> targets = new ArrayList<>();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_inject);
        loadProject();
        header("Injector", "Test code straight into your app");
        spType = (Spinner) findViewById(R.id.spType);
        spTarget = (Spinner) findViewById(R.id.spTarget);
        etMethod = (EditText) findViewById(R.id.etMethod);
        etTitle = (EditText) findViewById(R.id.etTitle);
        etMsg = (EditText) findViewById(R.id.etMsg);
        etOk = (EditText) findViewById(R.id.etOk);
        etUrl = (EditText) findViewById(R.id.etUrl);
        etRaw = (EditText) findViewById(R.id.etRaw);
        etLocals = (EditText) findViewById(R.id.etLocals);
        cbCancel = (CheckBox) findViewById(R.id.cbCancel);
        grpDialog = findViewById(R.id.grpDialog); grpMsg = findViewById(R.id.grpMsg); grpOk = findViewById(R.id.grpOk);
        grpUrl = findViewById(R.id.grpUrl); grpRaw = findViewById(R.id.grpRaw);
        lblMsg = (TextView) findViewById(R.id.lblMsg); lblUrl = (TextView) findViewById(R.id.lblUrl);
        tvLog = (TextView) findViewById(R.id.tvLog);

        spType.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, TYPES));
        Axml.Info info = Axml.info(project.entry(Refactor.MANIFEST));
        targets.addAll(info.activities);
        if (targets.isEmpty()) targets.add("(no activities found)");
        spTarget.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, targets));
        if (info.launcher != null && targets.contains(info.launcher)) spTarget.setSelection(targets.indexOf(info.launcher));

        spType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { layoutFor(pos); }
            public void onNothingSelected(AdapterView<?> p) {}
        });
        layoutFor(0);

        Button inject = (Button) findViewById(R.id.btnInject), undo = (Button) findViewById(R.id.btnUndo);
        Ui.pressable(inject); Ui.pressable(undo);
        inject.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { inject(); } });
        undo.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { undo(); } });
        showRecords();
    }

    private void layoutFor(int t) {
        grpDialog.setVisibility(t <= 1 ? View.VISIBLE : View.GONE);
        grpOk.setVisibility(t <= 1 ? View.VISIBLE : View.GONE);
        grpMsg.setVisibility(t <= 3 ? View.VISIBLE : View.GONE);
        grpUrl.setVisibility(t == 1 || t == 4 ? View.VISIBLE : View.GONE);
        grpRaw.setVisibility(t == 5 ? View.VISIBLE : View.GONE);
        lblMsg.setText(t == 2 ? "TOAST TEXT" : t == 3 ? "LOG MESSAGE" : "MESSAGE");
        lblUrl.setText(t == 1 ? "LINK URL (neutral button)" : "URL TO OPEN");
    }

    private void showRecords() {
        List<String> r = SmaliInjector.records(project);
        StringBuilder sb = new StringBuilder();
        for (String s : r) { String[] p = s.split("\\|"); sb.append("• ").append(p[0]).append("  ").append(p.length > 2 ? p[2] : "").append("  →  ").append(p.length > 1 ? p[1] : "").append('\n'); }
        tvLog.setText(r.isEmpty() ? "No injections yet." : "Active injections:\n" + sb);
    }

    private void inject() {
        final int type = spType.getSelectedItemPosition();
        final String cls = String.valueOf(spTarget.getSelectedItem());
        final String method = etMethod.getText().toString().trim();
        bg(new Runnable() { public void run() {
            try {
                File target = project.findSmaliForClass(cls);
                if (target == null) throw new Exception("Smali for " + cls + " not found. Pick another target.");
                String id = SmaliInjector.nextClassName(project);
                File base = new File(project.smaliDirs().get(0), SmaliInjector.PKG);
                List<String> lines = new ArrayList<>();
                lines.add(SmaliInjector.beginMark(id));
                int extra = 0;
                String msg = etMsg.getText().toString();
                String desc;
                if (type == 5) {
                    for (String l : etRaw.getText().toString().split("\n")) lines.add("    " + l.trim());
                    try { extra = Integer.parseInt(etLocals.getText().toString().trim()); } catch (Exception ignored) {}
                    desc = "raw";
                } else {
                    String call = "    invoke-static {p0}, L" + SmaliInjector.PKG + "/" + id + ";->run(Landroid/content/Context;)V";
                    lines.add(call);
                    String body;
                    if (type == 0 || type == 1) {
                        String url = etUrl.getText().toString().trim();
                        boolean link = type == 1 && !url.isEmpty();
                        body = SmaliInjector.dialog(id, etTitle.getText().toString(), msg, etOk.getText().toString(), url, cbCancel.isChecked(), link);
                        if (link) Files2.writeText(new File(base, id + "$L.smali"), SmaliInjector.linkListener(id));
                        desc = link ? "dialog+link" : "dialog";
                    } else if (type == 2) { body = SmaliInjector.toast(id, msg); desc = "toast"; }
                    else if (type == 3) { body = SmaliInjector.log(id, "Mutagen", msg); desc = "log"; }
                    else {
                        String url = etUrl.getText().toString().trim();
                        if (url.isEmpty()) throw new Exception("Enter a URL");
                        body = SmaliInjector.openUrl(id, url.contains("://") ? url : "https://" + url); desc = "url";
                    }
                    Files2.writeText(new File(base, id + ".smali"), body);
                }
                lines.add(SmaliInjector.endMark(id));
                String src = Files2.readText(target);
                String out = SmaliInjector.insert(src, method, lines, extra, true);
                Files2.writeText(target, out);
                java.io.FileWriter fw = new java.io.FileWriter(SmaliInjector.injectionsFile(project), true);
                fw.write(id + "|" + project.relativeSmali(target) + "|" + desc + "|" + method + "\n");
                fw.close();
                final String done = id;
                ui(new Runnable() { public void run() { toast("Injected " + done + " — build to test"); showRecords(); } });
            } catch (Throwable t) { showError(t); }
        }});
    }

    private void undo() {
        bg(new Runnable() { public void run() {
            try {
                List<String> r = SmaliInjector.records(project);
                if (r.isEmpty()) { ui(new Runnable() { public void run() { toast("Nothing to undo"); } }); return; }
                String last = r.get(r.size() - 1);
                String[] p = last.split("\\|");
                String id = p[0];
                for (File d : project.smaliDirs()) {
                    File t = new File(d, p[1]);
                    if (t.exists()) Files2.writeText(t, SmaliInjector.removeBlock(Files2.readText(t), id));
                    new File(d, SmaliInjector.PKG + "/" + id + ".smali").delete();
                    new File(d, SmaliInjector.PKG + "/" + id + "$L.smali").delete();
                }
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < r.size() - 1; i++) sb.append(r.get(i)).append('\n');
                Files2.writeText(SmaliInjector.injectionsFile(project), sb.toString());
                ui(new Runnable() { public void run() { toast("Undone"); showRecords(); } });
            } catch (Throwable t) { showError(t); }
        }});
    }
}
