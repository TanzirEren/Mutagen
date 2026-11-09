package com.mutagen.lab;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.mutagen.lab.core.Axml;
import com.mutagen.lab.core.Files2;
import com.mutagen.lab.core.Log;
import com.mutagen.lab.core.Refactor;

import java.io.ByteArrayOutputStream;
import java.util.Properties;
import java.util.zip.ZipEntry;

public class IdentityActivity extends BaseActivity {
    private static final int REQ_LOGO = 31;
    private EditText etName, etPkg, etVName, etVCode, etOldCls, etNewCls;
    private CheckBox cbFull, cbAuth;
    private ImageView imgLogo;
    private TextView tvLogoInfo;
    private Bitmap newLogo;
    private String oldLabel, oldPkg, oldVName;
    private int oldVCode;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_identity);
        loadProject();
        header("Identity", "App name · package · logo · class");
        etName = (EditText) findViewById(R.id.etName); etPkg = (EditText) findViewById(R.id.etPkg);
        etVName = (EditText) findViewById(R.id.etVName); etVCode = (EditText) findViewById(R.id.etVCode);
        etOldCls = (EditText) findViewById(R.id.etOldCls); etNewCls = (EditText) findViewById(R.id.etNewCls);
        cbFull = (CheckBox) findViewById(R.id.cbFull); cbAuth = (CheckBox) findViewById(R.id.cbAuth);
        imgLogo = (ImageView) findViewById(R.id.imgLogo); tvLogoInfo = (TextView) findViewById(R.id.tvLogoInfo);

        Properties meta = project.meta();
        Axml.Info info = Axml.info(project.entry(Refactor.MANIFEST));
        oldLabel = meta.getProperty("label", "");
        oldPkg = info.pkg; oldVName = info.versionName; oldVCode = info.versionCode;
        etName.setText(oldLabel); etPkg.setText(oldPkg); etVName.setText(oldVName); etVCode.setText(String.valueOf(oldVCode));

        PackageManager pm = getPackageManager();
        PackageInfo pi = pm.getPackageArchiveInfo(project.apk().getAbsolutePath(), 0);
        if (pi != null) {
            pi.applicationInfo.sourceDir = project.apk().getAbsolutePath();
            pi.applicationInfo.publicSourceDir = project.apk().getAbsolutePath();
            imgLogo.setImageDrawable(pi.applicationInfo.loadIcon(pm));
        }

        Button logo = (Button) findViewById(R.id.btnLogo), apply = (Button) findViewById(R.id.btnApply), ren = (Button) findViewById(R.id.btnRenameCls);
        Ui.pressable(logo); Ui.pressable(apply); Ui.pressable(ren);
        logo.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("image/*");
            startActivityForResult(i, REQ_LOGO);
        }});
        apply.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { apply(); } });
        ren.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { renameClass(); } });
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_LOGO && res == Activity.RESULT_OK && data != null && data.getData() != null) {
            try {
                Bitmap bm = BitmapFactory.decodeStream(getContentResolver().openInputStream(data.getData()));
                if (bm == null) { toast("Cannot read image"); return; }
                newLogo = bm;
                imgLogo.setImageBitmap(bm);
                tvLogoInfo.setText("New icon " + bm.getWidth() + "×" + bm.getHeight() + " — applied on 'Apply'");
                Ui.popIn(imgLogo, 0);
            } catch (Exception e) { toast("Failed: " + e.getMessage()); }
        }
    }

    private final Log nolog = new Log() { public void i(String s) {} };

    private void apply() {
        final String name = etName.getText().toString().trim();
        final String pkg = etPkg.getText().toString().trim();
        final String vName = etVName.getText().toString().trim();
        final String vCodeS = etVCode.getText().toString().trim();
        final boolean full = cbFull.isChecked(), auth = cbAuth.isChecked();
        bg(new Runnable() { public void run() {
            try {
                StringBuilder res = new StringBuilder();
                if (!name.isEmpty() && !name.equals(oldLabel)) {
                    int n = Refactor.renameApp(project, oldLabel, name, nolog);
                    res.append("App name updated (").append(n).append(" files)\n");
                    Properties m = project.meta(); m.setProperty("label", name); project.saveMeta(m);
                    oldLabel = name;
                }
                if (!pkg.isEmpty() && !pkg.equals(oldPkg)) {
                    if (!pkg.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")) throw new Exception("Invalid package id: " + pkg);
                    if (full) Refactor.renamePackageFull(project, oldPkg, pkg, nolog);
                    else Refactor.changeApplicationId(project, pkg, auth, nolog);
                    Properties m = project.meta(); m.setProperty("package", pkg); project.saveMeta(m);
                    res.append("Package ").append(full ? "refactored" : "id changed").append(" → ").append(pkg).append('\n');
                    oldPkg = pkg;
                }
                if (!vName.equals(oldVName) && !vName.isEmpty() && !oldVName.isEmpty()) {
                    byte[] man = project.entry(Refactor.MANIFEST);
                    project.putOverlay(Refactor.MANIFEST, Axml.replaceExact(man, oldVName, vName));
                    res.append("Version name updated\n"); oldVName = vName;
                }
                if (!vCodeS.isEmpty()) {
                    int vc = Integer.parseInt(vCodeS);
                    if (vc != oldVCode) {
                        project.putOverlay(Refactor.MANIFEST, Axml.setIntAttr(project.entry(Refactor.MANIFEST), "manifest", "versionCode", vc));
                        res.append("Version code updated\n"); oldVCode = vc;
                    }
                }
                if (newLogo != null) {
                    int n = replaceIcons(newLogo);
                    res.append(n > 0 ? "Icon replaced in " + n + " image(s)\n" : "No launcher icon images matched. Use Assets & files to replace the icon manually.\n");
                }
                final String out = res.length() == 0 ? "Nothing to change." : res.toString().trim();
                ui(new Runnable() { public void run() { Ui.message(IdentityActivity.this, "Applied", out); } });
            } catch (Throwable t) { showError(t); }
        }});
    }

    private int replaceIcons(Bitmap src) throws Exception {
        int n = 0;
        for (ZipEntry e : project.originalEntries()) {
            String nm = e.getName();
            if (!nm.startsWith("res/") || !nm.matches("(?i).*(ic_launcher|/icon|app_icon|logo).*\\.(png|webp)$")) continue;
            if (project.deleted().contains(nm)) continue;
            byte[] orig = project.originalEntry(nm);
            if (orig == null) continue;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(orig, 0, orig.length, o);
            if (o.outWidth <= 0 || o.outHeight <= 0) continue;
            Bitmap scaled = Bitmap.createScaledBitmap(src, o.outWidth, o.outHeight, true);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            scaled.compress(Bitmap.CompressFormat.PNG, 100, bo);
            project.putOverlay(nm, bo.toByteArray());
            n++;
        }
        return n;
    }

    private void renameClass() {
        final String o = etOldCls.getText().toString().trim(), n = etNewCls.getText().toString().trim();
        if (o.isEmpty() || n.isEmpty()) { toast("Fill both class names"); return; }
        bg(new Runnable() { public void run() {
            try {
                Refactor.renameClass(project, o, n, nolog);
                ui(new Runnable() { public void run() { Ui.message(IdentityActivity.this, "Class renamed", o + "\n→ " + n); } });
            } catch (Throwable t) { showError(t); }
        }});
    }
}
