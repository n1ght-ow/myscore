package cn.myscore.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

record Standings(String source, String season, List<Section> sections, List<Round> rounds) {
    Standings {
        sections = immutable(sections);
        rounds = immutable(rounds);
    }

    record Section(String title, List<String> columns, List<Row> rows) {
        Section { columns = immutable(columns); rows = immutable(rows); }
    }

    record Row(String rank, String name, String subtitle, String logo, List<String> values, String status,
               String teamId) {
        Row {
            values = immutable(values);
            if (teamId == null) teamId = "";
        }
        Row(String rank, String name, String subtitle, String logo, List<String> values, String status) {
            this(rank, name, subtitle, logo, values, status, "");
        }
        boolean opensTeam(String competition) {
            if (!teamId.matches("[1-9][0-9]*")) return false;
            return switch (competition) {
                case "epl", "ucl", "nba", "nfl" -> true;
                default -> false;
            };
        }
    }

    record Round(String label, List<Tie> ties, boolean started) {
        Round { ties = immutable(ties); }
        Round(String label, List<Tie> ties) {
            this(label, ties, ties.stream().anyMatch(tie -> scored(tie.first()) || scored(tie.second())));
        }
        private static boolean scored(Side side) { return !side.score().isBlank() && !side.score().equals("—"); }
    }

    record Tie(String date, String stage, Side first, Side second, String result) {}
    record Side(String name, String logo, String score, boolean winner) {}

    private static <T> List<T> immutable(List<T> list) { return Collections.unmodifiableList(new ArrayList<>(list)); }
}
