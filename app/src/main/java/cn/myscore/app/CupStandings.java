package cn.myscore.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class CupStandings {
    private static final DateTimeFormatter MATCH_TIME = Formats.zoned("M月d日 HH:mm");
    private static final Pattern LEG_NOTE = Pattern.compile("(?i)^(1st|2nd|first|second)\\s+leg\\b.*$");
    private static final Pattern AGGREGATE_NOTE = Pattern.compile(
        "(?i)^(?:1st|2nd|first|second)\\s+leg\\s*-\\s*(.+?)\\s+advance(?:s)?\\s+(\\d+)\\s*[-–]\\s*(\\d+)\\s+on\\s+aggregate.*$");
    private static final Pattern AGGREGATE_SCORE = Pattern.compile(
        "(?i).*?\\b(\\d+)\\s*[-–]\\s*(\\d+)\\s+on\\s+aggregate.*$");

    private CupStandings() {}

    static Standings load(Competition competition) throws Exception {
        if (competition == null || !(competition.id.equals("fa") || competition.id.equals("efl"))) {
            throw new IllegalArgumentException("杯赛数据不支持该赛事");
        }
        int currentYear = LocalDate.now(Match.ZONE).getYear();
        JSONObject current = new JSONObject(Repository.get(board(competition, currentYear)));
        int seasonYear = seasonYear(current);
        if (seasonYear <= 0) throw new IllegalStateException("杯赛来源未返回当前赛季");
        int otherYear = seasonYear == currentYear ? currentYear + 1 : seasonYear;
        JSONObject other = otherYear == currentYear ? null : new JSONObject(Repository.get(board(competition, otherYear)));
        return parse(competition, current, other, System.currentTimeMillis());
    }

    static Standings parse(Competition competition, JSONObject first, JSONObject second, long now) throws Exception {
        JSONObject league = league(first);
        if (league == null) league = league(second);
        if (league == null) throw new IllegalStateException("杯赛来源未返回联赛信息");
        int seasonYear = seasonYearOfLeague(league);
        if (seasonYear <= 0) throw new IllegalStateException("杯赛来源未返回当前赛季");
        JSONObject season = league.optJSONObject("season");
        String seasonName = seasonName(season, seasonYear);
        List<String> calendar = calendarLabels(first);
        if (calendar.isEmpty()) calendar = calendarLabels(second);
        Map<String, Integer> roundOrder = new LinkedHashMap<>();
        Map<String, String> roundNames = new LinkedHashMap<>();
        for (int index = 0; index < calendar.size(); index++) {
            String label = calendar.get(index);
            String key = normalize(label);
            if (key.isEmpty() || roundOrder.containsKey(key)) continue;
            roundOrder.put(key, index);
            roundNames.put(key, label);
        }

        Map<String, List<Entry>> groups = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        collect(first, seasonYear, roundNames, groups, seen, now);
        collect(second, seasonYear, roundNames, groups, seen, now);

        List<RoundGroup> ordered = new ArrayList<>();
        for (Map.Entry<String, List<Entry>> group : groups.entrySet()) {
            List<Entry> entries = group.getValue();
            entries.sort(Comparator.comparingLong((Entry entry) -> entry.start == 0 ? Long.MAX_VALUE : entry.start)
                .thenComparing(entry -> entry.tie.first().name())
                .thenComparing(entry -> entry.tie.second().name()));
            long earliest = entries.stream().mapToLong(entry -> entry.start == 0 ? Long.MAX_VALUE : entry.start).min().orElse(Long.MAX_VALUE);
            ordered.add(new RoundGroup(group.getKey(), entries, earliest));
        }
        ordered.sort(Comparator.comparingInt((RoundGroup group) -> roundOrder.getOrDefault(normalize(group.label), Integer.MAX_VALUE))
            .thenComparingLong(group -> group.earliest));

        List<Standings.Round> rounds = new ArrayList<>();
        for (RoundGroup group : ordered) {
            List<Standings.Tie> ties = new ArrayList<>();
            boolean started = false;
            for (Entry entry : group.entries) {
                ties.add(entry.tie);
                started |= entry.started;
            }
            rounds.add(new Standings.Round(displayRound(group.label), ties, started));
        }
        return new Standings("ESPN", seasonName, List.of(), rounds);
    }

    private static void collect(JSONObject root, int seasonYear, Map<String, String> roundNames,
                                Map<String, List<Entry>> groups, Set<String> seen, long now) {
        if (root == null) return;
        JSONArray events = root.optJSONArray("events");
        if (events == null) return;
        for (int index = 0; index < events.length(); index++) {
            JSONObject event = events.optJSONObject(index);
            if (event == null) continue;
            JSONObject season = event.optJSONObject("season");
            if (season == null || season.optInt("year", 0) != seasonYear) continue;
            JSONArray competitions = event.optJSONArray("competitions");
            if (competitions == null) continue;
            String eventId = text(event, "id");
            for (int part = 0; part < competitions.length(); part++) {
                JSONObject competition = competitions.optJSONObject(part);
                if (competition == null) continue;
                String competitionId = text(competition, "id");
                if (!seen.add(eventId + ":" + competitionId)) continue;
                String round = sourceRound(event, competition, roundNames);
                if (round.isEmpty()) continue;
                Entry entry = entry(competition, event, now);
                if (entry == null) continue;
                groups.computeIfAbsent(round, unused -> new ArrayList<>()).add(entry);
            }
        }
    }

    private static Entry entry(JSONObject competition, JSONObject event, long now) {
        JSONObject status = competition.optJSONObject("status");
        if (status == null) status = event.optJSONObject("status");
        JSONObject type = status == null ? null : status.optJSONObject("type");
        String statusName = type == null ? "" : text(type, "name");
        String rawState = type == null ? "" : text(type, "state");
        String state = Rules.state(statusName, rawState);
        boolean completed = state.equals("final");
        boolean live = state.equals("live");
        String rawDate = text(competition, "date");
        if (rawDate.isEmpty()) rawDate = text(competition, "startDate");
        if (rawDate.isEmpty()) rawDate = text(event, "date");
        long start = Match.parseTime(rawDate);
        boolean started = completed || live;
        String date = start > 0 ? MATCH_TIME.format(Instant.ofEpochMilli(start)) : "";

        JSONArray competitors = competition.optJSONArray("competitors");
        JSONObject home = null;
        JSONObject away = null;
        JSONObject firstUnassigned = null;
        JSONObject secondUnassigned = null;
        if (competitors != null) {
            for (int index = 0; index < competitors.length(); index++) {
                JSONObject competitor = competitors.optJSONObject(index);
                if (competitor == null) continue;
                String position = text(competitor, "homeAway");
                if (position.equals("home") && home == null) home = competitor;
                else if (position.equals("away") && away == null) away = competitor;
                else if (firstUnassigned == null) firstUnassigned = competitor;
                else if (secondUnassigned == null) secondUnassigned = competitor;
            }
        }
        if (home == null) home = firstUnassigned;
        if (away == null) away = secondUnassigned == null ? (firstUnassigned == home ? null : firstUnassigned) : secondUnassigned;
        if (home == null || away == null || home == away) return null;
        String homeId = text(team(home), "id");
        String awayId = text(team(away), "id");
        if (!homeId.isEmpty() && homeId.equals(awayId)) return null;

        String note = firstNote(competition);
        JSONObject leg = competition.optJSONObject("leg");
        int legValue = leg == null ? 0 : leg.optInt("value", 0);
        String stage = stage(leg, legValue, note);
        boolean showScore = completed || live;
        JSONObject homeTeam = team(home);
        JSONObject awayTeam = team(away);
        String homeName = name(homeTeam, "主队待定");
        String awayName = name(awayTeam, "客队待定");
        Standings.Side first = new Standings.Side(homeName, logo(homeTeam), showScore ? score(home) : "",
            completed && home.optBoolean("winner", false));
        Standings.Side second = new Standings.Side(awayName, logo(awayTeam), showScore ? score(away) : "",
            completed && away.optBoolean("winner", false));
        String result = result(competition, type, statusName, state, home, away, homeName, awayName, legValue, note, completed);
        return new Entry(new Standings.Tie(date, stage, first, second, result), start, started);
    }

    private static String result(JSONObject competition, JSONObject type, String statusName, String state,
                                 JSONObject home, JSONObject away, String homeName, String awayName,
                                 int legValue, String note, boolean completed) {
        if (!completed) return statusLabel(state, statusName, type == null ? "" : text(type, "detail"));
        List<String> parts = new ArrayList<>();
        if (legValue != 1) {
            String aggregate = aggregate(competition, home, away, homeName, awayName, note);
            if (!aggregate.isEmpty()) parts.add(aggregate);
        }
        String detail = type == null ? "" : text(type, "detail");
        if (statusName.contains("AET") || detail.equalsIgnoreCase("AET")) parts.add("加时");
        if (statusName.contains("PEN") || detail.toLowerCase(Locale.ROOT).contains("pens")) {
            String homePenalties = shootoutScore(home);
            String awayPenalties = shootoutScore(away);
            if (!homePenalties.isEmpty() && !awayPenalties.isEmpty()) {
                parts.add("点球 " + homePenalties + "–" + awayPenalties);
            }
        }
        return String.join(" · ", parts);
    }

    private static String statusLabel(String state, String statusName, String detail) {
        return switch (state) {
            case "live" -> "进行中";
            case "delayed" -> statusName.toLowerCase(Locale.ROOT).contains("suspend")
                || detail.toLowerCase(Locale.ROOT).contains("suspend") ? "暂停" : "延期";
            case "cancelled" -> statusName.toLowerCase(Locale.ROOT).contains("abandon")
                || detail.toLowerCase(Locale.ROOT).contains("abandon") ? "中止" : "取消";
            case "scheduled" -> "待开赛";
            default -> "";
        };
    }

    private static String aggregate(JSONObject competition, JSONObject home, JSONObject away,
                                    String homeName, String awayName, String note) {
        JSONObject series = competition.optJSONObject("series");
        if (series != null && series.optBoolean("completed", false) && series.optInt("totalCompetitions", 0) > 1) {
            String fromSeries = seriesAggregate(series, home, away, homeName, awayName);
            if (!fromSeries.isEmpty()) return fromSeries;
        }
        Matcher named = AGGREGATE_NOTE.matcher(note);
        if (named.matches()) {
            return named.group(1).trim() + " 总比分 " + named.group(2) + "–" + named.group(3) + " 晋级";
        }
        Matcher score = AGGREGATE_SCORE.matcher(note);
        if (score.matches()) return "总比分 " + score.group(1) + "–" + score.group(2) + " 晋级";
        return "";
    }

    private static String seriesAggregate(JSONObject series, JSONObject home, JSONObject away,
                                          String homeName, String awayName) {
        JSONArray competitors = series.optJSONArray("competitors");
        if (competitors == null) return "";
        String homeId = text(team(home), "id");
        String awayId = text(team(away), "id");
        JSONObject winner = null;
        JSONObject other = null;
        for (int index = 0; index < competitors.length(); index++) {
            JSONObject competitor = competitors.optJSONObject(index);
            if (competitor == null) continue;
            if (competitor.optBoolean("winner", false)) winner = competitor;
            else other = competitor;
        }
        if (winner == null || other == null) return "";
        String winnerId = text(winner, "id");
        String winnerName = winnerId.equals(homeId) ? homeName : winnerId.equals(awayId) ? awayName : "";
        String otherId = text(other, "id");
        String otherName = otherId.equals(homeId) ? homeName : otherId.equals(awayId) ? awayName : "";
        String winnerScore = aggregateScore(winner);
        String otherScore = aggregateScore(other);
        if (winnerName.isEmpty() || otherName.isEmpty() || winnerScore.isEmpty() || otherScore.isEmpty()) return "";
        return winnerName + " 总比分 " + winnerScore + "–" + otherScore + " 晋级";
    }

    private static String sourceRound(JSONObject event, JSONObject competition, Map<String, String> roundNames) {
        JSONObject season = event.optJSONObject("season");
        String slug = season == null ? "" : text(season, "slug");
        String fromSlug = roundNames.get(normalize(slug.replace('-', ' ')));
        if (fromSlug != null) return fromSlug;
        String alt = text(competition, "altGameNote");
        int comma = alt.lastIndexOf(',');
        String fromAlt = comma >= 0 ? alt.substring(comma + 1).trim() : alt.trim();
        String matched = roundNames.get(normalize(fromAlt));
        if (matched != null) return matched;
        if (!fromAlt.isEmpty()) return fromAlt;
        JSONObject series = competition.optJSONObject("series");
        String title = series == null ? "" : text(series, "title");
        String fromSeries = roundNames.get(normalize(title));
        if (fromSeries != null) return fromSeries;
        if (!title.isEmpty()) return title;
        return slug.isEmpty() ? "" : titleCase(slug.replace('-', ' '));
    }

    private static String stage(JSONObject leg, int value, String note) {
        if (value == 1) return "首回合";
        if (value == 2) return "次回合";
        if (value > 0 && leg != null) {
            String display = text(leg, "displayValue");
            if (!display.isEmpty()) return display;
        }
        Matcher matcher = LEG_NOTE.matcher(note);
        if (matcher.matches()) {
            String number = matcher.group(1).toLowerCase(Locale.ROOT);
            return number.equals("1st") || number.equals("first") ? "首回合" : "次回合";
        }
        return note.toLowerCase(Locale.ROOT).contains("replay") ? "重赛" : "";
    }

    private static String displayRound(String label) {
        return switch (normalize(label)) {
            case "preliminary round" -> "预赛";
            case "first round" -> "第一轮";
            case "second round" -> "第二轮";
            case "third round" -> "第三轮";
            case "fourth round" -> "第四轮";
            case "fifth round" -> "第五轮";
            case "quarterfinals", "quarter finals" -> "1/4决赛";
            case "semifinals", "semi finals" -> "半决赛";
            case "final" -> "决赛";
            default -> label;
        };
    }

    private static String seasonName(JSONObject season, int seasonYear) {
        String display = season == null ? "" : text(season, "displayName");
        int space = display.indexOf(' ');
        String first = space < 0 ? display : display.substring(0, space);
        if (first.matches("\\d{4}-\\d{2}")) return first;
        return seasonYear + "-" + String.format(Locale.ROOT, "%02d", (seasonYear + 1) % 100);
    }

    private static int seasonYear(JSONObject root) {
        return seasonYearOfLeague(league(root));
    }

    private static int seasonYearOfLeague(JSONObject league) {
        if (league == null) return 0;
        JSONObject season = league.optJSONObject("season");
        if (season == null) return 0;
        int year = season.optInt("year", 0);
        if (year > 0) return year;
        String display = text(season, "displayName");
        if (display.matches("^\\d{4}.*")) {
            try {
                return Integer.parseInt(display.substring(0, 4));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static JSONObject league(JSONObject root) {
        if (root == null) return null;
        JSONArray leagues = root.optJSONArray("leagues");
        return leagues == null || leagues.length() == 0 ? null : leagues.optJSONObject(0);
    }

    private static List<String> calendarLabels(JSONObject root) {
        JSONObject league = league(root);
        if (league == null) return List.of();
        JSONArray calendars = league.optJSONArray("calendar");
        if (calendars == null) return List.of();
        List<String> labels = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < calendars.length(); index++) {
            JSONObject calendar = calendars.optJSONObject(index);
            JSONArray entries = calendar == null ? null : calendar.optJSONArray("entries");
            if (entries == null) continue;
            for (int entry = 0; entry < entries.length(); entry++) {
                JSONObject item = entries.optJSONObject(entry);
                String label = item == null ? "" : text(item, "label").trim();
                if (!label.isEmpty() && seen.add(normalize(label))) labels.add(label);
            }
        }
        return labels;
    }

    private static String firstNote(JSONObject competition) {
        JSONArray notes = competition.optJSONArray("notes");
        if (notes == null) return "";
        for (int index = 0; index < notes.length(); index++) {
            JSONObject note = notes.optJSONObject(index);
            String value = note == null ? "" : text(note, "text");
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private static JSONObject team(JSONObject competitor) {
        JSONObject team = competitor.optJSONObject("team");
        return team == null ? competitor : team;
    }

    private static String name(JSONObject team, String fallback) {
        String name = text(team, "displayName");
        if (name.isEmpty()) name = text(team, "shortDisplayName");
        if (name.isEmpty()) name = text(team, "name");
        return name.isEmpty() ? fallback : name;
    }

    private static String logo(JSONObject team) {
        String direct = text(team, "logo");
        if (direct.startsWith("https://")) return direct;
        JSONArray logos = team.optJSONArray("logos");
        if (logos == null) return "";
        for (int index = 0; index < logos.length(); index++) {
            JSONObject item = logos.optJSONObject(index);
            String href = item == null ? "" : text(item, "href");
            if (href.startsWith("https://")) return href;
        }
        return "";
    }

    private static String score(JSONObject competitor) {
        String value = value(competitor, "score");
        return value.matches("\\d+\\.0+") ? value.substring(0, value.indexOf('.')) : value;
    }

    private static String shootoutScore(JSONObject competitor) {
        return value(competitor, "shootoutScore");
    }

    private static String aggregateScore(JSONObject competitor) {
        return value(competitor, "aggregateScore");
    }

    private static String value(JSONObject object, String key) {
        Object raw = object.opt(key);
        if (raw == null || raw == JSONObject.NULL) return "";
        if (raw instanceof JSONObject nested) {
            String display = text(nested, "displayValue");
            return display.isEmpty() ? text(nested, "value") : display;
        }
        String value = String.valueOf(raw).trim();
        if (value.endsWith(".0")) value = value.substring(0, value.length() - 2);
        return value.equals("null") ? "" : value;
    }

    private static String text(JSONObject object, String key) {
        if (object == null || !object.has(key) || object.isNull(key)) return "";
        String value = object.optString(key, "").trim();
        return value.equals("null") ? "" : value;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace('-', ' ').replaceAll("\\s+", " ").trim();
    }

    private static String titleCase(String value) {
        String[] words = value.split("\\s+");
        StringBuilder output = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (output.length() > 0) output.append(' ');
            output.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return output.toString();
    }

    private static String board(Competition competition, int year) {
        return "https://site.api.espn.com/apis/site/v2/sports/" + competition.path
            + "/scoreboard?dates=" + year + "&limit=1000";
    }

    private record Entry(Standings.Tie tie, long start, boolean started) {}
    private record RoundGroup(String label, List<Entry> entries, long earliest) {}
}
