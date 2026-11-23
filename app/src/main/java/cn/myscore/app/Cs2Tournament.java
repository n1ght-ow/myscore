package cn.myscore.app;

import static cn.myscore.app.DetailParsers.obj;
import static cn.myscore.app.DetailParsers.objAt;
import static cn.myscore.app.DetailParsers.text;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import org.json.JSONArray;
import org.json.JSONObject;

final class Cs2Tournament {
    private static final DateTimeFormatter SOURCE_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss", Locale.ROOT);
    private static final String EVENT_ID = "csgo_tt_[0-9]+";
    private static final String MATCH_ID = "csgo_mc_[0-9]+";
    private static final String[] ROUND_LABELS = {"第一轮", "第二轮", "第三轮", "第四轮", "第五轮"};
    private static final String[][] RECORDS = {{"0:0"}, {"1:0", "0:1"}, {"2:0", "1:1", "0:2"}, {"2:1", "1:2"}, {"2:2"}};
    private static final String[][] GROUPS = {{"groupAll"}, {"groupHigh", "groupLow"}, {"groupHigh", "groupMid", "groupLow"},
        {"groupHigh", "groupLow"}, {"groupAll"}};
    private static final int[][] STANDARD_COUNTS = {{8}, {4, 4}, {2, 4, 2}, {3, 3}, {3}};

    record Event(String id, String name, String dates) {}
    record Stage(String label, String description, List<Group> groups) {}
    record Group(String label, String format, List<Lane> lanes, List<Team> teams) {}
    record Lane(String label, List<Bucket> buckets) {}
    record Bucket(String id, String label, List<Game> games, List<String> next) {}
    record Game(String id, String graphId, String time, String status, String bestOf, Side first, Side second) {}
    record Side(String name, String logo, String score, boolean winner, String from, String fromLabel) {}
    record Team(String name, String logo, String record, String status) {}
    private record Dated(Event event, LocalDate start, LocalDate end) {}

    private Cs2Tournament() {}

    static List<Event> catalog(LocalDate around, Consumer<List<Event>> onRecent) throws Exception {
        return catalog(around, Repository::get, onRecent);
    }

    static List<Event> catalog(LocalDate around, DomesticEsports.Source source, Consumer<List<Event>> onRecent) throws Exception {
        Map<String, Dated> events = new LinkedHashMap<>();
        ExecutorService readers = Executors.newFixedThreadPool(3);
        List<Future<JSONArray>> pages = new ArrayList<>();
        try {
            for (int number = 1; number <= 3; number++) {
                String url = DomesticEsports.TIER_ONE + "&page=" + number;
                pages.add(readers.submit(() -> response(new JSONObject(source.get(url))).getJSONArray("matches")));
            }
            for (int index = 0; index < pages.size(); index++) {
                JSONArray rows;
                try { rows = pages.get(index).get(); }
                catch (ExecutionException error) {
                    if (error.getCause() instanceof InterruptedException interrupted) throw interrupted;
                    if (events.isEmpty()) {
                        if (error.getCause() instanceof Exception failure) throw failure;
                        throw error;
                    }
                    break;
                }
                collectEvents(rows, events);
                if (rows.length() < 100) break;
                if (index == 0) {
                    List<Event> recent = nearEvents(events, around);
                    if (!recent.isEmpty() && canOpenRecent(events, around)) onRecent.accept(recent);
                }
            }
            return nearEvents(events, around);
        } finally {
            for (Future<JSONArray> page : pages) page.cancel(true);
            readers.shutdownNow();
        }
    }

    private static void collectEvents(JSONArray rows, Map<String, Dated> events) {
        for (int index = 0; index < rows.length(); index++) {
            JSONObject event = obj(objAt(rows, index), "tt_info");
            String id = text(event, "id"), name = text(event, "disp_name");
            if (!id.matches(EVENT_ID) || name.isBlank() || events.containsKey(id)) continue;
            LocalDate start = date(text(event, "start_time"));
            LocalDate end = date(text(event, "end_time"));
            if (start == null) continue;
            if (end == null || end.isBefore(start)) end = start;
            events.put(id, new Dated(new Event(id, name, start.equals(end) ? start.toString() : start + " — " + end), start, end));
        }
    }

    private static boolean canOpenRecent(Map<String, Dated> events, LocalDate around) {
        LocalDate oldest = events.values().stream().map(Dated::start).min(LocalDate::compareTo).orElse(around);
        return around.isAfter(oldest);
    }

    private static List<Event> nearEvents(Map<String, Dated> events, LocalDate around) {
        List<Dated> ordered = new ArrayList<>(events.values());
        ordered.sort(Comparator.comparingLong((Dated entry) -> distance(entry, around)).thenComparing(Dated::start));
        List<Event> result = new ArrayList<>();
        for (Dated dated : ordered) result.add(dated.event());
        return result;
    }

