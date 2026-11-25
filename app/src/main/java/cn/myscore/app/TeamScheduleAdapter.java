package cn.myscore.app;

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

/** Modern fixture & result cards for Team Hub with form badges, Home/Away pills, and inline reminders. */
final class TeamScheduleAdapter extends BaseAdapter {
    interface Host { void openMatch(Match match); void toggleSubscription(Match match); boolean isSubscribed(String id); boolean isBusy(); }
    private final Ui ui;
    private final Host host;
    private final List<TeamScheduleRow> rows = new ArrayList<>();
    private ListView list;

    TeamScheduleAdapter(Ui ui, Host host) { this.ui = ui; this.host = host; }
    void attach(ListView list) { this.list = list; }
    void refreshSubscriptions() { notifyDataSetChanged(); }

    boolean updateRows(List<TeamScheduleRow> next) {
        boolean same = next.size() == rows.size();
        if (same) for (int i = 0; i < next.size(); i++) if (!next.get(i).key.equals(rows.get(i).key)) { same = false; break; }
        rows.clear();
        rows.addAll(next);
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
    @Override public TeamScheduleRow getItem(int position) { return rows.get(position); }
    @Override public long getItemId(int position) { return position; }
    @Override public boolean hasStableIds() { return false; }
    @Override public int getViewTypeCount() { return 3; }
    @Override public int getItemViewType(int position) { return rows.get(position).type; }

    @Override public View getView(int position, View convertView, ViewGroup parent) {
        TeamScheduleRow row = rows.get(position);
        if (row.type == TeamScheduleRow.SECTION) {
            TextView text = (TextView) convertView;
            if (text == null) {
                text = ui.eyebrow("", ui.muted);
                text.setPadding(ui.dp(4), ui.dp(14), 0, ui.dp(8));
            }
            text.setText(row.title);
            text.setTextColor(row.title.startsWith("Live") ? ui.live : ui.muted);
            return text;
        }
        if (row.type == TeamScheduleRow.NOTE) {
            LinearLayout box = (LinearLayout) convertView;
            if (box == null) {
                box = ui.column();
                box.setBackground(ui.panel());
                box.setPadding(ui.dp(18), ui.dp(16), ui.dp(18), ui.dp(16));
                TextView title = ui.text("", 15, ui.text, true);
                TextView message = ui.text("", 13, ui.muted, false);
                message.setLineSpacing(ui.dp(4), 1);
                message.setPadding(0, ui.dp(6), 0, 0);
                box.addView(title);
                box.addView(message);
                box.setTag(new NoteHolder(title, message));
            }
            NoteHolder holder = (NoteHolder) box.getTag();
            holder.title.setText(row.title);
            holder.message.setText(row.message);
            holder.message.setVisibility(row.message == null || row.message.isEmpty() ? View.GONE : View.VISIBLE);
            return box;
        }
        Object tag = convertView == null ? null : convertView.getTag();
        MatchHolder holder = tag instanceof MatchHolder ? (MatchHolder) tag : new MatchHolder();
        holder.root.setTag(holder);
        holder.bind(row);
        return holder.root;
    }

    private void text(TextView view, String value) { if (!value.contentEquals(view.getText())) view.setText(value); }

    private static final class NoteHolder {
        final TextView title, message;
        NoteHolder(TextView title, TextView message) { this.title = title; this.message = message; }
    }

    private final class MatchHolder {
        final LinearLayout root, card, numbers, livePill, footer, remindPill;
        final TextView date, sidePill, state, liveText, opponent, detail, timeText, remindLabel, resultBadge;
        final Ui.ScoreText score;
        final Ui.Icon remindIcon;
        final FrameLayout crestWrap;
        final Logos.Crest crest;
        final View footDivider;
        final int scoreWidth;
        TeamScheduleRow row;

        MatchHolder() {
            root = ui.column();
            root.setPadding(0, 0, 0, ui.dp(10));
            card = ui.column();
            card.setPadding(ui.dp(16), ui.dp(13), ui.dp(16), ui.dp(12));
            ui.clickableDrawable(card, ui.panel(), Ui.CARD_RADIUS);
            card.setFocusable(true);
            card.setOnClickListener(v -> { if (row != null && row.match != null) host.openMatch(row.match); });

            // Top meta row: Date + Home/Away pill + Status/Live pill
            LinearLayout top = ui.row();
            date = Ui.singleLine(ui.text("", 12, ui.muted, true));
            top.addView(date, new LinearLayout.LayoutParams(-2, -2));
            sidePill = ui.badge("", ui.tonal, ui.muted);
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, -2);
            sp.setMarginStart(ui.dp(8));
            top.addView(sidePill, sp);
            top.addView(new View(ui.c), new LinearLayout.LayoutParams(0, 1, 1f));
            livePill = ui.liveBadge("LIVE");
            liveText = (TextView) livePill.getChildAt(1);
            livePill.setVisibility(View.GONE);
            top.addView(livePill);
            state = ui.badge("", ui.tonal, ui.muted);
            top.addView(state);
            card.addView(top);
            ui.gap(card, 11);

            // Center row: Opponent crest + Name/Detail + Score + W/D/L pill
            LinearLayout main = ui.row();
            crestWrap = new FrameLayout(ui.c);
            crestWrap.setBackground(ui.outlined(ui.tonal, ui.border, 12));
            crest = Logos.crest(ui, 26, false);
            crestWrap.addView(crest.box, new FrameLayout.LayoutParams(ui.dp(26), ui.dp(26), Gravity.CENTER));
            main.addView(crestWrap, new LinearLayout.LayoutParams(ui.dp(38), ui.dp(38)));

            LinearLayout middle = ui.column();
            opponent = Ui.singleLine(ui.text("", 15, ui.text, true));
            middle.addView(opponent);
            detail = Ui.singleLine(ui.text("", 12, ui.muted, false));
            detail.setPadding(0, ui.dp(2), 0, 0);
            middle.addView(detail);
            LinearLayout.LayoutParams middleParams = new LinearLayout.LayoutParams(0, -2, 1f);
            middleParams.setMarginStart(ui.dp(12));
            main.addView(middle, middleParams);

            scoreWidth = Math.max(ui.dp(66), ui.scoreColumnWidth(20));
            numbers = ui.row();
            numbers.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            score = ui.score("", "", false, 20);
            numbers.addView(score, new LinearLayout.LayoutParams(-2, -2));
            resultBadge = ui.formPill("W");
            LinearLayout.LayoutParams rbp = (LinearLayout.LayoutParams) resultBadge.getLayoutParams();
            rbp.setMarginStart(ui.dp(8));
            numbers.addView(resultBadge, rbp);
            LinearLayout.LayoutParams numbersParams = new LinearLayout.LayoutParams(-2, -2);
            numbersParams.setMarginStart(ui.dp(10));
            main.addView(numbers, numbersParams);
            card.addView(main);

            // Clean footer row: kickoff time + Remind/Saved pill (hidden when no time/action needed)
            footDivider = ui.divider();
            LinearLayout.LayoutParams fdp = new LinearLayout.LayoutParams(-1, ui.dp(1));
            fdp.topMargin = ui.dp(11);
            fdp.bottomMargin = ui.dp(8);
            card.addView(footDivider, fdp);

            footer = ui.row();
            Ui.Icon clockIcon = new Ui.Icon(ui.c, "clock", ui.dim);
            LinearLayout.LayoutParams cip = new LinearLayout.LayoutParams(ui.dp(14), ui.dp(14));
            cip.setMarginEnd(ui.dp(6));
            footer.addView(clockIcon, cip);
            timeText = Ui.singleLine(ui.text("", 12, ui.muted, true));
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1f);
            tlp.setMarginEnd(ui.dp(8));
            footer.addView(timeText, tlp);

            remindPill = ui.row();
            remindPill.setGravity(Gravity.CENTER);
            remindPill.setMinimumHeight(ui.dp(34));
            remindPill.setPadding(ui.dp(11), ui.dp(5), ui.dp(12), ui.dp(5));
            remindIcon = new Ui.Icon(ui.c, "bell", ui.text);
            LinearLayout.LayoutParams rip = new LinearLayout.LayoutParams(ui.dp(14), ui.dp(14));
            rip.setMarginEnd(ui.dp(5));
            remindPill.addView(remindIcon, rip);
            remindLabel = Ui.singleLine(ui.text("Remind", 12, ui.text, true));
            remindPill.addView(remindLabel);
            remindPill.setFocusable(true);
            remindPill.setOnClickListener(v -> { if (row != null) host.toggleSubscription(row.match); });
            footer.addView(remindPill);
            card.addView(footer);

            root.addView(card, new LinearLayout.LayoutParams(-1, -2));
        }

