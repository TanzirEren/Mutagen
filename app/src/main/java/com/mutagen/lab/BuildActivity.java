package com.mutagen.lab;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import com.mutagen.lab.core.ApkBuilder;
import com.mutagen.lab.core.Files2;
import com.mutagen.lab.core.Log;

import java.io.File;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class BuildActivity extends BaseActivity {
    private static final int REQ_EXPORT = 51;
    private TextView body;
    private ScrollView scroll;
    private View progress;
    private File built;
    private String builtPkg;
    private Button bBuild, bInstall, bExport, bUninstall, bOpen;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_text);
        loadProject();
        header("Build & install", "Rebuild · sign · test");
        body = (TextView) findViewById(R.id.body);
        scroll = (ScrollView) findViewById(R.id.scroll);
        progress = findViewById(R.id.progress);
        bBuild = btn(R.id.btn1, "Build & sign", new Runnable() { public void run() { build(); } });
        bInstall = btn(R.id.btn2, "Install", new Runnable() { public void run() { install(); } });
        bExport = btn(R.id.btn3, "Export", new Runnable() { public void run() { export(); } });
        bUninstall = btn(R.id.btn4, "Uninstall old", new Runnable() { public void run() { uninstall(); } });
        bOpen = btn(R.id.btn5, "Open app", new Runnable() { public void run() { openApp(); } });
        btn(R.id.btn6, "Clear log", new Runnable() { public void run() { body.setText(""); } });
        body.setText("Ready.\n\nBuild re-assembles smali, applies your manifest / string / asset / icon edits, signs the APK (v1+v2+v3) and lets you install it.\n\nTip: the rebuilt APK is signed with Mutagen's key, so if the original is installed with a different signature, tap \"Uninstall old\" first (or change the package id so both can live side by side).\n");
        File prev = new File(project.outDir(), project.meta().getProperty("package", project.name) + "-mutagen.apk");
        if (prev.exists()) { built = prev; line("Previous build found: " + prev.getName() + " (" + Files2.human(prev.length()) + ")"); }
    }

    private Button btn(int id, String t, final Runnable r) {
        Button b = (Button) findViewById(id); b.setText(t); Ui.pressable(b);
        b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.run(); } });
        return b;
    }

    private void line(final String s) {
        ui(new Runnable() { public void run() {
            body.append(new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date()) + "  " + s + "\n");
            scroll.post(new Runnable() { public void run() { scroll.fullScroll(View.FOCUS_DOWN); } });
        }});
    }

    private void build() {
        progress.setVisibility(View.VISIBLE);
        bBuild.setEnabled(false);
        line("— build started —");
        bg(new Runnable() { public void run() {
            try {
                File out = ApkBuilder.build(BuildActivity.this, project, new Log() { public void i(String s) { line(s); } });
                built = out;
                PackageInfo pi = getPackageManager().getPackageArchiveInfo(out.getAbsolutePath(), 0);
                builtPkg = pi != null ? pi.packageName : null;
                line("✔ BUILD OK  " + (builtPkg != null ? builtPkg : ""));
            } catch (final Throwable t) {
                line("✘ BUILD FAILED");
                line(String.valueOf(t.getMessage() != null ? t.getMessage() : t));
            }
            ui(new Runnable() { public void run() { progress.setVisibility(View.INVISIBLE); bBuild.setEnabled(true); } });
        }});
    }

    private void install() {
        if (built == null || !built.exists()) { toast("Build first"); return; }
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            toast("Allow installs from Mutagen, then tap Install again");
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
            return;
        }
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(ShareProvider.uriFor(project, built), "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        line("Installer opened");
    }

    private String targetPkg() {
        if (builtPkg != null) return builtPkg;
        if (built != null) {
            PackageInfo pi = getPackageManager().getPackageArchiveInfo(built.getAbsolutePath(), 0);
            if (pi != null) return pi.packageName;
        }
        return project.meta().getProperty("package", null);
    }

    private void uninstall() {
        String p = project.meta().getProperty("package", null);
        if (p == null) { toast("Unknown package"); return; }
        Intent i = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + p));
        startActivity(i);
    }

    private void openApp() {
        String p = targetPkg();
        Intent i = p == null ? null : getPackageManager().getLaunchIntentForPackage(p);
        if (i == null) { toast("App not installed yet"); return; }
        startActivity(i);
    }

    private void export() {
        if (built == null || !built.exists()) { toast("Build first"); return; }
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/vnd.android.package-archive");
        i.putExtra(Intent.EXTRA_TITLE, built.getName());
        startActivityForResult(i, REQ_EXPORT);
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_EXPORT && res == Activity.RESULT_OK && data != null && data.getData() != null) {
            try {
                OutputStream o = getContentResolver().openOutputStream(data.getData());
                o.write(Files2.readBytes(built)); o.close();
                line("Exported " + built.getName());
            } catch (Exception e) { line("Export failed: " + e.getMessage()); }
        }
    }
}
