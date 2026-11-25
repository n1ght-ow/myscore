/** NBA players and CS2 players share two team panels, side by side on wide tablets. */

package cn.myscore.app;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.myscore.app.DetailFormat;
import cn.myscore.app.DetailParsers;
import cn.myscore.app.DetailRows;
import cn.myscore.app.DetailTableView;
import cn.myscore.app.Logos;
import cn.myscore.app.MatchDetail;
import cn.myscore.app.Sheet;
import cn.myscore.app.Ui;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

final class TeamPanelsView {
    private static final String INJURY = "伤停";
    private final Ui ui;
    private final MatchDetail detail;
    private final LinearLayout body;

    TeamPanelsView(Ui ui, MatchDetail detail, LinearLayout body) {
        this.ui = ui;
        this.detail = detail;
        this.body = body;
    }

    void show(String sort, boolean descending, Consumer<Sort> changed, int scrollX, IntConsumer savedScroll) {
        boolean anyInjuries;
        boolean nba = this.nba();
        String category = this.scope(nba);
        int players = this.countRows(this.teamTables(category, this.detail.match.homeId)) + this.countRows(this.teamTables(category, this.detail.match.awayId));
        anyInjuries = nba && !this.injuryTables(null).isEmpty();
        if (players == 0 && !anyInjuries) {
            this.body.addView((View)this.ui.text("Match data is not available", 18, this.ui.text, true));
            this.ui.gap(this.body, 10);
            this.body.addView((View)this.ui.text("Source-backed match data will appear after the next update.", 13, this.ui.muted, false));
            return;
        }
        String title = nba ? "Player stats" : (category.equals("全场") ? "Overall player stats" : EnglishDisplay.text(category) + " player stats");
        this.body.addView((View)Ui.singleLine(this.ui.text(title, 16, this.ui.text, true)));
        this.ui.gap(this.body, 2);
        TextView hint = Ui.singleLine(this.ui.text(players > 0 ? "Tap column to sort · Tap player for details" : "Injury report follows the source", 11, this.ui.dim, false));
        this.body.addView((View)hint);
        this.ui.gap(this.body, 8);
        List<HorizontalScrollView> scrollers = this.teams(category, nba, sort, descending, changed, scrollX, Map.of(), Map.of());
        this.bind(scrollers, savedScroll);
    }

    void scope(String category, String sort, boolean descending, Consumer<Sort> changed, int scrollX, IntConsumer savedScroll, Map<String, String> homeScore, Map<String, String> awayScore) {
        this.bind(this.teams(category, false, sort, descending, changed, scrollX, homeScore, awayScore), savedScroll);
    }

    private List<HorizontalScrollView> teams(String category, boolean injuries, String sort, boolean descending, Consumer<Sort> changed, int scrollX, Map<String, String> homeScore, Map<String, String> awayScore) {
        List<MatchDetail.Table> home = this.teamTables(category, this.detail.match.homeId);
        List<MatchDetail.Table> away = this.teamTables(category, this.detail.match.awayId);
        List<String> columns = !home.isEmpty() ? home.get((int)0).columns : (!away.isEmpty() ? away.get((int)0).columns : List.of());
        String effective = this.effectiveSort(columns, sort);
        boolean anyInjuries = injuries && !this.injuryTables(null).isEmpty();
        ArrayList<HorizontalScrollView> scrollers = new ArrayList<HorizontalScrollView>();
        LinearLayout left = this.ui.column(), right = this.ui.column();
        scrollers.addAll(this.team(this.detail.match.homeId, category, effective, descending, changed, scrollX, anyInjuries, homeScore, left));
        scrollers.addAll(this.team(this.detail.match.awayId, category, effective, descending, changed, scrollX, anyInjuries, awayScore, right));
        this.body.addView(this.ui.columns(left, right, 440, 18), new LinearLayout.LayoutParams(-1, -2));
        return scrollers;
    }

