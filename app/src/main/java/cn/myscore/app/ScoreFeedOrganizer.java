package cn.myscore.app;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Pure presentation & relationship logic for organizing matches, date strips,
 * team form snapshots, and standings qualification zones.
 */
final class ScoreFeedOrganizer {
    static final String GROUP_COMPETITION = "competition";
    static final String GROUP_STATUS = "status";

    static final String LENS_ALL = "all";
    static final String LENS_LIVE = "live";
    static final String LENS_SAVED = "saved";

    record DayChip(LocalDate date, String weekday, String dayNumber, boolean today, boolean selected) {}

    record FeedSummary(int total, int live, int upcoming, int completed, int saved, Map<String, Integer> bySport) {}

    record CompetitionGroup(String leagueId, String sportGroup, String title, int liveCount, boolean hasStandings, boolean hasCs2Tournament, List<Match> matches) {}

    record FormEntry(String result, String opponent, String score, Match match) {}

    record TeamSnapshot(int wins, int draws, int losses, int live, int upcoming, int completed, List<FormEntry> recentForm) {}

    enum Zone {
        NONE,
        CHAMPIONS,
        QUALIFY,
        PLAY_IN,
        RELEGATION,
        PODIUM_GOLD,
        PODIUM_SILVER,
        PODIUM_BRONZE
    }

    private ScoreFeedOrganizer() {}

    /** Builds a 7-day horizontal date rail centered on the selected date. */
    static List<DayChip> weekStrip(LocalDate selected, LocalDate today) {
        List<DayChip> days = new ArrayList<>(7);
        LocalDate start = selected.minusDays(3);
        for (int i = 0; i < 7; i++) {
            LocalDate d = start.plusDays(i);
            String wd = d.equals(today) ? "Today"
                : d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
            days.add(new DayChip(d, wd, String.valueOf(d.getDayOfMonth()), d.equals(today), d.equals(selected)));
        }
        return days;
    }

    /** Summarizes match counts across states and sport categories for feed badges and lens pills. */
    static FeedSummary summarize(List<Match> matches, Set<String> subscribedIds) {
        int live = 0, upcoming = 0, completed = 0, saved = 0;
        Map<String, Integer> bySport = new LinkedHashMap<>();
        if (matches != null) {
            for (Match m : matches) {
                if (m == null) continue;
                if (m.live()) live++;
                else if (m.scheduled()) upcoming++;
                else if (m.finalised()) completed++;
                if (subscribedIds != null && subscribedIds.contains(m.id)) saved++;
                String sport = m.group == null ? "" : m.group;
                if (!sport.isEmpty()) bySport.put(sport, bySport.getOrDefault(sport, 0) + 1);
            }
        }
        return new FeedSummary(matches == null ? 0 : matches.size(), live, upcoming, completed, saved, bySport);
    }

    /** Filters matches by sport tab and smart lens (All / Live / Saved). */
    static List<Match> filter(List<Match> matches, String sport, String lens, Set<String> subscribedIds) {
        List<Match> out = new ArrayList<>();
        if (matches == null) return out;
        boolean allSport = sport == null || sport.isEmpty() || "全部".equals(sport);
        for (Match m : matches) {
            if (m == null) continue;
            if (!allSport && !sport.equals(m.group)) continue;
            if (LENS_LIVE.equals(lens) && !m.live()) continue;
            if (LENS_SAVED.equals(lens) && (subscribedIds == null || !subscribedIds.contains(m.id))) continue;
            out.add(m);
        }
        return out;
    }

    /**
     * Groups matches by competition with live competitions prioritized first,
     * keeping contextual links to standings and CS2 tournaments.
     */
    static List<CompetitionGroup> groupByCompetition(List<Match> matches) {
        if (matches == null || matches.isEmpty()) return List.of();
        Map<String, List<Match>> buckets = new LinkedHashMap<>();
        for (Match m : matches) {
            String key = competitionKey(m);
            buckets.computeIfAbsent(key, k -> new ArrayList<>()).add(m);
        }
        List<CompetitionGroup> groups = new ArrayList<>(buckets.size());
        for (Map.Entry<String, List<Match>> entry : buckets.entrySet()) {
            List<Match> list = new ArrayList<>(entry.getValue());
            list.sort(Comparator.comparingInt(ScoreFeedOrganizer::statePriority)
                .thenComparingLong(m -> m.start == 0 ? Long.MAX_VALUE : m.start)
                .thenComparing(m -> m.id == null ? "" : m.id));
            Match first = list.get(0);
            int live = 0;
            boolean hasTournament = false;
            for (Match m : list) {
                if (m.live()) live++;
                if ("cs2".equals(m.league) && m.event != null && !m.event.isBlank()) hasTournament = true;
            }
            String title = competitionTitle(first);
            boolean hasStandings = supportsStandings(first.league);
            groups.add(new CompetitionGroup(first.league, first.group, title, live, hasStandings, hasTournament, list));
        }
        groups.sort(Comparator.comparingInt((CompetitionGroup g) -> g.liveCount() > 0 ? 0 : 1)
            .thenComparingInt(g -> competitionOrder(g.leagueId()))
            .thenComparing(CompetitionGroup::title));
        return groups;
    }

    static String competitionKey(Match m) {
        if ("cs2".equals(m.league) && m.event != null && !m.event.isBlank()) {
            return "cs2:" + m.event.trim();
        }
        return m.league == null || m.league.isBlank() ? (m.group == null ? "other" : m.group) : m.league;
    }

