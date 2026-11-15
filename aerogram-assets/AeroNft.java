package org.telegram.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Карточка NFT-подарка AeroGram: фон-градиент, эмодзи, номер и редкость. */
public class AeroNft {
    public static final String[] KEYS = {"common", "uncommon", "rare", "epic", "legendary", "mythic"};
    public static final String[] LABELS = {"Обычный", "Необычный", "Редкий", "Эпический", "Легендарный", "Мифический"};
    public static final int[] COLORS = {0xFF9AA0AA, 0xFF4CD964, 0xFF3D9BFF, 0xFFB650FF, 0xFFFFB300, 0xFFFF2D55};

    public static int idx(String r) { for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(r)) return i; return 0; }
    public static String label(String r) { return LABELS[idx(r)]; }
    public static int color(String r) { return COLORS[idx(r)]; }
    public static int parse(String hex, int def) { try { return Color.parseColor(hex); } catch (Exception e) { return def; } }

    public static View build(Context ctx, String emoji, String name, int serial, String rarity,
                             String c1, String c2, String backdrop) {
        float d = ctx.getResources().getDisplayMetrics().density;
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        l.setPadding((int) (8 * d), (int) (14 * d), (int) (8 * d), (int) (12 * d));
        l.setMinimumHeight((int) (170 * d));
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{parse(c1, 0xFFF50110), parse(c2, 0xFF7A0008)});
        g.setCornerRadius(18 * d);
        if (idx(rarity) >= 2) g.setStroke((int) (2 * d), color(rarity));
        l.setBackground(g);

        l.addView(tv(ctx, emoji, 52, 0xFFFFFFFF, false));
        TextView n = tv(ctx, name, 14, 0xFFFFFFFF, true);
        n.setSingleLine(); n.setEllipsize(TextUtils.TruncateAt.END);
        l.addView(n);
        l.addView(tv(ctx, "#" + serial, 12, 0xCCFFFFFF, false));
        if (backdrop != null && !backdrop.isEmpty()) l.addView(tv(ctx, backdrop, 11, 0x99FFFFFF, false));

        TextView pill = tv(ctx, label(rarity), 11, 0xFFFFFFFF, true);
        GradientDrawable pg = new GradientDrawable();
        pg.setColor(0x44000000); pg.setCornerRadius(10 * d); pg.setStroke((int) d, color(rarity));
        pill.setBackground(pg);
        pill.setPadding((int) (10 * d), (int) (3 * d), (int) (10 * d), (int) (3 * d));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = (int) (8 * d);
        l.addView(pill, lp);
        return l;
    }

    private static TextView tv(Context c, String s, int sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s); t.setTextColor(color); t.setGravity(Gravity.CENTER);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }
}