    private static long distance(Dated dated, LocalDate day) {
        if (day.isBefore(dated.start())) return dated.start().toEpochDay() - day.toEpochDay();
        return day.isAfter(dated.end()) ? day.toEpochDay() - dated.end().toEpochDay() : 0;
    }

    static List<Stage> load(Event event) throws Exception {
        if (event == null || !event.id().matches(EVENT_ID)) throw new IOException("赛事标识无效");
        Exception graphFailure = null;
        List<Stage> graph = List.of();
        try {
            graph = parse(new JSONObject(Repository.get(DomesticEsports.FIVE + "tournaments/" + event.id() + "/stages")));
            if (!graph.isEmpty() && graph.stream().allMatch(stage -> !stage.groups().isEmpty())) return graph;
        } catch (Exception error) {
            if (error instanceof InterruptedException || Thread.currentThread().isInterrupted()) throw error;
            graphFailure = error;
        }
        try {
            List<Stage> list = schedule(event, new JSONObject(Repository.get("https://app.5eplay.com/api/tournament/event_session_list?tt_id=" + event.id() + "&game_type=1")));
            if (graph.isEmpty()) return list;
            Map<String, Stage> merged = new LinkedHashMap<>();
            for (Stage stage : graph) merged.put(stage.label(), stage);
            for (Stage stage : list) if (!merged.containsKey(stage.label()) || merged.get(stage.label()).groups().isEmpty()) merged.put(stage.label(), stage);
            return new ArrayList<>(merged.values());
        } catch (Exception error) {
            if (!graph.isEmpty() && !(error instanceof InterruptedException) && !Thread.currentThread().isInterrupted()) return graph;
            if (graphFailure != null) error.addSuppressed(graphFailure);
            throw error;
        }
    }

    static List<Stage> parse(JSONObject root) throws Exception {
        if (!root.optBoolean("success") || root.optJSONArray("data") == null) throw new IOException("5EPlay 赛制读取失败");
        List<Stage> result = new ArrayList<>();
        JSONArray stages = root.getJSONArray("data");
        for (int index = 0; index < stages.length(); index++) {
            JSONObject stage = objAt(stages, index), info = obj(stage, "stageInfo");
            List<Group> groups = new ArrayList<>();
            JSONArray sourceGroups = array(stage, "groups");
            for (int groupIndex = 0; groupIndex < sourceGroups.length(); groupIndex++) {
                JSONObject source = objAt(sourceGroups, groupIndex);
                String raw = text(source, "graph");
                if (raw.isBlank()) continue;
                JSONObject graph = source.optJSONObject("graph");
                if (graph == null) graph = new JSONObject(raw);
                String label = text(source, "name", text(graph, "name", "第 " + (groupIndex + 1) + " 组"));
                String format = text(info, "fmtType");
                Group group = switch (format) {
                    case "swiss" -> swiss(source, graph, label);
                    case "sEB" -> knockout(graph, label, false);
                    case "dEB" -> knockout(graph, label, true);
                    default -> null;
                };
                if (group != null && !group.lanes().isEmpty()) groups.add(group);
            }
            if (!groups.isEmpty() || !text(info, "name").isBlank()) {
                result.add(new Stage(text(info, "name", "已公布阶段"), text(info, "desc"), groups));
            }
        }
        return result;
    }

    private static Group swiss(JSONObject source, JSONObject graph, String label) {
        JSONObject swiss = obj(graph, "swiss");
        boolean standard = standard(swiss);
        List<Lane> lanes = new ArrayList<>();
        for (int round = 0; round < ROUND_LABELS.length; round++) {
            JSONObject roundData = obj(swiss, "r" + (round + 1));
            List<Bucket> buckets = new ArrayList<>();
            for (int index = 0; index < GROUPS[round].length; index++) {
                JSONObject track = obj(roundData, GROUPS[round][index]);
                List<Game> matches = games(array(track, "matches"), "");
                if (matches.isEmpty()) continue;
                String record = RECORDS[round][index];
                String id = standard ? record : "r" + (round + 1) + ":" + index;
                List<String> next = standard ? flow(record) : List.of();
                buckets.add(new Bucket(id, standard ? record : text(track, "name"), matches, next));
            }
            if (standard && round >= 3) {
                int offset = round - 3;
                buckets.add(0, terminal("3:" + offset));
                buckets.add(terminal(offset + ":3"));
            }
            if (!buckets.isEmpty()) lanes.add(new Lane(text(obj(roundData, "basic"), "name", ROUND_LABELS[round]), buckets));
        }
        if (standard) lanes.add(new Lane("最终席位", List.of(terminal("3:2"), terminal("2:3"))));
        return new Group(label, "swiss", lanes, teams(source, graph));
    }

