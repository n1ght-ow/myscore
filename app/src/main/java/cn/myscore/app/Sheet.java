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
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;

/** One in-app sheet language for every secondary menu. No platform AlertDialog. */
final class Sheet {
    interface Pick { void onPick(int index); }
    interface PickDate { void onDate(LocalDate date); }
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
        LinearLayout list = ui.column();
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            boolean on = i == selected;
            LinearLayout row = ui.row();
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(ui.dp(56));
            row.setPadding(ui.dp(14), ui.dp(10), ui.dp(10), ui.dp(10));
            ui.clickable(row, on ? ui.tonal : Color.TRANSPARENT, Ui.CONTROL_RADIUS);
            row.setFocusable(true);
            row.setContentDescription(item.title + (item.detail.isEmpty() ? "" : "，" + item.detail) + (on ? "，已选中" : ""));
            LinearLayout texts = ui.column();
            texts.addView(ui.text(item.title, 15, on ? ui.primary : ui.text, true));
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
        content.addView(list, new LinearLayout.LayoutParams(-1, -2));
        TextView cancel = ui.button("取消", false, dialog::dismiss);
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(-1, -2);
        cancelParams.topMargin = ui.dp(12);
        content.addView(cancel, cancelParams);
        show(activity, dialog, content);
    }

    static void date(Activity activity, LocalDate selected, PickDate pick) {
        Ui ui=ui(activity);
        Dialog dialog=base(activity,ui);
        LinearLayout content=content(activity,ui,dialog,"选择日期","可查看任意一天的赛程与比分。");
        // Keep seven 48dp date targets on compact phones without shrinking the type.
        int side=Math.max(0,Math.min(ui.dp(24),(activity.getResources().getDisplayMetrics().widthPixels-ui.dp(336))/2));
        content.setPadding(side,content.getPaddingTop(),side,content.getPaddingBottom());
        YearMonth[] month={YearMonth.from(selected)};
        LinearLayout nav=ui.row();
        FrameLayout prev=new FrameLayout(activity);
        prev.addView(new Ui.Icon(activity,"back",ui.primary),new FrameLayout.LayoutParams(ui.dp(20),ui.dp(20),Gravity.CENTER));
        ui.clickable(prev,ui.tonal,Ui.CONTROL_RADIUS);prev.setFocusable(true);prev.setContentDescription("上一个月");
        nav.addView(prev,new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));
        TextView title=ui.text("",16,ui.text,true);title.setGravity(Gravity.CENTER);
        nav.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        FrameLayout next=new FrameLayout(activity);
        next.addView(new Ui.Icon(activity,"chevron",ui.primary),new FrameLayout.LayoutParams(ui.dp(20),ui.dp(20),Gravity.CENTER));
        ui.clickable(next,ui.tonal,Ui.CONTROL_RADIUS);next.setFocusable(true);next.setContentDescription("下一个月");
        nav.addView(next,new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));
        content.addView(nav);
        LinearLayout weekdays=ui.row();
        for(String label:new String[]{"一","二","三","四","五","六","日"}){TextView d=ui.text(label,11,ui.muted,false);d.setGravity(Gravity.CENTER);d.setPadding(0,ui.dp(6),0,ui.dp(6));weekdays.addView(d,new LinearLayout.LayoutParams(0,-2,1));}
        content.addView(weekdays);
        LinearLayout grid=ui.column();content.addView(grid);
        Runnable[] draw=new Runnable[1];
        draw[0]=()-> {
            title.setText(String.format(Locale.CHINA,"%d年%d月",month[0].getYear(),month[0].getMonthValue()));
            grid.removeAllViews();
            LocalDate first=month[0].atDay(1);
            int offset=first.getDayOfWeek().getValue()-1;
            int days=month[0].lengthOfMonth();
            int rows=Math.max(5,(offset+days+6)/7);
            int day=1;
            for(int r=0;r<rows;r++) {
                LinearLayout line=ui.row();
                for(int c=0;c<7;c++) {
                    int index=r*7+c;
                    if(index<offset||index>=offset+days){line.addView(new View(activity),new LinearLayout.LayoutParams(0,-2,1));continue;}
                    LocalDate value=month[0].atDay(day++);
                    boolean picked=value.equals(selected),today=value.equals(Match.today(System.currentTimeMillis()));
                    TextView cell=ui.text(String.valueOf(value.getDayOfMonth()),14,picked?ui.onPrimary:today?ui.primary:ui.text,picked||today);
                    cell.setGravity(Gravity.CENTER);cell.setMinHeight(ui.dp(48));ui.clickable(cell,picked?ui.primary:today?ui.tonal:Color.TRANSPARENT,Ui.CONTROL_RADIUS);cell.setSelected(picked);
                    cell.setFocusable(true);
                    cell.setContentDescription(value.getMonthValue()+"月"+value.getDayOfMonth()+"日"+(picked?"，已选中":today?"，今天":""));
                    cell.setOnClickListener(v->{dialog.dismiss();pick.onDate(value);});
                    line.addView(cell,new LinearLayout.LayoutParams(0,-2,1));
                }
                grid.addView(line);
            }
        };
        prev.setOnClickListener(v->{month[0]=month[0].minusMonths(1);draw[0].run();});
        next.setOnClickListener(v->{month[0]=month[0].plusMonths(1);draw[0].run();});
        TextView cancel=ui.button("取消",false,dialog::dismiss);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=ui.dp(12);content.addView(cancel,cp);
        draw[0].run();
        show(activity,dialog,content);
    }

    static void message(Activity activity, String title, String message) {        Ui ui = ui(activity);
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
        return new Ui(activity, Ui.isDark(activity));
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
            window.setDimAmount(0.64f);
            window.setNavigationBarColor(ui.surface);
            window.setWindowAnimations(R.style.SheetAnimation);
        }
        return dialog;
    }
    private static LinearLayout content(Activity activity, Ui ui, Dialog dialog, String title, String message) {
        LinearLayout root = ui.column();
        GradientDrawable background = new GradientDrawable();
        background.setColor(ui.surface);
        float r = ui.dp(Ui.SHEET_RADIUS);
        background.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        root.setBackground(background);
        root.setPadding(ui.dp(24), ui.dp(8), ui.dp(24), ui.dp(24));
        View handle = new View(activity);
        handle.setBackground(ui.shape(ui.line, 2));
        handle.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(ui.dp(38), ui.dp(4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.topMargin = ui.dp(6);
        root.addView(handle, handleParams);
        if (title != null && !title.isEmpty()) {
            TextView heading = ui.text(title, 22, ui.text, true);
            heading.setPadding(0, ui.dp(16), 0, 0);
            root.addView(heading);
        }
        if (message != null && !message.isEmpty()) {
            TextView body = ui.text(message, 13, ui.muted, false);
            body.setLineSpacing(ui.dp(3), 1f);
            body.setPadding(0, ui.dp(8), 0, ui.dp(10));
            root.addView(body);
        }
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
        MaxScroll scroll=new MaxScroll(activity,maxHeight(activity));
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setBackground(content.getBackground());scroll.setClipToOutline(true);content.setBackground(null);
        scroll.addView(content);dialog.setContentView(scroll);
        try{dialog.show();}catch(RuntimeException e){android.util.Log.w("MyScore","Sheet skipped because the activity left");return;}
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            window.getDecorView().setPadding(0, 0, 0, 0);
            Ui.highRefresh(window);
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
