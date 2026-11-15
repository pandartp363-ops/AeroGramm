package org.telegram.messenger;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.text.InputType;
import android.widget.EditText;

import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.text.DateFormat;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Ядро AeroGram: связь с сервером, кеш галочек, блокировка мода по устройству. */
public class AeroGram {
    public static final String SERVER = "https://YOUR-SERVER.example.com"; // <-- свой адрес
    private static final SharedPreferences prefs =
            ApplicationLoader.applicationContext.getSharedPreferences("aerogram", Context.MODE_PRIVATE);

    // состояние доступа: "ok" | "frozen" | "banned"
    public static volatile String state = "ok";
    public static volatile String reason = "";
    public static volatile long untilTs = 0;        // unix-секунды, 0 = навсегда
    public static volatile boolean canAppeal = false;
    public static volatile String appealStatus = "none";
    public static volatile String myBadge = "";
    public static volatile long stars = 0;          // -1 = бесконечные
    public static volatile long myId = 0;
    public static volatile boolean isOwner = false;
    public static volatile boolean premium = false;
    public static volatile long gram = 0;
    public static volatile int level = 1;
    public static volatile String anonNumber = "";
    public static final String BOT_LINK = "@YourAeroBot"; // <-- username твоего бота

    private static final ConcurrentHashMap<Long, String> badgeCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Long> requested = new ConcurrentHashMap<>();

    public static boolean blocked() { return !"ok".equals(state); }
    public static String token() { return prefs.getString("token", null); }

    /** ID устройства: хеш ANDROID_ID (переживает переустановку и смену аккаунтов; сбрасывается при wipe). */
    public static String deviceId() {
        String id = prefs.getString("dev", null);
        if (id != null) return id;
        String aid = Settings.Secure.getString(ApplicationLoader.applicationContext.getContentResolver(),
                Settings.Secure.ANDROID_ID);
        if (aid == null || aid.isEmpty() || aid.equals("9774d56d682e549c")) aid = UUID.randomUUID().toString();
        id = sha256(aid + "|aerogram");
        prefs.edit().putString("dev", id).apply();
        return id;
    }

