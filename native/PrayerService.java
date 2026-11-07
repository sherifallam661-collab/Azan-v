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
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.AudioManager;
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
        if (intent != null && "STOP_ADHAN".equals(intent.getAction())) stopSound();
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
        if (intent != null && "PLAY_ADHAN".equals(intent.getAction())) {
            if (intent.hasExtra("volume")) getSharedPreferences("prayer", 0).edit().putInt("volume", intent.getIntExtra("volume", 100)).apply();
            String w = intent.getStringExtra("adhan");
            playAdhan(w == null ? "abdulbasit" : w, intent.getStringExtra("name"), false);
        }
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
    static volatile boolean adhanPlaying = false;
    static volatile String adhanName = "";
    int prevAlarmVol = -1;

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
                playAdhan(sp.getString("adhan", "abdulbasit"), names.get(i), true);
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
                @Override public void onCompletion(MediaPlayer x) { if (mp == x) stopSound(); else x.release(); }
            });
            m.prepare();
            m.start();
            mp = m;
        } catch (Exception e) { }
    }

    void playAdhan(String which, String name, boolean full) {
        isAdhan = true;
        try {
            stopSound();
            MediaPlayer m = new MediaPlayer();
            m.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            File f = new File(getFilesDir(), "adhan_custom");
            int res = getResources().getIdentifier("adhan_" + which, "raw", getPackageName());
            if ("custom".equals(which) && f.exists()) m.setDataSource(f.getAbsolutePath());
            else if (res != 0) { AssetFileDescriptor afd = getResources().openRawResourceFd(res); m.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength()); afd.close(); }
            else m.setDataSource(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
            m.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override public void onCompletion(MediaPlayer x) { if (mp == x) stopSound(); else x.release(); }
            });
            float vv = getSharedPreferences("prayer", 0).getInt("volume", 100) / 100f;
            m.setVolume(vv, vv);
            maxAlarm();
            m.prepare();
            m.start();
            mp = m;
            adhanName = name == null ? "" : name;
            adhanPlaying = true;
            if (full) { postAdhanNotification(adhanName); launchOverApps(); }
        } catch (Exception e) { }
    }

    Bitmap numberIcon(int n) {
        Bitmap bmp = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setColor(Color.WHITE);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(9);
        cv.drawCircle(48, 48, 40, ring);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        p.setFakeBoldText(true);
        String txt = String.valueOf(n);
        p.setTextSize(n >= 10 ? 50 : 64);
        Rect r = new Rect();
        p.getTextBounds(txt, 0, txt.length(), r);
        cv.drawText(txt, 48f, 48f - r.exactCenterY(), p);
        return bmp;
    }

    // يفتح شاشة الأذان فوق التطبيقات الأخرى (يتطلب تفعيل "الظهور فوق التطبيقات الأخرى")
    void launchOverApps() {
        try {
            if (Build.VERSION.SDK_INT >= 23 && !android.provider.Settings.canDrawOverlays(this)) return;
            Intent i = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                .putExtra("adhan", true);
            startActivity(i);
        } catch (Exception e) { }
    }

    void stopSound() {
        if (mp != null) { try { mp.release(); } catch (Exception e) { } mp = null; }
        adhanPlaying = false;
        try {
            if (prevAlarmVol >= 0) ((AudioManager) getSystemService(AUDIO_SERVICE)).setStreamVolume(AudioManager.STREAM_ALARM, prevAlarmVol, 0);
        } catch (Exception e) { }
        prevAlarmVol = -1;
        try { ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(78); } catch (Exception e) { }
    }

    // يرفع قناة المنبه للحد الأقصى ليصبح مستوى الأذان هو المستوى المختار داخل التطبيق فقط
    void maxAlarm() {
        try {
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (prevAlarmVol < 0) prevAlarmVol = am.getStreamVolume(AudioManager.STREAM_ALARM);
            am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);
        } catch (Exception e) { }
    }

    void postAdhanNotification(String name) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel("prayer_adhan", "شاشة الأذان", NotificationManager.IMPORTANCE_HIGH);
                ch.setSound(null, null);
                nm.createNotificationChannel(ch);
            }
            Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("adhan", true);
            int fl = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
            PendingIntent pi = PendingIntent.getActivity(this, 2, open, fl);
            Intent si = new Intent(this, PrayerService.class).setAction("STOP_ADHAN");
            int icon = getResources().getIdentifier("ic_stat_icon", "drawable", getPackageName());
            if (icon == 0) icon = android.R.drawable.ic_lock_idle_alarm;
            Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, "prayer_adhan") : new Notification.Builder(this);
            b.setSmallIcon(icon).setContentTitle("حان الآن وقت صلاة " + name).setContentText("حيّ على الصلاة")
                .setContentIntent(pi).setFullScreenIntent(pi, true).setAutoCancel(true)
                .setCategory(Notification.CATEGORY_ALARM)
                .addAction(0, "إيقاف الأذان", PendingIntent.getService(this, 3, si, fl));
            nm.notify(78, b.build());
        } catch (Exception e) { }
    }

    @Override public void onDestroy() { h.removeCallbacks(tick); stopSound(); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
