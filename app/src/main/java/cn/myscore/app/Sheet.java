package cn.myscore.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;

/** One in-app sheet language for every secondary menu. No platform AlertDialog. */
final class Sheet {
    interface Pick { void onPick(int index); }
    interface Action { void run(); }
    static final class Item {
        final String title, detail;
        final boolean selected;
        Item(String title, String detail, boolean selected) {this.title=title;this.detail=detail;this.selected=selected;}
    }
    private Sheet() {}

    static void choose(Activity activity, String title, String message, List<Item> items, int selected, Pick pick) {
        Ui ui = ui(activity);
        Dialog dialog = base(activity, ui);
        LinearLayout content = content(activity, ui, dialog, title, message);
        MaxScroll scroll = new MaxScroll(activity, maxHeight(activity));
        LinearLayout list = ui.column();
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            boolean on = i == selected;
            LinearLayout row = ui.row();
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(ui.dp(56));
            row.setPadding(ui.dp(14), ui.dp(10), ui.dp(10), ui.dp(10));
            ui.clickable(row, on ? ui.tonal : ui.bg, 12);
            row.setFocusable(true);
            row.setContentDescription(item.title + (item.detail.isEmpty() ? "" : "，" + item.detail) + (on ? "，已选中" : ""));
            LinearLayout texts = ui.column();
            texts.addView(ui.text(item.title, 15, ui.text, true));
            if (!item.detail.isEmpty()) { ui.gap(texts, 3); texts.addView(ui.text(item.detail, 12, ui.muted, false)); }
            row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
            if (on) {
                FrameLayout tick = new FrameLayout(activity);
                tick.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                tick.addView(new Ui.Icon(activity, "check", ui.primary), new FrameLayout.LayoutParams(ui.dp(20), ui.dp(20), Gravity.CENTER));
                row.addView(tick, new LinearLayout.LayoutParams(ui.dp(28), ui.dp(28)));
            }
            final int index = i;
            row.setOnClickListener(v -> { dialog.dismiss(); if (pick != null) pick.onPick(index); });
            list.addView(row);
            if (i < items.size() - 1) { View gap = new View(activity); gap.setMinimumHeight(ui.dp(2)); list.addView(gap); }
        }
        scroll.addView(list);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
        TextView cancel = ui.button("取消", false, dialog::dismiss);
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(-1, -2);
        cancelParams.topMargin = ui.dp(12);
        content.addView(cancel, cancelParams);
        show(activity, dialog, content);
    }

    static void message(Activity activity, String title, String message) {
        Ui ui = ui(activity);
        Dialog dialog = base(activity, ui);
        LinearLayout content = content(activity, ui, dialog, title, message);
        content.addView(actions(activity, ui, dialog, "知道了", () -> {}, null));
        show(activity, dialog, content);
    }

    static void confirm(Activity activity, String title, String message, String confirmLabel, Action action) {
        Ui ui = ui(activity);
        Dialog dialog = base(activity, ui);
        LinearLayout content = content(activity, ui, dialog, title, message);
        content.addView(actions(activity, ui, dialog, confirmLabel, action, "取消"));
        show(activity, dialog, content);
    }

    private static Ui ui(Activity activity) {
        String theme = activity.getSharedPreferences("settings", 0).getString("theme", "system");
        boolean dark = "dark".equals(theme) || (!"light".equals(theme) && (activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES);
        return new Ui(activity, dark);
    }
    private static Dialog base(Activity activity, Ui ui) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.BOTTOM);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.46f);
            window.setWindowAnimations(R.style.SheetAnimation);
        }
        return dialog;
    }
    private static LinearLayout content(Activity activity, Ui ui, Dialog dialog, String title, String message) {
        LinearLayout root = ui.column();
        GradientDrawable background = new GradientDrawable();
        background.setColor(ui.surface);
        float r = ui.dp(22);
        background.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        root.setBackground(background);
        root.setPadding(ui.dp(20), ui.dp(8), ui.dp(20), ui.dp(18));
        View handle = new View(activity);
        handle.setBackground(ui.shape(ui.line, 2));
        handle.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(ui.dp(38), ui.dp(4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.topMargin = ui.dp(6);
        root.addView(handle, handleParams);
        if (title != null && !title.isEmpty()) {
            TextView heading = ui.text(title, 20, ui.text, true);
            heading.setPadding(0, ui.dp(16), 0, 0);
            root.addView(heading);
        }
        if (message != null && !message.isEmpty()) {
            TextView body = ui.text(message, 13, ui.muted, false);
            body.setLineSpacing(ui.dp(3), 1f);
            body.setPadding(0, ui.dp(8), 0, ui.dp(10));
            root.addView(body);
        }
        dialog.setContentView(root);
        return root;
    }
    private static LinearLayout actions(Activity activity, Ui ui, Dialog dialog, String primaryLabel, Action primary, String secondaryLabel) {
        LinearLayout row = ui.row();
        row.setPadding(0, ui.dp(12), 0, 0);
        if (secondaryLabel != null) {
            TextView cancel = ui.button(secondaryLabel, false, dialog::dismiss);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1);
            cp.setMarginEnd(ui.dp(10));
            row.addView(cancel, cp);
        }
        TextView ok = ui.button(primaryLabel, true, () -> { dialog.dismiss(); if (primary != null) primary.run(); });
        row.addView(ok, new LinearLayout.LayoutParams(secondaryLabel == null ? -1 : 0, -2, secondaryLabel == null ? 0 : 1));
        return row;
    }
    private static int maxHeight(Activity activity) {
        return Math.max(activity.getResources().getDisplayMetrics().heightPixels - activity.getResources().getDisplayMetrics().heightPixels / 4, 1);
    }
    private static void show(Activity activity, Dialog dialog, LinearLayout content) {
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            window.getDecorView().setPadding(0, 0, 0, 0);
        }
    }
    private static final class MaxScroll extends ScrollView {
        private final int max;
        MaxScroll(Context context, int max) {super(context);this.max=max;setFillViewport(false);setClipToPadding(false);}
        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST));
        }
    }
}
