package cn.myscore.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class StandingsBoard {
    private final Ui ui;

    StandingsBoard(Ui ui) { this.ui = ui; }

    record Panel(String title, List<Standings.Section> sections) {}

    static List<Panel> panels(String competition, List<Standings.Section> sections) {
        if (!competition.equals("nfl")) {
            return sections.stream().map(section -> new Panel(section.title(), List.of(section))).collect(Collectors.toList());
        }
        Map<String, List<Standings.Section>> groups = new LinkedHashMap<>();
        for (Standings.Section section : sections) {
            String title = section.title();
            String conference = title.startsWith("AFC") ? "AFC" : title.startsWith("NFC") ? "NFC" : title;
            String division = title.substring(conference.length()).trim();
            groups.computeIfAbsent(conference, unused -> new ArrayList<>())
                .add(new Standings.Section(division, section.columns(), section.rows()));
        }
        return groups.entrySet().stream().map(entry -> new Panel(entry.getKey(), entry.getValue())).collect(Collectors.toList());
    }

    void panel(LinearLayout into, Panel panel, String title, String competition, String identity, String rankLabel,
               String rankA11y, boolean showTitle, List<Standings.Section> peers) {
        if (showTitle && !title.isBlank()) heading(into, title, 16);
        int index = 0;
        for (Standings.Section section : panel.sections()) {
            if (index++ > 0) ui.gap(into, 16);
            if (!section.title().isBlank() && !section.title().equals(panel.title())) heading(into, section.title(), 14);
            into.addView(table(section, competition, identity, rankLabel, rankA11y, peers), new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private void heading(LinearLayout into, String label, int size) {
        TextView heading = ui.text(StandingsEnglish.text(label), size, ui.text, true);
        heading.setAccessibilityHeading(true);
        into.addView(heading);
        ui.gap(into, 8);
    }

    View table(Standings.Section section, String competition, String identity, String rankLabel, String rankA11y,
               List<Standings.Section> peers) {
        if (section.rows().isEmpty()) return ui.text("No standings published yet.", 14, ui.muted, false);
        List<Integer> order = columnOrder(section.columns());
        int[] widths = new int[order.size()];
        LinearLayout names = ui.column(), metrics = ui.column();
        List<View> nameRows = new ArrayList<>(), metricRows = new ArrayList<>();
        Paint headerPaint = ui.text("", 11, ui.muted, true).getPaint();
        Paint regular = ui.text("", 13, ui.text, false).getPaint();
        Paint strong = ui.text("", 13, ui.text, true).getPaint();
        Paint rankPaint = ui.text("", 12, ui.muted, true).getPaint();
        List<Standings.Row> peersRows = peers.stream().filter(peer -> peer.columns().equals(section.columns()))
            .flatMap(peer -> peer.rows().stream()).collect(Collectors.toList());
        boolean largeType = ui.c.getResources().getConfiguration().fontScale >= 1.5f;
        int rankWidth = width(headerPaint, rankLabel);
        boolean ranks = peersRows.stream().anyMatch(row -> !row.rank().isBlank());
        boolean crests = !largeType && peersRows.stream().anyMatch(row -> !row.logo().isBlank());
        for (Standings.Row row : peersRows) rankWidth = Math.max(rankWidth, width(rankPaint, row.rank()));
        rankWidth += ui.dp(8);

        LinearLayout labelRow = ui.row();
        for (int index = 0; index < order.size(); index++) {
            int column = order.get(index);
            String key = section.columns().get(column);
            String label = StandingsEnglish.column(competition, key);
            boolean primary = key.equals("积分");
            int cellWidth = width(headerPaint, label);
            for (Standings.Row row : peersRows) cellWidth = Math.max(cellWidth, width(primary ? strong : regular, value(row, column)));
            widths[index] = cellWidth + ui.dp(4) + ui.dp(6) + 2;
            TextView header = metric(label, 11, ui.muted, true);
            header.setAccessibilityHeading(true);
            labelRow.addView(header, new LinearLayout.LayoutParams(widths[index], -2));
        }
        View nameHeader = identity(identity, rankLabel, "", crests, ranks, rankWidth, "", true, false);
        addRow(names, nameRows, nameHeader);
        addRow(metrics, metricRows, labelRow);
        String lastStatus = "";
        for (Standings.Row row : section.rows()) {
            if (!row.status().equals(lastStatus)) {
                addRow(names, nameRows, zone(row.status()));
                addRow(metrics, metricRows, zone(""));
            }
            lastStatus = row.status();
            View name = identity(row.name(), row.rank(), row.logo(), crests, ranks, rankWidth, row.subtitle(), false,
                row.opensTeam(competition));
            boolean linked = teamLink(name, competition, row);
            List<String> announcement = new ArrayList<>();
            if (!row.rank().isBlank()) announcement.add(rankA11y + " " + row.rank());
            announcement.add(StandingsEnglish.name(row.name()));
            if (!row.subtitle().isBlank()) announcement.add(StandingsEnglish.text(row.subtitle()));
            addRow(names, nameRows, name);
            LinearLayout numbers = ui.row();
            for (int index = 0; index < order.size(); index++) {
                int column = order.get(index);
                String value = value(row, column);
                boolean missing = value.equals("—");
                String label = StandingsEnglish.column(competition, section.columns().get(column));
                boolean primary = section.columns().get(column).equals("积分") && !missing;
                TextView cell = metric(value, 13, missing ? ui.muted : primary ? ui.primary : ui.text, primary);
                announcement.add(label + " " + (missing ? "not provided" : value));
                numbers.addView(cell, new LinearLayout.LayoutParams(widths[index], -2));
            }
            name.setScreenReaderFocusable(true);
            name.setContentDescription(String.join(", ", announcement) + (linked ? ", open team schedule" : ""));
            numbers.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            addRow(metrics, metricRows, numbers);
        }

        int metricsWidth = java.util.Arrays.stream(widths).sum();
        HorizontalScrollView horizontal = new HorizontalScrollView(ui.c);
        horizontal.setTag("standings-metrics");
        horizontal.setHorizontalScrollBarEnabled(true);
        horizontal.addView(metrics, new android.widget.FrameLayout.LayoutParams(metricsWidth, -2));
        LinearLayout grid = new LinearLayout(ui.c) {
            private int previous = -1;

            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                int available = Math.max(2, MeasureSpec.getSize(widthSpec) - getPaddingStart() - getPaddingEnd());
                if (available != previous) {
                    previous = available;
                    int identityMin = Math.min((int)(available * 0.62f), Math.max(ui.dp(128), (int)(available * (largeType ? 0.58f : 0.41f))));
                    int rail = Math.max(identityMin, available - metricsWidth);
                    if (largeType && metricsWidth > available - rail) {
                        int visible = 0;
                        for (int columnWidth : widths) {
                            if (visible + columnWidth > available - rail) break;
                            visible += columnWidth;
                        }
                        if (visible > 0) rail = available - visible;
                    }
                    names.getLayoutParams().width = rail;
                    horizontal.getLayoutParams().width = available - rail;
                    for (int index = 0; index < nameRows.size(); index++) {
                        View name = nameRows.get(index), number = metricRows.get(index);
                        int minimum = "standings-zone".equals(name.getTag()) ? 0 : index == 0 ? 36 : 44;
                        int height = Math.max(ui.dp(minimum), Math.max(naturalHeight(name, rail), naturalHeight(number, metricsWidth)));
                        name.getLayoutParams().height = height;
                        number.getLayoutParams().height = height;
                    }
                }
                super.onMeasure(widthSpec, heightSpec);
            }
        };
        grid.setGravity(Gravity.TOP);
        grid.setTag("standings-table");
        grid.setBackground(ui.shape(ui.surface, Ui.CARD_RADIUS));
        grid.setPaddingRelative(ui.dp(8), ui.dp(4), ui.dp(8), ui.dp(4));
        grid.addView(names, new LinearLayout.LayoutParams(-2, -2));
        grid.addView(horizontal, new LinearLayout.LayoutParams(-2, -2));
        return grid;
    }

    static List<Integer> columnOrder(List<String> columns) {
        List<Integer> order = new ArrayList<>();
        int points = columns.indexOf("积分");
        if (points >= 0) order.add(points);
        for (int index = 0; index < columns.size(); index++) if (index != points) order.add(index);
        return order;
    }

    private View zone(String label) {
        LinearLayout zone = ui.column();
        zone.setTag("standings-zone");
        zone.addView(ui.divider());
        if (!label.isBlank()) {
            TextView text = ui.text(StandingsEnglish.text(label), 11, ui.muted, true);
            text.setPaddingRelative(ui.dp(4), ui.dp(3), ui.dp(4), ui.dp(2));
            text.setAccessibilityHeading(true);
            zone.addView(text);
        }
        return zone;
    }

    private View identity(String name, String rank, String logo, boolean crests, boolean ranks, int rankWidth,
                          String subtitle, boolean header, boolean linked) {
        LinearLayout row = ui.row();
        row.setPaddingRelative(0, ui.dp(5), ui.dp(8), ui.dp(5));
        String shownName = header ? name : StandingsEnglish.name(name);
        if (ranks) {
            TextView number = ui.text(rank, header ? 11 : 12, ui.muted, true);
            number.setGravity(Gravity.CENTER);
            if (header) number.setAccessibilityHeading(true);
            row.addView(number, new LinearLayout.LayoutParams(rankWidth, -2));
        }
        if (crests) {
            View crest = logo.isBlank() ? new View(ui.c) : crest(logo, shownName);
            LinearLayout.LayoutParams slot = new LinearLayout.LayoutParams(ui.dp(22), ui.dp(22));
            slot.setMarginEnd(ui.dp(8));
            row.addView(crest, slot);
        }
        LinearLayout words = ui.column();
        TextView title = ui.text(shownName, header ? 11 : 14, header ? ui.muted : ui.text, true);
        if (header) title.setAccessibilityHeading(true);
        if (linked) title.setPaintFlags(title.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        words.addView(title);
        if (!subtitle.isBlank()) words.addView(ui.text(StandingsEnglish.text(subtitle), 11, ui.muted, false));
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        return row;
    }

    private boolean teamLink(View row, String competition, Standings.Row data) {
        if (!data.opensTeam(competition) || !(ui.c instanceof Activity)) return false;
        Team team = new Team();
        team.league = competition;
        team.id = data.teamId();
        team.name = data.name();
        team.logo = data.logo();
        if (!team.usable()) return false;
        ui.clickable(row, android.graphics.Color.TRANSPARENT, 0);
        row.setMinimumWidth(ui.dp(48));
        row.setMinimumHeight(ui.dp(48));
        row.setFocusable(true);
        row.setTooltipText("Open team schedule");
        row.setOnClickListener(view -> ui.c.startActivity(new Intent(ui.c, TeamActivity.class)
            .putExtra("team", team.json().toString())));
        return true;
    }

    View round(Standings.Round round) {
        LinearLayout results = ui.column();
        results.setTag("standings-round");
        results.setPaddingRelative(ui.dp(12), ui.dp(4), ui.dp(12), ui.dp(4));
        results.setBackground(ui.shape(ui.surface, Ui.CARD_RADIUS));
        Paint scorePaint = ui.text("", 15, ui.text, true).getPaint();
        int scoreWidth = ui.dp(24);
        boolean crests = false;
        for (Standings.Tie tie : round.ties()) {
            scoreWidth = Math.max(scoreWidth, Math.max(width(scorePaint, score(tie.first())), width(scorePaint, score(tie.second()))));
            crests |= !tie.first().logo().isBlank() || !tie.second().logo().isBlank();
        }
        for (Standings.Tie tie : round.ties()) {
            if (results.getChildCount() > 0) results.addView(ui.divider());
            LinearLayout fixture = ui.column();
            fixture.setPadding(0, ui.dp(10), 0, ui.dp(10));
            String label = String.join(" · ", java.util.stream.Stream.of(
                    StandingsEnglish.text(tie.date()), StandingsEnglish.text(tie.stage()), StandingsEnglish.text(tie.result()))
                .filter(text -> !text.isBlank()).collect(Collectors.toList()));
            if (!label.isEmpty()) fixture.addView(ui.text(label, 12, ui.muted, false));
            fixture.addView(side(tie.first(), scoreWidth, crests));
            fixture.addView(side(tie.second(), scoreWidth, crests));
            results.addView(fixture, new LinearLayout.LayoutParams(-1, -2));
        }
        return results;
    }

    private View side(Standings.Side side, int scoreWidth, boolean crests) {
        LinearLayout row = ui.row();
        row.setMinimumHeight(ui.dp(30));
        String name = StandingsEnglish.name(side.name());
        if (crests) {
            View crest = side.logo().isBlank() ? new View(ui.c) : crest(side.logo(), name);
            LinearLayout.LayoutParams slot = new LinearLayout.LayoutParams(ui.dp(22), ui.dp(22));
            slot.setMarginEnd(ui.dp(8));
            row.addView(crest, slot);
        }
        row.addView(ui.text(name, 14, ui.text, side.winner()), new LinearLayout.LayoutParams(0, -2, 1));
        String score = score(side);
        TextView number = ui.text(score, 15, side.winner() ? ui.primary : score.equals("—") ? ui.muted : ui.text, true);
        number.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        number.setContentDescription(score.equals("—") ? "No score" : side.winner() ? score + ", winner" : score);
        LinearLayout.LayoutParams end = new LinearLayout.LayoutParams(scoreWidth, -2);
        end.setMarginStart(ui.dp(8));
        row.addView(number, end);
        return row;
    }

    private View crest(String logo, String name) {
        Logos.Crest crest = Logos.crest(ui, 22, false);
        crest.initials.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE);
        crest.initials.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 10);
        crest.box.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        Logos.bind(crest, logo, name);
        return crest.box;
    }

    private TextView metric(String value, int size, int color, boolean bold) {
        TextView text = ui.text(value, size, color, bold);
        text.setTag("standings-metric");
        text.setPaddingRelative(ui.dp(4), ui.dp(5), ui.dp(6), ui.dp(5));
        text.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        return text;
    }

    private static String score(Standings.Side side) { return side.score().isBlank() ? "—" : side.score(); }

    private static String value(Standings.Row row, int column) {
        return column >= row.values().size() || row.values().get(column).isBlank() ? "—" : row.values().get(column);
    }

    private static int width(Paint paint, String value) { return (int)Math.ceil(paint.measureText(value)); }

    private static int naturalHeight(View view, int width) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        return view.getMeasuredHeight();
    }

    private static void addRow(LinearLayout into, List<View> rows, View row) {
        into.addView(row, new LinearLayout.LayoutParams(-1, -2));
        rows.add(row);
    }
}
