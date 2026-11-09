package com.mutagen.lab;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

public class SplashActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_splash);
        View logo = findViewById(R.id.logo);
        View name = findViewById(R.id.name);
        View tag = findViewById(R.id.tag);
        View bar = findViewById(R.id.bar);

        logo.setScaleX(0.3f); logo.setScaleY(0.3f); logo.setAlpha(0f); logo.setRotation(-25f);
        logo.animate().scaleX(1f).scaleY(1f).alpha(1f).rotation(0f).setDuration(750)
                .setInterpolator(new OvershootInterpolator(1.8f)).start();
        ObjectAnimator pulse = ObjectAnimator.ofFloat(logo, "translationY", 0f, -Ui.dp(this, 8), 0f);
        pulse.setDuration(1600); pulse.setRepeatCount(ValueAnimator.INFINITE); pulse.setStartDelay(800); pulse.start();

        name.setAlpha(0f); name.setTranslationY(Ui.dp(this, 20));
        name.animate().alpha(1f).translationY(0).setStartDelay(450).setDuration(600).setInterpolator(new DecelerateInterpolator(2f)).start();
        tag.setAlpha(0f);
        tag.animate().alpha(1f).setStartDelay(800).setDuration(600).start();
        bar.setAlpha(0f);
        bar.animate().alpha(1f).setStartDelay(600).setDuration(400).start();

        new Handler().postDelayed(new Runnable() {
            public void run() {
                startActivity(new Intent(SplashActivity.this, MainActivity.class));
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                finish();
            }
        }, 2000);
    }
}
