package com.mutagen.lab.core;

import android.content.Context;

import com.android.apksig.ApkSigner;

import java.io.File;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;

public final class Signer {
    private Signer() {}

    public static void sign(Context c, File in, File out, int minSdk) throws Exception {
        InputStream ks = c.getAssets().open("mutagen.p12");
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(ks, "mutagen".toCharArray());
        ks.close();
        String alias = store.aliases().nextElement();
        PrivateKey key = (PrivateKey) store.getKey(alias, "mutagen".toCharArray());
        X509Certificate cert = (X509Certificate) store.getCertificate(alias);
        ApkSigner.SignerConfig cfg = new ApkSigner.SignerConfig.Builder("MUTAGEN", key, Collections.singletonList(cert)).build();
        new ApkSigner.Builder(Collections.singletonList(cfg))
                .setInputApk(in)
                .setOutputApk(out)
                .setMinSdkVersion(minSdk)
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(true)
                .build()
                .sign();
    }
}
