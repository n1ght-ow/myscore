package cn.myscore.app;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Single post-match notification for subscribed matches only. Never polls or stays resident. */
final class ResultNotify {
    static final String CHANNEL = "match-results";
    private static final String STORE = "result-notifications";
    private static final String PREFIX = "sent:";
    private static final int MAX_RECORDS = 128;
    private static final Object LOCK = new Object();

    private ResultNotify() {}

    static boolean permitted(Context context) {
        if (context == null) return false;
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null || !nm.areNotificationsEnabled()) return false;
        if (Build.VERSION.SDK_INT >= 33) {
            return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    static void requestIfNeeded(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || !activity.hasWindowFocus()) return;
        ensureChannel(activity);
        if (Build.VERSION.SDK_INT < 33 || ActivityManager.isUserAMonkey()) return;
        if (activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return;
        SharedPreferences prefs = activity.getSharedPreferences(STORE, Context.MODE_PRIVATE);
        if (prefs.getBoolean("asked_permission", false)) return;
        prefs.edit().putBoolean("asked_permission", true).apply();
        activity.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 43);
    }

    private static NotificationManager ensureChannel(Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return null;
        NotificationChannel channel = new NotificationChannel(CHANNEL, "比赛结果", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("已订阅比赛结束时推送最终比分与赛果");
        nm.createNotificationChannel(channel);
        return nm;
    }

    static String calendarTitle(Competition c, Match m) {
        String prefix = (c == null ? m.group : c.name) + " · ";
        if (m != null && m.finalised()) {
            if (m.hasScore() && !m.home.isBlank() && !m.away.isBlank()) {
                return prefix + m.home + " " + m.displayScore(true) + " - " + m.displayScore(false) + " " + m.away;
            }
            for (String r : m.results) {
                if (r != null && !r.isBlank()) return prefix + m.displayTitle() + " · " + r.trim();
            }
        }
        return prefix + (m == null ? "" : m.displayTitle());
    }

    static String title(Match m) {
        String label = MatchLabel.of(m);
        return (label.isBlank() ? m.group : label) + " · 比赛已结束";
    }

    static String body(Match m) {
        List<String> extras = new ArrayList<>();
        for (String r : m.results) {
            if (r != null && !r.isBlank()) extras.add(r.trim());
            if (extras.size() >= 5) break;
        }
        if (!m.home.isBlank() && !m.away.isBlank()) {
            String main = m.hasScore()
                ? m.home + " " + m.displayScore(true) + " : " + m.displayScore(false) + " " + m.away
                : m.displayTitle();
            return extras.isEmpty() ? main : main + "（" + String.join(" · ", extras) + "）";
        }
        if (!extras.isEmpty()) {
            return m.displayTitle() + " · " + String.join(" / ", extras.subList(0, Math.min(3, extras.size())));
        }
        String status = !m.detail.isBlank() ? m.detail : m.status;
        return status.isBlank() ? m.displayTitle() : m.displayTitle() + " · " + status;
    }

    static boolean notifyFinished(Context context, Match m) {
        if (context == null || m == null || m.id.isBlank() || !m.finalised()) return false;
        if ("TEST ONLY".equals(m.source)) return false;
        Context app = context.getApplicationContext();
        if (app == null) return false;
        NotificationManager nm = ensureChannel(app);
        if (nm == null || !permitted(app)) return false;
        synchronized (LOCK) {
            SharedPreferences prefs = app.getSharedPreferences(STORE, Context.MODE_PRIVATE);
            String key = PREFIX + m.id;
            if (prefs.contains(key)) return false;
            SharedPreferences.Editor editor = prefs.edit().putLong(key, System.currentTimeMillis());
            List<Map.Entry<String, Long>> history = new ArrayList<>();
            for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
                if (entry.getKey().startsWith(PREFIX) && entry.getValue() instanceof Long ts) {
                    history.add(Map.entry(entry.getKey(), ts));
                }
            }
            if (history.size() >= MAX_RECORDS) {
                history.sort(Comparator.comparingLong(Map.Entry::getValue));
                int removeCount = history.size() - MAX_RECORDS + 1;
                for (int i = 0; i < removeCount; i++) editor.remove(history.get(i).getKey());
            }
            editor.apply();
        }
        try {
            int id = 25000 + (m.id.hashCode() & 0x7fff);
            Intent intent = new Intent(app, DetailActivity.class)
                .putExtra("match", m.json().toString())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent open = PendingIntent.getActivity(
                app, id, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            String content = body(m);
            Notification notification = new Notification.Builder(app, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_score)
                .setContentTitle(title(m))
                .setContentText(content)
                .setStyle(new Notification.BigTextStyle().bigText(content))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_STATUS)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .build();
            nm.notify(id, notification);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
