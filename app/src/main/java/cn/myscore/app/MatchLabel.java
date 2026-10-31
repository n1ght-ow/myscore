package cn.myscore.app;

/**
 * Kicker shown above a match. CS2 needs per-match context rather than one competition name:
 * tier-1 events show their own name (e.g. PGL Bucharest), while Spirit's lower-tier matches keep
 * the familiar team label. Other sports keep the competition name.
 */
final class MatchLabel {
    private MatchLabel() {}

    static String of(Match match) {
        if (match == null) return "";
        if ("cs2".equals(match.league)) {
            if (match.tierOne && !match.event.isBlank()) return match.event;
            if (DomesticEsports.spiritMatch(match)) return "CS2 · Team Spirit";
            return "CS2";
        }
        Competition competition = Competition.find(match.league);
        return competition == null ? match.group : competition.name;
    }
}
