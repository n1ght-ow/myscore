package cn.myscore.app;

import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/**
 * Tablet master score rail with modern selectable cards, live coral highlights,
 * and competition section hubs linking directly to Standings / CS2 Brackets.
 */
final class PadScoreAdapter extends BaseAdapter {
    interface Host {
        void selectMatch(Match match);
        void openMatch(Match match);
        void openTeam(Match match, boolean home);
        default void openCompetition(ScoreRow sectionRow) {}
        void toggleSubscription(Match match);
        void openWatch(Match match);
        WatchLinks.Link watchLink(Match match);
        boolean isSubscribed(String id);
        void showSourceStatus();
        void resolveEmptySchedule();
    }

    private final Ui ui;
    private final Host host;
    private final boolean listOnly;
    private List<ScoreRow> rows = new ArrayList<>();
    private String selectedId = "";
    private ListView list;
    private boolean groupedByCompetition;

    PadScoreAdapter(Ui ui, Host host) { this(ui, host, false); }
    PadScoreAdapter(Ui ui, Host host, boolean listOnly) { this.ui = ui; this.host = host; this.listOnly = listOnly; }
    void attach(ListView list) { this.list = list; }
    void setGroupedByCompetition(boolean grouped) { this.groupedByCompetition = grouped; }

    void updateRows(List<ScoreRow> next) {
        boolean same = next.size() == rows.size();
        if (same) {
            for (int i = 0; i < next.size(); i++) {
                if (!next.get(i).key().equals(rows.get(i).key())) { same = false; break; }
            }
        }
        rows = next;
        if (!same || (list != null && list.isLayoutRequested())) { notifyDataSetChanged(); return; }
        if (list == null) return;
        int first = list.getFirstVisiblePosition();
        for (int i = 0; i < list.getChildCount(); i++) {
            int position = first + i;
            if (position >= 0 && position < rows.size()) getView(position, list.getChildAt(i), list);
        }
    }

    void setSelected(String id) {
        if (id == null) id = "";
        if (id.equals(selectedId)) return;
        selectedId = id;
        if (list == null || list.isLayoutRequested()) { notifyDataSetChanged(); return; }
        int first = list.getFirstVisiblePosition();
        for (int i = 0; i < list.getChildCount(); i++) {
            int position = first + i;
            if (position >= 0 && position < rows.size()) getView(position, list.getChildAt(i), list);
        }
    }

    @Override public int getCount() { return rows.size(); }
    @Override public ScoreRow getItem(int position) { return rows.get(position); }
    @Override public long getItemId(int position) { return position; }
    @Override public boolean hasStableIds() { return false; }
    @Override public int getViewTypeCount() { return 6; }
    @Override public int getItemViewType(int position) { return rows.get(position).type; }

