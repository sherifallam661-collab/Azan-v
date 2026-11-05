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
        JSArray days = call.getArray("days");
        String place = call.getString("place", "");
        ctx.getSharedPreferences("prayer", 0).edit()
            .putString("items", items == null ? "[]" : items.toString())
            .putString("days", days == null ? "[]" : days.toString())
            .putString("place", place == null ? "" : place)
            .putBoolean("auto", Boolean.TRUE.equals(call.getBoolean("auto", true)))
            .putString("adhan", call.getString("adhan", "abdulbasit")).apply();
        Intent i = new Intent(ctx, PrayerService.class);
        if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i); else ctx.startService(i);
        call.resolve();
    }

    @PluginMethod
    public void saveAdhan(PluginCall call) {
        try {
            String d = call.getString("data", "");
            int i = d.indexOf(",");
            byte[] b = android.util.Base64.decode(i >= 0 ? d.substring(i + 1) : d, android.util.Base64.DEFAULT);
            java.io.FileOutputStream o = new java.io.FileOutputStream(new java.io.File(getContext().getFilesDir(), "adhan_custom"));
            o.write(b);
            o.close();
            call.resolve();
        } catch (Exception e) { call.reject(String.valueOf(e.getMessage())); }
    }

    @PluginMethod
    public void stop(PluginCall call) {
        getContext().stopService(new Intent(getContext(), PrayerService.class));
        call.resolve();
    }
}
