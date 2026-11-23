package cn.myscore.app;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

final class Cs2EventGraph {
    private final Ui ui;
    private final Consumer<Cs2Tournament.Game> open;

    Cs2EventGraph(Ui ui, Consumer<Cs2Tournament.Game> open) { this.ui = ui; this.open = open; }

    private record Node(int column, int index, int total, String round, Cs2Tournament.Bucket bucket, TextView view) {}
    private record Edge(View from, View to, boolean selected) {}
    private record Feed(String id, View target) {}

    void render(LinearLayout into, Cs2Tournament.Group group, String selection, Consumer<String> remember) {
        if (group.lanes().isEmpty()) { into.addView(note("No published matchups.")); return; }
        switch (group.format()) {
            case "swiss" -> swiss(into, group, selection, remember);
            case "single", "double" -> knockout(into, group, selection, remember);
            default -> schedule(into, group.lanes());
        }
    }

    private void swiss(LinearLayout into, Cs2Tournament.Group group, String selection, Consumer<String> remember) {
        List<Cs2Tournament.Lane> columns = group.lanes();
        boolean mapped = columns.stream().flatMap(lane -> lane.buckets().stream()).anyMatch(bucket -> result(bucket.label()) != 0);
        if (!mapped) {
            title(into, "Swiss schedule");
            schedule(into, columns);
            return;
        }
        title(into, "Swiss stage");
        into.addView(note("3 wins advance. 3 losses eliminate."));
        ui.gap(into, 12);
        Map<String, Node> nodes = new LinkedHashMap<>();
        List<TextView> labels = new ArrayList<>();
        LinearLayout ledger = ui.column();
        FrameLayout map = new FrameLayout(ui.c) {
            private final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Path path = new Path();
            private int track, pitch, headerHeight;

            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                int width = MeasureSpec.getSize(widthSpec);
                int minimum = unitWidth() * columns.size() + ui.dp(8);
                if (MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED) width = minimum;
                width = Math.max(width, minimum);
                track = (width - ui.dp(8)) / columns.size();
                int labelWidth = Math.max(1, track - ui.dp(4));
                int labelSpec = MeasureSpec.makeMeasureSpec(labelWidth, MeasureSpec.EXACTLY);
                int free = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
                headerHeight = 0;
                for (TextView label : labels) {
                    label.measure(labelSpec, free);
                    headerHeight = Math.max(headerHeight, label.getMeasuredHeight());
                }
                pitch = ui.dp(48);
                int tracks = 1;
                for (Node node : nodes.values()) {
                    node.view().measure(labelSpec, free);
                    pitch = Math.max(pitch, node.view().getMeasuredHeight());
                    tracks = Math.max(tracks, node.total());
                }
                setMeasuredDimension(width, ui.dp(14) + headerHeight + tracks * pitch);
            }

            private int unitWidth() {
                int unit = ui.dp(52);
                for (Node node : nodes.values()) {
                    int natural = (int)Math.ceil(node.view().getPaint().measureText(node.view().getText().toString()));
                    unit = Math.max(unit, natural + ui.dp(16));
                }
                return unit;
            }

            @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                int pad = ui.dp(4);
                for (int index = 0; index < labels.size(); index++) place(labels.get(index), pad + track * index, ui.dp(6));
                int tracks = nodes.values().stream().mapToInt(Node::total).max().orElse(1);
                for (Node node : nodes.values()) {
                    int y = ui.dp(9) + headerHeight + (tracks - node.total()) * pitch / 2 + node.index() * pitch;
                    place(node.view(), pad + node.column() * track, y);
                }
            }

            private void place(View view, int left, int top) {
                view.layout(left, top, left + view.getMeasuredWidth(), top + view.getMeasuredHeight());
            }