    private static Bucket terminal(String record) { return new Bucket(record, record, List.of(), List.of()); }

    private static boolean standard(JSONObject swiss) {
        for (int round = 0; round < GROUPS.length; round++) {
            JSONObject row = obj(swiss, "r" + (round + 1));
            for (int index = 0; index < GROUPS[round].length; index++) {
                if (array(obj(row, GROUPS[round][index]), "matches").length() != STANDARD_COUNTS[round][index]) return false;
            }
        }
        return true;
    }

    private static List<String> flow(String record) {
        int wins = record.charAt(0) - '0', losses = record.charAt(2) - '0';
        return List.of((wins + 1) + ":" + losses, wins + ":" + (losses + 1));
    }

    private static List<Team> teams(JSONObject source, JSONObject graph) {
        Map<String, String> legends = new LinkedHashMap<>();
        JSONArray config = source.optJSONArray("legend_config");
        if (config == null) try { config = new JSONArray(text(source, "legend_config", "[]")); } catch (org.json.JSONException ignored) {}
        if (config != null) for (int index = 0; index < config.length(); index++) {
            JSONObject entry = objAt(config, index);
            legends.put(text(entry, "id"), text(entry, "text"));
        }
        if (legends.isEmpty()) legends = Map.of("1", "晋级", "4", "淘汰");
        List<Team> result = new ArrayList<>();
        JSONArray rows = array(graph, "curRankList");
        for (int index = 0; index < rows.length(); index++) {
            JSONObject row = objAt(rows, index);
            String name = teamName(row);
            if (name.equals("待定")) continue;
            String wins = digits(row, "winCnt"), losses = digits(row, "lossCnt");
            String record = wins.isBlank() || losses.isBlank() ? "" : wins + ":" + losses;
            String status = text(row, "promotion_result").equals("sure") ? legends.getOrDefault(text(row, "promotion_label"), "") : "";
            result.add(new Team(name, logo(row), record, status));
        }
        return result;
    }

    private static Group knockout(JSONObject graph, String label, boolean dual) {
        List<Lane> lanes = new ArrayList<>();
        if (dual) {
            JSONObject format = obj(graph, "dEB");
            addLane(lanes, obj(format, "upperEB"), "胜者组", "upper:");
            addLane(lanes, obj(format, "lowerEB"), "败者组", "lower:");
            JSONObject championship = format.optJSONObject("championMatch");
            if (championship != null && (championship.has("t1") || championship.has("t2"))) {
                Game match = game(championship, "");
                lanes.add(new Lane(text(obj(format, "championBasic"), "name", "总决赛"), List.of(new Bucket("final", "", List.of(match), List.of()))));
            }
        } else addLane(lanes, obj(obj(graph, "sEB"), "EB"), "", "");
        return new Group(label, dual ? "double" : "single", lanes, List.of());
    }

    private static void addLane(List<Lane> lanes, JSONObject graph, String name, String prefix) {
        List<Bucket> rounds = new ArrayList<>();
        JSONArray sourceRounds = array(graph, "rounds");
        for (int index = 0; index < sourceRounds.length(); index++) {
            JSONObject round = objAt(sourceRounds, index);
            List<Game> matches = games(array(round, "matches"), prefix);
            if (matches.isEmpty()) continue;
            String title = text(obj(round, "basic"), "name", "第 " + (index + 1) + " 轮");
            rounds.add(new Bucket(prefix + index, title, matches, List.of()));
        }
        if (!rounds.isEmpty()) lanes.add(new Lane(name, rounds));
    }

    private static List<Game> games(JSONArray matches, String prefix) {
        List<Game> result = new ArrayList<>();
        for (int index = 0; index < matches.length(); index++) {
            JSONObject match = matches.optJSONObject(index);
            if (match != null && (match.has("t1") || match.has("t2") || match.has("absId"))) result.add(game(match, prefix));
        }
        return result;
    }

    private static Game game(JSONObject game, String prefix) {
        String rawStatus = text(game, "status");
        boolean finished = rawStatus.equals("past"), live = rawStatus.equals("live");
        String result = finished ? text(game, "result") : "";
        Side first = side(obj(game, "t1"), finished || live ? digits(game, "t1Score") : "", result.equals("t1"), prefix);
        Side second = side(obj(game, "t2"), finished || live ? digits(game, "t2Score") : "", result.equals("t2"), prefix);
        String id = text(game, "absId"), graphId = text(game, "graphId"), format = digits(game, "format");
        return new Game(id.matches(MATCH_ID) ? id : "", graphId.isBlank() ? "" : prefix + graphId, time(text(game, "planTs")),
            finished ? "已结束" : live ? "进行中" : rawStatus.equals("upcoming") ? "" : status(rawStatus), format.isBlank() ? "" : "BO" + format, first, second);
    }

