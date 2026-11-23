package cn.myscore.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class LeagueStandings {
    private static final String API = "https://site.api.espn.com/apis/v2/sports/";
    private static final String SOURCE = "ESPN";
    private static final List<String> SOCCER_COLUMNS = List.of("场", "胜", "平", "负", "净胜", "积分");
    private static final List<String> NBA_COLUMNS = List.of("胜", "负", "胜率", "胜差");
    private static final List<String> NFL_COLUMNS = List.of("胜", "负", "平", "胜率", "净胜");

    private LeagueStandings() {}

    static Standings load(Competition competition) throws Exception {
        if (competition == null) throw new IllegalArgumentException("缺少赛事");
        return parse(competition, Repository.get(endpoint(competition)));
    }

    static String endpoint(Competition competition) throws Exception {
        if (competition == null || competition.path == null || competition.path.isBlank()) {
            throw new Exception("该赛事暂无积分榜来源");
        }
        return API + competition.path + "/standings" + (competition.id.equals("nfl") ? "?level=3" : "");
    }

    static Standings parse(Competition competition, String payload) throws Exception {
        if (competition == null) throw new IllegalArgumentException("缺少赛事");
        if (payload == null || payload.isBlank()) throw new Exception("积分榜来源返回空内容");
        JSONObject root = new JSONObject(payload);
        return switch (competition.id) {
            case "epl" -> parseEpl(root);
            case "ucl" -> parseUcl(root);
            case "nba" -> parseNba(root);
            case "nfl" -> parseNfl(root);
            default -> throw new Exception("该赛事暂无积分榜");
        };
    }

    private static Standings parseEpl(JSONObject root) throws Exception {
        JSONObject holder = firstStandingChild(root);
        JSONObject standings = standings(holder);
        JSONArray entries = entries(standings);
        List<String> columns = soccerColumns(entries);
        List<Standings.Row> rows = soccerRows("epl", entries, columns.size() > SOCCER_COLUMNS.size());
        sortByRank(rows);
        return new Standings(SOURCE, season(root, standings),
            List.of(new Standings.Section("积分榜", columns, rows)), List.of());
    }

    private static Standings parseUcl(JSONObject root) throws Exception {
        JSONObject holder = leaguePhaseChild(root);
        JSONObject standings = standings(holder);
        JSONArray entries = entries(standings);
        List<String> columns = soccerColumns(entries);
        List<Standings.Row> rows = soccerRows("ucl", entries, columns.size() > SOCCER_COLUMNS.size());
        sortByRank(rows);
        return new Standings(SOURCE, season(root, standings),
            List.of(new Standings.Section("联赛阶段", columns, rows)), List.of());
    }

    private static Standings parseNba(JSONObject root) throws Exception {
        List<Standings.Section> sections = new ArrayList<>();
        boolean east = false;
        boolean west = false;
        String season = "";
        for (JSONObject child : objects(array(root, "children"))) {
            JSONObject standings = child.optJSONObject("standings");
            if (standings == null) continue;
            JSONArray entries = standings.optJSONArray("entries");
            if (entries == null || entries.length() == 0) continue;
            String title = nbaTitle(child);
            if (title.isEmpty()) continue;
            List<Standings.Row> rows = nbaRows(entries);
            sortByRank(rows);
            sections.add(new Standings.Section(title, NBA_COLUMNS, rows));
            if (season.isEmpty()) season = season(root, standings);
            east |= title.equals("东部");
            west |= title.equals("西部");
        }
        if (!east || !west) throw new Exception("NBA 分区积分榜不完整");
        return new Standings(SOURCE, season, sections, List.of());
    }

    private static Standings parseNfl(JSONObject root) throws Exception {
        List<Standings.Section> sections = new ArrayList<>();
        Set<String> conferences = new HashSet<>();
        String season = "";
        for (JSONObject conference : objects(array(root, "children"))) {
            String code = nflConference(conference);
            if (code.isEmpty()) continue;
            for (JSONObject division : objects(array(conference, "children"))) {
                JSONObject standings = division.optJSONObject("standings");
                if (standings == null) continue;
                JSONArray entries = entries(standings);
                String title = code + " " + divisionTitle(code, division);
                sections.add(new Standings.Section(title, NFL_COLUMNS, nflRows(entries)));
                conferences.add(code);
                if (season.isEmpty()) season = season(root, standings);
            }
        }
        if (!conferences.contains("AFC") || !conferences.contains("NFC")) {
            throw new Exception("NFL 分区积分榜不完整");
        }
        return new Standings(SOURCE, season, sections, List.of());
    }

    private static List<String> soccerColumns(JSONArray entries) {
        List<String> columns = new ArrayList<>(SOCCER_COLUMNS);
        if (hasDeductions(entries)) columns.add("扣分");
        return columns;
    }

    private static List<Standings.Row> soccerRows(String league, JSONArray entries,
                                                   boolean includeDeductions) throws Exception {
        List<Standings.Row> rows = new ArrayList<>();
        for (JSONObject entry : objects(entries)) {
            JSONObject team = entry.optJSONObject("team");
            String name = teamName(league, team);
            if (name.isEmpty()) continue;
            Map<String, JSONObject> stats = stats(entry);
            List<String> values = new ArrayList<>();
            values.add(display(stats, "gamesPlayed"));
            values.add(display(stats, "wins"));
            values.add(display(stats, "ties"));
            values.add(display(stats, "losses"));
            values.add(display(stats, "pointDifferential"));
            values.add(display(stats, "points"));
            if (includeDeductions) values.add(deduction(stats));
            String status = noteStatus(league, entry.optJSONObject("note"));
            rows.add(new Standings.Row(positiveRank(stats, "rank"), name, "", logo(team), values, status));
        }
        if (rows.isEmpty()) throw new Exception("积分榜缺少球队");
        return rows;
    }

    private static List<Standings.Row> nbaRows(JSONArray entries) throws Exception {
        List<Standings.Row> rows = new ArrayList<>();
        for (JSONObject entry : objects(entries)) {
            JSONObject team = entry.optJSONObject("team");
            String name = teamName("nba", team);
            if (name.isEmpty()) continue;
            Map<String, JSONObject> stats = stats(entry);
            List<String> values = List.of(
                display(stats, "wins"),
                display(stats, "losses"),
                display(stats, "winPercent"),
                display(stats, "gamesBehind")
            );
            String rank = positiveRank(stats, "playoffSeed");
            if (rank.isEmpty()) rank = positiveRank(stats, "rank");
            rows.add(new Standings.Row(rank, name, "", logo(team), values, ""));
        }
        if (rows.isEmpty()) throw new Exception("NBA 积分榜缺少球队");
        return rows;
    }

    private static List<Standings.Row> nflRows(JSONArray entries) throws Exception {
        List<Standings.Row> rows = new ArrayList<>();
        for (JSONObject entry : objects(entries)) {
            JSONObject team = entry.optJSONObject("team");
            String name = teamName("nfl", team);
            if (name.isEmpty()) continue;
            Map<String, JSONObject> stats = stats(entry);
            List<String> values = List.of(
                display(stats, "wins"),
                display(stats, "losses"),
                display(stats, "ties"),
                display(stats, "winPercent"),
                firstDisplay(stats, "pointDifferential", "differential")
            );
            rows.add(new Standings.Row(positiveRank(stats, "playoffSeed"), name, "", logo(team), values, ""));
        }
        if (rows.isEmpty()) throw new Exception("NFL 分区缺少球队");
        return rows;
    }

    private static void sortByRank(List<Standings.Row> rows) {
        if (rows.isEmpty()) return;
        for (Standings.Row row : rows) if (rank(row.rank()) <= 0) return;
        rows.sort(Comparator.comparingInt(row -> rank(row.rank())));
    }

    private static int rank(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (RuntimeException ignored) {
            return -1;
        }
    }

    private static boolean hasDeductions(JSONArray entries) {
        for (JSONObject entry : objects(entries)) {
            Double value = statValue(stats(entry).get("deductions"));
            if (value != null && Math.abs(value) > 0.000001) return true;
        }
        return false;
    }

    private static String deduction(Map<String, JSONObject> stats) {
        JSONObject stat = stats.get("deductions");
        String shown = display(stat);
        if (!shown.isEmpty()) return shown;
        Double value = statValue(stat);
        return value == null ? "" : decimal(value);
    }

    private static JSONObject firstStandingChild(JSONObject root) throws Exception {
        for (JSONObject child : objects(array(root, "children"))) {
            JSONObject standings = child.optJSONObject("standings");
            JSONArray entries = standings == null ? null : standings.optJSONArray("entries");
            if (entries != null && entries.length() > 0) return child;
        }
        throw new Exception("积分榜缺少分组");
    }

    private static JSONObject leaguePhaseChild(JSONObject root) throws Exception {
        for (JSONObject child : objects(array(root, "children"))) {
            String label = (text(child, "name") + " " + text(child, "abbreviation")).toLowerCase(Locale.ROOT);
            if (!label.contains("league phase") && !label.contains("联赛阶段")) continue;
            JSONObject standings = child.optJSONObject("standings");
            JSONArray entries = standings == null ? null : standings.optJSONArray("entries");
            if (entries != null && entries.length() > 0) return child;
        }
        throw new Exception("欧冠当前暂无联赛阶段积分榜");
    }

    private static JSONObject standings(JSONObject holder) throws Exception {
        JSONObject standings = holder.optJSONObject("standings");
        if (standings == null) throw new Exception("积分榜缺少排名数据");
        return standings;
    }

    private static JSONArray entries(JSONObject standings) throws Exception {
        JSONArray entries = standings.optJSONArray("entries");
        if (entries == null || entries.length() == 0) throw new Exception("积分榜暂无数据");
        return entries;
    }

    private static String nbaTitle(JSONObject child) {
        String label = (text(child, "name") + " " + text(child, "abbreviation")).toLowerCase(Locale.ROOT);
        if (label.contains("east")) return "东部";
        if (label.contains("west")) return "西部";
        return "";
    }

    private static String nflConference(JSONObject conference) {
        String abbreviation = text(conference, "abbreviation").toUpperCase(Locale.ROOT);
        if (abbreviation.equals("AFC") || abbreviation.equals("NFC")) return abbreviation;
        String label = text(conference, "name").toLowerCase(Locale.ROOT);
        if (label.contains("afc") || label.contains("american")) return "AFC";
        if (label.contains("nfc") || label.contains("national")) return "NFC";
        return "";
    }

    private static String divisionTitle(String conference, JSONObject division) {
        String name = text(division, "name");
        String label = (name + " " + text(division, "abbreviation")).toLowerCase(Locale.ROOT);
        if (label.contains("east") || label.contains("东")) return "东区";
        if (label.contains("north") || label.contains("北")) return "北区";
        if (label.contains("south") || label.contains("南")) return "南区";
        if (label.contains("west") || label.contains("西")) return "西区";
        String upper = name.toUpperCase(Locale.ROOT);
        if (upper.startsWith(conference)) name = name.substring(conference.length()).trim();
        return name.isEmpty() ? "分区" : name;
    }

    private static String noteStatus(String league, JSONObject note) {
        String description = text(note, "description").toLowerCase(Locale.ROOT);
        if (description.isEmpty()) return "";
        if (league.equals("epl")) {
            if (description.contains("champions league")) return "欧冠区";
            if (description.contains("europa league")) return "欧联区";
            if (description.contains("conference league")) return "欧协联区";
            if (description.contains("relegation")) return "降级区";
            return "";
        }
        if (!league.equals("ucl")) return "";
        if (description.contains("round of 16") || description.contains("qualif")) return "前八区";
        if (description.contains("playoff") && description.contains("unseeded")) return "附加赛非种子区";
        if (description.contains("playoff") && description.contains("seeded")) return "附加赛种子区";
        if (description.contains("eliminat")) return "淘汰区";
        if (description.contains("playoff")) return "附加赛区";
        return "";
    }

    private static String season(JSONObject root, JSONObject standings) {
        JSONArray seasons = array(root, "seasons");
        if (seasons.length() > 0) {
            String years = text(seasons.optJSONObject(0), "seasonYears");
            if (!years.isEmpty()) return years;
        }
        String display = text(standings, "seasonDisplayName");
        if (!display.isEmpty()) return display;
        JSONObject season = root.optJSONObject("season");
        String rootDisplay = text(season, "displayName");
        if (!rootDisplay.isEmpty()) return rootDisplay;
        String year = text(season, "year");
        if (!year.isEmpty()) return year;
        return text(standings, "season");
    }

    private static String teamName(String league, JSONObject team) {
        String name = text(team, "displayName");
        if (name.isEmpty()) name = text(team, "shortDisplayName");
        if (name.isEmpty()) name = text(team, "name");
        if (name.isEmpty()) name = text(team, "location");
        if (name.isEmpty()) return "";
        String translated = MiguTeams.chinese(league, name);
        return translated == null || translated.isBlank() ? name : translated;
    }

    private static String logo(JSONObject team) {
        for (JSONObject image : objects(array(team, "logos"))) {
            String href = text(image, "href");
            if (href.startsWith("http://") || href.startsWith("https://")) return href;
        }
        String direct = text(team, "logo");
        return direct.startsWith("http://") || direct.startsWith("https://") ? direct : "";
    }

    private static Map<String, JSONObject> stats(JSONObject entry) {
        Map<String, JSONObject> result = new HashMap<>();
        for (JSONObject stat : objects(array(entry, "stats"))) {
            String name = text(stat, "name");
            if (!name.isEmpty()) result.put(name, stat);
        }
        return result;
    }

    private static String firstDisplay(Map<String, JSONObject> stats, String first, String second) {
        String value = display(stats, first);
        return value.isEmpty() ? display(stats, second) : value;
    }

    private static String positiveRank(Map<String, JSONObject> stats, String name) {
        String value = display(stats, name);
        return rank(value) > 0 ? value : "";
    }

    private static String display(Map<String, JSONObject> stats, String name) {
        return display(stats.get(name));
    }

    private static String display(JSONObject stat) {
        if (stat == null) return "";
        String shown = text(stat, "displayValue");
        if (!shown.isEmpty()) return shown;
        Double value = statValue(stat);
        return value == null ? "" : decimal(value);
    }

    private static Double statValue(JSONObject stat) {
        if (stat == null || !stat.has("value") || stat.isNull("value")) return null;
        double value = stat.optDouble("value", Double.NaN);
        return Double.isFinite(value) ? value : null;
    }

    private static String decimal(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static List<JSONObject> objects(JSONArray array) {
        List<JSONObject> result = new ArrayList<>();
        for (int index = 0; index < array.length(); index++) {
            JSONObject object = array.optJSONObject(index);
            if (object != null) result.add(object);
        }
        return result;
    }

    private static JSONArray array(JSONObject source, String key) {
        if (source == null) return new JSONArray();
        JSONArray value = source.optJSONArray(key);
        return value == null ? new JSONArray() : value;
    }

    private static String text(JSONObject source, String key) {
        if (source == null) return "";
        Object value = source.opt(key);
        if (value == null || value == JSONObject.NULL || value instanceof JSONObject || value instanceof JSONArray) {
            return "";
        }
        return String.valueOf(value).trim();
    }
}
