package com.mutagen.lab;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.mutagen.lab.core.Axml;
import com.mutagen.lab.core.DexInfo;
import com.mutagen.lab.core.Files2;

import java.util.List;
import java.util.Properties;

public class ProjectActivity extends BaseActivity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_project);
        loadProject();
        Properties meta = project.meta();
        header(meta.getProperty("label", project.name), "Project workspace");

        ((TextView) findViewById(R.id.appLabel)).setText(meta.getProperty("label", project.name));
        ((TextView) findViewById(R.id.appPkg)).setText(meta.getProperty("package", project.name));
        PackageManager pm = getPackageManager();
        PackageInfo pi = pm.getPackageArchiveInfo(project.apk().getAbsolutePath(), 0);
        if (pi != null) {
            pi.applicationInfo.sourceDir = project.apk().getAbsolutePath();
            pi.applicationInfo.publicSourceDir = project.apk().getAbsolutePath();
            ((ImageView) findViewById(R.id.appIcon)).setImageDrawable(pi.applicationInfo.loadIcon(pm));
        }
        List<DexInfo> dex = DexInfo.of(project);
        int classes = 0, methods = 0;
        for (DexInfo d : dex) { classes += d.classes; methods += d.methods; }
        String ver = meta.getProperty("versionName", "");
        ((TextView) findViewById(R.id.appMeta)).setText((ver.isEmpty() ? "" : "v" + ver + " · ") + Files2.human(project.apk().length())
                + " · " + dex.size() + " dex · " + classes + " classes · " + methods + " methods");

        GridLayout grid = (GridLayout) findViewById(R.id.grid);
        add(grid, "XML", "Manifest", "View & edit manifest", ManifestActivity.class, 0);
        add(grid, "SML", "Smali code", "Browse & edit classes", SmaliFilesActivity.class, 1);
        add(grid, "INJ", "Injector", "Dialog, toast, raw code", InjectActivity.class, 2);
        add(grid, "STR", "Strings", "Resources & smali text", StringsActivity.class, 3);
        add(grid, "ID", "Identity", "Name, package, logo, class", IdentityActivity.class, 4);
        add(grid, "AST", "Assets & files", "Replace, add, delete", AssetsActivity.class, 5);
        add(grid, "DEX", "Dex tools", "Info, add & delete class", DexActivity.class, 6);
        add(grid, "APK", "Build & install", "Rebuild, sign, run", BuildActivity.class, 7);
    }

    private void add(GridLayout grid, String glyph, String title, String sub, final Class<?> target, int i) {
        View v = LayoutInflater.from(this).inflate(R.layout.item_tool, grid, false);
        ((TextView) v.findViewById(R.id.glyph)).setText(glyph);
        ((TextView) v.findViewById(R.id.t1)).setText(title);
        ((TextView) v.findViewById(R.id.t2)).setText(sub);
        v.setOnClickListener(new View.OnClickListener() { public void onClick(View x) { open(target); } });
        Ui.pressable(v);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
        lp.width = 0;
        lp.setMargins(Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6));
        grid.addView(v, lp);
        Ui.popIn(v, 150 + i * 70);
    }
}
