package com.example.prayerqibla;

import android.content.Context;
import android.content.Intent;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import com.getcapacitor.JSObject;
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
            .putString("adhan", call.getString("adhan", "abdulbasit"))
            .putString("reminder", call.getString("reminder", "chime"))
            .putInt("volume", call.getInt("volume", 100)).apply();
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
    public void saveReminderSound(PluginCall call) {
        try {
            String d = call.getString("data", "");
            int i = d.indexOf(",");
            byte[] b = android.util.Base64.decode(i >= 0 ? d.substring(i + 1) : d, android.util.Base64.DEFAULT);
            java.io.FileOutputStream o = new java.io.FileOutputStream(new java.io.File(getContext().getFilesDir(), "reminder_custom"));
            o.write(b);
            o.close();
            call.resolve();
        } catch (Exception e) { call.reject(String.valueOf(e.getMessage())); }
    }

    @PluginMethod
    public void playSystem(PluginCall call) {
        try {
            android.net.Uri u = android.media.RingtoneManager.getDefaultUri("alarm".equals(call.getString("kind", "default")) ? android.media.RingtoneManager.TYPE_ALARM : android.media.RingtoneManager.TYPE_NOTIFICATION);
            android.media.Ringtone r = android.media.RingtoneManager.getRingtone(getContext(), u);
            if (r != null) r.play();
        } catch (Exception e) { }
        call.resolve();
    }

    // حفظ دائم للإعدادات في ذاكرة أندرويد (يبقى حتى لو أُغلق التطبيق فجأة)
    @PluginMethod
    public void saveSetting(PluginCall call) {
        String k = call.getString("key"), v = call.getString("value", "");
        if (k != null) {
            Context ctx = getContext();
            ctx.getSharedPreferences("settings", 0).edit().putString(k, v).apply();
            android.content.SharedPreferences.Editor pe = ctx.getSharedPreferences("prayer", 0).edit();
            try {
                if ("app-adhan-volume".equals(k)) pe.putInt("volume", Integer.parseInt(v));
                else if ("app-adhan".equals(k)) pe.putString("adhan", v);
                else if ("app-reminder".equals(k)) pe.putString("reminder", v);
                else if ("auto-adhan".equals(k)) pe.putBoolean("auto", !"0".equals(v));
            } catch (Exception e) { }
            pe.apply();
        }
        call.resolve();
    }

    @PluginMethod
    public void getSettings(PluginCall call) {
        JSObject o = new JSObject();
        for (java.util.Map.Entry<String, ?> e : getContext().getSharedPreferences("settings", 0).getAll().entrySet()) {
            o.put(e.getKey(), String.valueOf(e.getValue()));
        }
        call.resolve(o);
    }

    @PluginMethod
    public void playAdhanNow(PluginCall call) {
        Context ctx = getContext();
        Intent i = new Intent(ctx, PrayerService.class);
        i.setAction("PLAY_ADHAN");
        i.putExtra("adhan", call.getString("adhan", "abdulbasit"));
        i.putExtra("volume", call.getInt("volume", 100));
        i.putExtra("name", call.getString("name", "تجربة"));
        if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i); else ctx.startService(i);
        call.resolve();
    }

    @PluginMethod
    public void adhanState(PluginCall call) {
        JSObject o = new JSObject();
        o.put("playing", PrayerService.adhanPlaying);
        o.put("name", PrayerService.adhanName);
        call.resolve(o);
    }

    @PluginMethod
    public void overlayState(PluginCall call) {
        JSObject o = new JSObject();
        boolean g = true;
        try { if (Build.VERSION.SDK_INT >= 23) g = android.provider.Settings.canDrawOverlays(getContext()); } catch (Exception e) { }
        o.put("granted", g);
        call.resolve(o);
    }

    @PluginMethod
    public void openOverlaySettings(PluginCall call) {
        try {
            Intent i = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:" + getContext().getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            call.resolve();
        } catch (Exception e) { call.reject(String.valueOf(e.getMessage())); }
    }

    @PluginMethod
    public void openFullScreenSettings(PluginCall call) {
        try {
            String pkg = getContext().getPackageName();
            Intent i;
            if (Build.VERSION.SDK_INT >= 34) i = new Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT", android.net.Uri.parse("package:" + pkg));
            else i = new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, pkg);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            call.resolve();
        } catch (Exception e) { call.reject(String.valueOf(e.getMessage())); }
    }

    @PluginMethod
    public void openAppSettings(PluginCall call) {
        try {
            Intent i = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + getContext().getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            call.resolve();
        } catch (Exception e) { call.reject(String.valueOf(e.getMessage())); }
    }

    @PluginMethod
    public void stopAdhan(PluginCall call) {
        Context ctx = getContext();
        Intent i = new Intent(ctx, PrayerService.class);
        i.setAction("STOP_ADHAN");
        ctx.startService(i);
        call.resolve();
    }

    @PluginMethod
    public void stop(PluginCall call) {
        getContext().stopService(new Intent(getContext(), PrayerService.class));
        call.resolve();
    }

    // ===== بوصلة أصلية (حساس Rotation Vector مع تصحيح الانحراف المغناطيسي) =====
    private SensorManager sm;
    private SensorEventListener sl;
    private boolean compassOn = false, seeded = false;
    private float declination = 0f;
    private double smAngle = 0, lastSent = -1;
    private long lastEmit = 0;

    private boolean registerCompass() {
        try {
            if (sm == null) sm = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
            Sensor rv = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            if (rv == null) return false;
            if (sl != null) sm.unregisterListener(sl);
            sl = new SensorEventListener() {
                final float[] R = new float[9], R2 = new float[9], o = new float[3];
                @Override public void onSensorChanged(SensorEvent e) {
                    SensorManager.getRotationMatrixFromVector(R, e.values);
                    float[] M = R;
                    if (Math.abs(R[8]) < 0.7f) { // الهاتف قائم: نقيس اتجاه الكاميرا الخلفية
                        SensorManager.remapCoordinateSystem(R, SensorManager.AXIS_X, SensorManager.AXIS_Z, R2);
                        M = R2;
                    }
                    SensorManager.getOrientation(M, o);
                    double deg = (Math.toDegrees(o[0]) + declination + 360) % 360;
                    if (!seeded) { smAngle = deg; seeded = true; }
                    else {
                        double dd = ((deg - smAngle + 540) % 360) - 180;
                        double al = Math.abs(dd) > 30 ? 0.6 : Math.abs(dd) > 10 ? 0.25 : 0.07;
                        smAngle = (smAngle + al * dd + 360) % 360;
                    }
                    long now = System.currentTimeMillis();
                    if (now - lastEmit < 60) return;
                    if (lastSent >= 0 && Math.abs(((smAngle - lastSent + 540) % 360) - 180) < 0.7) return;
                    lastEmit = now;
                    lastSent = smAngle;
                    JSObject d = new JSObject();
                    d.put("heading", smAngle);
                    d.put("accuracy", e.accuracy);
                    notifyListeners("heading", d);
                }
                @Override public void onAccuracyChanged(Sensor s, int a) { }
            };
            return sm.registerListener(sl, rv, SensorManager.SENSOR_DELAY_UI);
        } catch (Exception ex) { return false; }
    }

    @PluginMethod
    public void startCompass(PluginCall call) {
        try {
            Double la = call.getDouble("lat", 0.0), ln = call.getDouble("lng", 0.0);
            declination = new GeomagneticField(la.floatValue(), ln.floatValue(), 0f, System.currentTimeMillis()).getDeclination();
        } catch (Exception e) { declination = 0f; }
        compassOn = true;
        seeded = false;
        lastSent = -1;
        if (registerCompass()) call.resolve(); else call.reject("no compass sensor");
    }

    @PluginMethod
    public void stopCompass(PluginCall call) {
        compassOn = false;
        try { if (sm != null && sl != null) sm.unregisterListener(sl); } catch (Exception e) { }
        call.resolve();
    }

    private void fire(String ev) {
        try {
            final android.webkit.WebView wv = getBridge().getWebView();
            final String js = "window.dispatchEvent(new Event('" + ev + "'))";
            wv.post(new Runnable() { @Override public void run() { wv.evaluateJavascript(js, null); } });
        } catch (Exception e) { }
    }

    @Override protected void handleOnPause() {
        fire("app-paused");
        try { if (compassOn && sm != null && sl != null) sm.unregisterListener(sl); } catch (Exception e) { }
    }
    @Override protected void handleOnResume() {
        fire("app-resumed");
        if (compassOn) registerCompass();
    }
}
