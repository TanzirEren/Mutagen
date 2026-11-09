package com.mutagen.lab;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.mutagen.lab.core.Files2;

import java.io.File;

public class EditorActivity extends BaseActivity {
    private EditText code, find, repl;
    private File file;
    private String original = "";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_editor);
        loadProject();
        file = new File(getIntent().getStringExtra("f"));
        header(file.getName(), project.relativeSmali(file));
        code = (EditText) findViewById(R.id.code);
        find = (EditText) findViewById(R.id.find);
        repl = (EditText) findViewById(R.id.repl);
        original = Files2.readText(file);
        code.setText(original);
        final int line = getIntent().getIntExtra("line", 0);
        if (line > 1) code.post(new Runnable() { public void run() { goTo(line); } });

        Button save = (Button) findViewById(R.id.hdrAction);
        save.setVisibility(View.VISIBLE);
        Ui.pressable(save);
        save.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { save(); } });

        Button next = (Button) findViewById(R.id.btnNext), all = (Button) findViewById(R.id.btnAll);
        Ui.pressable(next); Ui.pressable(all);
        next.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { findNext(); } });
        all.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { replaceAll(); } });
    }

    private void goTo(int line) {
        String t = code.getText().toString();
        int off = 0;
        for (int i = 1; i < line; i++) { int n = t.indexOf('\n', off); if (n < 0) break; off = n + 1; }
        code.requestFocus();
        code.setSelection(Math.min(off, t.length()));
    }

    private void save() {
        Files2.writeText(file, code.getText().toString());
        original = code.getText().toString();
        toast("Saved");
    }

    private void findNext() {
        String q = find.getText().toString();
        if (q.isEmpty()) return;
        String t = code.getText().toString();
        int from = code.getSelectionEnd();
        int i = t.indexOf(q, Math.max(0, from));
        if (i < 0) { i = t.indexOf(q); if (i >= 0) toast("Wrapped to top"); }
        if (i < 0) { toast("Not found"); return; }
        code.requestFocus();
        code.setSelection(i, i + q.length());
    }

    private void replaceAll() {
        String q = find.getText().toString();
        if (q.isEmpty()) return;
        String t = code.getText().toString();
        int count = 0, idx = 0;
        while ((idx = t.indexOf(q, idx)) >= 0) { count++; idx += q.length(); }
        if (count == 0) { toast("Not found"); return; }
        code.setText(t.replace(q, repl.getText().toString()));
        toast("Replaced " + count);
    }

    @Override public void onBackPressed() {
        if (!code.getText().toString().equals(original)) {
            Ui.confirm(this, "Unsaved changes", "Save before leaving?", new Runnable() { public void run() { save(); finish(); } });
        } else super.onBackPressed();
    }
}
