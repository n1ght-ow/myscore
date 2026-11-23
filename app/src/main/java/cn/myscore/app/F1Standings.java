package cn.myscore.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

final class F1Standings {
    private static final String SOURCE = "Jolpica F1";
    private static final String DRIVERS_URL = "https://api.jolpi.ca/ergast/f1/current/driverstandings.json?limit=100";
    private static final String CONSTRUCTORS_URL = "https://api.jolpi.ca/ergast/f1/current/constructorstandings.json?limit=100";
    private static final List<String> COLUMNS = List.of("积分", "胜场");

    private F1Standings() {}

    static Standings load() throws Exception {
        return load(Repository::get);
    }

    static Standings load(Fetcher fetcher) throws Exception {
        JSONObject drivers = null;
        JSONObject constructors = null;
        Exception driversFailure = null;
        Exception constructorsFailure = null;
        try {
            drivers = new JSONObject(fetcher.get(DRIVERS_URL));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } catch (InterruptedIOException e) {
            if (e instanceof SocketTimeoutException) {
                driversFailure = e;
            } else {
                Thread.currentThread().interrupt();
                throw e;
            }
        } catch (Exception e) {
            driversFailure = e;
        }
        try {
            constructors = new JSONObject(fetcher.get(CONSTRUCTORS_URL));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } catch (InterruptedIOException e) {
            if (e instanceof SocketTimeoutException) {
                constructorsFailure = e;
            } else {
                Thread.currentThread().interrupt();
                throw e;
            }
        } catch (Exception e) {
            constructorsFailure = e;
        }
        if (drivers == null && constructors == null) {
            throw new Exception("F1 积分榜来源不可用", driversFailure != null ? driversFailure : constructorsFailure);
        }
        return parse(drivers, constructors);
    }

    static Standings parse(JSONObject drivers, JSONObject constructors) throws Exception {
        String driverSeason = season(drivers);
        String constructorSeason = season(constructors);
        boolean hasDrivers = drivers != null && !driverSeason.isEmpty();
        boolean hasConstructors = constructors != null && !constructorSeason.isEmpty();
        if (hasDrivers && hasConstructors && !driverSeason.equals(constructorSeason)) {
            throw new Exception("F1 积分榜赛季不一致");
        }
        String season = driverSeason.isEmpty() ? constructorSeason : driverSeason;
        if (season.isEmpty()) throw new Exception("F1 积分榜来源未返回赛季");

        List<Standings.Section> sections = new ArrayList<>();
        Exception failure = null;
        if (hasDrivers) {
            try {
                sections.add(drivers(drivers));
            } catch (Exception e) {
                failure = e;
            }
        }
        if (hasConstructors) {
            try {
                sections.add(constructors(constructors));
            } catch (Exception e) {
                failure = e;
            }
        }
        if (sections.isEmpty()) throw new Exception("F1 积分榜来源未返回有效排名", failure);

        return new Standings(SOURCE, season, sections, List.of());
    }

    @FunctionalInterface
    interface Fetcher {
        String get(String url) throws Exception;
    }

    private static Standings.Section drivers(JSONObject root) throws Exception {
        JSONArray standings = rows(root, "DriverStandings");
        List<Standings.Row> rows = new ArrayList<>();
        for (int i = 0; i < standings.length(); i++) {
            JSONObject standing = standings.optJSONObject(i);
            if (standing == null) continue;
            JSONObject driver = standing.optJSONObject("Driver");
            if (driver == null) continue;
            String rank = rank(standing);
            String name = (text(driver, "givenName") + " " + text(driver, "familyName")).trim();
            String points = text(standing, "points");
            if (rank.isEmpty() || name.isEmpty() || points.isEmpty()) continue;
            rows.add(new Standings.Row(
                rank,
                name,
                constructors(standing.optJSONArray("Constructors")),
                "",
                List.of(points, text(standing, "wins")),
                text(standing, "status")
            ));
        }
        if (rows.isEmpty()) throw new Exception("车手积分榜来源未返回有效排名");
        return new Standings.Section("车手", new ArrayList<>(COLUMNS), rows);
    }

    private static Standings.Section constructors(JSONObject root) throws Exception {
        JSONArray standings = rows(root, "ConstructorStandings");
        List<Standings.Row> rows = new ArrayList<>();
        for (int i = 0; i < standings.length(); i++) {
            JSONObject standing = standings.optJSONObject(i);
            if (standing == null) continue;
            JSONObject constructor = standing.optJSONObject("Constructor");
            if (constructor == null) continue;
            String rank = rank(standing);
            String name = text(constructor, "name");
            String points = text(standing, "points");
            if (rank.isEmpty() || name.isEmpty() || points.isEmpty()) continue;
            rows.add(new Standings.Row(
                rank,
                name,
                "",
                "",
                List.of(points, text(standing, "wins")),
                text(standing, "status")
            ));
        }
        if (rows.isEmpty()) throw new Exception("车队积分榜来源未返回有效排名");
        return new Standings.Section("车队", new ArrayList<>(COLUMNS), rows);
    }

    private static JSONArray rows(JSONObject root, String key) {
        JSONObject list = firstList(root);
        JSONArray rows = list == null ? null : list.optJSONArray(key);
        return rows == null ? new JSONArray() : rows;
    }

    private static String season(JSONObject root) {
        JSONObject list = firstList(root);
        String value = list == null ? "" : text(list, "season");
        if (!value.isEmpty()) return value;
        return text(object(object(root, "MRData"), "StandingsTable"), "season");
    }

    private static JSONObject firstList(JSONObject root) {
        JSONArray lists = object(object(root, "MRData"), "StandingsTable").optJSONArray("StandingsLists");
        return lists == null || lists.length() == 0 ? null : lists.optJSONObject(0);
    }

    private static String rank(JSONObject standing) {
        String value = text(standing, "positionText");
        return value.isEmpty() ? text(standing, "position") : value;
    }

    private static String constructors(JSONArray constructors) {
        List<String> names = new ArrayList<>();
        if (constructors == null) return "";
        for (int i = 0; i < constructors.length(); i++) {
            JSONObject constructor = constructors.optJSONObject(i);
            String name = constructor == null ? "" : text(constructor, "name");
            if (!name.isEmpty() && !names.contains(name)) names.add(name);
        }
        return String.join(" / ", names);
    }

    private static JSONObject object(JSONObject root, String key) {
        if (root == null) return new JSONObject();
        JSONObject value = root.optJSONObject(key);
        return value == null ? new JSONObject() : value;
    }

    private static String text(JSONObject root, String key) {
        if (root == null) return "";
        Object value = root.opt(key);
        return value == null || value == JSONObject.NULL ? "" : String.valueOf(value).trim();
    }
}