    @Override public View getView(int position, View convertView, ViewGroup parent) {
        ScoreRow row = rows.get(position);
        switch (row.type) {
            case ScoreRow.LOADING: {
                TextView text = (TextView) convertView;
                if (text == null) { text = ui.text("", 13, ui.muted, false); text.setPadding(ui.dp(6), ui.dp(24), 0, ui.dp(12)); }
                text.setText(row.text);
                return text;
            }
            case ScoreRow.SECTION: {
                Object tag = convertView == null ? null : convertView.getTag();
                SectionHolder holder = tag instanceof SectionHolder ? (SectionHolder) tag : new SectionHolder();
                holder.root.setTag(holder);
                holder.bind(row);
                return holder.root;
            }
            case ScoreRow.FAILURE: {
                TextView banner = (TextView) convertView;
                if (banner == null) {
                    banner = ui.text("", 13, ui.error, true);
                    banner.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12));
                    ui.clickableDrawable(banner, ui.outlined(ui.errorSoft, Ui.alpha(ui.error, 90), Ui.CARD_RADIUS), Ui.CARD_RADIUS);
                    banner.setMinHeight(ui.dp(46));
                    banner.setFocusable(true);
                    banner.setOnClickListener(v -> host.showSourceStatus());
                }
                banner.setText(row.text);
                return banner;
            }
            case ScoreRow.EMPTY: {
                Object tag = convertView == null ? null : convertView.getTag();
                EmptyHolder holder = tag instanceof EmptyHolder ? (EmptyHolder) tag : new EmptyHolder();
                holder.root.setTag(holder);
                holder.root.bind(row);
                return holder.root;
            }
            case ScoreRow.F1: {
                Object tag = convertView == null ? null : convertView.getTag();
                F1Holder holder = tag instanceof F1Holder ? (F1Holder) tag : new F1Holder();
                holder.root.setTag(holder);
                holder.bind(row.match);
                return holder.root;
            }
            default: {
                Object tag = convertView == null ? null : convertView.getTag();
                MatchHolder holder = tag instanceof MatchHolder ? (MatchHolder) tag : new MatchHolder();
                holder.root.setTag(holder);
                holder.bind(row.match);
                return holder.root;
            }
        }
    }

    private final class EmptyHolder {
        final Ui.ScheduleEmpty root;
        EmptyHolder() { root = ui.new ScheduleEmpty(host::resolveEmptySchedule); }
    }

    private final class SectionHolder {
        final LinearLayout root, actionPill;
        final View accentBar;
        final TextView title, actionText;
        ScoreRow row;

        SectionHolder() {
            root = ui.row();
            root.setPadding(ui.dp(2), ui.dp(14), ui.dp(2), ui.dp(6));
            accentBar = new View(ui.c);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ui.dp(3), ui.dp(14));
            lp.setMarginEnd(ui.dp(8));
            root.addView(accentBar, lp);
            title = ui.text("", 13, ui.text, true);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            root.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
            actionPill = ui.row();
            actionPill.setPadding(ui.dp(9), ui.dp(4), ui.dp(7), ui.dp(4));
            ui.clickableDrawable(actionPill, ui.outlined(ui.tonal, ui.border, Ui.PILL_RADIUS), Ui.PILL_RADIUS);
            actionText = ui.text("", 11, ui.text, true);
            actionPill.addView(actionText);
            actionPill.addView(new Ui.Icon(ui.c, "chevron", ui.muted), new LinearLayout.LayoutParams(ui.dp(12), ui.dp(12)));
            actionPill.setFocusable(true);
            actionPill.setOnClickListener(v -> { if (row != null) host.openCompetition(row); });
            root.addView(actionPill);
        }

        void bind(ScoreRow value) {
            row = value;
            boolean isLive = value.text != null && value.text.startsWith("Live");
            accentBar.setBackground(ui.shape(isLive ? ui.live : ui.sportAccent(value.leagueId, value.sportGroup), 2));
            title.setText(value.text);
            title.setTextColor(isLive ? ui.live : ui.text);
            if (value.actionLabel != null && !value.actionLabel.isEmpty()) {
                actionText.setText(value.actionLabel);
                actionPill.setVisibility(View.VISIBLE);
            } else {
                actionPill.setVisibility(View.GONE);
            }
        }
    }

    private final class F1Holder {
        final LinearLayout root, card;
        final TextView title, state, time;
        Match match;
        int styleState = -1;

        F1Holder() {
            root = ui.column();
            root.setPadding(0, 0, 0, ui.dp(8));
            card = ui.column();
            card.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12));
            ui.clickableDrawable(card, ui.panel(), Ui.CARD_RADIUS);
            card.setFocusable(true);
            LinearLayout top = ui.row();
            time = ui.text("", 12, ui.muted, true);
            top.addView(time, new LinearLayout.LayoutParams(0, -2, 1f));
            state = ui.text("", 11, ui.primary, true);
            top.addView(state);
            card.addView(top);
            ui.gap(card, 6);
            title = ui.text("", 14, ui.text, true);
            card.addView(title);
            card.setOnClickListener(v -> {
                if (match == null) return;
                if (selectedId.equals(match.id)) host.openMatch(match);
                else host.selectMatch(match);
            });
            root.addView(card);
        }

        void bind(Match value) {
            match = value;
            boolean active = !listOnly && value.id.equals(selectedId);
            time.setText(value.timeConfirmed ? value.time() : "Time TBD");
            state.setText(EnglishDisplay.text(Rules.stateLabel(value.state)));
            state.setTextColor(value.live() ? ui.live : ui.muted);
            title.setText(EnglishDisplay.text(value.displayTitle()));
            styleState = applyCardStyle(card, active, value.live(), styleState);
        }
    }

    private final class MatchHolder {
        final LinearLayout root, card, actionRow;
        final View accentDot;
        final TextView state, league, home, away, homeScore, awayScore;
        final Logos.Crest homeCrest, awayCrest;
        final FrameLayout homeSlot, awaySlot, calendar;
        final Ui.WatchPill watch;
        final Ui.Icon calendarIcon;
        Match match;
        int styleState = -1;

        MatchHolder() {
            root = ui.column();
            root.setPadding(0, 0, 0, ui.dp(8));
            card = ui.column();
            card.setPadding(ui.dp(14), ui.dp(11), ui.dp(14), ui.dp(11));
            ui.clickableDrawable(card, ui.panel(), Ui.CARD_RADIUS);
            card.setFocusable(true);
            card.setOnClickListener(v -> {
                if (match == null) return;
                if (!listOnly && selectedId.equals(match.id)) host.openMatch(match);
                else if (listOnly) host.openMatch(match);
                else host.selectMatch(match);
            });

            LinearLayout meta = ui.row();
            accentDot = new View(ui.c);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ui.dp(6), ui.dp(6));
            dlp.setMarginEnd(ui.dp(6));
            meta.addView(accentDot, dlp);
            league = ui.text("", 11, ui.muted, true);
            league.setSingleLine(true);
            league.setEllipsize(TextUtils.TruncateAt.END);
            meta.addView(league, new LinearLayout.LayoutParams(0, -2, 1f));
            state = ui.text("", 11, ui.primary, true);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
            slp.setMarginStart(ui.dp(6));
            meta.addView(state, slp);
            card.addView(meta);
            ui.gap(card, 8);

            LinearLayout homeRow = ui.row();
            homeCrest = Logos.crest(ui, 22, false);
            homeSlot = crestSlot(homeCrest);
            homeRow.addView(homeSlot, new LinearLayout.LayoutParams(ui.dp(34), ui.dp(34)));
            home = ui.text("", 14, ui.text, true);
            home.setMaxLines(1);
            home.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(0, -2, 1f);
            hp.setMarginStart(ui.dp(8));
            homeRow.addView(home, hp);
            homeScore = ui.display("–", 18, ui.text);
            homeScore.setGravity(Gravity.END);
            LinearLayout.LayoutParams hsp = new LinearLayout.LayoutParams(-2, -2);
            hsp.setMarginStart(ui.dp(10));
            homeRow.addView(homeScore, hsp);
            card.addView(homeRow);
            ui.gap(card, 4);

            LinearLayout awayRow = ui.row();
            awayCrest = Logos.crest(ui, 22, false);
            awaySlot = crestSlot(awayCrest);
            awayRow.addView(awaySlot, new LinearLayout.LayoutParams(ui.dp(34), ui.dp(34)));
            away = ui.text("", 14, ui.text, true);
            away.setMaxLines(1);
            away.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0, -2, 1f);
            ap.setMarginStart(ui.dp(8));
            awayRow.addView(away, ap);
            awayScore = ui.display("–", 18, ui.text);
            awayScore.setGravity(Gravity.END);
            LinearLayout.LayoutParams asp = new LinearLayout.LayoutParams(-2, -2);
            asp.setMarginStart(ui.dp(10));
            awayRow.addView(awayScore, asp);
            card.addView(awayRow);

            actionRow = ui.row();
            actionRow.setPadding(0, ui.dp(8), 0, 0);
            actionRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            calendar = new FrameLayout(ui.c);
            calendarIcon = new Ui.Icon(ui.c, "calendar-add", ui.muted);
            calendar.addView(calendarIcon, new FrameLayout.LayoutParams(ui.dp(18), ui.dp(18), Gravity.CENTER));
            ui.clickableDrawable(calendar, ui.outlined(ui.tonal, ui.border, 10), 10);
            calendar.setFocusable(true);
            actionRow.addView(calendar, new LinearLayout.LayoutParams(ui.dp(36), ui.dp(36)));
            watch = ui.watchPill(36);
            LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(ui.dp(36), ui.dp(36));
            wp.setMarginStart(ui.dp(6));
            actionRow.addView(watch, wp);
            card.addView(actionRow);

            root.addView(card);
        }

        private FrameLayout crestSlot(Logos.Crest crest) {
            FrameLayout frame = new FrameLayout(ui.c);
            frame.setBackground(ui.shape(ui.tonal, 8));
            frame.addView(crest.box, new FrameLayout.LayoutParams(ui.dp(22), ui.dp(22), Gravity.CENTER));
            return frame;
        }

        void bind(Match value) {
            match = value;
            boolean active = !listOnly && value.id.equals(selectedId);
            boolean live = value.live();
            accentDot.setBackground(ui.shape(ui.sportAccent(value.league, value.group), Ui.PILL_RADIUS));
            accentDot.setVisibility(groupedByCompetition ? View.GONE : View.VISIBLE);
            league.setVisibility(groupedByCompetition ? View.INVISIBLE : View.VISIBLE);
            league.setText(EnglishDisplay.text(MatchLabel.of(value)));
            String st = live && !value.status.isBlank() ? value.status
                : value.scheduled() && value.timeConfirmed ? value.time()
                : Rules.stateLabel(value.state);
            state.setText(EnglishDisplay.text(st));
            state.setTextColor(live ? ui.live : value.cancelled() ? ui.error : value.scheduled() ? ui.text : ui.muted);

            home.setText(EnglishDisplay.text(value.home));
            away.setText(EnglishDisplay.text(value.away));
            int ho = value.outcome(true), ao = value.outcome(false);
            home.setTextColor(value.finalised() && ho < 0 ? ui.muted : ui.text);
            away.setTextColor(value.finalised() && ao < 0 ? ui.muted : ui.text);

            boolean showScore = value.hasScore();
            homeScore.setText(showScore ? value.displayScore(true) : "–");
            awayScore.setText(showScore ? value.displayScore(false) : "–");
            homeScore.setTextColor(!showScore ? ui.dim : live ? ui.live : ho > 0 ? ui.primary : ho < 0 ? ui.muted : ui.text);
            awayScore.setTextColor(!showScore ? ui.dim : live ? ui.live : ao > 0 ? ui.primary : ao < 0 ? ui.muted : ui.text);

            styleState = applyCardStyle(card, active, live, styleState);

            bindTeam(homeSlot, value, true);
            bindTeam(awaySlot, value, false);

            WatchLinks.Link link = host.watchLink(value);
            watch.bind(link, live, () -> host.openWatch(value));
            boolean subscribed = host.isSubscribed(value.id);
            boolean canSubscribe = value.scheduled() && value.timeConfirmed && value.start > System.currentTimeMillis();
            calendar.setVisibility(canSubscribe ? View.VISIBLE : View.GONE);
            calendarIcon.set(subscribed ? "calendar-check" : "calendar-add", subscribed ? ui.primary : ui.muted);
            calendar.setOnClickListener(v -> host.toggleSubscription(value));
            actionRow.setVisibility(canSubscribe || link != null ? View.VISIBLE : View.GONE);
        }

        private void bindTeam(FrameLayout slot, Match m, boolean isHome) {
            String id = isHome ? m.homeId : m.awayId, name = isHome ? m.home : m.away;
            Logos.bind(isHome ? homeCrest : awayCrest, isHome ? m.homeLogo : m.awayLogo, name);
            boolean available = id != null && !id.isBlank();
            slot.setClickable(available);
            slot.setFocusable(available);
            slot.setOnClickListener(available ? v -> host.openTeam(m, isHome) : null);
        }
    }

    private int applyCardStyle(LinearLayout card, boolean active, boolean live, int previous) {
        int next = (active ? 2 : 0) + (live ? 1 : 0);
        if (next == previous) return previous;
        if (active) {
            ui.clickableDrawable(card, ui.outlined(ui.elevated, ui.primary, Ui.CARD_RADIUS), Ui.CARD_RADIUS);
        } else if (live) {
            ui.clickableDrawable(card, ui.liveCardBg(), Ui.CARD_RADIUS);
        } else {
            ui.clickableDrawable(card, ui.panel(), Ui.CARD_RADIUS);
        }
        card.setSelected(active);
        return next;
    }
}
