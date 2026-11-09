package com.mutagen.lab;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import com.mutagen.lab.core.Axml;
import com.mutagen.lab.core.Files2;
import com.mutagen.lab.core.Refactor;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;

public class AssetsActivity extends BaseActivity {
    private static final int REQ_ADD = 41, REQ_REPLACE = 42, REQ_EXPORT = 43;
    private static final String[] FILTERS = {"assets/", "res/", "lib/", ""};
    private static final String[] FILTER_NAMES = {"assets", "res", "lib", "all"};
    private int filter = 0;
    private Ui.RowAdapter adapter;
    private EditText search;
    private TextView status;
    private String pendingEntry;
    private final List<Ui.Row> all = new ArrayList<>();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_list);
        loadProject();
        header("Assets & files", "Everything inside the APK");
        search = (EditText) findViewById(R.id.search);
        status = (TextView) findViewById(R.id.status);
        ListView list = (ListView) findViewById(R.id.list);
        adapter = new Ui.RowAdapter(this);
        list.setAdapter(adapter);
        search.setHint("Search files");
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence c, int a, int x, int d) {}
            public void onTextChanged(CharSequence c, int a, int x, int d) { apply(); }
            public void afterTextChanged(Editable e) {}
        });
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) { actions((String) adapter.rows.get(pos).tag); }
        });
        Button a = (Button) findViewById(R.id.btnA), c2 = (Button) findViewById(R.id.btnB);
        a.setText("Add file"); c2.setText("Filter: assets");
        Ui.pressable(a); Ui.pressable(c2);
        a.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            pendingEntry = null; pick(REQ_ADD);
        }});
        final Button fb = c2;
        c2.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            filter = (filter + 1) % FILTERS.length; fb.setText("Filter: " + FILTER_NAMES[filter]); load();
        }});
        load();
    }

    private void pick(int req) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*");
        startActivityForResult(i, req);
    }

    private void load() {
        Map<String, Ui.Row> map = new LinkedHashMap<>();
        Set<String> del = project.deleted();
        for (ZipEntry e : project.originalEntries()) {
            String n = e.getName();
            if (!n.startsWith(FILTERS[filter])) continue;
            String state = del.contains(n) ? "deleted" : project.hasOverlay(n) ? "replaced" : "";
            map.put(n, new Ui.Row(ext(n), n, state, Files2.human(e.getSize()), n));
        }
        for (String n : project.overlayEntries()) {
            if (!n.startsWith(FILTERS[filter]) || map.containsKey(n)) continue;
            map.put(n, new Ui.Row(ext(n), n, "added", Files2.human(project.overlayFile(n).length()), n));
        }
        all.clear(); all.addAll(map.values());
        apply();
    }

    private String ext(String n) {
        int i = n.lastIndexOf('.');
        String e = i < 0 ? "FILE" : n.substring(i + 1).toUpperCase();
        return e.length() > 4 ? e.substring(0, 4) : e;
    }

    private void apply() {
        String q = search.getText().toString().toLowerCase();
        List<Ui.Row> out = new ArrayList<>();
        for (Ui.Row r : all) if (q.isEmpty() || r.t1.toLowerCase().contains(q)) out.add(r);
        adapter.set(out);
        status.setText(out.size() + " files  ·  tap for actions");
    }

    private void actions(final String entry) {
        final boolean del = project.deleted().contains(entry);
        Ui.choose(this, entry, new String[]{"View", "Export to device", "Replace with file", del ? "Restore" : "Delete"}, new Ui.Cb<Integer>() {
            public void on(Integer i) {
                if (i == 0) view(entry);
                else if (i == 1) { pendingEntry = entry; export(entry); }
                else if (i == 2) { pendingEntry = entry; pick(REQ_REPLACE); }
                else { if (del) project.restoreEntry(entry); else project.deleteEntry(entry); load(); }
            }
        });
    }

    private void export(String entry) {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("application/octet-stream");
        i.putExtra(Intent.EXTRA_TITLE, entry.substring(entry.lastIndexOf('/') + 1));
        startActivityForResult(i, REQ_EXPORT);
    }

    private void view(String entry) {
        byte[] d = project.entry(entry);
        if (d == null) { toast("Empty"); return; }
        String low = entry.toLowerCase();
        if (low.endsWith(".png") || low.endsWith(".jpg") || low.endsWith(".jpeg") || low.endsWith(".webp")) {
            Bitmap bm = BitmapFactory.decodeByteArray(d, 0, d.length);
            if (bm != null) {
                ImageView iv = new ImageView(this);
                iv.setImageBitmap(bm); iv.setAdjustViewBounds(true);
                iv.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16));
                new AlertDialog.Builder(this).setTitle(entry + "  " + bm.getWidth() + "×" + bm.getHeight()).setView(iv).setPositiveButton("Close", null).show();
                return;
            }
        }
        String text;
        try {
            if ((low.endsWith(".xml") && (entry.startsWith("res/") || entry.equals("AndroidManifest.xml"))) && d.length > 8 && d[0] == 3 && d[1] == 0)
                text = Axml.dump(d);
            else text = new String(d, 0, Math.min(d.length, 40000), "UTF-8");
        } catch (Throwable t) { text = "(binary file, " + d.length + " bytes)"; }
        TextView tv = new TextView(this);
        tv.setText(text); tv.setTextSize(11); tv.setTypeface(android.graphics.Typeface.MONOSPACE);
        tv.setTextColor(0xFFCFC8F5); tv.setTextIsSelectable(true);
        tv.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
        ScrollView sv = new ScrollView(this); sv.addView(tv);
        new AlertDialog.Builder(this).setTitle(entry).setView(sv).setPositiveButton("Close", null).show();
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != Activity.RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        try {
            if (req == REQ_EXPORT) {
                byte[] d = project.entry(pendingEntry);
                OutputStream o = getContentResolver().openOutputStream(uri);
                o.write(d); o.close(); toast("Exported");
            } else if (req == REQ_REPLACE) {
                byte[] d = Files2.readAll(getContentResolver().openInputStream(uri));
                project.putOverlay(pendingEntry, d); toast("Replaced"); load();
            } else if (req == REQ_ADD) {
                final byte[] d = Files2.readAll(getContentResolver().openInputStream(uri));
                String nm = uri.getLastPathSegment();
                if (nm == null) nm = "file.bin";
                nm = nm.substring(nm.lastIndexOf('/') + 1);
                nm = nm.substring(nm.lastIndexOf(':') + 1);
                Ui.prompt(this, "Path inside APK", "assets/name.ext", "assets/" + nm, new Ui.Cb<String>() {
                    public void on(String p) { if (!p.trim().isEmpty()) { project.putOverlay(p.trim(), d); toast("Added"); load(); } }
                });
            }
        } catch (Exception e) { toast("Failed: " + e.getMessage()); }
    }
}
