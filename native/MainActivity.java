package com.example.prayerqibla;

import android.os.Bundle;
import android.view.View;
import android.view.Window;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(PrayerLivePlugin.class);
        super.onCreate(savedInstanceState);
        try {
            Window w = getWindow();
            w.setStatusBarColor(0xFF060606);
            w.setNavigationBarColor(0xFF060606);
            w.getDecorView().setBackgroundColor(0xFF060606);
            int f = w.getDecorView().getSystemUiVisibility();
            f &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            f &= ~0x00000010; // LIGHT_NAVIGATION_BAR
            w.getDecorView().setSystemUiVisibility(f);
            if (getBridge() != null && getBridge().getWebView() != null) getBridge().getWebView().setBackgroundColor(0xFF060606);
        } catch (Exception e) { }
    }
}
