package cn.edu.hpu.powerwidget;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.text.DecimalFormat;
import java.util.List;

final class LowBatteryNotifier {
    private static final String CHANNEL_ID = "low_battery";
    private static final int NOTIFICATION_ID = 1001;

    private LowBatteryNotifier() {}

    static void check(Context context) {
        if (!LowBatteryStore.enabled(context)) return;
        double lightingThreshold = LowBatteryStore.lightingThreshold(context);
        double airThreshold = LowBatteryStore.airThreshold(context);
        boolean wantLighting = LowBatteryStore.lighting(context);
        boolean wantAir = LowBatteryStore.air(context);
        List<String> ids = LowBatteryStore.roomIds(context);
        if (ids.isEmpty()) return;
        StringBuilder lines = new StringBuilder();
        for (String id : ids) {
            Room room = null;
            for (Room r : RoomStore.rooms(context)) { if (r.id.equals(id)) { room = r; break; } }
            if (room == null) continue;
            PowerCache.Snapshot snap = PowerCache.read(context, room);
            if (snap.data == null) continue;
            UsageResult data = snap.data;
            StringBuilder roomLines = new StringBuilder();
            if (wantLighting && data.lightingKwh < lightingThreshold) roomLines.append("照明 ").append(trim(data.lightingKwh)).append(" 度");
            if (wantAir && data.airKwh < airThreshold) {
                if (roomLines.length() > 0) roomLines.append(" · ");
                roomLines.append("空调 ").append(trim(data.airKwh)).append(" 度");
            }
            if (roomLines.length() > 0) {
                if (lines.length() > 0) lines.append("\n");
                lines.append(room.displayName()).append("：").append(roomLines);
            }
        }
        if (lines.length() == 0) return;
        post(context, "部分寝室电量低于阈值", lines.toString());
    }

    private static void post(Context context, String title, String text) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "低电量提醒", NotificationManager.IMPORTANCE_DEFAULT);
            manager.createNotificationChannel(channel);
        }
        Intent intent = new Intent(context, SettingsActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);
        b.setSmallIcon(R.drawable.ic_launcher);
        b.setContentTitle(title);
        b.setContentText(text);
        b.setStyle(new Notification.BigTextStyle().bigText(text));
        b.setContentIntent(pi);
        b.setAutoCancel(true);
        try { manager.notify(NOTIFICATION_ID, b.build()); } catch (SecurityException ignored) {}
    }

    private static String trim(double value) { return new DecimalFormat("0.##").format(value); }
}
