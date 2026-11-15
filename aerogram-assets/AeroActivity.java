package org.telegram.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.*;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AeroGram;
import org.telegram.messenger.R;

import java.text.DateFormat;
import java.util.Date;

/** Экран AeroGram: привязка аккаунта, звёзды, галочки, магазин подарков. Всё рисуется кодом, XML не нужен. */
public class AeroActivity extends Activity {
    static final int BG = 0xFF0E0E13, CARD = 0xFF1A1A22, ACCENT = 0xFFF50110, TXT = 0xFFF2F2F5, SUB = 0xFF9A9AA8;

    private LinearLayout root;
    private JSONArray giftsData = new JSONArray();
    private JSONArray receivedData = new JSONArray();
    private JSONArray nftData = new JSONArray();
    private JSONArray usernamesData = new JSONArray();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(24), dp(16), dp(32));
        sv.addView(root);
        setContentView(sv);
        render();
        reload();
    }

    @Override protected void onResume() { super.onResume(); reload(); }

    // ---------- данные ----------
    private void reload() {
        AeroGram.request("GET", "/status", null, (j, err) -> {
            if (j == null) { toast("Нет связи с сервером AeroGram"); return; }
            AeroGram.applyStatus(j);
            render();
            if (AeroGram.blocked()) { AeroGram.showBlockedDialog(this); return; }
            if (AeroGram.token() == null) return;
            AeroGram.request("GET", "/gifts", null, (g, e2) -> {
                if (g != null) { giftsData = g.optJSONArray("list"); render(); }
            });
            AeroGram.request("GET", "/nft/of/" + AeroGram.myId, null, (r, e4) -> {
                if (r != null) { nftData = r.optJSONArray("list"); render(); }
            });
            AeroGram.request("GET", "/usernames/of/" + AeroGram.myId, null, (r, e5) -> {
                if (r != null) { usernamesData = r.optJSONArray("list"); render(); }
            });
            AeroGram.request("GET", "/gifts/received/" + AeroGram.myId, null, (r, e3) -> {
                if (r != null) { receivedData = r.optJSONArray("list"); render(); }
            });
        });
    }

    // ---------- интерфейс ----------
    private void render() {
        root.removeAllViews();
        header();
        if (AeroGram.token() == null) linkCard(); else { accountCard(); aerogramMenuCard(); }
        badgesCard();
        if (AeroGram.token() != null && !AeroGram.blocked()) { shopCard(); collectionCard(); receivedCard(); }
    }

    private void header() {
        LinearLayout h = row();
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.aero_logo);
        logo.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(18)); }
        });
        logo.setClipToOutline(true);
        h.addView(logo, new LinearLayout.LayoutParams(dp(68), dp(68)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(14), 0, 0, 0);
        col.addView(text("AeroGram", 28, TXT, true));
        String st = AeroGram.blocked() ? ("banned".equals(AeroGram.state) ? "Доступ заблокирован" : "Доступ заморожен")
                : AeroGram.token() == null ? "Аккаунт не привязан" : "Подключено · ID " + AeroGram.myId;
        col.addView(text(st, 14, AeroGram.blocked() ? ACCENT : SUB, false));
        h.addView(col);
        h.setPadding(0, 0, 0, dp(16));
        root.addView(h);
    }

    private void linkCard() {
        LinearLayout c = card();
        c.addView(text("Привязка аккаунта", 18, TXT, true));
        c.addView(text("1. Откройте бота " + AeroGram.BOT_LINK + "\n2. Нажмите /start и получите 6-значный код\n3. Введите код ниже",
                14, SUB, false), mt(8));
        EditText et = new EditText(this);
        et.setHint("Код из бота");
        et.setHintTextColor(SUB); et.setTextColor(TXT);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setBackground(rounded(0xFF26262F, 12));
        et.setPadding(dp(14), dp(12), dp(14), dp(12));
        c.addView(et, mt(12));
        c.addView(button("Привязать", v -> {
            String code = et.getText().toString().trim();
            if (code.length() != 6) { toast("Введите 6 цифр"); return; }
            AeroGram.link(code, this, (j, err) -> {
                if (j != null) { toast("Готово!"); reload(); }
                else if (!AeroGram.blocked()) toast("Неверный или просроченный код");
            });
        }), mt(12));
        root.addView(c, mb());
    }

    private void accountCard() {
        LinearLayout c = card();
        c.addView(text("Мой аккаунт", 18, TXT, true));
        String bal = AeroGram.stars < 0 ? "⭐ ∞" : "⭐ " + AeroGram.stars;
        c.addView(text(bal, 34, TXT, true), mt(6));
        c.addView(text("GRAM: " + AeroGram.gram + "   ·   Уровень " + AeroGram.level
                + (AeroGram.premium ? "   ·   ✦ Premium" : ""), 14, SUB, false), mt(4));
        if (!AeroGram.anonNumber.isEmpty())
            c.addView(text("Анонимный номер: " + AeroGram.anonNumber, 14, SUB, false), mt(4));
        if (!AeroGram.myBadge.isEmpty()) {
            LinearLayout r = row();
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(new AeroGram.BadgeDrawable(AeroGram.myBadge, dp(26)));
            r.addView(iv, new LinearLayout.LayoutParams(dp(26), dp(26)));
            TextView t = text(AeroGram.badgeName(AeroGram.myBadge), 15, TXT, false);
            t.setPadding(dp(8), 0, 0, 0);
            r.addView(t);
            c.addView(r, mt(8));
        }
        c.addView(button("Получить +100 ⭐ AeroGram · без лимита", v ->
                AeroGram.request("POST", "/aerogram/claim/stars", "{}", (j, err) -> {
                    if (j != null) { toast("+100 локальных ⭐"); reload(); }
                    else toast(errorText(err));
                })), mt(12));
        c.addView(button("Aerogram · подарить звёзды бесплатно", v -> showStarTransfer()), mt(8));
        if (AeroGram.isOwner) {
            c.addView(button("Меню выдачи (владелец)", v ->
                    startActivity(new android.content.Intent(this, AeroAdminActivity.class))), mt(8));
        }
        root.addView(c, mb());
    }


    // ---------- пользовательские возможности AeroGram ----------
    private void aerogramMenuCard() {
        LinearLayout c = card();
        c.addView(text("Возможности AeroGram", 18, TXT, true));
        c.addView(text("Локальные награды сообщества. Не являются активами Telegram.", 13, SUB, false), mt(4));
        c.addView(button("🎁 Забрать бесплатный подарок", v -> showFreeGift()), mt(10));
        c.addView(button("💎 Создать бесплатный NFT", v -> showFreeNft()), mt(8));
        c.addView(button("🔢 Получить анонимный номер", v -> claim("/aerogram/claim/number", "{}", "Ваш номер выдан")), mt(8));
        c.addView(button("@ Получить коллекционный юзернейм", v -> showFreeUsername()), mt(8));
        root.addView(c, mb());
    }

    private void showStarTransfer() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        EditText to = new EditText(this); to.setHint("ID получателя AeroGram");
        to.setInputType(InputType.TYPE_CLASS_NUMBER); form.addView(to);
        EditText amount = new EditText(this); amount.setHint("Количество локальных звёзд");
        amount.setInputType(InputType.TYPE_CLASS_NUMBER); form.addView(amount);
        new AlertDialog.Builder(this).setTitle("Aerogram · перевод звёзд")
                .setMessage("Перевод бесплатный, комиссия 0%. Используются только звёзды AeroGram.")
                .setView(form).setPositiveButton("Подарить", (d, w) -> {
                    try {
                        long uid = Long.parseLong(to.getText().toString().trim());
                        int n = Integer.parseInt(amount.getText().toString().trim());
                        AeroGram.request("POST", "/aerogram/send-stars",
                                "{\"to_id\":" + uid + ",\"amount\":" + n + "}",
                                (j, err) -> { if (j != null) { toast(j.optString("info", "Звёзды отправлены")); reload(); }
                                    else toast(errorText(err)); });
                    } catch (Exception e) { toast("Проверьте ID и количество"); }
                }).setNegativeButton("Отмена", null).show();
    }

    private void showFreeGift() {
        if (giftsData.length() == 0) { toast("Каталог подарков ещё загружается"); return; }
        String[] labels = new String[giftsData.length()];
        for (int i = 0; i < labels.length; i++) {
            JSONObject g = giftsData.optJSONObject(i);
            labels[i] = g.optString("emoji") + " " + g.optString("name");
        }
        new AlertDialog.Builder(this).setTitle("Бесплатный подарок AeroGram")
                .setItems(labels, (d, which) -> {
                    JSONObject g = giftsData.optJSONObject(which);
                    claim("/aerogram/claim/gift", "{\"gift_id\":" + g.optInt("id") + "}", "Подарок добавлен");
                }).setNegativeButton("Отмена", null).show();
    }

    private void showFreeNft() {
        LinearLayout form = new LinearLayout(this);
        form.setPadding(dp(16), dp(8), dp(16), 0);
        form.setOrientation(LinearLayout.VERTICAL);
        EditText name = new EditText(this); name.setHint("Название коллекции"); name.setText("AeroGram");
        form.addView(name);
        EditText emoji = new EditText(this); emoji.setHint("Эмодзи NFT"); emoji.setText("🎁");
        form.addView(emoji);
        new AlertDialog.Builder(this).setTitle("Создать NFT AeroGram")
                .setMessage("Бесплатное создание без лимита. NFT отображается только в AeroGram.")
                .setView(form).setPositiveButton("Создать", (d, w) -> {
                    String body = "{\"collection\":" + JSONObject.quote(name.getText().toString())
                            + ",\"emoji\":" + JSONObject.quote(emoji.getText().toString()) + "}";
                    claim("/aerogram/claim/nft", body, "NFT добавлен в коллекцию");
                }).setNegativeButton("Отмена", null).show();
    }

    private void showFreeUsername() {
        EditText name = new EditText(this); name.setHint("например, aerofan_01");
        new AlertDialog.Builder(this).setTitle("Коллекционный юзернейм")
                .setMessage("Бесплатно, без временных ограничений. Формат: a-z, 0-9, _.")
                .setView(name).setPositiveButton("Зарегистрировать", (d, w) -> {
                    String body = "{\"name\":" + JSONObject.quote(name.getText().toString()) + "}";
                    claim("/aerogram/claim/username", body, "Юзернейм добавлен");
                }).setNegativeButton("Отмена", null).show();
    }

    private void claim(String path, String body, String success) {
        AeroGram.request("POST", path, body, (j, err) -> {
            if (j != null) { toast(j.optString("info", success)); reload(); }
            else toast(errorText(err));
        });
    }

    private String errorText(String err) {
        try { return new JSONObject(err).optString("detail", err); }
        catch (Exception e) { return err == null ? "Ошибка сервера" : err; }
    }

    private void collectionCard() {
        if (nftData.length() == 0 && usernamesData.length() == 0) return;
        LinearLayout c = card();
        c.addView(text("Коллекция", 18, TXT, true));
        for (int i = 0; i < usernamesData.length(); i++) {
            JSONObject u = usernamesData.optJSONObject(i);
            if (u == null) continue;
            c.addView(text("@" + u.optString("name") + "  ·  " + AeroNft.label(u.optString("rarity")), 14, TXT, false), mt(8));
        }
        LinearLayout rowL = null;
        for (int i = 0; i < nftData.length(); i++) {
            JSONObject o = nftData.optJSONObject(i);
            if (o == null) continue;
            if (i % 2 == 0) { rowL = row(); c.addView(rowL, mt(10)); }
            View card = AeroNft.build(this, o.optString("emoji"), o.optString("collection"), o.optInt("serial"),
                    o.optString("rarity"), o.optString("c1"), o.optString("c2"), o.optString("backdrop"));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(4), 0, dp(4), 0);
            rowL.addView(card, lp);
        }
        root.addView(c, mb());
    }

    private void badgesCard() {
        LinearLayout c = card();
        c.addView(text("Галочки AeroGram", 18, TXT, true));
        c.addView(text("Видны только пользователям AeroGram. Выдаёт владелец.", 13, SUB, false), mt(4));
        LinearLayout r = row();
        r.setGravity(Gravity.CENTER);
        String[][] items = {{"owner", "Владелец"}, {"admin", "Администратор"}, {"channel", "Офиц. канал"}};
        for (String[] it : items) {
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER);
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(new AeroGram.BadgeDrawable(it[0], dp(52)));
            col.addView(iv, new LinearLayout.LayoutParams(dp(52), dp(52)));
            TextView t = text(it[1], 12, SUB, false);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(6), 0, 0);
            col.addView(t);
            r.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        c.addView(r, mt(14));
        root.addView(c, mb());
    }

    private void shopCard() {
        LinearLayout c = card();
        c.addView(text("Магазин подарков", 18, TXT, true));
        c.addView(text("За звёзды AeroGram. Получатель увидит подарок в приложении.", 13, SUB, false), mt(4));
        LinearLayout rowL = null;
        for (int i = 0; i < giftsData.length(); i++) {
            JSONObject g = giftsData.optJSONObject(i);
            if (g == null) continue;
            if (i % 2 == 0) { rowL = row(); c.addView(rowL, mt(10)); }
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setBackground(rounded(0xFF26262F, 16));
            cell.setPadding(dp(8), dp(16), dp(8), dp(14));
            cell.addView(text(g.optString("emoji"), 44, TXT, false));
            TextView n = text(g.optString("name"), 15, TXT, true);
            n.setGravity(Gravity.CENTER);
            cell.addView(n);
            TextView pr = text("⭐ " + g.optInt("price"), 14, ACCENT, true);
            pr.setGravity(Gravity.CENTER);
            cell.addView(pr);
            cell.setOnClickListener(v -> askRecipient(g));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(4), 0, dp(4), 0);
            rowL.addView(cell, lp);
        }
        root.addView(c, mb());
    }

    private void receivedCard() {
        LinearLayout c = card();
        c.addView(text("Мои подарки", 18, TXT, true));
        if (receivedData.length() == 0) {
            c.addView(text("Пока пусто", 14, SUB, false), mt(6));
        }
        for (int i = 0; i < receivedData.length() && i < 20; i++) {
            JSONObject r = receivedData.optJSONObject(i);
            if (r == null) continue;
            String when = DateFormat.getDateInstance().format(new Date(r.optLong("ts") * 1000));
            c.addView(text(r.optString("emoji") + "  " + r.optString("name") + "  ·  от ID " + r.optLong("from_id")
                    + "  ·  " + when, 14, TXT, false), mt(8));
        }
        root.addView(c, mb());
    }

    // ---------- отправка подарка ----------
    private void askRecipient(JSONObject gift) {
        EditText et = new EditText(this);
        et.setHint("ID получателя или @алиас");
        et.setSingleLine();
        new AlertDialog.Builder(this)
                .setTitle("Подарить " + gift.optString("emoji") + " " + gift.optString("name"))
                .setView(et)
                .setPositiveButton("Отправить", (d, w) -> {
                    String t = et.getText().toString().trim();
                    if (t.isEmpty()) return;
                    if (t.matches("-?\\d+")) sendGift(Long.parseLong(t), gift);
                    else AeroGram.request("GET", "/resolve/" + t.replace("@", ""), null, (j, err) -> {
                        if (j == null) toast("Алиас не найден"); else sendGift(j.optLong("tg_id"), gift);
                    });
                })
                .setNegativeButton("Отмена", null).show();
    }

    private void sendGift(long toId, JSONObject gift) {
        AeroGram.request("POST", "/gifts/send", "{\"to_id\":" + toId + ",\"gift_id\":" + gift.optInt("id") + "}",
                (j, err) -> {
                    if (j != null) { toast("Подарок отправлен " + gift.optString("emoji")); reload(); }
                    else if (err != null && err.contains("not enough")) toast("Недостаточно звёзд");
                    else if (err != null && err.contains("\"detail\"")) reload();
                    else toast("Не удалось отправить");
                });
    }

    // ---------- помощники ----------
    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + .5f); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color); g.setCornerRadius(dp(radiusDp));
        return g;
    }
    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }
    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(rounded(CARD, 20));
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        return c;
    }
    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }
    private Button button(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s); b.setTextColor(0xFFFFFFFF); b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(rounded(ACCENT, 14));
        b.setOnClickListener(l);
        return b;
    }
    private LinearLayout.LayoutParams mt(int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(topDp); return p;
    }
    private LinearLayout.LayoutParams mb() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = dp(14); return p;
    }
}
