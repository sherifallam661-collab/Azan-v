package com.example.prayerqibla;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.List;

public class PrayerService extends Service {
    static final String CH = "prayer_live";
    static final int NID = 77;
    final Handler h = new Handler(Looper.getMainLooper());
    final List<String> names = new ArrayList<>();
    final List<Long> times = new ArrayList<>();
    final java.util.Map<String, String> hijri = new java.util.HashMap<>();
    String place = "";
    final Runnable tick = new Runnable() {
        @Override public void run() {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.notify(NID, build());
            checkAdhan();
            if (mp != null && isAdhan) {
                try { float v = getSharedPreferences("prayer", 0).getInt("volume", 100) / 100f; mp.setVolume(v, v); } catch (Exception e) { }
            }
            h.postDelayed(this, 1000);
        }
    };

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP_ADHAN".equals(intent.getAction()) && mp != null) { mp.release(); mp = null; }
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CH, "الصلاة القادمة", NotificationManager.IMPORTANCE_LOW));
        }
        Notification n = build();
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(NID, n, 1); // 1 = dataSync
            else startForeground(NID, n);
        } catch (Exception e) { stopSelf(); return START_NOT_STICKY; }
        h.removeCallbacks(tick);
        h.postDelayed(tick, 1000);
        return START_STICKY;
    }

    void load() {
        names.clear(); times.clear(); hijri.clear();
        place = getSharedPreferences("prayer", 0).getString("place", "");
        try {
            JSONArray d = new JSONArray(getSharedPreferences("prayer", 0).getString("days", "[]"));
            for (int i = 0; i < d.length(); i++) { JSONObject o = d.getJSONObject(i); hijri.put(o.getString("key"), o.getString("hijri")); }
        } catch (Exception e) { }
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
        if (iconRes == 0) iconRes = android.R.drawable.ic_lock_idle_alarm;
        Calendar today = Calendar.getInstance();
        String key = String.format(Locale.ENGLISH, "%04d-%02d-%02d", today.get(Calendar.YEAR), today.get(Calendar.MONTH) + 1, today.get(Calendar.DAY_OF_MONTH));
        String line1 = new SimpleDateFormat("EEEE d MMMM yyyy", new Locale("ar")).format(new Date());
        if (hijri.containsKey(key)) line1 += " • " + hijri.get(key);
        if (place != null && place.length() > 0) line1 += " • " + place;
        b.setContentTitle(line1);
        if (mp != null) {
            Intent si = new Intent(this, PrayerService.class).setAction("STOP_ADHAN");
            b.addAction(0, "إيقاف الصوت", PendingIntent.getService(this, 1, si, fl));
        }
        if (idx < 0) {
            b.setSmallIcon(iconRes).setContentText("افتح التطبيق لتحديث المواقيت");
            return b.build();
        }
        long at = times.get(idx), diff = at - now;
        long secs = Math.max(0, diff / 1000);
        String line2 = "الصلاة القادمة: " + names.get(idx) + " • "
                + String.format(Locale.ENGLISH, "%02d:%02d:%02d", secs / 3600, (secs / 60) % 60, secs % 60);
        b.setContentText(line2);
        if (diff <= 15 * 60000L) {
            int mins = (int) Math.ceil(diff / 60000.0);
            b.setSmallIcon(Icon.createWithBitmap(numberIcon(mins)));
        } else {
            b.setSmallIcon(iconRes);
        }
        return b.build();
    }

    MediaPlayer mp;
    boolean isAdhan;

    void checkAdhan() {
        android.content.SharedPreferences sp = getSharedPreferences("prayer", 0);
        String rem = sp.getString("reminder", "chime");
        if (!"none".equals(rem)) {
            long nowR = System.currentTimeMillis(), lastR = sp.getLong("lastReminder", 0);
            for (int i = 0; i < times.size(); i++) {
                long w = times.get(i) - 15 * 60000L;
                if (w <= nowR && nowR - w < 60000 && w > lastR && !"الشروق".equals(names.get(i))) {
                    sp.edit().putLong("lastReminder", w).apply();
                    playReminder(rem);
                    break;
                }
            }
        }
        if (!sp.getBoolean("auto", true)) return;
        long now = System.currentTimeMillis(), last = sp.getLong("lastAdhan", 0);
        for (int i = 0; i < times.size(); i++) {
            long t = times.get(i);
            if (t <= now && now - t < 60000 && t > last && !"الشروق".equals(names.get(i))) {
                sp.edit().putLong("lastAdhan", t).apply();
                playAdhan(sp.getString("adhan", "abdulbasit"));
                break;
            }
        }
    }

    void playReminder(String which) {
        isAdhan = false;
        try {
            if (mp != null) { mp.release(); mp = null; }
            MediaPlayer m = new MediaPlayer();
            m.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            File f = new File(getFilesDir(), "reminder_custom");
            int res = getResources().getIdentifier("reminder_" + which, "raw", getPackageName());
            if ("custom".equals(which) && f.exists()) m.setDataSource(f.getAbsolutePath());
            else if (res != 0) { AssetFileDescriptor afd = getResources().openRawResourceFd(res); m.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength()); afd.close(); }
            else m.setDataSource(this, RingtoneManager.getDefaultUri("alarm".equals(which) ? RingtoneManager.TYPE_ALARM : RingtoneManager.TYPE_NOTIFICATION));
            m.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override public void onCompletion(MediaPlayer x) { x.release(); if (mp == x) mp = null; }
            });
            m.prepare();
            m.start();
            mp = m;
        } catch (Exception e) { }
    }

    void playAdhan(String which) {
        isAdhan = true;
        try {
            if (mp != null) { mp.release(); mp = null; }
            MediaPlayer m = new MediaPlayer();
            m.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            File f = new File(getFilesDir(), "adhan_custom");
            int res = getResources().getIdentifier("adhan_" + which, "raw", getPackageName());
            if ("custom".equals(which) && f.exists()) m.setDataSource(f.getAbsolutePath());
            else if (res != 0) { AssetFileDescriptor afd = getResources().openRawResourceFd(res); m.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength()); afd.close(); }
            else m.setDataSource(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
            m.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override public void onCompletion(MediaPlayer x) { x.release(); if (mp == x) mp = null; }
            });
            float vv = getSharedPreferences("prayer", 0).getInt("volume", 100) / 100f;
            m.setVolume(vv, vv);
            m.prepare();
            m.start();
            mp = m;
        } catch (Exception e) { }
    }

    Bitmap numberIcon(int n) {
        Bitmap bmp = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setColor(Color.WHITE);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(8);
        cv.drawCircle(48, 48, 41, ring);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextSize(n >= 10 ? 44 : 58);
        float y = 48 - (p.descent() + p.ascent()) / 2;
        cv.drawText(String.valueOf(n), 48, y, p);
        return bmp;
    }

    @Override public void onDestroy() { h.removeCallbacks(tick); if (mp != null) { mp.release(); mp = null; } super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
