package com.mutagen.lab;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.mutagen.lab.core.Axml;
import com.mutagen.lab.core.Refactor;

public class ManifestActivity extends BaseActivity {
    private TextView body;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_text);
        loadProject();
        header("Manifest", "AndroidManifest.xml (binary, decoded view)");
        body = (TextView) findViewById(R.id.body);
        btn(R.id.btn1, "Replace text", new Runnable() { public void run() { replace(); } });
        btn(R.id.btn2, "+ Permission", new Runnable() { public void run() { addPerm(); } });
        btn(R.id.btn3, "− Permission", new Runnable() { public void run() { removePerm(); } });
        btn(R.id.btn4, "Set number", new Runnable() { public void run() { setNumber(); } });
        btn(R.id.btn5, "Copy XML", new Runnable() { public void run() {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("manifest", body.getText()));
            toast("Copied");
        }});
        btn(R.id.btn6, "Reset", new Runnable() { public void run() {
            Ui.confirm(ManifestActivity.this, "Reset manifest?", "Drops all manifest edits.", new Runnable() { public void run() {
                project.restoreEntry(Refactor.MANIFEST); show(); toast("Restored original");
            }});
        }});
        show();
    }

    private void btn(int id, String text, final Runnable r) {
        Button b = (Button) findViewById(id);
        b.setText(text);
        Ui.pressable(b);
        b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.run(); } });
    }

    private byte[] cur() { return project.entry(Refactor.MANIFEST); }

    private void show() {
        try { body.setText(Axml.dump(cur())); } catch (Throwable t) { body.setText("Cannot decode manifest: " + t); }
        body.setAlpha(0f); body.animate().alpha(1f).setDuration(300).start();
    }

    private void save(byte[] d) { project.putOverlay(Refactor.MANIFEST, d); show(); }

    private void replace() {
        Ui.prompt2(this, "Replace in manifest strings", "find", "", "replace with", "", new Ui.Cb2() {
            public void on(String a, String b2) {
                if (a.isEmpty()) return;
                byte[] r = Axml.replaceSubstring(cur(), a, b2);
                save(r);
                toast("Done");
            }
        });
    }

    private void addPerm() {
        Ui.prompt(this, "Add uses-permission", "android.permission.CAMERA", "android.permission.", new Ui.Cb<String>() {
            public void on(String v) {
                if (v.trim().length() < 5) return;
                byte[] r = Axml.addPermission(cur(), v.trim());
                save(r);
            }
        });
    }

    private void removePerm() {
        final String[] perms = Axml.info(cur()).permissions.toArray(new String[0]);
        if (perms.length == 0) { toast("No permissions"); return; }
        Ui.choose(this, "Remove permission", perms, new Ui.Cb<Integer>() {
            public void on(Integer i) { save(Axml.removePermission(cur(), perms[i])); }
        });
    }

    private void setNumber() {
        final String[][] opts = {{"versionCode", "manifest", "versionCode"}, {"minSdkVersion", "uses-sdk", "minSdkVersion"}, {"targetSdkVersion", "uses-sdk", "targetSdkVersion"}};
        Ui.choose(this, "Set number", new String[]{"versionCode", "minSdkVersion", "targetSdkVersion"}, new Ui.Cb<Integer>() {
            public void on(Integer i) {
                final String[] o = opts[i];
                Ui.prompt(ManifestActivity.this, o[0], "number", "", new Ui.Cb<String>() {
                    public void on(String v) {
                        try { save(Axml.setIntAttr(cur(), o[1], o[2], Integer.parseInt(v.trim()))); }
                        catch (NumberFormatException e) { toast("Not a number"); }
                    }
                });
            }
        });
    }
}
