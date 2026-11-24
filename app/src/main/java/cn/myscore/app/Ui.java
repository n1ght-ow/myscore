package cn.myscore.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Modern sports-editorial visual design system: Obsidian Midnight (dark) and Crisp Porcelain (light),
 * layered surface hierarchy, semantic sport/status accents, and reusable UI primitives.
 */
final class Ui {
    private static final Typeface REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);
    private static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private static final Typeface BOLD = Typeface.create("sans-serif", Typeface.BOLD);

    /** A window hint only: Android still owns power, thermal and user refresh policy. */
    static void highRefresh(android.view.Window window) {
        if (window == null) return;
        android.view.Display display = window.getDecorView().getDisplay();
        if (display == null) return;
        android.view.Display.Mode current = display.getMode();
        float best = current.getRefreshRate();
        for (android.view.Display.Mode mode : display.getSupportedModes()) {
            if (mode.getPhysicalWidth() == current.getPhysicalWidth()
                && mode.getPhysicalHeight() == current.getPhysicalHeight()) {
                best = Math.max(best, mode.getRefreshRate());
            }
        }
        android.view.WindowManager.LayoutParams params = window.getAttributes();
        if (params.preferredDisplayModeId == 0 && Math.abs(params.preferredRefreshRate - best) < 0.1f) return;
        params.preferredDisplayModeId = 0;
        params.preferredRefreshRate = best;
        window.setAttributes(params);
    }

    final Context c;
    final boolean dark;
    static final int CARD_RADIUS = 18, CONTROL_RADIUS = 14, PILL_RADIUS = 999, SHEET_RADIUS = 26;
    static final float ACTION_ICON_ALPHA = 0.88f;

    // Core surface & typography tokens (preserving existing public/package field names)
    final int bg, surface, elevated, text, muted, dim, primary, onPrimary, primarySoft;
    final int tonal, line, border, borderStrong, error, errorSoft, secondary, action, onAction;
    final int live, liveSoft, info, infoSoft, warn, warnSoft, navBg;

    private static Typeface interRegular, interMedium, interBold;
    private final Typeface regular, medium, heavy;

    static boolean isDark(Context context) {
        String theme = context.getSharedPreferences("settings", 0).getString("theme", "dark");
        return "dark".equals(theme)
            || ("system".equals(theme)
            && (context.getResources().getConfiguration().uiMode
            & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
            == android.content.res.Configuration.UI_MODE_NIGHT_YES);
    }

    Ui(Context c, boolean dark) {
        this.c = c;
        this.dark = dark;
        if (interRegular == null) {
            try {
                interRegular = new Typeface.Builder(c.getAssets(), "fonts/Inter.ttf").setWeight(400).build();
                interMedium = new Typeface.Builder(c.getAssets(), "fonts/Inter.ttf").setWeight(600).build();
                interBold = new Typeface.Builder(c.getAssets(), "fonts/Inter.ttf").setWeight(700).build();
            } catch (RuntimeException ignored) {}
        }
        regular = interRegular == null ? REGULAR : interRegular;
        medium = interMedium == null ? MEDIUM : interMedium;
        heavy = interBold == null ? BOLD : interBold;

        if (dark) {
            bg = Color.parseColor("#090B10");
            surface = Color.parseColor("#121620");
            elevated = Color.parseColor("#1B2230");
            tonal = Color.parseColor("#171D29");
            navBg = Color.parseColor("#0E121B");
            line = Color.parseColor("#1E2535");
            border = Color.parseColor("#232B3D");
            borderStrong = Color.parseColor("#33405A");
            text = Color.parseColor("#F3F6FB");
            muted = Color.parseColor("#92A0B8");
            dim = Color.parseColor("#62708B");
            primary = Color.parseColor("#22C58B");
            onPrimary = Color.parseColor("#052218");
            primarySoft = Color.parseColor("#122D25");
            live = Color.parseColor("#FF4560");
            liveSoft = Color.parseColor("#33141D");
            info = Color.parseColor("#4F8BFF");
            infoSoft = Color.parseColor("#142442");
            warn = Color.parseColor("#F5A524");
            warnSoft = Color.parseColor("#302410");
            error = Color.parseColor("#F85C70");
            errorSoft = Color.parseColor("#33151E");
            secondary = Color.parseColor("#A5B3CB");
            action = Color.parseColor("#F3F6FB");
            onAction = Color.parseColor("#090B10");
        } else {
            bg = Color.parseColor("#F3F5F9");
            surface = Color.parseColor("#FFFFFF");
            elevated = Color.parseColor("#FFFFFF");
            tonal = Color.parseColor("#E9EEF6");
            navBg = Color.parseColor("#FFFFFF");
            line = Color.parseColor("#E5EAF2");
            border = Color.parseColor("#DFE6F0");
            borderStrong = Color.parseColor("#C7D2E3");
            text = Color.parseColor("#0E1525");
            muted = Color.parseColor("#54627A");
            dim = Color.parseColor("#8896AE");
            primary = Color.parseColor("#0D9464");
            onPrimary = Color.parseColor("#FFFFFF");
            primarySoft = Color.parseColor("#DFF6EC");
            live = Color.parseColor("#E11D48");
            liveSoft = Color.parseColor("#FFE4EA");
            info = Color.parseColor("#2563EB");
            infoSoft = Color.parseColor("#E0ECFF");
            warn = Color.parseColor("#D97706");
            warnSoft = Color.parseColor("#FEF3C7");
            error = Color.parseColor("#DC2626");
            errorSoft = Color.parseColor("#FEE2E2");
            secondary = Color.parseColor("#4B5B76");
            action = Color.parseColor("#0E1525");
            onAction = Color.parseColor("#FFFFFF");
        }
    }

    /** Tasteful identity accent color per competition / sport. */
    int sportAccent(String leagueId, String sportGroup) {
        if (leagueId != null) {
            switch (leagueId) {
                case "epl": return Color.parseColor(dark ? "#A78BFA" : "#7C3AED");
                case "ucl": return Color.parseColor(dark ? "#60A5FA" : "#2563EB");
                case "fa": return Color.parseColor(dark ? "#F472B6" : "#DB2777");
                case "efl": return Color.parseColor(dark ? "#34D399" : "#059669");
                case "intl": return Color.parseColor(dark ? "#38BDF8" : "#0284C7");
                case "nba": return Color.parseColor(dark ? "#FB923C" : "#EA580C");
                case "nfl": return Color.parseColor(dark ? "#34D399" : "#0D9464");
                case "f1": return Color.parseColor(dark ? "#F87171" : "#DC2626");
                case "cs2": return Color.parseColor(dark ? "#FBBF24" : "#D97706");
            }
        }
        if (sportGroup != null) {
            if ("NBA".equals(sportGroup)) return Color.parseColor(dark ? "#FB923C" : "#EA580C");
            if ("NFL".equals(sportGroup)) return Color.parseColor(dark ? "#34D399" : "#0D9464");
            if ("F1".equals(sportGroup)) return Color.parseColor(dark ? "#F87171" : "#DC2626");
            if ("CS2".equals(sportGroup)) return Color.parseColor(dark ? "#FBBF24" : "#D97706");
            if ("足球".equals(sportGroup) || "Football".equals(sportGroup)) return Color.parseColor(dark ? "#60A5FA" : "#2563EB");
        }
        return primary;
    }

    int zoneColor(ScoreFeedOrganizer.Zone zone) {
        if (zone == null) return Color.TRANSPARENT;
        return switch (zone) {
            case CHAMPIONS -> info;
            case QUALIFY -> primary;
            case PLAY_IN -> warn;
            case RELEGATION -> error;
            case PODIUM_GOLD -> Color.parseColor(dark ? "#FBBF24" : "#D97706");
            case PODIUM_SILVER -> Color.parseColor(dark ? "#CBD5E1" : "#64748B");
            case PODIUM_BRONZE -> Color.parseColor(dark ? "#FB923C" : "#C2410C");
            default -> Color.TRANSPARENT;
        };
    }

    static int alpha(int color, int a) {
        return (color & 0x00FFFFFF) | ((Math.max(0, Math.min(255, a)) & 0xFF) << 24);
    }

    int dp(float n) { return Math.round(n * c.getResources().getDisplayMetrics().density); }

    /** Scores and the last action share a center axis, including three digits at large font sizes. */
    int scoreColumnWidth(int sp) {
        return Math.max(dp(48), (int) Math.ceil(text("", sp, text, true).getPaint().measureText("888")) + dp(8));
    }

    int scoreColumnWidth(int sp, Match match) {
        Paint p = text("", sp, text, true).getPaint();
        return Math.max(scoreColumnWidth(sp),
            (int) Math.ceil(Math.max(p.measureText(match.displayScore(true)), p.measureText(match.displayScore(false)))) + dp(8));
    }

    TextView fixtureScore(String value, int sp, int color) {
        TextView score = text(value, sp, color, true);
        score.setGravity(Gravity.CENTER);
        score.setMinWidth(scoreColumnWidth(sp));
        score.setTag("fixture-score");
        return score;
    }

    void alignFixtureActions(View calendar, View watch, int column) {
        boolean plays = watch != null && watch.getVisibility() != View.GONE;
        if (watch != null && watch.getLayoutParams().width != column) {
            watch.getLayoutParams().width = column;
            watch.requestLayout();
        }
        int width = plays ? dp(48) : column;
        if (calendar.getLayoutParams().width != width) {
            calendar.getLayoutParams().width = width;
            calendar.requestLayout();
        }
    }

    /** The measured pane, rather than the device width, owns its column breakpoint. */
    LinearLayout columns(LinearLayout left, LinearLayout right, int minimumDp, int gapDp) {
        View gap = new View(c);
        LinearLayout pair = new LinearLayout(c) {
            private int mode = -1;
            @Override protected void onMeasure(int width, int height) {
                boolean hasLeft = left.getChildCount() > 0, hasRight = right.getChildCount() > 0;
                boolean wide = MeasureSpec.getMode(width) != MeasureSpec.UNSPECIFIED
                    && MeasureSpec.getSize(width) >= dp(2 * minimumDp * Math.max(1, c.getResources().getConfiguration().fontScale) + gapDp);
                int next = (wide ? 4 : 0) + (hasLeft ? 2 : 0) + (hasRight ? 1 : 0);
                if (next != mode) {
                    mode = next;
                    setOrientation(wide ? HORIZONTAL : VERTICAL);
                    left.setVisibility(hasLeft ? VISIBLE : GONE);
                    right.setVisibility(hasRight ? VISIBLE : GONE);
                    gap.setVisibility(hasLeft && hasRight ? VISIBLE : GONE);
                    left.setLayoutParams(new LinearLayout.LayoutParams(wide && hasRight ? 0 : -1, -2, wide && hasRight ? 1 : 0));
                    right.setLayoutParams(new LinearLayout.LayoutParams(wide && hasLeft ? 0 : -1, -2, wide && hasLeft ? 1 : 0));
                    gap.setLayoutParams(new LinearLayout.LayoutParams(wide ? dp(gapDp) : 1, wide ? 1 : dp(gapDp)));
                }
                super.onMeasure(width, height);
            }
        };
        pair.setGravity(Gravity.TOP);
        pair.setTag("adaptive-columns");
        pair.addView(left);
        pair.addView(gap);
        pair.addView(right);
        return pair;
    }

    static boolean isPad(Context context) {
        android.content.res.Configuration cfg = context.getResources().getConfiguration();
        return cfg.smallestScreenWidthDp >= 720 && cfg.screenWidthDp >= 720;
    }

    int contentWidthDp() {
        int width = c.getResources().getConfiguration().screenWidthDp;
        return isPad(c) ? Math.min(1160, width) : Math.min(640, width);
    }

    int pageInset() { return isPad(c) ? 0 : dp(Math.max(0, (c.getResources().getConfiguration().screenWidthDp - 640) / 2f)); }

    LinearLayout column() { LinearLayout v = new LinearLayout(c); v.setOrientation(LinearLayout.VERTICAL); return v; }
    LinearLayout row() { LinearLayout v = new LinearLayout(c); v.setOrientation(LinearLayout.HORIZONTAL); v.setGravity(Gravity.CENTER_VERTICAL); return v; }

    static final class ReadingScroll extends android.widget.ScrollView {
        private boolean fingerDown;
        private long lastScroll;
        ReadingScroll(Context context) { super(context); }
        boolean isInteracting() { return fingerDown || android.os.SystemClock.uptimeMillis() - lastScroll < 250; }
        @Override public boolean dispatchTouchEvent(android.view.MotionEvent event) {
            int action = event.getActionMasked();
            if (action == android.view.MotionEvent.ACTION_DOWN) fingerDown = true;
            else if (action == android.view.MotionEvent.ACTION_UP || action == android.view.MotionEvent.ACTION_CANCEL) fingerDown = false;
            return super.dispatchTouchEvent(event);
        }
        @Override protected void onScrollChanged(int x, int y, int oldX, int oldY) { super.onScrollChanged(x, y, oldX, oldY); lastScroll = android.os.SystemClock.uptimeMillis(); }
        @Override protected void onDetachedFromWindow() { fingerDown = false; super.onDetachedFromWindow(); }
    }

    private static class DisplayText extends TextView {
        DisplayText(Context context) { super(context); }
        @Override public void setText(CharSequence value, BufferType type) { super.setText(EnglishDisplay.text(value), type); }
        @Override public void setContentDescription(CharSequence value) { super.setContentDescription(value == null ? null : EnglishDisplay.text(value)); }
    }

    TextView text(String s, int sp, int color, boolean bold) {
        if ("null".equals(s)) s = "";
        TextView v = new DisplayText(c);
        v.setText(s);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setFontFeatureSettings("tnum");
        v.setIncludeFontPadding(true);
        v.setFallbackLineSpacing(true);
        v.setTypeface(bold ? medium : regular);
        v.setLineSpacing(dp(2), 1f);
        return v;
    }

    TextView display(String s, int sp, int color) {
        TextView v = text(s, sp, color, true);
        v.setTypeface(heavy);
        v.setLetterSpacing(-0.02f);
        return v;
    }

    TextView eyebrow(String s, int color) {
        TextView v = text(s, 11, color, true);
        v.setAllCaps(true);
        v.setLetterSpacing(0.08f);
        return v;
    }

    ScoreText score(String home, String away, boolean available, int maxSp) { return new ScoreText(home, away, available, maxSp); }
    int scoreColor(Match match, boolean home) { int result = match.outcome(home); return result > 0 ? primary : result < 0 ? muted : text; }
    int scoreColor(String own, String other, boolean finished) { int result = DetailFormat.outcome(own, other, finished); return result > 0 ? primary : result < 0 ? error : text; }

    final class ScoreText extends TextView {
        private final Paint measure = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int maxSp;
        private String home = "", away = "", single = "VS";
        private boolean available;
        private int homeColor = text, awayColor = text;
        private int ceiling;

        ScoreText(String home, String away, boolean available, int maxSp) {
            super(Ui.this.c);
            this.maxSp = maxSp;
            setTypeface(heavy);
            setFontFeatureSettings("tnum");
            setLetterSpacing(-0.02f);
            setIncludeFontPadding(true);
            setFallbackLineSpacing(true);
            setTextColor(text);
            setGravity(Gravity.CENTER);
            setScore(home, away, available);
        }

        void setScore(String home, String away, boolean available) {
            if (ceiling != 0 && this.available == available && this.home.equals(home) && this.away.equals(away)) return;
            this.home = home;
            this.away = away;
            this.available = available;
            single = available ? home + " – " + away : "VS";
            int next = available ? maxSp : Math.min(22, maxSp);
            ceiling = next;
            setContentDescription(available ? "Score: " + home + " to " + away : "Score not available");
            if (!single.contentEquals(getText())) paintScore(single);
        }

        void setResultColors(int home, int away) {
            if (homeColor == home && awayColor == away && getText() instanceof android.text.Spanned) return;
            homeColor = home;
            awayColor = away;
            paintScore(getText().toString());
        }

        private void paintScore(String display) {
            android.text.SpannableString colored = new android.text.SpannableString(display);
            if (available && display.length() >= home.length() + away.length()) {
                colored.setSpan(new android.text.style.ForegroundColorSpan(homeColor), 0, home.length(), 0);
                colored.setSpan(new android.text.style.ForegroundColorSpan(awayColor), display.length() - away.length(), display.length(), 0);
            } else if (!available) {
                colored.setSpan(new android.text.style.ForegroundColorSpan(dim), 0, display.length(), 0);
            }
            setText(colored);
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight();
            measure.set(getPaint());
            measure.setTextSize(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 22, c.getResources().getDisplayMetrics()));
            boolean stack = available && MeasureSpec.getMode(widthSpec) != MeasureSpec.UNSPECIFIED && width > 0 && measure.measureText(single) > width;
            String display = stack ? home + "\n" + away : single;
            int lines = stack ? 2 : 1;
            if (getMaxLines() != lines) setMaxLines(lines);
            if (!display.contentEquals(getText())) paintScore(display);
            int size = ceiling;
            if (MeasureSpec.getMode(widthSpec) != MeasureSpec.UNSPECIFIED && width > 0) {
                for (; size > 22; size--) {
                    measure.setTextSize(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, size, c.getResources().getDisplayMetrics()));
                    if ((stack ? Math.max(measure.measureText(home), measure.measureText(away)) : measure.measureText(single)) <= width) break;
                }
            }
            float pixels = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, size, c.getResources().getDisplayMetrics());
            if (Math.abs(getTextSize() - pixels) > 0.1f) setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, size);
            super.onMeasure(widthSpec, heightSpec);
        }
    }

    GradientDrawable shape(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        return g;
    }

    GradientDrawable outlined(int fill, int strokeColor, int radius) {
        GradientDrawable g = shape(fill, radius);
        g.setStroke(Math.max(1, dp(1)), strokeColor);
        return g;
    }

    GradientDrawable panel() {
        GradientDrawable card = shape(surface, CARD_RADIUS);
        card.setStroke(Math.max(1, dp(1)), border);
        return card;
    }

    /** Competition-tinted gradient hero surface for Match Center, Team Hub, and Featured Cards. */
    GradientDrawable heroPanel(int accentColor) {
        int topTint = blend(surface, accentColor, dark ? 0.18f : 0.09f);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{topTint, surface});
        g.setCornerRadius(dp(CARD_RADIUS + 2));
        g.setStroke(Math.max(1, dp(1)), blend(border, accentColor, dark ? 0.35f : 0.25f));
        return g;
    }

    /** Subtle live-highlighted card surface with soft coral border glow. */
    GradientDrawable liveCardBg() {
        int tint = blend(surface, live, dark ? 0.10f : 0.04f);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{tint, surface});
        g.setCornerRadius(dp(CARD_RADIUS));
        g.setStroke(Math.max(1, dp(1.2f)), alpha(live, dark ? 115 : 90));
        return g;
    }

    static int blend(int base, int overlay, float ratio) {
        float inv = 1f - ratio;
        int r = Math.round(Color.red(base) * inv + Color.red(overlay) * ratio);
        int g = Math.round(Color.green(base) * inv + Color.green(overlay) * ratio);
        int b = Math.round(Color.blue(base) * inv + Color.blue(overlay) * ratio);
        return Color.rgb(Math.min(255, Math.max(0, r)), Math.min(255, Math.max(0, g)), Math.min(255, Math.max(0, b)));
    }

    void clickable(View v, int fill, int radius) {
        GradientDrawable background = shape(fill, radius);
        if (fill == surface && radius > 0) background.setStroke(Math.max(1, dp(1)), border);
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(alpha(text, dark ? 30 : 20)), background, shape(Color.WHITE, radius)));
    }

    void clickableDrawable(View v, Drawable background, int radius) {
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(alpha(text, dark ? 30 : 20)), background, shape(Color.WHITE, radius)));
    }

    /** Crisp status/category badge pill. */
    TextView badge(String label, int fill, int ink) {
        TextView b = text(label, 11, ink, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(9), dp(3), dp(9), dp(3));
        b.setBackground(shape(fill, PILL_RADIUS));
        return b;
    }

    /** Live status pill with glowing indicator dot. */
    LinearLayout liveBadge(String label) {
        LinearLayout pill = row();
        pill.setPadding(dp(8), dp(3), dp(9), dp(3));
        pill.setBackground(outlined(liveSoft, alpha(live, 90), PILL_RADIUS));
        View dot = new View(c);
        dot.setBackground(shape(live, PILL_RADIUS));
        LinearLayout.LayoutParams dpLp = new LinearLayout.LayoutParams(dp(6), dp(6));
        dpLp.setMarginEnd(dp(5));
        pill.addView(dot, dpLp);
        TextView t = text(label, 11, live, true);
        t.setLetterSpacing(0.03f);
        pill.addView(t);
        return pill;
    }

    /** W/D/L compact rounded-square form badge. */
    TextView formPill(String result) {
        int fill = tonal, ink = muted;
        if ("W".equals(result)) { fill = primarySoft; ink = primary; }
        else if ("L".equals(result)) { fill = errorSoft; ink = error; }
        else if ("D".equals(result)) { fill = warnSoft; ink = warn; }
        TextView v = text(result, 12, ink, true);
        v.setGravity(Gravity.CENTER);
        v.setBackground(outlined(fill, alpha(ink, 60), 8));
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(26), dp(26)));
        return v;
    }

    /** Circular/rounded tinted icon badge used in settings, section headers, and hero strips. */
    FrameLayout iconBadge(String kind, int tint, int fill, int boxDp, int iconDp) {
        FrameLayout box = new FrameLayout(c);
        box.setBackground(shape(fill, boxDp / 3));
        Icon ic = new Icon(c, kind, tint);
        box.addView(ic, new FrameLayout.LayoutParams(dp(iconDp), dp(iconDp), Gravity.CENTER));
        box.setLayoutParams(new LinearLayout.LayoutParams(dp(boxDp), dp(boxDp)));
        return box;
    }

    /** Modern pill filter chip with subtle border & active accent contrast. */
    TextView chip(String label, boolean selected, Runnable action) {
        return chip(label, selected, this.action, this.onAction, action);
    }

    TextView chip(String label, boolean selected, int activeFill, int activeInk, Runnable action) {
        TextView chip = text(label, 13, selected ? activeInk : muted, selected);
        chip.setGravity(Gravity.CENTER);
        chip.setMinHeight(dp(40));
        chip.setPadding(dp(15), dp(8), dp(15), dp(8));
        GradientDrawable bg = selected ? shape(activeFill, PILL_RADIUS) : outlined(tonal, border, PILL_RADIUS);
        clickableDrawable(chip, bg, PILL_RADIUS);
        chip.setSelected(selected);
        chip.setFocusable(true);
        chip.setOnClickListener(v -> action.run());
        return chip;
    }

    /** Modern segmented tab pill bar container. */
    LinearLayout segmentedTrack() {
        LinearLayout bar = row();
        bar.setPadding(dp(4), dp(4), dp(4), dp(4));
        bar.setBackground(outlined(tonal, border, CONTROL_RADIUS + 2));
        return bar;
    }

    /** Modern pill tab — works inside both horizontal scrolling rows and segmented tracks. */
    TextView tab(String label, boolean selected, Runnable action) {
        String display = EnglishDisplay.text(label);
        TextView tab = new DisplayText(c);
        tab.setText(display);
        tab.setTextSize(13);
        tab.setTextColor(selected ? text : muted);
        tab.setTypeface(selected ? medium : regular);
        tab.setIncludeFontPadding(true);
        tab.setFallbackLineSpacing(true);
        tab.setGravity(Gravity.CENTER);
        tab.setMinHeight(dp(40));
        tab.setPadding(dp(14), dp(8), dp(14), dp(8));
        Paint measure = new Paint(tab.getPaint());
        measure.setTypeface(regular);
        float regularWidth = measure.measureText(display);
        measure.setTypeface(medium);
        tab.setMinWidth((int) Math.ceil(Math.max(regularWidth, measure.measureText(display))) + dp(28));
        applyTabStyle(tab, selected);
        tab.setSelected(selected);
        tab.setFocusable(true);
        tab.setOnClickListener(v -> action.run());
        return tab;
    }

    void selectTab(TextView tab, boolean selected) {
        tab.setSelected(selected);
        tab.setTextColor(selected ? text : muted);
        tab.setTypeface(selected ? medium : regular);
        applyTabStyle(tab, selected);
    }

    private void applyTabStyle(TextView tab, boolean selected) {
        if (selected) {
            GradientDrawable bg = outlined(elevated, borderStrong, CONTROL_RADIUS - 2);
            clickableDrawable(tab, bg, CONTROL_RADIUS - 2);
        } else {
            clickable(tab, Color.TRANSPARENT, CONTROL_RADIUS - 2);
        }
    }

    TextView button(String label, boolean filled, Runnable action) {
        TextView b = text(label, 14, filled ? onAction : text, true);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(48));
        b.setPadding(dp(20), dp(12), dp(20), dp(12));
        GradientDrawable bg = filled ? shape(this.action, CONTROL_RADIUS) : outlined(tonal, border, CONTROL_RADIUS);
        clickableDrawable(b, bg, CONTROL_RADIUS);
        b.setFocusable(true);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    TextView accentButton(String label, int fill, int ink, Runnable action) {
        TextView b = text(label, 13, ink, true);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(42));
        b.setPadding(dp(16), dp(9), dp(16), dp(9));
        clickableDrawable(b, shape(fill, PILL_RADIUS), PILL_RADIUS);
        b.setFocusable(true);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    TextView textButton(String label, Runnable action) {
        TextView b = text(label, 14, primary, true);
        b.setMinHeight(dp(44));
        b.setPadding(dp(4), dp(10), dp(12), dp(10));
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        clickable(b, Color.TRANSPARENT, 12);
        b.setFocusable(true);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    /** Opposing horizontal comparison bar for Head-to-Head Match Stats. */
    LinearLayout statComparisonRow(String label, String homeVal, String awayVal) {
        LinearLayout box = column();
        box.setPadding(0, dp(8), 0, dp(8));

        double h = ScoreFeedOrganizer.statMagnitude(homeVal);
        double a = ScoreFeedOrganizer.statMagnitude(awayVal);
        boolean numeric = !Double.isNaN(h) && !Double.isNaN(a) && (h > 0 || a > 0);
        boolean homeLeads = numeric && h > a;
        boolean awayLeads = numeric && a > h;

        LinearLayout header = row();
        TextView left = text(homeVal, 14, homeLeads ? primary : text, homeLeads);
        TextView mid = text(label, 12, muted, false);
        mid.setGravity(Gravity.CENTER);
        TextView right = text(awayVal, 14, awayLeads ? info : text, awayLeads);
        right.setGravity(Gravity.END);

        header.addView(left, new LinearLayout.LayoutParams(dp(64), -2));
        header.addView(mid, new LinearLayout.LayoutParams(0, -2, 1f));
        header.addView(right, new LinearLayout.LayoutParams(dp(64), -2));
        box.addView(header, new LinearLayout.LayoutParams(-1, -2));

        if (numeric) {
            gap(box, 6);
            double total = Math.max(0.0001, Math.abs(h) + Math.abs(a));
            float homeRatio = (float) Math.max(0.04, Math.min(0.96, Math.abs(h) / total));
            float awayRatio = 1f - homeRatio;
            LinearLayout track = row();
            track.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(6)));

            View homeBar = new View(c);
            homeBar.setBackground(shape(homeLeads ? primary : alpha(primary, dark ? 110 : 90), 4));
            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(0, dp(6), homeRatio);
            hp.setMarginEnd(dp(3));
            track.addView(homeBar, hp);

            View awayBar = new View(c);
            awayBar.setBackground(shape(awayLeads ? info : alpha(info, dark ? 110 : 90), 4));
            LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0, dp(6), awayRatio);
            track.addView(awayBar, ap);
            box.addView(track);
        }
        return box;
    }

    /** Compact media shortcut pill for match cards. */
    final class WatchPill extends FrameLayout {
        private final Icon play;
        private String shown = "";
        private boolean liveShown;

        WatchPill(int targetHeightDp) {
            super(c);
            setMinimumWidth(dp(44));
            setMinimumHeight(dp(Math.max(40, targetHeightDp)));
            play = new Icon(c, "play", muted);
            play.setAlpha(ACTION_ICON_ALPHA);
            addView(play, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
            clickableDrawable(this, outlined(tonal, border, CONTROL_RADIUS), CONTROL_RADIUS);
            setFocusable(true);
            setTag("watch-shortcut");
        }

        void bind(WatchLinks.Link link, boolean live, Runnable action) {
            if (link == null) { setVisibility(GONE); return; }
            boolean activeLive = live && link.kind != WatchLinks.Kind.PENDING && link.kind != WatchLinks.Kind.REPLAY;
            String next = link.kind.icon;
            if (!next.equals(shown) || activeLive != liveShown) {
                shown = next;
                liveShown = activeLive;
                play.set(link.kind.icon, activeLive ? Ui.this.live : primary);
                clickableDrawable(this, activeLive ? outlined(liveSoft, alpha(Ui.this.live, 90), CONTROL_RADIUS) : outlined(tonal, border, CONTROL_RADIUS), CONTROL_RADIUS);
            }
            setContentDescription(EnglishDisplay.watchTitle(link) + ", opens in " + EnglishDisplay.text(link.provider));
            setTooltipText(EnglishDisplay.text(link.shortProvider) + " · " + EnglishDisplay.text(link.kind.label));
            setOnClickListener(v -> action.run());
            setVisibility(VISIBLE);
        }
    }

    WatchPill watchPill(int targetHeightDp) { return new WatchPill(targetHeightDp); }

    /** Modern media banner control for Match Center. */
    final class WatchRow extends LinearLayout {
        private final TextView provider;
        private final TextView state;
        private final Icon play;
        private final FrameLayout badge;

        WatchRow() {
            super(c);
            setOrientation(LinearLayout.HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setMinimumHeight(dp(50));
            setPadding(dp(14), dp(10), dp(14), dp(10));
            badge = new FrameLayout(c);
            badge.setBackground(shape(primarySoft, 10));
            play = new Icon(c, "play", primary);
            badge.addView(play, new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
            addView(badge, new LinearLayout.LayoutParams(dp(34), dp(34)));
            provider = text("", 14, text, true);
            provider.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(0, -2, 1f);
            pp.setMarginStart(dp(12));
            addView(provider, pp);
            state = text("", 12, primary, true);
            state.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, -2);
            sp.setMarginStart(dp(10));
            addView(state, sp);
            clickableDrawable(this, outlined(tonal, border, CONTROL_RADIUS), CONTROL_RADIUS);
            setFocusable(true);
            setTag("watch-control");
        }

        void bind(WatchLinks.Link link, boolean live, Runnable action) {
            if (link == null) { setVisibility(GONE); return; }
            boolean activeLive = live && link.kind != WatchLinks.Kind.PENDING && link.kind != WatchLinks.Kind.REPLAY;
            provider.setText(link.provider);
            state.setText(link.kind.label);
            int tint = activeLive ? Ui.this.live : primary;
            int soft = activeLive ? liveSoft : primarySoft;
            state.setTextColor(tint);
            play.set(link.kind.icon, tint);
            badge.setBackground(shape(soft, 10));
            setContentDescription(EnglishDisplay.watchTitle(link) + ". Open with " + EnglishDisplay.text(link.provider) + ".");
            setTooltipText(EnglishDisplay.watchTitle(link));
            setOnClickListener(v -> action.run());
            setVisibility(VISIBLE);
        }
    }

    WatchRow watchRow() { return new WatchRow(); }

    /** Empty schedule card with crisp icon badge and clear recovery action. */
    final class ScheduleEmpty extends LinearLayout {
        final TextView title, message, action;

        ScheduleEmpty(Runnable next) {
            super(c);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER_HORIZONTAL);
            setPadding(dp(24), dp(36), dp(24), dp(36));
            setBackground(panel());
            FrameLayout iconBox = iconBadge("calendar", muted, tonal, 52, 24);
            iconBox.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(iconBox);
            gap(this, 16);
            title = text("", 18, text, true);
            title.setGravity(Gravity.CENTER);
            addView(title, new LinearLayout.LayoutParams(-1, -2));
            gap(this, 8);
            message = text("", 13, muted, false);
            message.setGravity(Gravity.CENTER);
            message.setLineSpacing(dp(4), 1);
            addView(message, new LinearLayout.LayoutParams(-1, -2));
            gap(this, 20);
            action = accentButton("", tonal, text, next);
            addView(action, new LinearLayout.LayoutParams(-2, -2));
        }

        void bind(ScoreRow row) {
            title.setText(row.title);
            message.setText(row.message);
            action.setText(row.text == null ? "Refresh scores" : row.text);
        }
    }

    View divider() { View d = new View(c); d.setBackgroundColor(line); d.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(1))); return d; }
    void gap(LinearLayout into, int h) { View space = new View(c); into.addView(space, new LinearLayout.LayoutParams(1, dp(h))); }

    final class MatchTime extends LinearLayout {
        private final TextView time, caption;
        MatchTime() {
            super(c);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setMinimumHeight(dp(40));
            setPadding(0, dp(2), 0, dp(2));
            setScreenReaderFocusable(true);
            time = text("", 15, Ui.this.text, true);
            caption = text("", 11, muted, false);
            addView(time, new LinearLayout.LayoutParams(-1, -2));
            addView(caption, new LinearLayout.LayoutParams(-1, -2));
        }
        void bind(String start, String provenance) {
            String shownTime = EnglishDisplay.text(start), shownSource = EnglishDisplay.text(provenance);
            if (!shownTime.contentEquals(time.getText())) time.setText(shownTime);
            if (!shownSource.contentEquals(caption.getText())) caption.setText(shownSource);
            caption.setVisibility(provenance.isBlank() ? GONE : VISIBLE);
        }
    }

    static final class Icon extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private String kind;
        private int color;
        private final Path refreshArrow = new Path(), playPath = new Path();
        private Drawable glyph;
        private Path appPath;

        Icon(Context c, String kind, int color) {
            super(c);
            this.kind = kind;
            this.color = color;
            loadGlyph();
            refreshArrow.moveTo(18.2f, 3.8f);
            refreshArrow.lineTo(18.2f, 7.8f);
            refreshArrow.lineTo(14.2f, 7.8f);
            playPath.moveTo(8.6f, 5.5f);
            playPath.lineTo(18.6f, 12f);
            playPath.lineTo(8.6f, 18.5f);
            playPath.close();
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        private void loadGlyph() {
            appPath = AppIcons.path(kind);
            int resource = kind.equals("mark") ? R.drawable.brand_s : 0;
            glyph = resource == 0 ? null : getContext().getDrawable(resource).mutate();
            if (glyph != null && !kind.equals("mark")) glyph.setTint(color);
        }

        void set(String kind, int color) {
            if (kind.equals(this.kind) && color == this.color) return;
            boolean changed = !kind.equals(this.kind);
            this.kind = kind;
            this.color = color;
            if (changed) loadGlyph();
            else if (glyph != null && !kind.equals("mark")) glyph.setTint(color);
            invalidate();
        }

        int color() { return color; }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (glyph != null) {
                float fit = Math.min(getWidth() / (float) glyph.getIntrinsicWidth(), getHeight() / (float) glyph.getIntrinsicHeight());
                int width = Math.round(glyph.getIntrinsicWidth() * fit), height = Math.round(glyph.getIntrinsicHeight() * fit);
                int left = (getWidth() - width) / 2, top = (getHeight() - height) / 2;
                glyph.setBounds(left, top, left + width, top + height);
                glyph.draw(canvas);
                return;
            }
            canvas.save();
            float scale = Math.min(getWidth(), getHeight()) / 24f;
            canvas.translate((getWidth() - 24 * scale) / 2, (getHeight() - 24 * scale) / 2);
            canvas.scale(scale, scale);
            p.setColor(color);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.85f);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            if (appPath != null) { canvas.drawPath(appPath, p); }
            else if (kind.equals("back")) { canvas.drawLine(19f, 12f, 5f, 12f, p); canvas.drawLine(5f, 12f, 11.5f, 5.5f, p); canvas.drawLine(5f, 12f, 11.5f, 18.5f, p); }
            else if (kind.equals("check")) { canvas.drawLine(5.5f, 12.5f, 10f, 17f, p); canvas.drawLine(10f, 17f, 18.5f, 7.5f, p); }
            else if (kind.equals("play")) { p.setStyle(Paint.Style.FILL_AND_STROKE); canvas.drawPath(playPath, p); }
            else if (kind.equals("chevron")) { canvas.drawLine(9.5f, 6.5f, 15f, 12f, p); canvas.drawLine(15f, 12f, 9.5f, 17.5f, p); }
            else if (kind.equals("refresh")) { canvas.drawArc(4.5f, 4.5f, 19.5f, 19.5f, 45, 280, false, p); canvas.drawPath(refreshArrow, p); }
            else if (kind.equals("calendar")) {
                canvas.drawRoundRect(4.5f, 5.5f, 19.5f, 20.5f, 2.5f, 2.5f, p);
                canvas.drawLine(8.5f, 3.2f, 8.5f, 6.8f, p);
                canvas.drawLine(15.5f, 3.2f, 15.5f, 6.8f, p);
                canvas.drawLine(4.5f, 9.8f, 19.5f, 9.8f, p);
                canvas.drawLine(8.5f, 13.5f, 10.5f, 13.5f, p);
                canvas.drawLine(13.5f, 13.5f, 15.5f, 13.5f, p);
                canvas.drawLine(8.5f, 16.8f, 10.5f, 16.8f, p);
                canvas.drawLine(13.5f, 16.8f, 15.5f, 16.8f, p);
            } else if (kind.equals("calendar-add") || kind.equals("calendar-check")) {
                canvas.drawRoundRect(4.5f, 5.5f, 19.5f, 20.5f, 2.5f, 2.5f, p);
                canvas.drawLine(8.5f, 3.2f, 8.5f, 6.8f, p);
                canvas.drawLine(15.5f, 3.2f, 15.5f, 6.8f, p);
                canvas.drawLine(4.5f, 9.8f, 19.5f, 9.8f, p);
                if (kind.equals("calendar-add")) {
                    canvas.drawLine(9.2f, 15.1f, 14.8f, 15.1f, p);
                    canvas.drawLine(12f, 12.3f, 12f, 17.9f, p);
                } else {
                    canvas.drawLine(8.8f, 15.2f, 11f, 17.4f, p);
                    canvas.drawLine(11f, 17.4f, 15.2f, 13.2f, p);
                }
            } else if (kind.equals("settings")) {
                for (int rowIndex = 0; rowIndex < 3; rowIndex++) {
                    float y = 6f + rowIndex * 6f;
                    float cx = rowIndex == 1 ? 9f : 15f;
                    canvas.drawLine(4f, y, cx - 2.3f, y, p);
                    canvas.drawCircle(cx, y, 2.3f, p);
                    canvas.drawLine(cx + 2.3f, y, 20f, y, p);
                }
            } else if (kind.equals("clock")) {
                canvas.drawCircle(12f, 12f, 8.2f, p);
                canvas.drawLine(12f, 7.5f, 12f, 12f, p);
                canvas.drawLine(12f, 12f, 15.5f, 14f, p);
            } else if (kind.equals("theme")) {
                canvas.drawCircle(12f, 12f, 8.2f, p);
                p.setStyle(Paint.Style.FILL);
                canvas.drawArc(3.8f, 3.8f, 20.2f, 20.2f, 90f, 180f, true, p);
            } else if (kind.equals("info")) {
                canvas.drawCircle(12f, 12f, 8.2f, p);
                canvas.drawLine(12f, 11.2f, 12f, 16f, p);
                p.setStyle(Paint.Style.FILL);
                canvas.drawCircle(12f, 8.1f, 0.9f, p);
            } else if (kind.equals("download")) {
                canvas.drawLine(12f, 4f, 12f, 14.5f, p);
                canvas.drawLine(7.8f, 10.3f, 12f, 14.5f, p);
                canvas.drawLine(16.2f, 10.3f, 12f, 14.5f, p);
                canvas.drawLine(4.5f, 16.5f, 4.5f, 19.5f, p);
                canvas.drawLine(4.5f, 19.5f, 19.5f, 19.5f, p);
                canvas.drawLine(19.5f, 19.5f, 19.5f, 16.5f, p);
            } else {
                canvas.drawRoundRect(3, 5, 21, 20, 2, 2, p);
                canvas.drawLine(6, 10, 12, 10, p);
                canvas.drawLine(6, 15, 12, 15, p);
                canvas.drawLine(17, 9, 17, 11, p);
                canvas.drawLine(17, 14, 17, 16, p);
            }
            canvas.restore();
        }
    }
}