    private List<HorizontalScrollView> team(String teamId, String category, String sort, boolean descending, Consumer<Sort> changed, int scrollX, boolean anyInjuries, Map<String, String> score, LinearLayout host) {
        List<MatchDetail.Table> tables = this.displayTables(this.teamTables(category, teamId));
        List<DetailRows.Entry> rows = DetailRows.select(tables, category, teamId, sort, descending);
        LinearLayout section = this.ui.column();
        section.setTag((Object)"team-panel");
        host.addView((View)section);
        LinearLayout header = this.ui.row();
        header.setTag((Object)"team-panel-header");
        header.setPadding(0, this.ui.dp(2), 0, this.ui.dp(2));
        String name = this.teamName(teamId);
        header.addView((View)Logos.view(this.ui, this.teamLogo(teamId), name, 24), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.ui.dp(24.0f), this.ui.dp(24.0f)));
        TextView title = Ui.singleLine(this.ui.text(name, 15, this.ui.text, true));
        title.setPadding(this.ui.dp(8.0f), 0, this.ui.dp(8.0f), 0);
        header.addView((View)title, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        header.addView((View)this.ui.badge(players(rows.size()), this.ui.tonal, this.ui.muted));
        header.setContentDescription((CharSequence)EnglishDisplay.text(name + ", " + players(rows.size())));
        section.addView((View)header);
        this.ui.gap(section, 8);
        if (score != null && !score.isEmpty()) {
            LinearLayout band = this.ui.row();
            band.setTag((Object)"team-map-score");
            band.setBackground((Drawable)this.ui.outlined(this.ui.tonal, this.ui.border, 12));
            band.setPadding(this.ui.dp(8.0f), this.ui.dp(8.0f), this.ui.dp(8.0f), this.ui.dp(8.0f));
            StringBuilder description = new StringBuilder(name);
            for (Map.Entry<String, String> cell : score.entrySet()) {
                LinearLayout item = this.ui.column();
                item.setGravity(1);
                String labelText = EnglishDisplay.text(cell.getKey());
                TextView label2 = Ui.singleLine(this.ui.text(labelText, 10, this.ui.muted, true));
                label2.setGravity(17);
                String valueText = EnglishDisplay.text(cell.getValue());
                boolean isTotal = cell.getKey().equals("总分");
                TextView value = Ui.singleLine(this.ui.text(valueText, 14, isTotal ? this.ui.primary : this.ui.text, true));
                value.setGravity(17);
                if (isTotal) {
                    value.setTag((Object)"team-map-score-total");
                }
                item.addView((View)label2);
                item.addView((View)value);
                band.addView((View)item, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
                description.append(", ").append(labelText).append(' ').append(valueText);
            }
            band.setContentDescription((CharSequence)EnglishDisplay.text(description.toString()));
            section.addView((View)band);
            this.ui.gap(section, 8);
        }
        ArrayList<HorizontalScrollView> scrollers = new ArrayList<HorizontalScrollView>();
        if (rows.isEmpty()) {
            section.addView((View)this.ui.text("The source did not provide data for this team.", 13, this.ui.muted, false));
        } else {
            View table = new DetailTableView(this.ui, this.nba(), false, !this.nba(), 0).build(tables.get((int)0).columns, rows, sort, descending, changed == null ? null : label -> changed.accept(new Sort((String)label, label.equals(sort) ? !descending : true)), null, scrollX);
            table.setContentDescription((CharSequence)EnglishDisplay.text(name + " player stats"));
            section.addView(table, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
            HorizontalScrollView scroller = (HorizontalScrollView)table.findViewWithTag((Object)"detail-metrics-scroll");
            if (scroller != null) {
                scrollers.add(scroller);
            }
        }
        if (anyInjuries) {
            this.ui.gap(section, 12);
            this.injuries(section, teamId);
        }
        return scrollers;
    }

    private void injuries(LinearLayout section, String teamId) {
        List<MatchDetail.Table> tables = this.injuryTables(teamId);
        List<DetailRows.Entry> entries = DetailRows.select(tables, INJURY, teamId, "", true);
        LinearLayout caption = this.ui.row();
        caption.setTag((Object)"team-panel-injury-header");
        caption.setPadding(this.ui.dp(4.0f), 0, this.ui.dp(4.0f), 0);
        caption.addView((View)this.ui.text(EnglishDisplay.text(INJURY), 13, this.ui.text, true), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        if (!entries.isEmpty()) {
            caption.addView((View)this.ui.text(players(entries.size()), 11, this.ui.muted, false));
        }
        caption.setContentDescription((CharSequence)("Injuries, " + (entries.isEmpty() ? "No injury report from the source" : players(entries.size()))));
        section.addView((View)caption);
        if (entries.isEmpty()) {
            this.ui.gap(section, 6);
            section.addView((View)this.ui.text("No injury report from the source", 12, this.ui.muted, false));
            return;
        }
        for (int i = 0; i < entries.size(); ++i) {
            if (i > 0) {
                section.addView(this.ui.divider());
            }
            section.addView(this.injuryRow(entries.get(i), teamId));
        }
    }

    private View injuryRow(DetailRows.Entry entry, String teamId) {
        MatchDetail.Person person = entry.person();
        String name = this.injuryName(entry);
        String team = this.teamName(teamId);
        int statusIndex = entry.table().columns.indexOf("状态");
        int noteIndex = entry.table().columns.indexOf("说明");
        String status = DetailParsers.label(DetailRows.cell(entry.values(), statusIndex < 0 ? 1 : statusIndex)).trim();
        String statusText = EnglishDisplay.text(status);
        String rawNote = DetailRows.cell(entry.values(), noteIndex < 0 ? 2 : noteIndex);
        String note = rawNote.equals("—") ? "" : rawNote;
        String noteText = EnglishDisplay.text(note);
        LinearLayout row = this.ui.row();
        row.setGravity(48);
        row.setTag((Object)"team-panel-injury");
        row.setMinimumHeight(this.ui.dp(44.0f));
        row.setPadding(this.ui.dp(4.0f), this.ui.dp(8.0f), this.ui.dp(4.0f), this.ui.dp(8.0f));
        LinearLayout words = this.ui.column();
        words.addView((View)Ui.singleLine(this.ui.text(name, 13, this.ui.text, true)));
        if (!noteText.isBlank()) {
            this.ui.gap(words, 3);
            words.addView((View)this.ui.text(noteText, 12, this.ui.muted, false));
        }
        row.addView((View)words, (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        if (!status.isBlank() && !status.equals("—")) {
            String lower = statusText.toLowerCase(java.util.Locale.ROOT);
            boolean out = lower.contains("out") || lower.contains("suspend") || lower.contains("缺阵");
            boolean doubtful = lower.contains("question") || lower.contains("day") || lower.contains("doubt") || lower.contains("出战成疑");
            int fill = out ? this.ui.errorSoft : doubtful ? this.ui.warnSoft : this.ui.tonal;
            int ink = out ? this.ui.error : doubtful ? this.ui.warn : this.ui.text;
            TextView badge = this.ui.badge(statusText, fill, ink);
            badge.setTag((Object)"team-panel-injury-status");
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
            params.setMarginStart(this.ui.dp(8.0f));
            row.addView((View)badge, (ViewGroup.LayoutParams)params);
        }
        String description = name + (!status.isBlank() && !status.equals("—") ? ", " + statusText : "") + (noteText.isBlank() ? "" : ", " + noteText);
        row.setContentDescription((CharSequence)EnglishDisplay.text(description));
        row.setFocusable(true);
        this.ui.clickable((View)row, this.ui.bg, 0);
        String fullName = person == null || person.name().isBlank() ? name : person.name();
        row.setOnClickListener(v -> {
            if (!(this.ui.c instanceof Activity)) {
                return;
            }
            StringBuilder text = new StringBuilder("Team  ").append(team);
            if (!status.isBlank() && !status.equals("—")) {
                text.append('\n').append("Status  ").append(statusText);
            }
            if (!noteText.isBlank()) {
                text.append('\n').append("Note  ").append(noteText);
            }
            Sheet.message((Activity)this.ui.c, fullName, text.toString());
        });
        return row;
    }

    private String injuryName(DetailRows.Entry entry) {
        MatchDetail.Person person = entry.person();
        if (person == null) {
            return DetailRows.cell(entry.values(), 0);
        }
        String shortName = person.shortName();
        return shortName == null || shortName.isBlank() ? DetailFormat.shortName(person.name()) : shortName;
    }

    private List<MatchDetail.Table> displayTables(List<MatchDetail.Table> source) {
        if (this.nba()) {
            return source;
        }
        ArrayList<MatchDetail.Table> tables = new ArrayList<MatchDetail.Table>(source.size());
        for (MatchDetail.Table table : source) {
            tables.add(this.ratingFirst(table));
        }
        return tables;
    }

    private MatchDetail.Table ratingFirst(MatchDetail.Table source) {
        int rating = source.columns.indexOf("Rating");
        if (rating < 0 || source.columns.isEmpty() || !source.columns.get(0).equals("选手")) {
            return source;
        }
        MatchDetail.Table display = new MatchDetail.Table(source.tab, source.title, new String[0]);
        display.teamId = source.teamId;
        display.category = source.category;
        display.columns.add(source.columns.get(0));
        display.columns.add("Rating");
        for (int i = 1; i < source.columns.size(); ++i) {
            if (i == rating) continue;
            display.columns.add(source.columns.get(i));
        }
        for (List<String> row : source.rows) {
            if (row.size() <= rating) {
                return source;
            }
            ArrayList<String> values = new ArrayList<String>();
            values.add(row.get(0));
            values.add(row.get(rating));
            for (int i = 1; i < row.size(); ++i) {
                if (i == rating) continue;
                values.add(row.get(i));
            }
            display.rows.add(values);
        }
        display.people.addAll(source.people);
        return display;
    }

    private String effectiveSort(List<String> columns, String sort) {
        String value = sort == null ? "" : sort;
        if (!value.isBlank() && columns.contains(value)) {
            return value;
        }
        return this.nba() && columns.contains("得分") ? "得分" : "";
    }

    private String scope(boolean nba) {
        String preferred = nba ? "球员" : "全场";
        String first = "";
        for (MatchDetail.Table table : this.detail.tables) {
            if (!table.tab.equals("lineup") || table.category.isBlank() || table.category.equals(INJURY)) continue;
            if (table.category.equals(preferred)) {
                return preferred;
            }
            if (!first.isEmpty()) continue;
            first = table.category;
        }
        return nba && !first.isEmpty() ? first : preferred;
    }

    private List<MatchDetail.Table> teamTables(String category, String teamId) {
        ArrayList<MatchDetail.Table> tables = new ArrayList<MatchDetail.Table>();
        for (MatchDetail.Table table : this.detail.tables) {
            if (!table.tab.equals("lineup") || !table.category.equals(category) || teamId != null && !table.teamId.equals(teamId)) continue;
            tables.add(table);
        }
        return tables;
    }

    private List<MatchDetail.Table> injuryTables(String teamId) {
        ArrayList<MatchDetail.Table> tables = new ArrayList<MatchDetail.Table>();
        for (MatchDetail.Table table : this.detail.tables) {
            if (!table.tab.equals("lineup") || !table.category.equals(INJURY) || teamId != null && !table.teamId.equals(teamId)) continue;
            tables.add(table);
        }
        return tables;
    }

    private int countRows(List<MatchDetail.Table> tables) {
        int count = 0;
        for (MatchDetail.Table table : tables) {
            count += table.rows.size();
        }
        return count;
    }

    private boolean nba() {
        return this.detail.match.group.equals("NBA");
    }

    private String teamName(String teamId) {
        return teamId.equals(this.detail.match.awayId) ? this.detail.match.away : this.detail.match.home;
    }

    private String teamLogo(String teamId) {
        return teamId.equals(this.detail.match.awayId) ? this.detail.match.awayLogo : this.detail.match.homeLogo;
    }

    private void bind(List<HorizontalScrollView> scrollers, IntConsumer savedScroll) {
        if (scrollers.isEmpty()) {
            return;
        }
        boolean[] moving = new boolean[]{false};
        for (HorizontalScrollView scroller : scrollers) {
            scroller.setOnScrollChangeListener((v, x, y, oldX, oldY) -> {
                if (savedScroll != null) {
                    savedScroll.accept(x);
                }
                if (moving[0]) {
                    return;
                }
                moving[0] = true;
                for (HorizontalScrollView other : scrollers) {
                    if (other == v) continue;
                    other.scrollTo(x, 0);
                }
                moving[0] = false;
            });
        }
    }

    private static String players(int count) {
        return count + " " + (count == 1 ? "player" : "players");
    }

    record Sort(String column, boolean descending) {
    }
}
