package com.mutagen.lab;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.mutagen.lab.core.Project;

import java.io.File;

public abstract class BaseActivity extends Activity {
    protected Project project;
    protected final Handler main = new Handler(Looper.getMainLooper());

    protected void loadProject() {
        String n = getIntent().getStringExtra("p");
        if (n != null) project = new Project(new File(Project.root(this), n));
    }

    protected void header(String title, String sub) {
        TextView t = (TextView) findViewById(R.id.hdrTitle);
        TextView s = (TextView) findViewById(R.id.hdrSub);
        if (t != null) t.setText(title);
        if (s != null) s.setText(sub);
        View back = findViewById(R.id.hdrBack);
        if (back != null) { Ui.pressable(back); back.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { finish(); } }); }
        View h = findViewById(R.id.header);
        if (h != null) { h.setAlpha(0f); h.setTranslationY(-Ui.dp(this, 14)); h.animate().alpha(1f).translationY(0).setDuration(380).start(); }
    }

    protected void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    protected void open(Class<?> c) {
        Intent i = new Intent(this, c);
        if (project != null) i.putExtra("p", project.name);
        startActivity(i);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    @Override public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    /** Runs work on a thread; ui callbacks on main. */
    protected void bg(final Runnable work) {
        new Thread(work).start();
    }

    protected void ui(Runnable r) { runOnUiThread(r); }

    protected void showError(final Throwable t) {
        final String msg = t.getMessage() == null ? t.toString() : t.getMessage();
        ui(new Runnable() { public void run() { Ui.message(BaseActivity.this, "Error", msg); } });
    }
}
