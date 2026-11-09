package com.mutagen.lab;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.mutagen.lab.core.Files2;
import com.mutagen.lab.core.Importer;
import com.mutagen.lab.core.Log;
import com.mutagen.lab.core.Project;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends BaseActivity {
    private static final int REQ_APK = 11;
    private LinearLayout projects;
    private TextView empty;
    private AlertDialog progress;
    private TextView progressText;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        projects = (LinearLayout) findViewById(R.id.projects);
        empty = (TextView) findViewById(R.id.empty);
        View imp = findViewById(R.id.btnImport);
        View ins = findViewById(R.id.btnInstalled);
        Ui.pressable(imp); Ui.pressable(ins);
        imp.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/vnd.android.package-archive", "application/octet-stream", "application/zip"});
            startActivityForResult(i, REQ_APK);
        }});
        ins.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pickInstalled(); } });
        Ui.stagger((ViewGroup) ((ViewGroup) findViewById(R.id.top).getParent()));
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    private void refresh() {
        projects.removeAllViews();
        List<Project> list = Project.all(this);
        empty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        int i = 0;
        for (final Project p : list) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_row, projects, false);
            ((LinearLayout.LayoutParams) row.getLayoutParams()).setMargins(0, Ui.dp(this, 5), 0, Ui.dp(this, 5));
            ((TextView) row.findViewById(R.id.glyph)).setText("APK");
            String label = p.meta().getProperty("label", p.name);
            ((TextView) row.findViewById(R.id.t1)).setText(label);
            ((TextView) row.findViewById(R.id.t2)).setText(p.meta().getProperty("package", p.name));
            ((TextView) row.findViewById(R.id.t3)).setText(Files2.human(p.apk().length()));
            row.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                Intent in = new Intent(MainActivity.this, ProjectActivity.class);
                in.putExtra("p", p.name);
                startActivity(in);
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            }});
            row.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) {
                Ui.confirm(MainActivity.this, "Delete project?", "Removes the decompiled workspace for " + p.name + ".", new Runnable() {
                    public void run() { p.deleteProject(); refresh(); }
                });
                return true;
            }});
            Ui.pressable(row);
            projects.addView(row);
            Ui.popIn(row, 120 + i * 70); i++;
        }
    }

    // ------------------------------------------------------------ import flow
    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_APK && res == RESULT_OK && data != null && data.getData() != null) {
            final Uri uri = data.getData();
            startImport(new Runnable() { public void run() { } }, uri, null);
        }
    }

    private void showProgress() {
        progressText = new TextView(this);
        progressText.setTextColor(0xFFA79FCB);
        progressText.setTextSize(12);
        progressText.setTypeface(android.graphics.Typeface.MONOSPACE);
        progressText.setPadding(Ui.dp(this, 22), Ui.dp(this, 12), Ui.dp(this, 22), Ui.dp(this, 6));
        progressText.setText("Starting...");
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        android.widget.ProgressBar pb = new android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pb.setIndeterminate(true);
        pb.setPadding(Ui.dp(this, 22), Ui.dp(this, 10), Ui.dp(this, 22), 0);
        box.addView(pb);
        box.addView(progressText);
        progress = new AlertDialog.Builder(this).setTitle("Decompiling").setView(box).setCancelable(false).create();
        progress.show();
    }

    private void startImport(Runnable unused, final Uri uri, final File fileSrc) {
        showProgress();
        bg(new Runnable() { public void run() {
            try {
                File tmp = new File(getCacheDir(), "import.apk");
                if (fileSrc != null) Files2.copy(new java.io.FileInputStream(fileSrc), tmp);
                else Files2.copy(getContentResolver().openInputStream(uri), tmp);
                Importer.importApk(MainActivity.this, tmp, new Log() {
                    public void i(final String s) { ui(new Runnable() { public void run() { progressText.setText(s); } }); }
                });
                tmp.delete();
                ui(new Runnable() { public void run() { progress.dismiss(); refresh(); toast("Project ready"); } });
            } catch (final Throwable t) {
                ui(new Runnable() { public void run() { progress.dismiss(); } });
                showError(t);
            }
        }});
    }

    // ------------------------------------------------------------ installed apps
    private static class AppEntry { String label, pkg, path; Drawable icon; long size; boolean system; }

    private void pickInstalled() {
        showProgress();
        progress.setTitle("Loading apps");
        bg(new Runnable() { public void run() {
            PackageManager pm = getPackageManager();
            final List<AppEntry> apps = new ArrayList<>();
            for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
                AppEntry e = new AppEntry();
                e.pkg = ai.packageName; e.path = ai.sourceDir;
                e.label = String.valueOf(ai.loadLabel(pm));
                e.icon = ai.loadIcon(pm);
                e.system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                e.size = new File(ai.sourceDir).length();
                apps.add(e);
            }
            Collections.sort(apps, new Comparator<AppEntry>() {
                public int compare(AppEntry a, AppEntry b) {
                    if (a.system != b.system) return a.system ? 1 : -1;
                    return a.label.compareToIgnoreCase(b.label);
                }
            });
            ui(new Runnable() { public void run() { progress.dismiss(); showAppDialog(apps); } });
        }});
    }

    private void showAppDialog(final List<AppEntry> apps) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        final EditText q = new EditText(this);
        q.setHint("Search apps..."); q.setSingleLine(true);
        q.setBackgroundResource(R.drawable.bg_input);
        q.setTextColor(0xFFF4F1FF); q.setHintTextColor(0xFF6F678F);
        q.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
        box.addView(q, lp);
        final ListView lv = new ListView(this);
        lv.setDivider(null);
        box.addView(lv, new LinearLayout.LayoutParams(-1, Ui.dp(this, 380)));
        final Ui.RowAdapter ad = new Ui.RowAdapter(this);
        lv.setAdapter(ad);
        final AlertDialog dlg = new AlertDialog.Builder(this).setTitle("Installed apps").setView(box).setNegativeButton("Close", null).create();
        final Runnable fill = new Runnable() { public void run() {
            String s = q.getText().toString().toLowerCase();
            List<Ui.Row> rows = new ArrayList<>();
            for (AppEntry e : apps) {
                if (!s.isEmpty() && !e.label.toLowerCase().contains(s) && !e.pkg.toLowerCase().contains(s)) continue;
                Ui.Row r = new Ui.Row("", e.label, e.pkg, Files2.human(e.size), e);
                r.icon = e.icon;
                rows.add(r);
            }
            ad.set(rows);
        }};
        fill.run();
        q.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence c, int a, int b, int d) {}
            public void onTextChanged(CharSequence c, int a, int b, int d) { fill.run(); }
            public void afterTextChanged(Editable e) {}
        });
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                AppEntry e = (AppEntry) ad.rows.get(pos).tag;
                dlg.dismiss();
                startImport(null, null, new File(e.path));
            }
        });
        dlg.show();
    }
}
