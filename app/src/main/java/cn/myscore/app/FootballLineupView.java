/** Source-backed formation and squad events; numbers replace portraits so the shape stays the data. */

package cn.myscore.app;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.myscore.app.DetailFormat;
import cn.myscore.app.DetailParsers;
import cn.myscore.app.DetailRows;
import cn.myscore.app.FootballLayout;
import cn.myscore.app.FootballMetrics;
import cn.myscore.app.Logos;
import cn.myscore.app.Match;
import cn.myscore.app.MatchDetail;
import cn.myscore.app.Sheet;
import cn.myscore.app.Ui;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

final class FootballLineupView {
    private final Ui ui;
    private final MatchDetail detail;
    private final LinearLayout body;

    FootballLineupView(Ui ui, MatchDetail detail, LinearLayout body) {
        this.ui = ui;
        this.detail = detail;
        this.body = body;
    }

    void show(String selected, Consumer<String> changed) {
        Match match = this.detail.match;
        boolean home = !selected.equals(match.awayId);
        String teamId = home ? match.homeId : match.awayId;
        LinearLayout selector = this.ui.row();
        selector.setTag((Object)"football-team-filter");
        String[] ids = new String[]{match.homeId, match.awayId};
        String[] names = new String[]{match.home, match.away};
        String[] logos = new String[]{match.homeLogo, match.awayLogo};
        for (int i = 0; i < 2; ++i) {
            String id = ids[i];
            boolean on = i == 0 ? home : !home;
            LinearLayout choice = this.ui.row();
            choice.setGravity(17);
            choice.setPadding(this.ui.dp(8.0f), this.ui.dp(10.0f), this.ui.dp(8.0f), this.ui.dp(10.0f));
            choice.setMinimumHeight(this.ui.dp(48.0f));
            this.ui.clickable((View)choice, on ? this.ui.tonal : this.ui.surface, 24);
            choice.addView((View)Logos.view(this.ui, logos[i], names[i], 22));
            TextView name = this.ui.text(names[i], 12, on ? this.ui.text : this.ui.muted, on);
            name.setPadding(this.ui.dp(8.0f), 0, 0, 0);
            choice.addView((View)name, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
            choice.setContentDescription((CharSequence)(names[i] + (on ? "，已选中" : "，查看阵容")));
            choice.setSelected(on);
            choice.setFocusable(true);
            choice.setOnClickListener(v -> changed.accept(id));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1.0f);
            if (i > 0) {
                params.setMarginStart(this.ui.dp(8.0f));
            }
            selector.addView((View)choice, (ViewGroup.LayoutParams)params);
        }
        this.body.addView((View)selector);
        this.ui.gap(this.body, 16);
        String team = home ? match.home : match.away;
        String formation = this.detail.facts.stream().filter(f -> f.label().equals(team + " 阵型")).map(MatchDetail.Fact::value).findFirst().orElse("");
        MatchDetail.Table starters = this.find(teamId, "首发");
        LinearLayout title = this.ui.row();
        title.addView((View)this.ui.text("首发阵容", 16, this.ui.text, true), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        title.addView((View)this.ui.text(formation.isBlank() ? "阵型未提供" : formation, 16, this.ui.text, true));

        List lines = starters == null ? List.of() : FootballLayout.lines(starters, formation);
        LinearLayout pitchHost = this.ui.column(), benchHost = this.ui.column();
        title.setMinimumHeight(this.ui.dp(48));pitchHost.addView(title);
        title.setTag("football-starters-heading");
        this.ui.gap(pitchHost, 12);
        this.body.addView(this.ui.columns(pitchHost, benchHost, 360, 24), new LinearLayout.LayoutParams(-1, -2));
        if (lines.isEmpty()) {
            pitchHost.addView((View)this.ui.text("完整首发站位尚未提供", 13, this.ui.muted, false));
            if (starters != null) {
                this.list(starters, pitchHost);
            }
        } else {
            LinearLayout pitch = new LinearLayout(this.ui.c){
                private final Paint ink;
                {
                    this.ink = new Paint(1);
                }

                protected void onDraw(Canvas canvas) {
                    super.onDraw(canvas);
                    this.ink.setColor(FootballLineupView.this.ui.secondary);
                    this.ink.setAlpha(48);
                    this.ink.setStyle(Paint.Style.STROKE);
                    this.ink.setStrokeWidth((float)FootballLineupView.this.ui.dp(1.0f));
                    float w = this.getWidth();
                    float h = this.getHeight();
                    float m = FootballLineupView.this.ui.dp(8.0f);
                    float box = w * 0.135f;
                    float goal = w * 0.05f;
                    canvas.drawRect(m, m, w - m, h - m, this.ink);
                    canvas.drawLine(m, h * 0.5f, w - m, h * 0.5f, this.ink);
                    int lineAlpha = this.ink.getAlpha();
                    this.ink.setAlpha(32);
                    canvas.drawCircle(w / 2.0f, h * 0.5f, w * 0.15f, this.ink);
                    this.ink.setAlpha(lineAlpha);
                    this.ink.setStyle(Paint.Style.FILL);
                    canvas.drawCircle(w / 2.0f, h * 0.5f, (float)FootballLineupView.this.ui.dp(1.4f), this.ink);
                    this.ink.setStyle(Paint.Style.STROKE);
                    canvas.drawRect(w * 0.22f, m, w * 0.78f, m + box, this.ink);
                    canvas.drawRect(w * 0.35f, m, w * 0.65f, m + goal, this.ink);
                    canvas.drawRect(w * 0.22f, h - m - box, w * 0.78f, h - m, this.ink);
                    canvas.drawRect(w * 0.35f, h - m - goal, w * 0.65f, h - m, this.ink);
                    float spot = FootballLineupView.this.ui.dp(1.2f);
                    this.ink.setStyle(Paint.Style.FILL);
                    canvas.drawCircle(w / 2.0f, m + box * 0.62f, spot, this.ink);
                    canvas.drawCircle(w / 2.0f, h - m - box * 0.62f, spot, this.ink);
                    this.ink.setStyle(Paint.Style.STROKE);
                    canvas.drawArc(w / 2.0f - w * 0.1f, m + box * 0.62f - w * 0.1f, w / 2.0f + w * 0.1f, m + box * 0.62f + w * 0.1f, 25.0f, 130.0f, false, this.ink);
                    canvas.drawArc(w / 2.0f - w * 0.1f, h - m - box * 0.62f - w * 0.1f, w / 2.0f + w * 0.1f, h - m - box * 0.62f + w * 0.1f, 205.0f, 130.0f, false, this.ink);
                    float corner = FootballLineupView.this.ui.dp(10.0f);
                    canvas.drawArc(m, m, m + corner * 2.0f, m + corner * 2.0f, 0.0f, 90.0f, false, this.ink);
                    canvas.drawArc(w - m - corner * 2.0f, m, w - m, m + corner * 2.0f, 90.0f, 90.0f, false, this.ink);
                    canvas.drawArc(m, h - m - corner * 2.0f, m + corner * 2.0f, h - m, 270.0f, 90.0f, false, this.ink);
                    canvas.drawArc(w - m - corner * 2.0f, h - m - corner * 2.0f, w - m, h - m, 180.0f, 90.0f, false, this.ink);
                }
            };
            pitch.setOrientation(LinearLayout.VERTICAL);
            pitch.setWillNotDraw(false);
            GradientDrawable background = this.ui.panel();
            background.setCornerRadius((float)this.ui.dp(20.0f));
            pitch.setBackground((Drawable)background);
            pitch.setClipToOutline(true);
            pitch.setTag((Object)"football-pitch");
            pitch.setPadding(this.ui.dp(6.0f), this.ui.dp(8.0f), this.ui.dp(6.0f), this.ui.dp(8.0f));
            float font = this.ui.c.getResources().getConfiguration().fontScale;
            ArrayList display = new ArrayList(lines);
            Collections.reverse(display);
            for (Object lineObj : display) {
                List line = (List)lineObj;
                if (font >= 1.5f) {
                    for (Object playerObj : line) {
                        pitch.addView(this.listPlayer((DetailRows.Entry)playerObj));
                    }
                    continue;
                }
                LinearLayout row = this.ui.row();
                row.setGravity(49);
                row.setPadding(0, this.ui.dp(9.0f), 0, this.ui.dp(9.0f));
                ArrayList across = new ArrayList(line);
                Collections.reverse(across);
                for (Object playerObj : across) {
                    row.addView(this.pitchPlayer((DetailRows.Entry)playerObj), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
                }
                pitch.addView((View)row);
            }
            pitchHost.addView((View)pitch, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
            this.ui.gap(pitchHost, 8);
            pitchHost.addView((View)this.ui.text(font >= 1.5f ? "按首发阵型分行 · 点球员查看详情" : "首发阵型示意，非平均站位 · 点球员查看详情", 11, this.ui.muted, false));
        }
        this.ui.gap(pitchHost, 12);
        this.legend(pitchHost);
        TextView benchHeading=this.ui.text("替补",16,this.ui.text,true);benchHeading.setMinimumHeight(this.ui.dp(48));benchHeading.setGravity(android.view.Gravity.CENTER_VERTICAL);benchHeading.setTag("football-bench-heading");benchHost.addView(benchHeading);this.ui.gap(benchHost,12);
        MatchDetail.Table bench = this.find(teamId, "替补");
        if (bench == null) {
            benchHost.addView((View)this.ui.text("替补名单尚未提供", 13, this.ui.muted, false));
        } else {
            this.list(bench, benchHost);
        }
        MatchDetail.Table injuries = this.find(teamId, "伤停");
        if (injuries != null) {
            this.headingInto(benchHost, "受伤与停赛");
            this.list(injuries, benchHost);
        }

    }

    private MatchDetail.Table find(String team, String category) {
        return this.detail.tables.stream().filter(t -> t.tab.equals("lineup") && t.teamId.equals(team) && t.category.equals(category)).findFirst().orElse(null);
    }

    private void headingInto(LinearLayout host, String title) {
        this.ui.gap(host, 20);
        host.addView((View)this.ui.text(title, 16, this.ui.text, true));
        this.ui.gap(host, 10);
    }

    private void list(MatchDetail.Table table, LinearLayout host) {
        LinearLayout list = this.ui.column();
        list.setPadding(this.ui.dp(12.0f), 0, this.ui.dp(12.0f), 0);
        list.setBackground((Drawable)this.ui.panel());
        for (int i = 0; i < table.rows.size(); ++i) {
            if (i > 0) {
                list.addView(this.ui.divider());
            }
            list.addView(this.listPlayer(new DetailRows.Entry(table, table.rows.get(i), i < table.people.size() ? table.people.get(i) : null)));
        }
        host.addView((View)list);
    }

    private View pitchPlayer(DetailRows.Entry entry) {
        MatchDetail.Person person = entry.person();
        LinearLayout player = this.ui.column();
        player.setGravity(17);
        player.setPadding(this.ui.dp(1.0f), this.ui.dp(1.0f), this.ui.dp(1.0f), this.ui.dp(1.0f));
        player.setTag((Object)"football-pitch-player");
        FrameLayout holder = new FrameLayout(this.ui.c);
        holder.addView(this.jersey(person, 42, 46, 15), (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(this.ui.dp(42.0f), this.ui.dp(46.0f), 17));
        player.addView((View)holder, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, this.ui.dp(48.0f)));
        TextView name = this.ui.text(this.shortName(person), 11, this.ui.text, true);
        name.setGravity(17);
        player.addView((View)name, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
        View stats = this.stats(entry, false);
        if (stats != null) {
            this.ui.gap(player, 3);
            player.addView(stats, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
        }
        this.click((View)player, entry);
        return player;
    }

    private View listPlayer(DetailRows.Entry entry) {
        LinearLayout row = this.ui.row();
        row.setPadding(this.ui.dp(4.0f), this.ui.dp(10.0f), this.ui.dp(4.0f), this.ui.dp(10.0f));
        MatchDetail.Person person = entry.person();
        if (person != null) {
            row.addView(this.jersey(person, 30, 34, 12), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.ui.dp(30.0f), this.ui.dp(34.0f)));
        }
        LinearLayout words = this.ui.column();
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        wp.setMarginStart(this.ui.dp(10.0f));
        words.addView((View)this.ui.text(person == null ? DetailRows.cell(entry.values(), 0) : person.name(), 14, this.ui.text, true));
        View meta = this.stats(entry, true);
        if (meta != null) {
            this.ui.gap(words, 4);
            words.addView(meta, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
        }
        row.addView((View)words, (ViewGroup.LayoutParams)wp);
        this.click((View)row, entry);
        return row;
    }

    private View stats(DetailRows.Entry entry, boolean pairing) {
        String swap;
        String position;
        Wrap line = new Wrap(!pairing);
        line.setGap(this.ui.dp(6.0f), this.ui.dp(3.0f));
        if (pairing && entry.person() != null && !(position = DetailParsers.label(entry.person().position())).isBlank() && !position.equals("替补")) {
            line.addView((View)this.ui.text(position, 10, this.ui.muted, false));
        }
        int goals = (int)DetailRows.number(FootballLayout.value(entry, "进球"));
        int yellow = (int)DetailRows.number(FootballLayout.value(entry, "黄牌"));
        int red = (int)DetailRows.number(FootballLayout.value(entry, "红牌"));
        if (goals > 0) {
            line.addView(this.mark("goal", goals > 1 ? String.valueOf(goals) : "", false));
        }
        if (yellow > 0) {
            line.addView(this.mark("yellow", yellow > 1 ? String.valueOf(yellow) : "", false));
        }
        if (red > 0) {
            line.addView(this.mark("red", red > 1 ? String.valueOf(red) : "", false));
        }
        String state = FootballLayout.value(entry, "状态");
        boolean off = state.equals("已换下");
        boolean in = state.equals("替补登场");
        if (off || in) {
            line.addView(this.mark(off ? "out" : "in", this.minute(entry), false));
        }
        if (pairing && (off || in) && !(swap = this.replacement(entry)).isBlank()) {
            line.addView((View)this.ui.text(swap, 10, this.ui.muted, false));
        }
        if (line.getChildCount() == 0) {
            return null;
        }
        return line;
    }

    private View mark(String kind, String text, boolean outline) {
        LinearLayout item = this.ui.row();
        EventMark icon = new EventMark(kind);
        item.addView((View)icon, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.ui.dp(12.0f), this.ui.dp(12.0f)));
        if (text != null && !text.isBlank()) {
            TextView label = this.ui.text(text, 10, this.ui.muted, true);
            label.setPadding(this.ui.dp(2.0f), 0, 0, 0);
            item.addView((View)label);
        }
        return item;
    }

    private String minute(DetailRows.Entry entry) {
        String sub = FootballLayout.value(entry, "换人");
        if (FootballMetrics.missing(sub)) {
            return "";
        }
        String first = sub.split(" ", 2)[0];
        return first.matches("[0-9+'’]+") ? first : "";
    }

    private String replacement(DetailRows.Entry entry) {
        String sub = FootballLayout.value(entry, "换人");
        if (FootballMetrics.missing(sub)) {
            return "";
        }
        int split = sub.indexOf("· ");
        return split < 0 ? "" : sub.substring(split + 2).trim();
    }

    private void legend(LinearLayout host) {
        LinearLayout legend = this.ui.column();
        legend.setTag((Object)"football-event-legend");
        Wrap wrap = new Wrap(true);
        wrap.setGap(this.ui.dp(12.0f), this.ui.dp(4.0f));
        for (String[] item : new String[][]{{"goal", "进球"}, {"yellow", "黄牌"}, {"red", "红牌"}, {"out", "换下"}, {"in", "换上"}}) {
            LinearLayout pair = this.ui.row();
            pair.addView(this.mark(item[0], "", false), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-2, -2));
            TextView label = this.ui.text(item[1], 11, this.ui.muted, false);
            label.setPadding(this.ui.dp(4.0f), 0, 0, 0);
            pair.addView((View)label);
            wrap.addView((View)pair);
        }
        legend.addView((View)wrap, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
        host.addView((View)legend);
    }

    private View jersey(MatchDetail.Person person, int width, int height, int numberSp) {
        FrameLayout token = new FrameLayout(this.ui.c);
        boolean keeper = person != null && DetailParsers.label(person.position()).equals("门将");
        token.addView((View)new Jersey(keeper), (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(-1, -1));
        TextView number = this.ui.text(person == null || person.jersey().isBlank() ? "—" : person.jersey(), numberSp, keeper ? this.ui.primary : this.ui.text, true);
        number.setTextSize(1, (float)numberSp);
        number.setGravity(17);
        number.setIncludeFontPadding(false);
        token.addView((View)number, (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(-1, -1));
        token.setImportantForAccessibility(2);
        return token;
    }

    private String shortName(MatchDetail.Person person) {
        if (person == null) {
            return "";
        }
        String name = DetailFormat.shortName(person.name());
        return name.isBlank() ? person.name() : name;
    }

    private void click(View view, DetailRows.Entry entry) {
        view.setMinimumHeight(this.ui.dp(48.0f));
        view.setFocusable(true);
        this.ui.clickable(view, 0, 8);
        String who = entry.person() == null ? DetailRows.cell(entry.values(), 0) : (entry.person().jersey().isBlank() ? "" : "球衣号 " + entry.person().jersey() + "，") + entry.person().name() + "，" + DetailParsers.label(entry.person().position());
        String events = FootballMetrics.events(entry);
        view.setContentDescription((CharSequence)(who + (events.isBlank() ? "" : "，" + events)));
        view.setOnClickListener(v -> {
            StringBuilder text = new StringBuilder();
            if (entry.person() != null && !entry.person().jersey().isBlank()) {
                text.append("球衣号  ").append(entry.person().jersey()).append('\n');
            }
            for (int i = 1; i < entry.table().columns.size(); ++i) {
                String key = entry.table().columns.get(i);
                if (key.equals("站位")) continue;
                text.append(key).append("  ").append(DetailParsers.label(DetailRows.cell(entry.values(), i))).append('\n');
            }
            Sheet.message((Activity)this.ui.c, entry.person() == null ? DetailRows.cell(entry.values(), 0) : entry.person().name(), text.toString());
        });
    }

    private int blend(int from, int to, float ratio) {
        return Color.rgb((int)Math.round((float)Color.red((int)from) + (float)(Color.red((int)to) - Color.red((int)from)) * ratio), (int)Math.round((float)Color.green((int)from) + (float)(Color.green((int)to) - Color.green((int)from)) * ratio), (int)Math.round((float)Color.blue((int)from) + (float)(Color.blue((int)to) - Color.blue((int)from)) * ratio));
    }

    private final class Wrap
    extends ViewGroup {
        private final boolean centered;
        private int gapX;
        private int gapY;
        private int[] lineOf;
        private int[] lineWidth;
        private int[] lineHeight;
        private int lineCount;

        Wrap(boolean centered) {
            super(FootballLineupView.this.ui.c);
            this.lineOf = new int[0];
            this.lineWidth = new int[0];
            this.lineHeight = new int[0];
            this.centered = centered;
            this.setClipChildren(false);
        }

        void setGap(int x, int y) {
            this.gapX = x;
            this.gapY = y;
        }

        protected void onMeasure(int widthSpec, int heightSpec) {
            int width = View.MeasureSpec.getSize((int)widthSpec);
            if (View.MeasureSpec.getMode((int)widthSpec) == View.MeasureSpec.UNSPECIFIED) {
                width = FootballLineupView.this.ui.c.getResources().getDisplayMetrics().widthPixels;
            }
            int available = Math.max(0, width - this.getPaddingLeft() - this.getPaddingRight());
            int count = this.getChildCount();
            this.ensure(count);
            int used = 0;
            int height = this.getPaddingTop() + this.getPaddingBottom();
            for (int i = 0; i < count; ++i) {
                View child = this.getChildAt(i);
                child.measure(View.MeasureSpec.makeMeasureSpec((int)available, (int)Integer.MIN_VALUE), View.MeasureSpec.makeMeasureSpec((int)0, (int)0));
                int cw = Math.min(child.getMeasuredWidth(), available);
                int ch = child.getMeasuredHeight();
                if (used > 0 && used + this.gapX + cw > available) {
                    height += this.lineHeight[this.lineCount - 1] + this.gapY;
                    used = 0;
                }
                if (used == 0) {
                    this.lineOf[i] = this.lineCount;
                    this.lineWidth[this.lineCount] = cw;
                    this.lineHeight[this.lineCount] = ch;
                    ++this.lineCount;
                } else {
                    this.lineOf[i] = this.lineCount - 1;
                    int n = this.lineCount - 1;
                    this.lineWidth[n] = this.lineWidth[n] + (this.gapX + cw);
                    this.lineHeight[this.lineCount - 1] = Math.max(this.lineHeight[this.lineCount - 1], ch);
                }
                used += used == 0 ? cw : this.gapX + cw;
            }
            if (this.lineCount > 0) {
                height += this.lineHeight[this.lineCount - 1];
            }
            this.setMeasuredDimension(width, height);
        }

        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int available = Math.max(0, this.getWidth() - this.getPaddingLeft() - this.getPaddingRight());
            int y = this.getPaddingTop();
            int index = 0;
            for (int line = 0; line < this.lineCount; ++line) {
                int x = this.centered ? this.getPaddingLeft() + Math.max(0, (available - this.lineWidth[line]) / 2) : this.getPaddingLeft();
                int first = index;
                while (index < this.getChildCount() && this.lineOf[index] == line) {
                    View child = this.getChildAt(index);
                    child.layout(x, y + Math.max(0, (this.lineHeight[line] - child.getMeasuredHeight()) / 2), x + child.getMeasuredWidth(), y + Math.max(0, (this.lineHeight[line] - child.getMeasuredHeight()) / 2) + child.getMeasuredHeight());
                    x += child.getMeasuredWidth() + this.gapX;
                    ++index;
                }
                if (index == first) break;
                y += this.lineHeight[line] + this.gapY;
            }
        }

        private void ensure(int count) {
            if (this.lineOf.length < count) {
                this.lineOf = new int[count];
                this.lineWidth = new int[count];
                this.lineHeight = new int[count];
            }
            this.lineCount = 0;
        }
    }

    private final class EventMark
    extends View {
        private final String kind;
        private final Paint paint;
        private final Path ball;

        EventMark(String kind) {
            super(FootballLineupView.this.ui.c);
            this.paint = new Paint(1);
            this.ball = new Path();
            this.kind = kind;
            this.setImportantForAccessibility(2);
            for (int i = 0; i < 5; ++i) {
                double a = Math.PI * 2 * (double)i / 5.0 - 1.5707963267948966;
                float x = 10.0f + (float)Math.cos(a) * 3.4f;
                float y = 10.0f + (float)Math.sin(a) * 3.4f;
                if (i == 0) {
                    this.ball.moveTo(x, y);
                    continue;
                }
                this.ball.lineTo(x, y);
            }
            this.ball.close();
        }

        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.save();
            canvas.scale((float)this.getWidth() / 20.0f, (float)this.getHeight() / 20.0f);
            this.paint.setStyle(Paint.Style.FILL);
            if (this.kind.equals("yellow") || this.kind.equals("red")) {
                this.paint.setColor(this.kind.equals("yellow") ? Color.rgb((int)244, (int)201, (int)64) : Color.rgb((int)217, (int)55, (int)66));
                canvas.drawRoundRect(5.0f, 2.0f, 15.0f, 18.0f, 2.2f, 2.2f, this.paint);
            } else if (this.kind.equals("goal")) {
                this.paint.setColor(FootballLineupView.this.ui.text);
                this.paint.setStyle(Paint.Style.STROKE);
                this.paint.setStrokeWidth(1.6f);
                this.paint.setStrokeCap(Paint.Cap.ROUND);
                canvas.drawCircle(10.0f, 10.0f, 7.0f, this.paint);
                this.paint.setStyle(Paint.Style.FILL);
                canvas.drawPath(this.ball, this.paint);
            } else {
                this.paint.setColor(this.kind.equals("out") ? FootballLineupView.this.ui.error : FootballLineupView.this.ui.primary);
                this.paint.setStrokeWidth(2.2f);
                this.paint.setStrokeCap(Paint.Cap.ROUND);
                boolean out = this.kind.equals("out");
                canvas.drawLine(10.0f, 4.0f, 10.0f, 16.0f, this.paint);
                float tip = out ? 16.0f : 4.0f;
                canvas.drawLine(10.0f, tip, 6.0f, out ? 12.0f : 8.0f, this.paint);
                canvas.drawLine(10.0f, tip, 14.0f, out ? 12.0f : 8.0f, this.paint);
            }
            canvas.restore();
        }
    }

    private final class Jersey
    extends View {
        private final boolean keeper;
        private final Paint ink;
        private final Path shirt;

        Jersey(boolean keeper) {
            super(FootballLineupView.this.ui.c);
            this.ink = new Paint(1);
            this.shirt = new Path();
            this.keeper = keeper;
            this.setImportantForAccessibility(2);
        }

        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = this.getWidth();
            float h = this.getHeight();
            float pad = Math.max(1, FootballLineupView.this.ui.dp(0.8f));
            this.shirt.reset();
            this.shirt.moveTo(w * 0.3f, pad);
            this.shirt.lineTo(w * 0.42f, pad);
            this.shirt.quadTo(w * 0.5f, h * 0.1f, w * 0.58f, pad);
            this.shirt.lineTo(w * 0.7f, pad);
            this.shirt.lineTo(w * 0.97f - pad, h * 0.19f);
            this.shirt.lineTo(w * 0.86f, h * 0.36f);
            this.shirt.lineTo(w * 0.79f, h * 0.3f);
            this.shirt.lineTo(w * 0.79f, h - pad);
            this.shirt.quadTo(w * 0.79f, h - h * 0.03f, w * 0.72f, h - h * 0.03f);
            this.shirt.lineTo(w * 0.28f, h - h * 0.03f);
            this.shirt.quadTo(w * 0.21f, h - h * 0.03f, w * 0.21f, h - pad);
            this.shirt.lineTo(w * 0.21f, h * 0.3f);
            this.shirt.lineTo(w * 0.14f, h * 0.36f);
            this.shirt.lineTo(w * 0.03f + pad, h * 0.19f);
            this.shirt.close();
            this.ink.setStyle(Paint.Style.FILL);
            this.ink.setColor(this.keeper ? FootballLineupView.this.blend(FootballLineupView.this.ui.tonal, FootballLineupView.this.ui.primary, 0.2f) : FootballLineupView.this.ui.tonal);
            canvas.drawPath(this.shirt, this.ink);
            this.ink.setStyle(Paint.Style.STROKE);
            this.ink.setStrokeWidth((float)FootballLineupView.this.ui.dp(1.0f));
            this.ink.setColor(this.keeper ? FootballLineupView.this.ui.primary : FootballLineupView.this.ui.line);
            canvas.drawPath(this.shirt, this.ink);
        }
    }
}