    private static String sha256(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return UUID.randomUUID().toString(); }
    }

    public interface Callback { void done(JSONObject json, String error); }

    public static void request(String method, String path, String body, Callback cb) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(SERVER + path).openConnection();
                c.setRequestMethod(method);
                c.setConnectTimeout(8000); c.setReadTimeout(8000);
                c.setRequestProperty("X-Device", deviceId());
                if (token() != null) c.setRequestProperty("X-Token", token());
                if (body != null) {
                    c.setDoOutput(true);
                    c.setRequestProperty("Content-Type", "application/json");
                    c.getOutputStream().write(body.getBytes("UTF-8"));
                }
                int code = c.getResponseCode();
                InputStream is = code < 400 ? c.getInputStream() : c.getErrorStream();
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[4096]; int n;
                while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
                String s = bo.toString("UTF-8");
                if (code >= 400) { AndroidUtilities.runOnUIThread(() -> cb.done(null, s)); return; }
                JSONObject j = new JSONObject(s.startsWith("[") ? "{\"list\":" + s + "}" : s);
                AndroidUtilities.runOnUIThread(() -> cb.done(j, null));
            } catch (Exception e) {
                AndroidUtilities.runOnUIThread(() -> cb.done(null, e.toString()));
            }
        });
    }

    private static void applyState(JSONObject j) {
        state = j.optString("state", "ok");
        reason = j.optString("reason", "");
        untilTs = j.optLong("until", 0);
        canAppeal = j.optBoolean("can_appeal", false);
        appealStatus = j.optString("appeal_status", "none");
    }

    /** Привязка по коду из бота. Если устройство забанено, привязка не пройдёт ни для какого аккаунта. */
    public static void link(String code, Activity act, Callback cb) {
        request("POST", "/link", "{\"code\":\"" + code + "\"}", (j, err) -> {
            if (j != null) {
                prefs.edit().putString("token", j.optString("token")).apply();
            } else if (err != null && err.contains("\"detail\"")) {
                try { applyState(new JSONObject(err).getJSONObject("detail")); showBlockedDialog(act); } catch (Exception ignored) {}
            }
            cb.done(j, err);
        });
    }

    public static void applyStatus(JSONObject j) {
        applyState(j);
        myBadge = j.optString("badge");
        stars = j.optLong("stars");
        myId = j.optLong("tg_id");
        isOwner = j.optBoolean("is_owner");
        premium = j.optBoolean("premium");
        gram = j.optLong("gram");
        level = Math.max(1, j.optInt("level", 1));
        anonNumber = j.optString("anon_number", "");
    }

    public static String badgeName(String b) {
        switch (b == null ? "" : b) {
            case "owner": return "Владелец AeroGram";
            case "admin": return "Администратор AeroGram";
            case "channel": return "Официальный канал AeroGram";
            
            default: return "";
        }
    }

    /** Вызывать в LaunchActivity.onCreate и onResume. Работает и до привязки (проверка устройства). */
    public static void refreshMe(Activity act) {
        request("GET", "/status", null, (j, err) -> {
            if (j == null) return;
            applyStatus(j);
            if (blocked()) showBlockedDialog(act);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.updateInterfaces, 0);
        });
    }

    /** Полноэкранная блокировка: закрыть нельзя, только обжаловать (заморозка) или выйти из приложения. */
    public static void showBlockedDialog(Activity act) {
        if (act == null || act.isFinishing() || !blocked()) return;
        boolean ban = "banned".equals(state);
        StringBuilder msg = new StringBuilder("Причина: ").append(reason.isEmpty() ? "не указана" : reason);
        if (!ban && untilTs > 0)
            msg.append("\nЗаморозка до: ").append(DateFormat.getDateTimeInstance().format(new Date(untilTs * 1000)));
        if (ban) msg.append("\n\nДоступ закрыт на этом устройстве для всех аккаунтов. Аккаунт Telegram не затронут.");
        if ("pending".equals(appealStatus)) msg.append("\n\nАпелляция отправлена и рассматривается.");
        if ("rejected".equals(appealStatus)) msg.append("\n\nАпелляция отклонена. Заморозка снимется сама по окончании срока.");

        AlertDialog.Builder b = new AlertDialog.Builder(act)
                .setTitle(ban ? "Доступ к AeroGram заблокирован" : "Доступ к AeroGram заморожен")
                .setMessage(msg.toString())
                .setCancelable(false)
                .setNegativeButton("Закрыть приложение", (d, w) -> act.finishAffinity());
        if (canAppeal) b.setPositiveButton("Обжаловать", (d, w) -> showAppealDialog(act));
        b.show();
    }

    private static void showAppealDialog(Activity act) {
        EditText et = new EditText(act);
        et.setHint("Почему заморозку стоит снять?");
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        new AlertDialog.Builder(act).setTitle("Апелляция").setView(et).setCancelable(false)
                .setPositiveButton("Отправить", (d, w) ->
                        request("POST", "/appeal", "{\"text\":" + JSONObject.quote(et.getText().toString()) + "}",
                                (j, err) -> refreshMe(act)))
                .setNegativeButton("Назад", (d, w) -> showBlockedDialog(act))
                .show();
    }

    /** Галочка пользователя ("" если нет). У ограниченных и при своей блокировке не показывается. */
    public static String badgeFor(long userId) {
        if (blocked() || token() == null) return "";
        String b = badgeCache.get(userId);
        if (b != null) return b;
        Long t = requested.get(userId);
        if (t == null || System.currentTimeMillis() - t > 60_000) {
            requested.put(userId, System.currentTimeMillis());
            request("GET", "/badges?ids=" + userId, null, (j, err) -> {
                if (j == null) return;
                JSONObject o = j.optJSONObject(String.valueOf(userId));
                badgeCache.put(userId, o == null ? "" : o.optString("badge"));
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.updateInterfaces, 0);
            });
        }
        return "";
    }

    /** Точные графические значки из aerogram-assets/badges. */
    public static class BadgeDrawable extends Drawable {
        private final Drawable image;
        private final int size;

        public BadgeDrawable(String type, int sizePx) {
            this.size = sizePx;
            String name;
            switch (type == null ? "" : type) {
                case "owner": name = "badge_owner"; break;
                case "channel": name = "badge_channel"; break;
                case "admin": name = "badge_admin"; break;
                default: name = "badge_admin"; break;
            }
            Context context = ApplicationLoader.applicationContext;
            int id = context.getResources().getIdentifier(name, "drawable", context.getPackageName());
            if (id != 0) {
                image = new android.graphics.drawable.BitmapDrawable(
                        context.getResources(), android.graphics.BitmapFactory.decodeResource(context.getResources(), id));
            } else {
                image = new android.graphics.drawable.ColorDrawable(0x00000000);
            }
            image.setBounds(0, 0, sizePx, sizePx);
        }

        @Override public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            image.setBounds(bounds);
            image.draw(canvas);
        }
        @Override public int getIntrinsicWidth() { return size; }
        @Override public int getIntrinsicHeight() { return size; }
        @Override public void setAlpha(int alpha) { image.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter filter) { image.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

}
