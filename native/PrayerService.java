package com.example.prayerqibla;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class PrayerService extends Service {
    static final String CH = "prayer_live";
    static final int NID = 77;
    final Handler h = new Handler(Looper.getMainLooper());
    final List<String> names = new ArrayList<>();
    final List<Long> times = new ArrayList<>();
    final Runnable tick = new Runnable() {
        @Override public void run() {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.notify(NID, build());
            h.postDelayed(this, 20000);
        }
    };

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CH, "الصلاة القادمة", NotificationManager.IMPORTANCE_LOW));
        }
        Notification n = build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(NID, n, 1); // 1 = dataSync
        else startForeground(NID, n);
        h.removeCallbacks(tick);
        h.postDelayed(tick, 20000);
        return START_STICKY;
    }

    void load() {
        names.clear(); times.clear();
        try {
            JSONArray a = new JSONArray(getSharedPreferences("prayer", 0).getString("items", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                names.add(o.getString("name"));
                times.add(o.getLong("at"));
            }
        } catch (Exception e) { }
    }

    Notification build() {
        load();
        long now = System.currentTimeMillis();
        int idx = -1;
        for (int i = 0; i < times.size(); i++) if (times.get(i) > now) { idx = i; break; }
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CH) : new Notification.Builder(this);
        Intent li = getPackageManager().getLaunchIntentForPackage(getPackageName());
        int fl = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
        b.setContentIntent(PendingIntent.getActivity(this, 0, li, fl)).setOngoing(true).setOnlyAlertOnce(true);
        int iconRes = getResources().getIdentifier("ic_stat_icon", "drawable", getPackageName());
        if (idx < 0) {
            b.setSmallIcon(iconRes).setContentTitle("افتح التطبيق لتحديث المواقيت");
            return b.build();
        }
        long at = times.get(idx), diff = at - now;
        String name = names.get(idx);
        if (diff <= 15 * 60000L) {
            int mins = (int) Math.ceil(diff / 60000.0);
            b.setSmallIcon(Icon.createWithBitmap(numberIcon(mins))).setContentTitle("اقتربت صلاة " + name);
        } else {
            b.setSmallIcon(iconRes).setContentTitle("الصلاة القادمة: " + name);
        }
        Calendar c = Calendar.getInstance(); c.setTimeInMillis(at);
        int hr = c.get(Calendar.HOUR) == 0 ? 12 : c.get(Calendar.HOUR);
        String t = hr + ":" + String.format("%02d", c.get(Calendar.MINUTE)) + (c.get(Calendar.AM_PM) == Calendar.AM ? " ص" : " م");
        b.setContentText("الموعد " + t);
        if (Build.VERSION.SDK_INT >= 24) {
            b.setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true).setWhen(at);
        }
        return b.build();
    }

    Bitmap numberIcon(int n) {
        Bitmap bmp = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextSize(n >= 10 ? 62 : 80);
        float y = 48 - (p.descent() + p.ascent()) / 2;
        cv.drawText(String.valueOf(n), 48, y, p);
        return bmp;
    }

    @Override public void onDestroy() { h.removeCallbacks(tick); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
