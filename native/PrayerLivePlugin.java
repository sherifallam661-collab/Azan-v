package com.example.prayerqibla;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import com.getcapacitor.JSArray;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "PrayerLive")
public class PrayerLivePlugin extends Plugin {
    @PluginMethod
    public void start(PluginCall call) {
        JSArray items = call.getArray("items");
        Context ctx = getContext();
        ctx.getSharedPreferences("prayer", 0).edit().putString("items", items == null ? "[]" : items.toString()).apply();
        Intent i = new Intent(ctx, PrayerService.class);
        if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i); else ctx.startService(i);
        call.resolve();
    }

    @PluginMethod
    public void stop(PluginCall call) {
        getContext().stopService(new Intent(getContext(), PrayerService.class));
        call.resolve();
    }
}
