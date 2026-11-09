package com.mutagen.lab.core;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Generates and inserts smali for test injections (dialog, toast, log, url, raw code). */
public final class SmaliInjector {
    private SmaliInjector() {}

    public static final String PKG = "com/mutagen/inject";

    public static String esc(String s) {
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '\\': b.append("\\\\"); break;
                case '"': b.append("\\\""); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (ch < 32 || ch > 126) b.append(String.format("\\u%04x", (int) ch)); else b.append(ch);
            }
        }
        return b.toString();
    }

    // ------------------------------------------------------------ helper classes
    static String header(String cls) {
        return ".class public L" + PKG + "/" + cls + ";\n.super Ljava/lang/Object;\n\n";
    }

    public static String toast(String cls, String msg) {
        return header(cls)
            + ".method public static run(Landroid/content/Context;)V\n"
            + "    .locals 2\n"
            + "    const-string v0, \"" + esc(msg) + "\"\n"
            + "    const/4 v1, 0x1\n"
            + "    invoke-static {p0, v0, v1}, Landroid/widget/Toast;->makeText(Landroid/content/Context;Ljava/lang/CharSequence;I)Landroid/widget/Toast;\n"
            + "    move-result-object v0\n"
            + "    invoke-virtual {v0}, Landroid/widget/Toast;->show()V\n"
            + "    return-void\n"
            + ".end method\n";
    }

    public static String log(String cls, String tag, String msg) {
        return header(cls)
            + ".method public static run(Landroid/content/Context;)V\n"
            + "    .locals 2\n"
            + "    const-string v0, \"" + esc(tag) + "\"\n"
            + "    const-string v1, \"" + esc(msg) + "\"\n"
            + "    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I\n"
            + "    return-void\n"
            + ".end method\n";
    }

    public static String openUrl(String cls, String url) {
        return header(cls)
            + ".method public static run(Landroid/content/Context;)V\n"
            + "    .locals 4\n"
            + "    new-instance v0, Landroid/content/Intent;\n"
            + "    const-string v1, \"android.intent.action.VIEW\"\n"
            + "    const-string v2, \"" + esc(url) + "\"\n"
            + "    invoke-static {v2}, Landroid/net/Uri;->parse(Ljava/lang/String;)Landroid/net/Uri;\n"
            + "    move-result-object v2\n"
            + "    invoke-direct {v0, v1, v2}, Landroid/content/Intent;-><init>(Ljava/lang/String;Landroid/net/Uri;)V\n"
            + "    const/high16 v3, 0x10000000\n"
            + "    invoke-virtual {v0, v3}, Landroid/content/Intent;->addFlags(I)Landroid/content/Intent;\n"
            + "    invoke-virtual {p0, v0}, Landroid/content/Context;->startActivity(Landroid/content/Intent;)V\n"
            + "    return-void\n"
            + ".end method\n";
    }

    /** Dialog helper. When link != null a second class opens the URL from the neutral button. */
    public static String dialog(String cls, String title, String msg, String okText, String linkText, boolean cancelable, boolean hasLink) {
        String listener = "L" + PKG + "/" + cls + "$L;";
        StringBuilder s = new StringBuilder(header(cls));
        s.append(".method public static run(Landroid/content/Context;)V\n");
        s.append("    .locals 4\n");
        s.append("    new-instance v0, Landroid/app/AlertDialog$Builder;\n");
        s.append("    invoke-direct {v0, p0}, Landroid/app/AlertDialog$Builder;-><init>(Landroid/content/Context;)V\n");
        s.append("    const-string v1, \"" + esc(title) + "\"\n");
        s.append("    invoke-virtual {v0, v1}, Landroid/app/AlertDialog$Builder;->setTitle(Ljava/lang/CharSequence;)Landroid/app/AlertDialog$Builder;\n");
        s.append("    move-result-object v0\n");
        s.append("    const-string v1, \"" + esc(msg) + "\"\n");
        s.append("    invoke-virtual {v0, v1}, Landroid/app/AlertDialog$Builder;->setMessage(Ljava/lang/CharSequence;)Landroid/app/AlertDialog$Builder;\n");
        s.append("    move-result-object v0\n");
        s.append("    const-string v1, \"" + esc(okText) + "\"\n");
        s.append("    const/4 v2, 0x0\n");
        s.append("    invoke-virtual {v0, v1, v2}, Landroid/app/AlertDialog$Builder;->setPositiveButton(Ljava/lang/CharSequence;Landroid/content/DialogInterface$OnClickListener;)Landroid/app/AlertDialog$Builder;\n");
        s.append("    move-result-object v0\n");
        if (hasLink) {
            s.append("    new-instance v2, " + listener + "\n");
            s.append("    const-string v3, \"" + esc(linkText.startsWith("http") || linkText.contains("://") ? linkText : "https://" + linkText) + "\"\n");
            s.append("    invoke-direct {v2, p0, v3}, " + listener + "-><init>(Landroid/content/Context;Ljava/lang/String;)V\n");
            s.append("    const-string v1, \"Open link\"\n");
            s.append("    invoke-virtual {v0, v1, v2}, Landroid/app/AlertDialog$Builder;->setNeutralButton(Ljava/lang/CharSequence;Landroid/content/DialogInterface$OnClickListener;)Landroid/app/AlertDialog$Builder;\n");
            s.append("    move-result-object v0\n");
        }
        s.append("    const/" + "4 v1, " + (cancelable ? "0x1" : "0x0") + "\n");
        s.append("    invoke-virtual {v0, v1}, Landroid/app/AlertDialog$Builder;->setCancelable(Z)Landroid/app/AlertDialog$Builder;\n");
        s.append("    move-result-object v0\n");
        s.append("    invoke-virtual {v0}, Landroid/app/AlertDialog$Builder;->show()Landroid/app/AlertDialog;\n");
        s.append("    return-void\n");
        s.append(".end method\n");
        return s.toString();
    }

    public static String linkListener(String cls) {
        String self = "L" + PKG + "/" + cls + "$L;";
        return ".class public " + self + "\n.super Ljava/lang/Object;\n.implements Landroid/content/DialogInterface$OnClickListener;\n\n"
            + ".field private final a:Landroid/content/Context;\n.field private final b:Ljava/lang/String;\n\n"
            + ".method public constructor <init>(Landroid/content/Context;Ljava/lang/String;)V\n"
            + "    .locals 0\n"
            + "    invoke-direct {p0}, Ljava/lang/Object;-><init>()V\n"
            + "    iput-object p1, p0, " + self + "->a:Landroid/content/Context;\n"
            + "    iput-object p2, p0, " + self + "->b:Ljava/lang/String;\n"
            + "    return-void\n"
            + ".end method\n\n"
            + ".method public onClick(Landroid/content/DialogInterface;I)V\n"
            + "    .locals 4\n"
            + "    new-instance v0, Landroid/content/Intent;\n"
            + "    const-string v1, \"android.intent.action.VIEW\"\n"
            + "    iget-object v2, p0, " + self + "->b:Ljava/lang/String;\n"
            + "    invoke-static {v2}, Landroid/net/Uri;->parse(Ljava/lang/String;)Landroid/net/Uri;\n"
            + "    move-result-object v2\n"
            + "    invoke-direct {v0, v1, v2}, Landroid/content/Intent;-><init>(Ljava/lang/String;Landroid/net/Uri;)V\n"
            + "    iget-object v1, p0, " + self + "->a:Landroid/content/Context;\n"
            + "    invoke-virtual {v1, v0}, Landroid/content/Context;->startActivity(Landroid/content/Intent;)V\n"
            + "    return-void\n"
            + ".end method\n";
    }

    // ------------------------------------------------------------ naming / records
    public static String nextClassName(Project p) {
        int n = 1;
        for (File d : p.smaliDirs()) {
            File dir = new File(d, PKG);
            File[] fs = dir.listFiles();
            if (fs != null) for (File f : fs) {
                String nm = f.getName();
                if (nm.startsWith("Inj")) {
                    try { int k = Integer.parseInt(nm.substring(3).replaceAll("[^0-9].*", "")); if (k >= n) n = k + 1; } catch (Exception ignored) {}
                }
            }
        }
        return "Inj" + n;
    }

    public static File injectionsFile(Project p) { return new File(p.dir, "injections.txt"); }

    public static List<String> records(Project p) {
        List<String> l = new ArrayList<>();
        if (injectionsFile(p).exists()) for (String s : Files2.readLines(injectionsFile(p))) if (!s.isEmpty()) l.add(s);
        return l;
    }

    // ------------------------------------------------------------ text insertion
    public static String beginMark(String id) { return "    # mutagen:begin:" + id; }
    public static String endMark(String id) { return "    # mutagen:end:" + id; }

    /** Inserts lines into a method. Creates onCreate(Bundle) if the class does not define it. */
    public static String insert(String src, String method, List<String> lines, int extraLocals, boolean afterSuper) throws Exception {
        List<String> L = new ArrayList<>();
        for (String s : src.split("\n", -1)) L.add(s);
        int ms = -1, me = -1;
        for (int i = 0; i < L.size(); i++) {
            String t = L.get(i);
            if (t.startsWith(".method ") && t.endsWith(" " + method)) {
                ms = i;
                for (int j = i + 1; j < L.size(); j++) if (L.get(j).startsWith(".end method")) { me = j; break; }
                break;
            }
        }
        if (ms < 0) {
            if (!method.equals("onCreate(Landroid/os/Bundle;)V")) throw new Exception("Method not found: " + method);
            String sup = null;
            for (String t : L) if (t.startsWith(".super ")) { sup = t.substring(7).trim(); break; }
            if (sup == null) throw new Exception("No .super found");
            List<String> m = new ArrayList<>();
            m.add(""); m.add(".method protected onCreate(Landroid/os/Bundle;)V"); m.add("    .locals 0");
            m.add("    invoke-super {p0, p1}, " + sup + "->onCreate(Landroid/os/Bundle;)V");
            m.addAll(lines);
            m.add("    return-void"); m.add(".end method");
            L.addAll(m);
            return join(L);
        }
        int regLine = -1;
        for (int i = ms + 1; i < me; i++) {
            String t = L.get(i).trim();
            if (t.startsWith(".locals ") || t.startsWith(".registers ")) { regLine = i; break; }
        }
        if (regLine < 0) throw new Exception("Method has no code (abstract/native)");
        if (extraLocals > 0) {
            String t = L.get(regLine).trim();
            String[] parts = t.split("\\s+");
            int n = Integer.parseInt(parts[1]) + extraLocals;
            L.set(regLine, "    " + parts[0] + " " + n);
        }
        int at = -1;
        String simple = method.substring(0, method.indexOf('('));
        if (afterSuper) {
            for (int i = regLine + 1; i < me; i++) {
                String t = L.get(i).trim();
                if (t.startsWith("invoke-super") && t.contains("->" + simple + "(")) { at = i + 1; break; }
            }
        }
        if (at < 0) {
            boolean inAnno = false;
            at = regLine + 1;
            for (int i = regLine + 1; i < me; i++) {
                String t = L.get(i).trim();
                if (inAnno) { if (t.startsWith(".end annotation")) inAnno = false; continue; }
                if (t.startsWith(".annotation")) { inAnno = true; continue; }
                if (t.isEmpty() || t.startsWith("#") || t.startsWith(".")) continue;
                at = i; break;
            }
        }
        L.addAll(at, lines);
        return join(L);
    }

    private static String join(List<String> l) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < l.size(); i++) { b.append(l.get(i)); if (i < l.size() - 1) b.append('\n'); }
        return b.toString();
    }

    public static String removeBlock(String src, String id) {
        List<String> out = new ArrayList<>();
        boolean skip = false;
        for (String s : src.split("\n", -1)) {
            if (s.trim().equals("# mutagen:begin:" + id)) { skip = true; continue; }
            if (s.trim().equals("# mutagen:end:" + id)) { skip = false; continue; }
            if (!skip) out.add(s);
        }
        return join(out);
    }
}