    static String competitionTitle(Match m) {
        if ("cs2".equals(m.league)) {
            return m.event != null && !m.event.isBlank() ? EnglishDisplay.tournament(m.event) : "CS2";
        }
        Competition c = Competition.find(m.league);
        if (c != null) return EnglishDisplay.text(c.name);
        return EnglishDisplay.text(m.group);
    }

    static boolean supportsStandings(String leagueId) {
        if (leagueId == null || leagueId.isBlank()) return false;
        return !NationalTeams.LEAGUE.equals(leagueId);
    }

    static int statePriority(Match m) {
        if (m.live()) return 0;
        if (m.scheduled()) return 1;
        if ("delayed".equals(m.state)) return 2;
        if (m.finalised()) return 3;
        return 4;
    }

    static int competitionOrder(String leagueId) {
        if (leagueId == null) return 99;
        return switch (leagueId) {
            case "epl" -> 0;
            case "ucl" -> 1;
            case "fa" -> 2;
            case "efl" -> 3;
            case "intl" -> 4;
            case "nba" -> 5;
            case "nfl" -> 6;
            case "f1" -> 7;
            case "cs2" -> 8;
            default -> 20;
        };
    }

    /**
     * Computes team record and recent form (most recent 5 finished numeric games, oldest->newest or newest->oldest).
     * Returns newest-first up to 5 completed results purely from actual published match outcomes.
     */
    static TeamSnapshot summarizeTeam(Team team, List<Match> matches) {
        int wins = 0, draws = 0, losses = 0, live = 0, upcoming = 0, completed = 0;
        List<Match> finished = new ArrayList<>();
        if (matches != null && team != null) {
            for (Match m : matches) {
                if (m == null) continue;
                if (m.live()) live++;
                else if (m.scheduled() || "delayed".equals(m.state)) upcoming++;
                else if (m.finalised()) {
                    completed++;
                    if (m.hasScore()) {
                        boolean home = team.id.equals(m.homeId) || (!team.id.equals(m.awayId) && team.name.equals(m.home));
                        int outcome = m.outcome(home);
                        if (outcome > 0) wins++;
                        else if (outcome < 0) losses++;
                        else if (m.homeScore.matches("\\d+") && m.awayScore.matches("\\d+")) draws++;
                        finished.add(m);
                    }
                }
            }
        }
        finished.sort(Comparator.comparingLong((Match m) -> m.start).reversed());
        List<FormEntry> recent = new ArrayList<>();
        for (int i = 0; i < Math.min(5, finished.size()); i++) {
            Match m = finished.get(i);
            TeamScheduleRow row = TeamScheduleRow.match(m, team);
            String r = row.displayResult();
            if (!r.isEmpty()) recent.add(new FormEntry(r, row.displayOpponent(), row.score, m));
        }
        return new TeamSnapshot(wins, draws, losses, live, upcoming, completed, recent);
    }

    /** Classifies a standings row into a meaningful qualification / relegation zone. */
    static Zone standingsZone(String leagueId, String tableTitle, int rank, int totalRows) {
        if (rank <= 0 || leagueId == null) return Zone.NONE;
        String lower = tableTitle == null ? "" : tableTitle.toLowerCase(Locale.ROOT);
        return switch (leagueId) {
            case "epl" -> {
                if (rank <= 4) yield Zone.CHAMPIONS;
                if (rank == 5) yield Zone.PLAY_IN;
                if (totalRows >= 18 && rank > totalRows - 3) yield Zone.RELEGATION;
                yield Zone.NONE;
            }
            case "ucl" -> {
                if (rank <= 8) yield Zone.QUALIFY;
                if (rank <= 24) yield Zone.CHAMPIONS;
                yield Zone.RELEGATION;
            }
            case "nba" -> {
                if (rank <= 6) yield Zone.QUALIFY;
                if (rank <= 10) yield Zone.PLAY_IN;
                yield Zone.NONE;
            }
            case "nfl" -> {
                if (rank == 1) yield Zone.QUALIFY;
                if (rank <= 7 && (lower.contains("conference") || lower.contains("afc") || lower.contains("nfc") || totalRows >= 10)) yield Zone.CHAMPIONS;
                yield Zone.NONE;
            }
            case "f1" -> {
                if (rank == 1) yield Zone.PODIUM_GOLD;
                if (rank == 2) yield Zone.PODIUM_SILVER;
                if (rank == 3) yield Zone.PODIUM_BRONZE;
                yield Zone.NONE;
            }
            case "cs2" -> {
                if (rank <= 3) yield Zone.PODIUM_GOLD;
                if (rank <= 12) yield Zone.QUALIFY;
                yield Zone.NONE;
            }
            default -> Zone.NONE;
        };
    }

    /** Parses a numeric stat string (like "62%", "14", "45.2", "32:10") for visual comparison bars. */
    static double statMagnitude(String raw) {
        if (raw == null || raw.isBlank()) return Double.NaN;
        String s = raw.trim();
        if (s.contains("/")) {
            // e.g. "5/12" or "18/34 (53%)" -> prefer leading completed or percentage
            int pct = s.indexOf('%');
            if (pct > 0) {
                int start = pct - 1;
                while (start > 0 && (Character.isDigit(s.charAt(start - 1)) || s.charAt(start - 1) == '.')) start--;
                try { return Double.parseDouble(s.substring(start, pct)); } catch (NumberFormatException ignored) {}
            }
            s = s.substring(0, s.indexOf('/')).trim();
        }
        return DetailRows.number(s);
    }
}
