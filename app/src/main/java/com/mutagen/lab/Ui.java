package com.mutagen.lab;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public final class Ui {
    private Ui() {}

    public interface Cb<T> { void on(T v); }
    public interface Cb2 { void on(String a, String b); }

    public static int dp(Context c, float v) { return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f); }

    // ---------------------------------------------------------- animation
    public static void stagger(ViewGroup g) { stagger(g, 60); }

    public static void stagger(ViewGroup g, int stepMs) {
        for (int i = 0; i < g.getChildCount(); i++) {
            View v = g.getChildAt(i);
            v.setAlpha(0f);
            v.setTranslationY(dp(g.getContext(), 24));
            v.animate().alpha(1f).translationY(0f).setStartDelay(80 + i * stepMs).setDuration(420)
                    .setInterpolator(new DecelerateInterpolator(2f)).start();
        }
    }

    public static void popIn(View v, int delay) {
        v.setScaleX(0.85f); v.setScaleY(0.85f); v.setAlpha(0f);
        v.animate().scaleX(1f).scaleY(1f).alpha(1f).setStartDelay(delay).setDuration(380)
                .setInterpolator(new OvershootInterpolator(1.6f)).start();
    }

    /** Subtle press-scale feedback for cards and buttons. */
    public static void pressable(View v) {
        v.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View view, MotionEvent e) {
                int a = e.getActionMasked();
                if (a == MotionEvent.ACTION_DOWN) view.animate().scaleX(0.96f).scaleY(0.96f).setDuration(110).start();
                else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) view.animate().scaleX(1f).scaleY(1f).setDuration(160).start();
                return false;
            }
        });
    }

    // ---------------------------------------------------------- dialogs
    public static void prompt(Activity a, String title, String hint, String initial, final Cb<String> cb) {
        final EditText et = field(a, hint, initial, false);
        LinearLayout box = wrap(a, et);
        new AlertDialog.Builder(a).setTitle(title).setView(box)
                .setPositiveButton("OK", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) { cb.on(et.getText().toString()); }
                }).setNegativeButton("Cancel", null).show();
    }

    public static void prompt2(Activity a, String title, String h1, String i1, String h2, String i2, final Cb2 cb) {
        final EditText e1 = field(a, h1, i1, false), e2 = field(a, h2, i2, false);
        LinearLayout box = wrap(a, e1);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(a, 10);
        e2.setLayoutParams(lp);
        box.addView(e2);
        new AlertDialog.Builder(a).setTitle(title).setView(box)
                .setPositiveButton("OK", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) { cb.on(e1.getText().toString(), e2.getText().toString()); }
                }).setNegativeButton("Cancel", null).show();
    }

    private static EditText field(Activity a, String hint, String initial, boolean multi) {
        EditText et = new EditText(a);
        et.setHint(hint);
        et.setText(initial == null ? "" : initial);
        et.setBackgroundResource(R.drawable.bg_input);
        et.setTextColor(0xFFF4F1FF);
        et.setHintTextColor(0xFF6F678F);
        et.setTextSize(14);
        et.setPadding(dp(a, 12), dp(a, 12), dp(a, 12), dp(a, 12));
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        return et;
    }

    private static LinearLayout wrap(Activity a, View v) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(a, 20), dp(a, 8), dp(a, 20), 0);
        box.addView(v, new LinearLayout.LayoutParams(-1, -2));
        return box;
    }

    public static void confirm(Activity a, String title, String msg, final Runnable yes) {
        new AlertDialog.Builder(a).setTitle(title).setMessage(msg)
                .setPositiveButton("Yes", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) { yes.run(); }
                }).setNegativeButton("Cancel", null).show();
    }

    public static void choose(Activity a, String title, final String[] items, final Cb<Integer> cb) {
        new AlertDialog.Builder(a).setTitle(title).setItems(items, new android.content.DialogInterface.OnClickListener() {
            public void onClick(android.content.DialogInterface d, int w) { cb.on(w); }
        }).show();
    }

    public static void message(Activity a, String title, String msg) {
        new AlertDialog.Builder(a).setTitle(title).setMessage(msg).setPositiveButton("OK", null).show();
    }

    // ---------------------------------------------------------- list rows
    public static final class Row {
        public String glyph = "", t1 = "", t2 = "", t3 = "";
        public Drawable icon;
        public Object tag;
        public Row(String glyph, String t1, String t2, String t3, Object tag) {
            this.glyph = glyph; this.t1 = t1; this.t2 = t2; this.t3 = t3; this.tag = tag;
        }
    }

    public static class RowAdapter extends BaseAdapter {
        private final Context ctx;
        public List<Row> rows = new ArrayList<>();
        private int lastAnimated = -1;

        public RowAdapter(Context c) { ctx = c; }
        public void set(List<Row> r) { rows = r; lastAnimated = -1; notifyDataSetChanged(); }
        public int getCount() { return rows.size(); }
        public Object getItem(int i) { return rows.get(i); }
        public long getItemId(int i) { return i; }

        public View getView(int pos, View v, ViewGroup parent) {
            if (v == null) v = LayoutInflater.from(ctx).inflate(R.layout.item_row, parent, false);
            Row r = rows.get(pos);
            TextView g = (TextView) v.findViewById(R.id.glyph);
            ImageView ic = (ImageView) v.findViewById(R.id.icon);
            if (r.icon != null) { ic.setVisibility(View.VISIBLE); ic.setImageDrawable(r.icon); g.setVisibility(View.GONE); }
            else { ic.setVisibility(View.GONE); g.setVisibility(View.VISIBLE); g.setText(r.glyph); }
            ((TextView) v.findViewById(R.id.t1)).setText(r.t1);
            TextView t2 = (TextView) v.findViewById(R.id.t2);
            t2.setText(r.t2); t2.setVisibility(r.t2 == null || r.t2.isEmpty() ? View.GONE : View.VISIBLE);
            ((TextView) v.findViewById(R.id.t3)).setText(r.t3);
            if (pos > lastAnimated && pos < 14) {
                lastAnimated = pos;
                v.setAlpha(0f); v.setTranslationY(dp(ctx, 18));
                v.animate().alpha(1f).translationY(0).setStartDelay(pos * 35L).setDuration(300).start();
            } else { v.setAlpha(1f); v.setTranslationY(0); }
            return v;
        }
    }
}