            @Override protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                for (Node from : nodes.values()) for (String next : from.bucket().next()) {
                    Node to = nodes.get(next);
                    if (to == null) continue;
                    boolean active = from.view().isSelected() || to.view().isSelected();
                    int outcome = result(to.bucket().label());
                    int ink = active && outcome == 1 ? ui.primary : active && outcome == -1 ? ui.error : active ? ui.text : ui.muted;
                    path(canvas, pen, path, from.view().getRight() - ui.dp(5), middle(from.view()),
                        to.view().getLeft() + ui.dp(5), middle(to.view()), ink, active);
                }
            }
        };
        map.setWillNotDraw(false);
        map.setBackground(ui.shape(ui.surface, 16));
        for (int index = 0; index < columns.size(); index++) {
            Cs2Tournament.Lane column = columns.get(index);
            String round = index < 5 ? "Round " + (index + 1) : "Outcome";
            TextView label = ui.text(index < 5 ? "R" + (index + 1) : "End", 11, ui.muted, true);
            label.setGravity(Gravity.CENTER);
            label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            labels.add(label);
            map.addView(label);
            for (int slot = 0; slot < column.buckets().size(); slot++) {
                Cs2Tournament.Bucket bucket = column.buckets().get(slot);
                TextView record = ui.text(record(bucket.label()), 13, ui.text, true);
                record.setGravity(Gravity.CENTER);
                record.setMinHeight(ui.dp(48));
                record.setContentDescription(round + ", " + record(bucket.label()) + (result(bucket.label()) == 1 ? ", advances"
                    : result(bucket.label()) == -1 ? ", eliminated" : ""));
                record.setTag("cs2-record-" + bucket.id());
                map.addView(record);
                nodes.put(bucket.id(), new Node(index, slot, column.buckets().size(), round, bucket, record));
            }
        }
        HorizontalScrollView viewport = new HorizontalScrollView(ui.c);
        viewport.setFillViewport(true);
        viewport.setTag("cs2-swiss-flow");
        viewport.addView(map, new HorizontalScrollView.LayoutParams(-2, -2));
        into.addView(viewport, new LinearLayout.LayoutParams(-1, -2));
        ui.gap(into, 18);
        into.addView(ledger, new LinearLayout.LayoutParams(-1, -2));
        Node initial = nodes.get(selection);
        if (initial == null && !nodes.isEmpty()) initial = nodes.values().iterator().next();
        if (initial == null) return;
        Consumer<Node> select = node -> {
            remember.accept(node.bucket().id());
            for (Node item : nodes.values()) styleNode(item, item == node);
            map.invalidate();
            ledger.removeAllViews();
            if (result(node.bucket().label()) == 0) {
                title(ledger, node.round() + "  ·  " + record(node.bucket().label()));
                games(ledger, node.bucket().games());
            } else {
                title(ledger, record(node.bucket().label()) + (result(node.bucket().label()) > 0 ? "  ·  Advances" : "  ·  Eliminated"));
                teams(ledger, group.teams(), node.bucket().label());
            }
        };
        for (Node node : nodes.values()) node.view().setOnClickListener(view -> select.accept(node));
        select.accept(initial);
        if (!selection.isEmpty()) {
            View selected = initial.view();
            selected.post(() -> viewport.scrollTo(Math.max(0, selected.getRight() - viewport.getWidth()), 0));
        }
    }

    private void styleNode(Node node, boolean selected) {
        int outcome = result(node.bucket().label());
        int color = selected ? ui.onAction : outcome == 1 ? ui.primary : outcome == -1 ? ui.error : ui.text;
        node.view().setSelected(selected);
        node.view().setTextColor(color);
        node.view().setFocusable(true);
        ui.clickable(node.view(), selected ? ui.action : ui.tonal, 12);
        android.graphics.drawable.InsetDrawable inset = new android.graphics.drawable.InsetDrawable(node.view().getBackground(), ui.dp(5), ui.dp(6), ui.dp(5), ui.dp(6));
        node.view().setBackground(inset);
        node.view().setPadding(0, 0, 0, 0);
    }

    private void knockout(LinearLayout into, Cs2Tournament.Group group, String selection, Consumer<String> remember) {
        List<Cs2Tournament.Lane> lanes = group.lanes();
        title(into, group.format().equals("double") ? "Double elimination" : "Playoff bracket");
        LinearLayout bracket = ui.column();
        List<TextView> controls = new ArrayList<>();
        Consumer<Integer> choose = position -> {
            Cs2Tournament.Lane lane = lanes.get(position);
            remember.accept("lane:" + position);
            for (int index = 0; index < controls.size(); index++) ui.selectTab(controls.get(index), index == position);
            bracket.removeAllViews();
            bracket.addView(tree(lane), new LinearLayout.LayoutParams(-1, -2));
        };
        if (lanes.size() > 1) {
            LinearLayout row = ui.row();
            HorizontalScrollView strip = new HorizontalScrollView(ui.c);
            strip.setHorizontalScrollBarEnabled(false);
            for (int index = 0; index < lanes.size(); index++) {
                int position = index;
                TextView button = ui.tab(lanes.get(index).label(), index == 0, () -> choose.accept(position));
                controls.add(button);
                row.addView(button, new LinearLayout.LayoutParams(-2, -2));
            }
            strip.addView(row, new HorizontalScrollView.LayoutParams(-2, -2));
            into.addView(strip);
        }
        ui.gap(into, 8);
        into.addView(bracket, new LinearLayout.LayoutParams(-1, -2));
        int position = 0;
        for (int index = 0; index < lanes.size(); index++) if (selection.equals("lane:" + index)) position = index;
        choose.accept(position);
    }

    private View tree(Cs2Tournament.Lane lane) {
        LinearLayout row = ui.row();
        row.setGravity(Gravity.TOP);
        row.setPadding(0, 0, 0, ui.dp(8));
        Map<String, View> anchors = new HashMap<>();
        Set<String> duplicate = new HashSet<>();
        List<Feed> feeds = new ArrayList<>();
        for (Cs2Tournament.Bucket round : lane.buckets()) {
            LinearLayout column = ui.column();
            column.addView(ui.text(EnglishDisplay.text(round.label()), 13, ui.muted, true));
            ui.gap(column, 8);
            for (Cs2Tournament.Game game : round.games()) {
                View slot = game(game, false);
                column.addView(slot, new LinearLayout.LayoutParams(-1, -2));
                ui.gap(column, 6);
                anchor(anchors, duplicate, game.graphId(), slot);
                anchor(anchors, duplicate, game.id(), slot);
                feeds.add(new Feed(game.first().from(), slot));
                feeds.add(new Feed(game.second().from(), slot));
            }
            LinearLayout.LayoutParams width = new LinearLayout.LayoutParams(ui.dp(172), -2);
            if (row.getChildCount() > 0) width.setMarginStart(ui.dp(20));
            row.addView(column, width);
        }
        List<Edge> edges = new ArrayList<>();
        for (Feed feed : feeds) {
            View from = anchors.get(feed.id());
            if (from != null && from != feed.target() && !duplicate.contains(feed.id())) edges.add(new Edge(from, feed.target(), false));
        }
        FrameLayout tree = new FrameLayout(ui.c) {
            private final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Rect from = new Rect(), to = new Rect();
            private final Path wire = new Path();

            @Override protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                for (Edge edge : edges) {
                    from.set(0, 0, edge.from().getWidth(), edge.from().getHeight());
                    to.set(0, 0, edge.to().getWidth(), edge.to().getHeight());
                    offsetDescendantRectToMyCoords(edge.from(), from);
                    offsetDescendantRectToMyCoords(edge.to(), to);
                    if (from.right < to.left) path(canvas, pen, wire, from.right, from.exactCenterY(), to.left, to.exactCenterY(), ui.muted, edge.selected());
                }
            }
        };
        tree.setWillNotDraw(false);
        tree.addView(row, new FrameLayout.LayoutParams(-2, -2));
        HorizontalScrollView viewport = new HorizontalScrollView(ui.c) {
            private int previous;
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                int width = MeasureSpec.getSize(widthSpec);
                boolean large = ui.c.getResources().getConfiguration().fontScale >= 1.5f;
                int next = Math.min(ui.dp(large ? 240 : 176), Math.max(1, width - ui.dp(20)));
                if (next != previous) {
                    previous = next;
                    for (int item = 0; item < row.getChildCount(); item++) row.getChildAt(item).getLayoutParams().width = next;
                }
                super.onMeasure(widthSpec, heightSpec);
            }
        };
        viewport.setTag("cs2-knockout-flow");
        viewport.addView(tree, new HorizontalScrollView.LayoutParams(-2, -2));
        return viewport;
    }

    private void schedule(LinearLayout into, List<Cs2Tournament.Lane> lanes) {
        for (Cs2Tournament.Lane lane : lanes) {
            if (!lane.label().isBlank()) title(into, lane.label());
            for (Cs2Tournament.Bucket bucket : lane.buckets()) {
                if (!bucket.label().isBlank() && !bucket.label().equals(lane.label())) title(into, bucket.label());
                games(into, bucket.games());
                ui.gap(into, 12);
            }
        }
    }

    private void games(LinearLayout into, List<Cs2Tournament.Game> games) {
        if (games.isEmpty()) { into.addView(note("No matchups published.")); return; }
        LinearLayout panel = ui.column();
        panel.setBackground(ui.shape(ui.surface, 16));
        for (Cs2Tournament.Game entry : games) {
            if (panel.getChildCount() > 0) panel.addView(ui.divider());
            panel.addView(game(entry, true), new LinearLayout.LayoutParams(-1, -2));
        }
        into.addView(panel, new LinearLayout.LayoutParams(-1, -2));
    }

    private View game(Cs2Tournament.Game game, boolean ledger) {
        LinearLayout item = ui.column();
        item.setPadding(ui.dp(12), ui.dp(ledger ? 9 : 7), ui.dp(12), ui.dp(ledger ? 9 : 7));
        boolean assigned = !unknown(game.first()) || !unknown(game.second());
        if (assigned || !game.first().from().isBlank() || !game.second().from().isBlank()) {
            item.addView(side(game.first()));
            item.addView(side(game.second()));
        } else item.addView(note("Matchup TBD"));
        String status = EnglishDisplay.text(game.status());
        String meta = ledger ? join(game.time(), game.bestOf(), status) : join(game.bestOf(), status.equals("Final") ? "" : status);
        if (!meta.isBlank()) item.addView(note(meta));
        if (game.id().matches("(?:five:cs2:)?csgo_mc_[0-9]+")) {
            item.setMinimumHeight(ui.dp(48));
            ui.clickable(item, ledger ? Color.TRANSPARENT : ui.surface, ledger ? 0 : 12);
            item.setFocusable(true);
            item.setContentDescription(join(EnglishDisplay.text(game.time()), game.bestOf(), status,
                assigned ? announce(game.first()) + " vs " + announce(game.second()) : "Matchup TBD"));
            item.setTag("cs2-event-match");
            item.setOnClickListener(view -> open.accept(game));
        } else if (!ledger) item.setBackground(ui.shape(ui.surface, 12));
        return item;
    }

    private View side(Cs2Tournament.Side side) {
        LinearLayout row = ui.row();
        row.setMinimumHeight(ui.dp(26));
        String name = unknown(side) ? "TBD" : EnglishDisplay.text(side.name());
        addCrest(row, side.logo(), name);
        row.addView(ui.text(name, 13, unknown(side) ? ui.muted : ui.text, side.winner()), new LinearLayout.LayoutParams(0, -2, 1));
        if (!side.score().isBlank()) {
            TextView score = ui.text(side.score(), 13, side.winner() ? ui.primary : ui.text, true);
            score.setPaddingRelative(ui.dp(8), 0, 0, 0);
            row.addView(score);
        }
        return row;
    }

    private void teams(LinearLayout into, List<Cs2Tournament.Team> entries, String record) {
        LinearLayout panel = ui.column();
        panel.setPadding(ui.dp(12), ui.dp(4), ui.dp(12), ui.dp(4));
        panel.setBackground(ui.shape(ui.surface, 16));
        String expected = result(record) == 1 ? "晋级" : "淘汰";
        for (Cs2Tournament.Team team : entries) if (record.equals(team.record()) && expected.equals(team.status())) {
            LinearLayout row = ui.row();
            row.setMinimumHeight(ui.dp(42));
            addCrest(row, team.logo(), team.name());
            row.addView(ui.text(team.name(), 14, ui.text, true), new LinearLayout.LayoutParams(0, -2, 1));
            panel.addView(row);
        }
        if (panel.getChildCount() == 0) into.addView(note("No teams confirmed for this record yet."));
        else into.addView(panel);
    }

    private void addCrest(LinearLayout into, String logo, String name) {
        if (ui.c.getResources().getConfiguration().fontScale >= 1.5f) return;
        View crest = logo.isBlank() ? new View(ui.c) : Logos.view(ui, logo, name, 18);
        crest.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        LinearLayout.LayoutParams slot = new LinearLayout.LayoutParams(ui.dp(18), ui.dp(18));
        slot.setMarginEnd(ui.dp(8));
        into.addView(crest, slot);
    }

    private void title(LinearLayout into, String label) {
        TextView heading = ui.text(label, 16, ui.text, true);
        heading.setAccessibilityHeading(true);
        into.addView(heading);
        ui.gap(into, 5);
    }

    private TextView note(String text) { return ui.text(text, 12, ui.muted, false); }

    private void path(Canvas canvas, Paint pen, Path path, float x, float y, float endX, float endY, int color, boolean active) {
        if (x >= endX) return;
        pen.setColor(color);
        pen.setAlpha(active ? 205 : 80);
        pen.setStyle(Paint.Style.STROKE);
        pen.setStrokeWidth(Math.max(1, ui.dp(active ? 1.5f : 1)));
        float middle = (x + endX) / 2;
        path.reset();
        path.moveTo(x, y);
        path.cubicTo(middle, y, middle, endY, endX, endY);
        canvas.drawPath(path, pen);
    }

    private static float middle(View view) { return (view.getTop() + view.getBottom()) * .5f; }

    private static void anchor(Map<String, View> anchors, Set<String> duplicate, String id, View slot) {
        if (id.isBlank()) return;
        View old = anchors.putIfAbsent(id, slot);
        if (old != null && old != slot) duplicate.add(id);
    }

    private static boolean unknown(Cs2Tournament.Side side) {
        return List.of("", "TBD", "Matchup TBD").contains(EnglishDisplay.text(side.name()));
    }

    private static int result(String record) { return record.matches("3:[0-2]") ? 1 : record.matches("[0-2]:3") ? -1 : 0; }
    private static String record(String label) { return label.replace(':', '–'); }

    private static String announce(Cs2Tournament.Side side) { return join(unknown(side) ? "TBD" : side.name(), side.score()); }

    private static String join(String... parts) {
        List<String> values = new ArrayList<>();
        for (String part : parts) if (!part.isBlank()) values.add(part);
        return String.join(" · ", values);
    }
}
