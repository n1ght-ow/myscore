package cn.myscore.app;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/**
 * Modern editorial match cards for the phone score feed.
 * Competition headers act as direct hub jumps into Standings or CS2 Brackets.
 * Per-card provenance noise is removed in favor of crisp status pills, outcome emphasis, and inline actions.
 */
final class ScoreAdapter extends BaseAdapter {
    interface Host {
        void openMatch(Match match);
        void openTeam(Match match, boolean home);
        default void openCompetition(ScoreRow sectionRow) {}
        void toggleSubscription(Match match);
        void openWatch(Match match);
        WatchLinks.Link watchLink(Match match);
        void retryScores();
        void resolveEmptySchedule();
        void showSourceStatus();
        boolean isSubscribed(String id);
    }

    private final Ui ui;
    private final Host host;
    private List<ScoreRow> rows = new ArrayList<>();
    private ScoreList list;
    private boolean groupedByCompetition;

    ScoreAdapter(Ui ui, Host host) { this.ui = ui; this.host = host; }
    void attach(ScoreList list) { this.list = list; }
    void setGroupedByCompetition(boolean grouped) { this.groupedByCompetition = grouped; }

    boolean updateRows(List<ScoreRow> next) {
        boolean same = next.size() == rows.size();
        if (same) {
            for (int i = 0; i < next.size(); i++) {
                if (!next.get(i).key().equals(rows.get(i).key())) { same = false; break; }
            }
        }
        rows = next;
        if (!same || (list != null && list.isLayoutRequested())) { notifyDataSetChanged(); return true; }
        if (list == null) return false;
        int first = list.getFirstVisiblePosition();
        for (int i = 0; i < list.getChildCount(); i++) {
            int position = first + i;
            if (position >= 0 && position < rows.size()) getView(position, list.getChildAt(i), list);
        }
        return false;
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
                LinearLayout box = (LinearLayout) convertView;
                if (box == null) {
                    box = ui.column();
                    box.setPadding(0, ui.dp(20), 0, ui.dp(20));
                    TextView label = ui.text("", 13, ui.muted, true);
                    label.setGravity(Gravity.CENTER);
                    label.setPadding(ui.dp(16), ui.dp(18), ui.dp(16), ui.dp(18));
                    label.setBackground(ui.panel());
                    box.addView(label, new LinearLayout.LayoutParams(-1, -2));
                }
                ((TextView) box.getChildAt(0)).setText(row.text);
                return box;
            }
            case ScoreRow.SECTION: {
                Object tag = convertView == null ? null : convertView.getTag();
                SectionHolder holder = tag instanceof SectionHolder ? (SectionHolder) tag : new SectionHolder();
                holder.root.setTag(holder);
                holder.bind(row);
                return holder.root;
            }
            case ScoreRow.FAILURE: {
                LinearLayout box = (LinearLayout) convertView;
                if (box == null) {
                    box = ui.column();
                    box.setPadding(0, ui.dp(4), 0, ui.dp(12));
                    LinearLayout banner = ui.row();
                    banner.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12));
                    ui.clickableDrawable(banner, ui.outlined(ui.errorSoft, Ui.alpha(ui.error, 90), Ui.CARD_RADIUS), Ui.CARD_RADIUS);
                    banner.setFocusable(true);
                    banner.setOnClickListener(v -> host.showSourceStatus());
                    banner.addView(new Ui.Icon(ui.c, "info", ui.error), new LinearLayout.LayoutParams(ui.dp(18), ui.dp(18)));
                    TextView msg = ui.text("", 13, ui.error, true);
                    LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, -2, 1);
                    mp.setMarginStart(ui.dp(10));
                    banner.addView(msg, mp);
                    banner.addView(new Ui.Icon(ui.c, "chevron", ui.error), new LinearLayout.LayoutParams(ui.dp(16), ui.dp(16)));
                    box.addView(banner);
                }
                LinearLayout banner = (LinearLayout) box.getChildAt(0);
                ((TextView) banner.getChildAt(1)).setText(row.text);
                return box;
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

    private void text(TextView view, String value) {
        String shown = EnglishDisplay.text(value);
        if (!shown.contentEquals(view.getText())) view.setText(shown);
    }

    private void bindStatusPill(LinearLayout pill, View dot, TextView label, Match m) {
        if (m.live()) {
            pill.setBackground(ui.outlined(ui.liveSoft, Ui.alpha(ui.live, 95), Ui.PILL_RADIUS));
            dot.setVisibility(View.VISIBLE);
            dot.setBackground(ui.shape(ui.live, Ui.PILL_RADIUS));
            String clock = !m.status.isBlank() ? m.status : "LIVE";
            text(label, clock);
            label.setTextColor(ui.live);
        } else if (m.scheduled()) {
            pill.setBackground(ui.shape(ui.tonal, Ui.PILL_RADIUS));
            dot.setVisibility(View.GONE);
            text(label, m.timeConfirmed ? m.time() : "TBD");
            label.setTextColor(ui.text);
        } else if (m.cancelled()) {
            pill.setBackground(ui.shape(ui.errorSoft, Ui.PILL_RADIUS));
            dot.setVisibility(View.GONE);
            text(label, Rules.stateLabel(m.state));
            label.setTextColor(ui.error);
        } else if ("delayed".equals(m.state)) {
            pill.setBackground(ui.shape(ui.warnSoft, Ui.PILL_RADIUS));
            dot.setVisibility(View.GONE);
            text(label, Rules.stateLabel(m.state));
            label.setTextColor(ui.warn);
        } else {
            pill.setBackground(ui.shape(ui.tonal, Ui.PILL_RADIUS));
            dot.setVisibility(View.GONE);
            text(label, Rules.stateLabel(m.state));
            label.setTextColor(ui.muted);
        }
    }

    private FrameLayout crestSlot(Logos.Crest crest) {
        FrameLayout slot = new FrameLayout(ui.c);
        FrameLayout frame = new FrameLayout(ui.c);
        frame.setBackground(ui.shape(ui.tonal, 10));
        frame.addView(crest.box, new FrameLayout.LayoutParams(ui.dp(24), ui.dp(24), Gravity.CENTER));
        slot.addView(frame, new FrameLayout.LayoutParams(ui.dp(34), ui.dp(34), Gravity.START | Gravity.CENTER_VERTICAL));
        ui.clickable(slot, Color.TRANSPARENT, 12);
        return slot;
    }

    private void teamSlot(FrameLayout slot, String id, String name, boolean home, Match m) {
        boolean available = id != null && !id.isBlank();
        slot.setClickable(available);
        slot.setFocusable(available);
        slot.setContentDescription(available ? "View " + EnglishDisplay.text(name) + " schedule" : null);
        slot.setOnClickListener(available ? v -> host.openTeam(m, home) : null);
    }

    private void bindActionPills(LinearLayout bar, LinearLayout watchBtn, Ui.Icon watchIcon, TextView watchLabel,
                                 LinearLayout remindBtn, Ui.Icon remindIcon, TextView remindLabel, Match m) {
        WatchLinks.Link link = host.watchLink(m);
        boolean canWatch = link != null;
        boolean subscribed = host.isSubscribed(m.id);
        boolean canSubscribe = m.scheduled() && m.timeConfirmed && m.start > System.currentTimeMillis();

        if (canWatch) {
            boolean activeLive = m.live() && link.kind != WatchLinks.Kind.PENDING && link.kind != WatchLinks.Kind.REPLAY;
            int tint = activeLive ? ui.live : ui.primary;
            int fill = activeLive ? ui.liveSoft : ui.primarySoft;
            watchBtn.setVisibility(View.VISIBLE);
            watchIcon.set(link.kind.icon, tint);
            text(watchLabel, link.shortProvider + " · " + link.kind.label);
            watchLabel.setTextColor(tint);
            ui.clickableDrawable(watchBtn, ui.outlined(fill, Ui.alpha(tint, 80), Ui.PILL_RADIUS), Ui.PILL_RADIUS);
            watchBtn.setContentDescription(EnglishDisplay.watchTitle(link) + ", opens in " + EnglishDisplay.text(link.provider));
            watchBtn.setOnClickListener(v -> host.openWatch(m));
        } else {
            watchBtn.setVisibility(View.GONE);
        }

        if (canSubscribe) {
            remindBtn.setVisibility(View.VISIBLE);
            int tint = subscribed ? ui.primary : ui.muted;
            int fill = subscribed ? ui.primarySoft : ui.tonal;
            remindIcon.set(subscribed ? "calendar-check" : "calendar-add", tint);
            text(remindLabel, subscribed ? "Saved" : "Remind");
            remindLabel.setTextColor(subscribed ? ui.primary : ui.text);
            ui.clickableDrawable(remindBtn, ui.outlined(fill, subscribed ? Ui.alpha(ui.primary, 90) : ui.border, Ui.PILL_RADIUS), Ui.PILL_RADIUS);
            remindBtn.setSelected(subscribed);
            String title = EnglishDisplay.text(m.displayTitle());
            remindBtn.setContentDescription(subscribed ? "Unsubscribe from " + title : "Subscribe to " + title);
            remindBtn.setOnClickListener(v -> host.toggleSubscription(m));
        } else {
            remindBtn.setVisibility(View.GONE);
        }

        bar.setVisibility(canWatch || canSubscribe ? View.VISIBLE : View.GONE);
    }

    private final class SectionHolder {
        final LinearLayout root, actionPill;
        final View accentBar;
        final TextView title, countBadge, liveTag, actionText;
        ScoreRow row;

        SectionHolder() {
            root = ui.row();
            root.setPadding(ui.dp(2), ui.dp(18), ui.dp(2), ui.dp(8));

            accentBar = new View(ui.c);
            LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(ui.dp(3.5f), ui.dp(16));
            barLp.setMarginEnd(ui.dp(9));
            root.addView(accentBar, barLp);

            title = ui.text("", 14, ui.text, true);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            root.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

            liveTag = ui.badge("LIVE", ui.liveSoft, ui.live);
            LinearLayout.LayoutParams liveLp = new LinearLayout.LayoutParams(-2, -2);
            liveLp.setMarginStart(ui.dp(6));
            root.addView(liveTag, liveLp);

            countBadge = ui.text("", 12, ui.dim, true);
            LinearLayout.LayoutParams countLp = new LinearLayout.LayoutParams(-2, -2);
            countLp.setMarginStart(ui.dp(8));
            root.addView(countBadge, countLp);

            actionPill = ui.row();
            actionPill.setPadding(ui.dp(10), ui.dp(5), ui.dp(8), ui.dp(5));
            ui.clickableDrawable(actionPill, ui.outlined(ui.tonal, ui.border, Ui.PILL_RADIUS), Ui.PILL_RADIUS);
            actionText = ui.text("", 11, ui.text, true);
            actionPill.addView(actionText);
            Ui.Icon chev = new Ui.Icon(ui.c, "chevron", ui.muted);
            LinearLayout.LayoutParams chevLp = new LinearLayout.LayoutParams(ui.dp(13), ui.dp(13));
            chevLp.setMarginStart(ui.dp(2));
            actionPill.addView(chev, chevLp);
            actionPill.setFocusable(true);
            actionPill.setOnClickListener(v -> { if (row != null) host.openCompetition(row); });
            LinearLayout.LayoutParams actLp = new LinearLayout.LayoutParams(-2, -2);
            actLp.setMarginStart(ui.dp(8));
            root.addView(actionPill, actLp);
        }

        void bind(ScoreRow value) {
            row = value;
            boolean isLiveSection = value.text != null && value.text.startsWith("Live");
            int accent = isLiveSection ? ui.live : ui.sportAccent(value.leagueId, value.sportGroup);
            accentBar.setBackground(ui.shape(accent, 2));
            text(title, value.text);
            title.setTextColor(isLiveSection ? ui.live : ui.text);
            if (value.liveCount > 0 && !isLiveSection) {
                liveTag.setText(value.liveCount + " LIVE");
                liveTag.setVisibility(View.VISIBLE);
            } else {
                liveTag.setVisibility(View.GONE);
            }
            if (value.count > 0) {
                countBadge.setText(String.valueOf(value.count));
                countBadge.setVisibility(View.VISIBLE);
            } else {
                countBadge.setVisibility(View.GONE);
            }
            if (value.actionLabel != null && !value.actionLabel.isEmpty()) {
                text(actionText, value.actionLabel);
                actionPill.setVisibility(View.VISIBLE);
            } else {
                actionPill.setVisibility(View.GONE);
            }
        }
    }

    private final class MatchHolder {
        final LinearLayout root, card, statusPill, actionBar, watchBtn, remindBtn;
        final View accentDot, statusDot;
        final TextView contextLabel, statusLabel, home, away, homeScore, awayScore, stale, watchLabel, remindLabel;
        final Logos.Crest homeCrest, awayCrest;
        final FrameLayout homeSlot, awaySlot;
        final Ui.Icon watchIcon, remindIcon;
        Match match;
        boolean liveStyled;

        MatchHolder() {
            root = ui.column();
            root.setPadding(0, 0, 0, ui.dp(10));

            card = ui.column();
            card.setPadding(ui.dp(16), ui.dp(13), ui.dp(16), ui.dp(13));
            ui.clickableDrawable(card, ui.panel(), Ui.CARD_RADIUS);
            card.setFocusable(true);
            card.setOnClickListener(v -> { if (match != null) host.openMatch(match); });

            // Top context strip: competition/stage on left, status pill on right
            LinearLayout metaRow = ui.row();
            accentDot = new View(ui.c);
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(ui.dp(7), ui.dp(7));
            dotLp.setMarginEnd(ui.dp(7));
            metaRow.addView(accentDot, dotLp);

            contextLabel = ui.text("", 12, ui.muted, true);
            contextLabel.setSingleLine(true);
            contextLabel.setEllipsize(TextUtils.TruncateAt.END);
            metaRow.addView(contextLabel, new LinearLayout.LayoutParams(0, -2, 1f));

            statusPill = ui.row();
            statusPill.setPadding(ui.dp(9), ui.dp(3), ui.dp(9), ui.dp(3));
            statusDot = new View(ui.c);
            LinearLayout.LayoutParams sdLp = new LinearLayout.LayoutParams(ui.dp(6), ui.dp(6));
            sdLp.setMarginEnd(ui.dp(5));
            statusPill.addView(statusDot, sdLp);
            statusLabel = ui.text("", 11, ui.text, true);
            statusPill.addView(statusLabel);
            LinearLayout.LayoutParams pillLp = new LinearLayout.LayoutParams(-2, -2);
            pillLp.setMarginStart(ui.dp(8));
            metaRow.addView(statusPill, pillLp);
            card.addView(metaRow);
            ui.gap(card, 10);

            // Home row
            LinearLayout homeRow = ui.row();
            homeCrest = Logos.crest(ui, 24, false);
            homeSlot = crestSlot(homeCrest);
            homeRow.addView(homeSlot, new LinearLayout.LayoutParams(ui.dp(42), ui.dp(42)));
            home = ui.text("", 15, ui.text, true);
            home.setMaxLines(1);
            home.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(0, -2, 1f);
            hp.setMarginStart(ui.dp(6));
            homeRow.addView(home, hp);
            homeScore = ui.display("—", 20, ui.text);
            homeScore.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams hsp = new LinearLayout.LayoutParams(-2, -2);
            hsp.setMarginStart(ui.dp(12));
            homeRow.addView(homeScore, hsp);
            card.addView(homeRow);
            ui.gap(card, 4);

            // Away row
            LinearLayout awayRow = ui.row();
            awayCrest = Logos.crest(ui, 24, false);
            awaySlot = crestSlot(awayCrest);
            awayRow.addView(awaySlot, new LinearLayout.LayoutParams(ui.dp(42), ui.dp(42)));
            away = ui.text("", 15, ui.text, true);
            away.setMaxLines(1);
            away.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0, -2, 1f);
            ap.setMarginStart(ui.dp(6));
            awayRow.addView(away, ap);
            awayScore = ui.display("—", 20, ui.text);
            awayScore.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams asp = new LinearLayout.LayoutParams(-2, -2);
            asp.setMarginStart(ui.dp(12));
            awayRow.addView(awayScore, asp);
            card.addView(awayRow);

            // Action bar (Watch / Remind pills — hidden when neither applies)
            actionBar = ui.row();
            actionBar.setPadding(0, ui.dp(10), 0, 0);
            watchBtn = ui.row();
            watchBtn.setPadding(ui.dp(11), ui.dp(6), ui.dp(12), ui.dp(6));
            watchBtn.setMinimumHeight(ui.dp(36));
            watchBtn.setFocusable(true);
            watchIcon = new Ui.Icon(ui.c, "play", ui.primary);
            LinearLayout.LayoutParams wiLp = new LinearLayout.LayoutParams(ui.dp(14), ui.dp(14));
            wiLp.setMarginEnd(ui.dp(6));
            watchBtn.addView(watchIcon, wiLp);
            watchLabel = ui.text("", 12, ui.primary, true);
            watchBtn.addView(watchLabel);
            LinearLayout.LayoutParams wbLp = new LinearLayout.LayoutParams(-2, -2);
            wbLp.setMarginEnd(ui.dp(8));
            actionBar.addView(watchBtn, wbLp);

            remindBtn = ui.row();
            remindBtn.setPadding(ui.dp(11), ui.dp(6), ui.dp(12), ui.dp(6));
            remindBtn.setMinimumHeight(ui.dp(36));
            remindBtn.setFocusable(true);
            remindIcon = new Ui.Icon(ui.c, "calendar-add", ui.muted);
            LinearLayout.LayoutParams riLp = new LinearLayout.LayoutParams(ui.dp(14), ui.dp(14));
            riLp.setMarginEnd(ui.dp(6));
            remindBtn.addView(remindIcon, riLp);
            remindLabel = ui.text("Remind", 12, ui.text, true);
            remindBtn.addView(remindLabel);
            actionBar.addView(remindBtn, new LinearLayout.LayoutParams(-2, -2));
            card.addView(actionBar);

            stale = ui.text("Live signal delayed · pull to refresh", 11, ui.warn, false);
            stale.setPadding(0, ui.dp(6), 0, 0);
            stale.setVisibility(View.GONE);
            card.addView(stale);

            root.addView(card);
        }

        void bind(Match m) {
            match = m;
            boolean live = m.live();
            if (live != liveStyled) {
                liveStyled = live;
                ui.clickableDrawable(card, live ? ui.liveCardBg() : ui.panel(), Ui.CARD_RADIUS);
            }
            accentDot.setBackground(ui.shape(ui.sportAccent(m.league, m.group), Ui.PILL_RADIUS));
            accentDot.setVisibility(groupedByCompetition ? View.GONE : View.VISIBLE);
            contextLabel.setVisibility(groupedByCompetition ? View.INVISIBLE : View.VISIBLE);
            String meta = MatchLabel.of(m);
            if (!m.scheduled() && m.timeConfirmed && !m.time().isBlank()) {
                meta = meta + " · " + m.time();
            }
            text(contextLabel, meta);
            bindStatusPill(statusPill, statusDot, statusLabel, m);

            text(home, m.home);
            text(away, m.away);

            int homeOutcome = m.outcome(true);
            int awayOutcome = m.outcome(false);
            home.setTextColor(m.finalised() && homeOutcome < 0 ? ui.muted : ui.text);
            away.setTextColor(m.finalised() && awayOutcome < 0 ? ui.muted : ui.text);

            boolean showScore = m.hasScore();
            text(homeScore, showScore ? m.displayScore(true) : "–");
            text(awayScore, showScore ? m.displayScore(false) : "–");
            homeScore.setTextColor(!showScore ? ui.dim : live ? ui.live : homeOutcome > 0 ? ui.primary : homeOutcome < 0 ? ui.muted : ui.text);
            awayScore.setTextColor(!showScore ? ui.dim : live ? ui.live : awayOutcome > 0 ? ui.primary : awayOutcome < 0 ? ui.muted : ui.text);

            boolean staleNow = live && System.currentTimeMillis() - m.updated > 120_000;
            stale.setVisibility(staleNow ? View.VISIBLE : View.GONE);

            Logos.bind(homeCrest, m.homeLogo, m.home);
            Logos.bind(awayCrest, m.awayLogo, m.away);
            teamSlot(homeSlot, m.homeId, m.home, true, m);
            teamSlot(awaySlot, m.awayId, m.away, false, m);

            bindActionPills(actionBar, watchBtn, watchIcon, watchLabel, remindBtn, remindIcon, remindLabel, m);
        }
    }

    private final class F1Holder {
        final LinearLayout root, card, statusPill, actionBar, watchBtn, remindBtn;
        final View accentDot, statusDot;
        final TextView league, statusLabel, title, first, second, stale, watchLabel, remindLabel;
        final Ui.Icon watchIcon, remindIcon;
        Match match;
        boolean liveStyled;

        F1Holder() {
            root = ui.column();
            root.setPadding(0, 0, 0, ui.dp(10));
            card = ui.column();
            card.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14));
            ui.clickableDrawable(card, ui.panel(), Ui.CARD_RADIUS);
            card.setFocusable(true);
            card.setOnClickListener(v -> { if (match != null) host.openMatch(match); });

            LinearLayout metaRow = ui.row();
            accentDot = new View(ui.c);
            accentDot.setBackground(ui.shape(ui.sportAccent("f1", "F1"), Ui.PILL_RADIUS));
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(ui.dp(7), ui.dp(7));
            dotLp.setMarginEnd(ui.dp(7));
            metaRow.addView(accentDot, dotLp);

            league = ui.text("", 12, ui.muted, true);
            metaRow.addView(league, new LinearLayout.LayoutParams(0, -2, 1f));

            statusPill = ui.row();
            statusPill.setPadding(ui.dp(9), ui.dp(3), ui.dp(9), ui.dp(3));
            statusDot = new View(ui.c);
            LinearLayout.LayoutParams sdLp = new LinearLayout.LayoutParams(ui.dp(6), ui.dp(6));
            sdLp.setMarginEnd(ui.dp(5));
            statusPill.addView(statusDot, sdLp);
            statusLabel = ui.text("", 11, ui.text, true);
            statusPill.addView(statusLabel);
            metaRow.addView(statusPill);
            card.addView(metaRow);
            ui.gap(card, 8);

            title = ui.display("", 16, ui.text);
            card.addView(title);
            first = ui.text("", 13, ui.primary, true);
            first.setPadding(0, ui.dp(6), 0, 0);
            card.addView(first);
            second = ui.text("", 12, ui.muted, false);
            second.setPadding(0, ui.dp(3), 0, 0);
            card.addView(second);

            actionBar = ui.row();
            actionBar.setPadding(0, ui.dp(10), 0, 0);
            watchBtn = ui.row();
            watchBtn.setVisibility(View.GONE);
            watchIcon = new Ui.Icon(ui.c, "play", ui.primary);
            watchLabel = ui.text("", 12, ui.primary, true);
            watchBtn.addView(watchIcon);
            watchBtn.addView(watchLabel);
            actionBar.addView(watchBtn);

            remindBtn = ui.row();
            remindBtn.setPadding(ui.dp(11), ui.dp(6), ui.dp(12), ui.dp(6));
            remindBtn.setMinimumHeight(ui.dp(36));
            remindBtn.setFocusable(true);
            remindIcon = new Ui.Icon(ui.c, "calendar-add", ui.muted);
            LinearLayout.LayoutParams riLp = new LinearLayout.LayoutParams(ui.dp(14), ui.dp(14));
            riLp.setMarginEnd(ui.dp(6));
            remindBtn.addView(remindIcon, riLp);
            remindLabel = ui.text("Remind", 12, ui.text, true);
            remindBtn.addView(remindLabel);
            actionBar.addView(remindBtn);
            card.addView(actionBar);

            stale = ui.text("Live signal delayed · pull to refresh", 11, ui.warn, false);
            stale.setPadding(0, ui.dp(6), 0, 0);
            stale.setVisibility(View.GONE);
            card.addView(stale);
            root.addView(card);
        }

        void bind(Match m) {
            match = m;
            boolean live = m.live();
            if (live != liveStyled) {
                liveStyled = live;
                ui.clickableDrawable(card, live ? ui.liveCardBg() : ui.panel(), Ui.CARD_RADIUS);
            }
            accentDot.setVisibility(groupedByCompetition ? View.GONE : View.VISIBLE);
            league.setVisibility(groupedByCompetition ? View.INVISIBLE : View.VISIBLE);
            text(league, MatchLabel.of(m));
            bindStatusPill(statusPill, statusDot, statusLabel, m);
            text(title, m.displayTitle());
            text(first, m.results.size() > 0 ? m.results.get(0) : "");
            first.setVisibility(m.results.size() > 0 ? View.VISIBLE : View.GONE);
            text(second, m.results.size() > 1 ? m.results.get(1) : "");
            second.setVisibility(m.results.size() > 1 ? View.VISIBLE : View.GONE);
            stale.setVisibility(live && System.currentTimeMillis() - m.updated > 120_000 ? View.VISIBLE : View.GONE);
            bindActionPills(actionBar, watchBtn, watchIcon, watchLabel, remindBtn, remindIcon, remindLabel, m);
        }
    }

    private final class EmptyHolder {
        final Ui.ScheduleEmpty root;
        EmptyHolder() { root = ui.new ScheduleEmpty(host::resolveEmptySchedule); }
    }
}