    private static Side side(JSONObject team, String score, boolean winner, String prefix) {
        String from = text(team, "fromMatchId");
        String description = text(team, "fromDesc");
        if (description.matches("m_[a-z0-9_-]+")) description = "";
        if (!from.matches("(?:m_r[0-9]+-[0-9]+|csgo_mc_[0-9]+)")) from = "";
        if (!from.isBlank() && !prefix.isBlank() && !from.matches(MATCH_ID)) {
            from = description.startsWith("胜者组") && prefix.equals("lower:") || description.startsWith("败者组") && prefix.equals("upper:") ? "" : prefix + from;
        }
        return new Side(teamName(team), logo(team), score, winner, from, description);
    }

    private static List<Stage> schedule(Event event, JSONObject root) throws Exception {
        Map<String, Map<String, List<Game>>> byStage = new LinkedHashMap<>();
        JSONArray rows = array(response(root), "matches");
        List<Match> matches = new ArrayList<>();
        Map<String, String[]> labels = new LinkedHashMap<>();
        for (int index = 0; index < rows.length(); index++) {
            JSONObject row = objAt(rows, index), raw = obj(row, "mc_info");
            if (!event.id().equals(text(obj(row, "tt_info"), "id"))) continue;
            Match match = DomesticEsports.fiveMatch(row, 0, false);
            if (!match.id.matches("five:cs2:" + MATCH_ID) || labels.containsKey(match.id)) continue;
            labels.put(match.id, new String[]{text(raw, "tt_stage", "已公布对阵"), text(raw, "round_name"), digits(raw, "format")});
            matches.add(match);
        }
        matches.sort(Comparator.comparingLong(match -> match.start > 0 ? match.start : Long.MAX_VALUE));
        for (Match match : matches) {
            String[] slots = labels.get(match.id);
            boolean score = match.live() || match.finalised();
            Side first = new Side(match.home, match.homeLogo, score ? match.homeScore : "", match.outcome(true) > 0, "", "");
            Side second = new Side(match.away, match.awayLogo, score ? match.awayScore : "", match.outcome(false) > 0, "", "");
            Game game = new Game(match.id, "", Formats.instant(match.start, "M月d日 HH:mm", ""), match.status,
                slots[2].isBlank() ? "" : "BO" + slots[2], first, second);
            byStage.computeIfAbsent(slots[0], unused -> new LinkedHashMap<>()).computeIfAbsent(slots[1], unused -> new ArrayList<>()).add(game);
        }
        List<Stage> stages = new ArrayList<>();
        for (Map.Entry<String, Map<String, List<Game>>> stage : byStage.entrySet()) {
            List<Bucket> rounds = new ArrayList<>();
            for (Map.Entry<String, List<Game>> round : stage.getValue().entrySet()) {
                rounds.add(new Bucket("", round.getKey(), round.getValue(), List.of()));
            }
            Group group = new Group("", "schedule", List.of(new Lane("", rounds)), List.of());
            stages.add(new Stage(stage.getKey(), "来源暂未提供完整签表。", List.of(group)));
        }
        return stages;
    }

    private static JSONObject response(JSONObject root) throws IOException {
        if (!root.optBoolean("success") || root.optJSONObject("data") == null) throw new IOException("5EPlay 赛事读取失败");
        return root.optJSONObject("data");
    }

    private static JSONArray array(JSONObject root, String key) {
        JSONArray array = root.optJSONArray(key);
        return array == null ? new JSONArray() : array;
    }

    private static String teamName(JSONObject team) {
        String name = text(team, "nameEn", text(team, "abbrEn", text(team, "nameZh", text(team, "abbrZh"))));
        return team.optBoolean("TBD") || name.isBlank() || name.equalsIgnoreCase("TBD") ? "待定" : name;
    }

    private static String logo(JSONObject team) { return teamName(team).equals("待定") ? "" : DetailSources.https(text(team, "logo")); }

    private static String digits(JSONObject source, String key) {
        String number = text(source, key);
        return number.matches("[0-9]+") ? number : "";
    }

    private static LocalDate date(String text) {
        try { return LocalDate.parse(text.substring(0, 10)); } catch (RuntimeException ignored) { return null; }
    }

    private static String time(String raw) {
        try {
            long millis = LocalDateTime.parse(raw, SOURCE_TIME).toInstant(ZoneOffset.UTC).toEpochMilli();
            return Formats.instant(millis, "M月d日 HH:mm", "");
        } catch (RuntimeException ignored) { return ""; }
    }

    private static String status(String raw) {
        return switch (raw) { case "cancelled", "canceled" -> "取消"; case "postponed" -> "延期"; default -> ""; };
    }
}
