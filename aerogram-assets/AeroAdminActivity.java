package org.telegram.ui;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AeroGram;

/** Меню выдачи (только владелец): звёзды, GRAM, Premium, уровень, номер, юзернеймы, подарки, NFT. */
public class AeroAdminActivity extends Activity {
    static final int BG = 0xFF0E0E13, CARD = 0xFF1A1A22, FIELD = 0xFF26262F, ACCENT = 0xFFF50110, TXT = 0xFFF2F2F5, SUB = 0xFF9A9AA8;

    interface Sel { void on(int i); }

    private EditText recipient;
    private LinearLayout content, previewHolder;
    private JSONArray gifts = new JSONArray();

    // состояние NFT-конструктора
    private int nftGift = 0, nftRarity = 3, nftPreset = 0;
    private String nftName = "", hex1 = "", hex2 = "";

    // пресеты фонов: название, цвет 1, цвет 2
    static final String[] BD_NAMES = {"Cherry", "Ocean", "Emerald", "Amethyst", "Sunset", "Gold", "Onyx", "Ice", "Rose", "Lime", "Graphite", "Aero"};
    static final String[] BD_C1 = {"#E53935", "#1E88E5", "#26A65B", "#9C4DFF", "#FF8A3D", "#FFC83D", "#3A3F4B", "#7FE0FF", "#FF7EB6", "#B6F23D", "#8E99A8", "#F50110"};
    static final String[] BD_C2 = {"#8E0E1A", "#0D3B7A", "#0B5D2E", "#4A148C", "#C2185B", "#B26A00", "#0B0D12", "#2C7BB5", "#B0306A", "#4C8A0B", "#3B4452", "#7A0008"};

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!AeroGram.isOwner) { finish(); return; }
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(24), dp(16), dp(32));
        sv.addView(root);
        setContentView(sv);

        root.addView(text("Меню выдачи", 26, TXT, true));
        root.addView(text("Только для владельца AeroGram", 13, SUB, false));

        LinearLayout rc = card();
        rc.addView(text("Получатель", 15, TXT, true));
        LinearLayout rr = row();
        recipient = field("ID получателя", InputType.TYPE_CLASS_NUMBER);
        recipient.setText(String.valueOf(AeroGram.myId));
        rr.addView(recipient, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView me = chip("Себе", false);
        me.setOnClickListener(v -> recipient.setText(String.valueOf(AeroGram.myId)));
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-2, -2);
        mlp.leftMargin = dp(8);
        rr.addView(me, mlp);
        rc.addView(rr, mt(8));
        root.addView(rc, mb());

        String[] tabs = {"⭐ Звёзды", "GRAM", "✦ Premium", "Уровень", "Номер", "@ Юзернейм", "🎁 Подарок", "💎 NFT", "✓ Верификация"};
        root.addView(chips(tabs, 0, this::show));
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams clp = mt(12);
        root.addView(content, clp);

        AeroGram.request("GET", "/gifts", null, (j, err) -> {
            if (j != null) { JSONArray g = j.optJSONArray("list"); if (g != null) gifts = g; }
            show(0);
        });
    }

    private void show(int tab) {
        content.removeAllViews();
        switch (tab) {
            case 0: amount("Выдать звёзды", "/grant/stars", "⭐"); break;
            case 1: amount("Выдать GRAM", "/grant/gram", "GRAM"); break;
            case 2: premium(); break;
            case 3: level(); break;
            case 4: number(); break;
            case 5: username(); break;
            case 6: gift(); break;
            case 7: nft(); break;
            case 8: verification(); break;
        }
    }

    private void verification() {
        LinearLayout c = card();
        c.addView(text("Верификация AeroGram", 18, TXT, true));
        c.addView(text("Синяя галочка предназначена только назначенным администраторам. Фиолетовый значок владельца назначается автоматически; серые значки каналов выдаются через бота.", 13, SUB, false), mt(6));
        c.addView(button("Выдать синюю галочку администратору", v -> {
            long id = rid(); if (id == 0) return;
            grant("/owner/verify-admin", json("to_id", id), "Верификация");
        }), mt(12));
        c.addView(button("Снять верификацию", v -> {
            long id = rid(); if (id == 0) return;
            grant("/owner/unverify", json("to_id", id), "Верификация");
        }), mt(8));
        content.addView(c);
    }

    // ---------- разделы ----------
    private void amount(String title, String path, String unit) {
        LinearLayout c = card();
        c.addView(text(title, 18, TXT, true));
        c.addView(text("Отрицательное число списывает.", 13, SUB, false), mt(2));
        EditText et = field("Количество", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        c.addView(et, mt(10));
        String[] q = {"100", "1000", "10000", "100000", "1000000"};
        c.addView(chips(new String[]{"100", "1 000", "10 000", "100 000", "1 000 000"}, -1, i -> et.setText(q[i])), mt(10));
        c.addView(button("Выдать " + unit, v -> {
            long id = rid(); if (id == 0) return;
            long a; try { a = Long.parseLong(et.getText().toString().trim()); } catch (Exception e) { toast("Введите число"); return; }
            grant(path, json("to_id", id, "amount", a), "Готово");
        }), mt(12));
        content.addView(c);
    }

    private void premium() {
        LinearLayout c = card();
        c.addView(text("AeroGram Premium", 18, TXT, true));
        c.addView(text("Локальный статус внутри мода. Серверный Premium Telegram он не заменяет.", 13, SUB, false), mt(2));
        String[] l = {"30 дней", "90 дней", "1 год", "Навсегда", "Снять"};
        int[] days = {30, 90, 365, -1, 0};
        c.addView(chips(l, -1, i -> {
            long id = rid(); if (id == 0) return;
            grant("/grant/premium", json("to_id", id, "days", days[i]), "Готово");
        }), mt(12));
        content.addView(c);
    }

    private void level() {
        LinearLayout c = card();
        c.addView(text("Уровень", 18, TXT, true));
        TextView val = text("Уровень 1", 28, TXT, true);
        c.addView(val, mt(8));
        SeekBar sb = new SeekBar(this);
        sb.setMax(99); sb.setProgress(0);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) { val.setText("Уровень " + (p + 1)); }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        c.addView(sb, mt(8));
        c.addView(button("Установить уровень", v -> {
            long id = rid(); if (id == 0) return;
            grant("/grant/level", json("to_id", id, "level", sb.getProgress() + 1), "Готово");
        }), mt(12));
        content.addView(c);
    }

    private void number() {
        LinearLayout c = card();
        c.addView(text("Анонимный номер", 18, TXT, true));
        c.addView(text("Формат AG-888-1234. Это внутренняя метка AeroGram, не телефонный номер и не номер Telegram.", 13, SUB, false), mt(2));
        c.addView(button("Выдать случайный номер", v -> {
            long id = rid(); if (id == 0) return;
            grant("/grant/number", json("to_id", id, "mode", "random"), "Номер выдан");
        }), mt(12));
        EditText et = field("AG-123-4567", InputType.TYPE_CLASS_TEXT);
        c.addView(et, mt(12));
        c.addView(button("Задать свой номер", v -> {
            long id = rid(); if (id == 0) return;
            grant("/grant/number", json("to_id", id, "mode", "set", "number", et.getText().toString()), "Номер выдан");
        }), mt(8));
        TextView rm = chip("Снять номер", false);
        rm.setOnClickListener(v -> { long id = rid(); if (id != 0) grant("/grant/number", json("to_id", id, "mode", "revoke"), "Готово"); });
        c.addView(rm, mt(10));
        content.addView(c);
    }

    private int uRarity = 0;
    private void username() {
        LinearLayout c = card();
        c.addView(text("Коллекционный юзернейм", 18, TXT, true));
        EditText et = field("имя (a-z, 0-9, _)", InputType.TYPE_CLASS_TEXT);
        c.addView(et, mt(10));
        c.addView(text("Редкость", 13, SUB, false), mt(12));
        c.addView(chips(AeroNft.LABELS, uRarity, i -> uRarity = i), mt(6));
        c.addView(button("Выдать юзернейм", v -> {
            long id = rid(); if (id == 0) return;
            grant("/grant/username", json("to_id", id, "name", et.getText().toString(), "rarity", AeroNft.KEYS[uRarity]), "Выдан");
        }), mt(12));
        content.addView(c);
    }

    private int gSel = 0;
    private void gift() {
        LinearLayout c = card();
        c.addView(text("Обычный подарок", 18, TXT, true));
        if (gifts.length() == 0) { c.addView(text("Каталог пуст или не загрузился", 14, SUB, false), mt(8)); content.addView(c); return; }
        String[] names = new String[gifts.length()];
        for (int i = 0; i < names.length; i++) names[i] = gifts.optJSONObject(i).optString("emoji") + " " + gifts.optJSONObject(i).optString("name");
        c.addView(chips(names, gSel, i -> gSel = i), mt(10));
        EditText cnt = field("Количество (1–100)", InputType.TYPE_CLASS_NUMBER);
        cnt.setText("1");
        c.addView(cnt, mt(10));
        c.addView(button("Выдать подарок", v -> {
            long id = rid(); if (id == 0) return;
            int n; try { n = Integer.parseInt(cnt.getText().toString().trim()); } catch (Exception e) { n = 1; }
            grant("/grant/gift", json("to_id", id, "gift_id", gifts.optJSONObject(gSel).optInt("id"), "count", n), "Выдано");
        }), mt(12));
        content.addView(c);
    }

    // ---------- NFT-конструктор ----------
    private void nft() {
        LinearLayout c = card();
        c.addView(text("NFT-подарок", 18, TXT, true));
        c.addView(text("Выберите модель, редкость и фон. Предпросмотр обновляется сразу.", 13, SUB, false), mt(2));

        previewHolder = new LinearLayout(this);
        previewHolder.setGravity(Gravity.CENTER);
        c.addView(previewHolder, mt(12));

        if (gifts.length() > 0) {
            c.addView(text("Модель", 13, SUB, false), mt(14));
            String[] names = new String[gifts.length()];
            for (int i = 0; i < names.length; i++) names[i] = gifts.optJSONObject(i).optString("emoji") + " " + gifts.optJSONObject(i).optString("name");
            c.addView(chips(names, nftGift, i -> { nftGift = i; nftName = ""; preview(); }), mt(6));
        }
        EditText nm = field("Название коллекции (по умолчанию как у подарка)", InputType.TYPE_CLASS_TEXT);
        nm.addTextChangedListener(new SimpleWatcher(s -> { nftName = s; preview(); }));
        c.addView(nm, mt(10));

        c.addView(text("Редкость", 13, SUB, false), mt(14));
        c.addView(chips(AeroNft.LABELS, nftRarity, i -> { nftRarity = i; preview(); }), mt(6));

        c.addView(text("Фон", 13, SUB, false), mt(14));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout sw = new LinearLayout(this);
        View[] views = new View[BD_NAMES.length];
        for (int i = 0; i < BD_NAMES.length; i++) {
            View v = new View(this);
            final int idx = i;
            v.setOnClickListener(x -> { nftPreset = idx; hex1 = ""; hex2 = ""; paintSwatches(views); preview(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(44));
            lp.rightMargin = dp(8);
            sw.addView(v, lp);
            views[i] = v;
        }
        paintSwatches(views);
        hs.addView(sw);
        c.addView(hs, mt(8));

        c.addView(text("Свой цвет (необязательно, #RRGGBB)", 13, SUB, false), mt(12));
        LinearLayout hr = row();
        EditText h1 = field("#E53935", InputType.TYPE_CLASS_TEXT);
        EditText h2 = field("#8E0E1A", InputType.TYPE_CLASS_TEXT);
        h1.addTextChangedListener(new SimpleWatcher(s -> { hex1 = s; preview(); }));
        h2.addTextChangedListener(new SimpleWatcher(s -> { hex2 = s; preview(); }));
        hr.addView(h1, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, -2, 1f);
        l2.leftMargin = dp(8);
        hr.addView(h2, l2);
        c.addView(hr, mt(6));

        c.addView(button("Выпустить NFT", v -> {
            long id = rid(); if (id == 0) return;
            if (gifts.length() == 0) { toast("Каталог подарков не загрузился"); return; }
            String c1 = col1(), c2 = col2();
            grant("/grant/nft", json("to_id", id, "collection", name(), "emoji", emoji(), "rarity", AeroNft.KEYS[nftRarity],
                    "c1", c1, "c2", c2, "backdrop", hex1.isEmpty() ? BD_NAMES[nftPreset] : "Custom"), "NFT выпущен");
        }), mt(16));
        content.addView(c);
        preview();
    }

    private String emoji() { return gifts.length() > 0 ? gifts.optJSONObject(nftGift).optString("emoji") : "🎁"; }
    private String name() { return !nftName.trim().isEmpty() ? nftName.trim() : (gifts.length() > 0 ? gifts.optJSONObject(nftGift).optString("name") : "Gift"); }
    private String col1() { return hex1.matches("#[0-9A-Fa-f]{6}") ? hex1 : BD_C1[nftPreset]; }
    private String col2() { return hex2.matches("#[0-9A-Fa-f]{6}") ? hex2 : BD_C2[nftPreset]; }

    private void preview() {
        if (previewHolder == null) return;
        previewHolder.removeAllViews();
        View v = AeroNft.build(this, emoji(), name(), 1, AeroNft.KEYS[nftRarity], col1(), col2(),
                hex1.isEmpty() ? BD_NAMES[nftPreset] : "Custom");
        previewHolder.addView(v, new LinearLayout.LayoutParams(dp(170), -2));
    }

    private void paintSwatches(View[] views) {
        for (int i = 0; i < views.length; i++) {
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{AeroNft.parse(BD_C1[i], 0), AeroNft.parse(BD_C2[i], 0)});
            g.setShape(GradientDrawable.OVAL);
            if (i == nftPreset && hex1.isEmpty()) g.setStroke(dp(3), 0xFFFFFFFF);
            views[i].setBackground(g);
        }
    }

    // ---------- сеть ----------
    private long rid() {
        try { long v = Long.parseLong(recipient.getText().toString().trim()); if (v != 0) return v; } catch (Exception ignored) {}
        toast("Укажите ID получателя");
        return 0;
    }

    private String json(Object... kv) {
        JSONObject o = new JSONObject();
        try { for (int i = 0; i < kv.length; i += 2) o.put((String) kv[i], kv[i + 1]); } catch (Exception ignored) {}
        return o.toString();
    }

    private void grant(String path, String body, String ok) {
        AeroGram.request("POST", path, body, (j, err) -> {
            if (j != null) toast(ok + (j.has("info") ? " · " + j.optString("info") : ""));
            else toast(errText(err));
        });
    }

    private String errText(String err) {
        try { return new JSONObject(err).optString("detail", err); } catch (Exception e) { return String.valueOf(err); }
    }

    // ---------- UI-помощники ----------
    static class SimpleWatcher implements android.text.TextWatcher {
        interface Cb { void on(String s); }
        final Cb cb;
        SimpleWatcher(Cb cb) { this.cb = cb; }
        @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
        @Override public void onTextChanged(CharSequence s, int a, int b, int c) { cb.on(s.toString()); }
        @Override public void afterTextChanged(android.text.Editable e) {}
    }

    private HorizontalScrollView chips(String[] labels, int sel, Sel cb) {
        LinearLayout r = new LinearLayout(this);
        TextView[] tv = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) {
            TextView t = chip(labels[i], i == sel);
            final int idx = i;
            t.setOnClickListener(v -> {
                for (int k = 0; k < tv.length; k++) tv[k].setBackground(rounded(k == idx ? ACCENT : FIELD, 18));
                cb.on(idx);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = dp(6);
            r.addView(t, lp);
            tv[i] = t;
        }
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(r);
        return hs;
    }

    private TextView chip(String s, boolean selected) {
        TextView t = text(s, 14, TXT, false);
        t.setPadding(dp(14), dp(8), dp(14), dp(8));
        t.setBackground(rounded(selected ? ACCENT : FIELD, 18));
        return t;
    }

    private EditText field(String hint, int type) {
        EditText et = new EditText(this);
        et.setHint(hint); et.setHintTextColor(SUB); et.setTextColor(TXT);
        et.setInputType(type);
        et.setBackground(rounded(FIELD, 12));
        et.setPadding(dp(14), dp(12), dp(14), dp(12));
        return et;
    }

    private Button button(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s); b.setTextColor(0xFFFFFFFF); b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(rounded(ACCENT, 14));
        b.setOnClickListener(l);
        return b;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(rounded(CARD, 20));
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        return c;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private GradientDrawable rounded(int color, int r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color); g.setCornerRadius(dp(r));
        return g;
    }

    private LinearLayout.LayoutParams mt(int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(top); return p;
    }
    private LinearLayout.LayoutParams mb() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(14); p.bottomMargin = dp(14); return p;
    }
    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + .5f); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
