package cn.myscore.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Cs2Standings {
    private static final String API = "https://api.github.com/repos/ValveSoftware/counter-strike_regional_standings/contents/live/";
    private static final List<String> SCOPES = List.of("global", "europe", "americas", "asia");
    private static final List<String> TITLES = List.of("全球", "欧洲", "美洲", "亚洲");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuu_MM_dd", java.util.Locale.ROOT);
    private static final Pattern FILENAME = Pattern.compile("standings_(global|europe|americas|asia)_(\\d{4}_\\d{2}_\\d{2})\\.md");
    private static final Pattern HEADING = Pattern.compile("### (?:Regional )?Standings(?: for (Europe|Americas|Asia))? as of (\\d{4}_\\d{2}_\\d{2})(?:<br\\s*/?>)?");
    private static final Pattern NUMBER = Pattern.compile("\\d+");
    private static final Map<String, String> HEADERS = Map.of("Accept", "application/vnd.github+json");

    private Cs2Standings() {}

    static Standings load() throws Exception {
        LocalDate today = LocalDate.now(Match.ZONE);
        LocalDate published = null;
        for (int year = today.getYear(); year >= today.getYear() - 1 && published == null; year--) {
            try {
                published = latestCommon(new JSONArray(Repository.get(API + year, HEADERS)), year, today);
            } catch (Http.HttpStatusException error) {
                if (error.code != 404) throw error;
            }
        }
        if (published == null) throw new IOException("Valve VRS 未发布完整榜单");
        LocalDate release = published;
        ExecutorService workers = Executors.newFixedThreadPool(SCOPES.size());
        try {
            List<Callable<Standings.Section>> tasks = new ArrayList<>();
            for (String scope : SCOPES) tasks.add(() -> fetch(scope, release));
            List<Standings.Section> sections = new ArrayList<>();
            for (Future<Standings.Section> result : workers.invokeAll(tasks)) sections.add(result.get());
            return new Standings("Valve VRS", release + " 榜单", sections, List.of());
        } finally {
            workers.shutdownNow();
        }
    }

    private static Standings.Section fetch(String scope, LocalDate date) throws Exception {
        String name = "standings_" + scope + "_" + DATE.format(date) + ".md";
        JSONObject file = new JSONObject(Repository.get(API + date.getYear() + "/" + name, HEADERS));
        if (!name.equals(file.optString("name")) || !"base64".equals(file.optString("encoding"))) {
            throw new IOException("Valve VRS 文件不匹配");
        }
        byte[] bytes = Base64.getMimeDecoder().decode(file.optString("content"));
        if (bytes.length != file.optInt("size", -1)) throw new IOException("Valve VRS 文件不完整");
        return parse(new String(bytes, StandardCharsets.UTF_8), scope, date);
    }

    static LocalDate latestCommon(JSONArray entries, int year, LocalDate today) {
        Map<LocalDate, Set<String>> dates = new HashMap<>();
        for (int index = 0; index < entries.length(); index++) {
            JSONObject file = entries.optJSONObject(index);
            if (file == null || !"file".equals(file.optString("type"))) continue;
            Matcher name = FILENAME.matcher(file.optString("name"));
            if (!name.matches()) continue;
            try {
                LocalDate date = LocalDate.parse(name.group(2), DATE.withResolverStyle(java.time.format.ResolverStyle.STRICT));
                if (date.getYear() == year && !date.isAfter(today)) {
                    dates.computeIfAbsent(date, ignored -> new HashSet<>()).add(name.group(1));
                }
            } catch (DateTimeParseException ignored) {}
        }
        return dates.entrySet().stream().filter(entry -> entry.getValue().containsAll(SCOPES))
            .map(Map.Entry::getKey).max(LocalDate::compareTo).orElse(null);
    }

    static Standings.Section parse(String markdown, String scope, LocalDate published) throws IOException {
        int region = SCOPES.indexOf(scope);
        if (region < 0) throw new IOException("Valve VRS 地区未知");
        boolean matchingHeading = false;
        boolean header = false;
        List<Standings.Row> rows = new ArrayList<>();
        for (String raw : markdown.split("\\R")) {
            String line = raw.trim();
            Matcher heading = HEADING.matcher(line);
            if (heading.matches()) {
                String headingRegion = heading.group(1);
                boolean matchesRegion = region == 0 ? headingRegion == null : scope.equalsIgnoreCase(headingRegion);
                if (!matchesRegion || !DATE.format(published).equals(heading.group(2))) {
                    throw new IOException("Valve VRS 日期或地区不一致");
                }
                matchingHeading = true;
            }
            if (!line.startsWith("|")) continue;
            String[] cells = line.split("(?<!\\\\)\\|", -1);
            if (!header) {
                header = cells.length >= 6 && cells[1].trim().equals("Standing") && cells[2].trim().equals("Points")
                    && cells[3].trim().equals("Team Name") && cells[4].trim().equals("Roster");
                continue;
            }
            if (cells.length >= 2 && cells[1].trim().equals(":-")) continue;
            if (cells.length < 6 || !cells[1].trim().equals(String.valueOf(rows.size() + 1))
                || !NUMBER.matcher(cells[2].trim()).matches() || cells[3].trim().isEmpty()) {
                throw new IOException("Valve VRS 数据行不完整");
            }
            rows.add(new Standings.Row(cells[1].trim(), cell(cells[3]), cell(cells[4]), "", List.of(cells[2].trim()), ""));
        }
        if (!matchingHeading || rows.isEmpty()) throw new IOException("Valve VRS 暂无可用排名");
        return new Standings.Section(TITLES.get(region), List.of("积分"), rows);
    }

    private static String cell(String text) { return text.trim().replace("\\|", "|"); }
}