        void bind(TeamScheduleRow value) {
            row = value;
            boolean live = value.match.live();
            ui.clickableDrawable(card, live ? ui.liveCardBg() : ui.panel(), Ui.CARD_RADIUS);

            text(date, value.displayDate());
            String sideText = value.displaySide();
            if (sideText.isEmpty()) {
                sidePill.setVisibility(View.GONE);
            } else {
                sidePill.setVisibility(View.VISIBLE);
                text(sidePill, sideText);
                boolean isHome = "Home".equalsIgnoreCase(sideText);
                sidePill.setBackground(ui.shape(isHome ? ui.infoSoft : ui.tonal, Ui.PILL_RADIUS));
                sidePill.setTextColor(isHome ? ui.info : ui.muted);
            }

            if (live) {
                livePill.setVisibility(View.VISIBLE);
                state.setVisibility(View.GONE);
                text(liveText, value.displayState().isBlank() ? "LIVE" : value.displayState());
            } else {
                livePill.setVisibility(View.GONE);
                state.setVisibility(View.VISIBLE);
                text(state, value.displayState());
                boolean warn = value.match.cancelled() || value.match.state.equals("delayed");
                state.setBackground(ui.shape(warn ? ui.errorSoft : ui.tonal, Ui.PILL_RADIUS));
                state.setTextColor(warn ? ui.error : ui.muted);
            }

            text(opponent, value.displayOpponent());
            String detailText = value.displayDetail();
            text(detail, detailText);
            detail.setVisibility(detailText.isBlank() ? View.GONE : View.VISIBLE);

            boolean scored = !value.score.isEmpty();
            String res = value.displayResult();
            numbers.setVisibility(scored || !res.isEmpty() ? View.VISIBLE : View.GONE);
            String own = value.match.displayScore(value.home), other = value.match.displayScore(!value.home);
            score.setScore(own, other, scored);
            score.setResultColors(ui.scoreColor(value.match, value.home), ui.scoreColor(value.match, !value.home));
            score.setVisibility(scored ? View.VISIBLE : View.GONE);

            if (res.isEmpty()) {
                resultBadge.setVisibility(View.GONE);
            } else {
                resultBadge.setVisibility(View.VISIBLE);
                text(resultBadge, res);
                int fill = "W".equals(res) ? ui.primarySoft : "L".equals(res) ? ui.errorSoft : ui.warnSoft;
                int ink = "W".equals(res) ? ui.primary : "L".equals(res) ? ui.error : ui.warn;
                resultBadge.setTextColor(ink);
                resultBadge.setBackground(ui.outlined(fill, Ui.alpha(ink, 65), 8));
            }

            boolean subscribed = host.isSubscribed(value.match.id);
            boolean eligible = value.match.scheduled() && value.match.timeConfirmed && value.match.start > System.currentTimeMillis();
            boolean showRemind = eligible || subscribed;
            String startTime = value.displayTime();
            boolean showFooter = showRemind || (!value.match.finalised() && !startTime.isBlank());
            footDivider.setVisibility(showFooter ? View.VISIBLE : View.GONE);
            footer.setVisibility(showFooter ? View.VISIBLE : View.GONE);
            text(timeText, startTime);

            remindPill.setVisibility(showRemind ? View.VISIBLE : View.GONE);
            remindPill.setEnabled(!host.isBusy());
            remindPill.setAlpha(host.isBusy() ? 0.5f : 1f);
            remindIcon.set(subscribed ? "calendar-check" : "bell", subscribed ? ui.primary : ui.text);
            text(remindLabel, subscribed ? "Saved" : "Remind");
            remindLabel.setTextColor(subscribed ? ui.primary : ui.text);
            ui.clickableDrawable(remindPill, subscribed
                ? ui.outlined(ui.primarySoft, Ui.alpha(ui.primary, 100), Ui.PILL_RADIUS)
                : ui.outlined(ui.tonal, ui.border, Ui.PILL_RADIUS), Ui.PILL_RADIUS);
            remindPill.setContentDescription((subscribed ? "Remove match against " : "Add match against ") + value.displayOpponent() + (subscribed ? " from calendar" : " to calendar"));

            Logos.bind(crest, value.home ? value.match.awayLogo : value.match.homeLogo, value.opponent);
            card.setContentDescription(value.displayDate() + ", " + value.displayTime()
                + (value.side.isEmpty() ? "" : ", " + value.displaySide()) + ", vs " + value.displayOpponent()
                + ", " + value.displayState() + (scored ? ", score " + value.score : ""));
        }
    }
}
