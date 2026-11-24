package cn.myscore.app;

/** One adapter row. Section rows carry competition context for direct Standings / Bracket jumps. */
final class ScoreRow {
    static final int LOADING = 0, FAILURE = 1, SECTION = 2, MATCH = 3, F1 = 4, EMPTY = 5;
    final int type;
    final Match match;
    final String text, title, message;
    final String leagueId, sportGroup, actionLabel, tournamentName;
    final int count, liveCount;

    private ScoreRow(int type, Match match, String text, String title, String message,
                     String leagueId, String sportGroup, int count, int liveCount,
                     String actionLabel, String tournamentName) {
        this.type = type;
        this.match = match;
        this.text = text;
        this.title = title;
        this.message = message;
        this.leagueId = leagueId;
        this.sportGroup = sportGroup;
        this.count = count;
        this.liveCount = liveCount;
        this.actionLabel = actionLabel;
        this.tournamentName = tournamentName;
    }

    static ScoreRow loading(String text) {
        return new ScoreRow(LOADING, null, text, null, null, "", "", 0, 0, "", "");
    }

    static ScoreRow failure(String text) {
        return new ScoreRow(FAILURE, null, text, null, null, "", "", 0, 0, "", "");
    }

    static ScoreRow section(String text) {
        return new ScoreRow(SECTION, null, text, null, null, "", "", 0, 0, "", "");
    }

    static ScoreRow competitionSection(ScoreFeedOrganizer.CompetitionGroup group) {
        String action = group.hasCs2Tournament() ? "Bracket"
            : group.hasStandings() ? "Standings" : "";
        String tournament = group.hasCs2Tournament() && !group.matches().isEmpty()
            ? group.matches().get(0).event : "";
        return new ScoreRow(SECTION, null, group.title(), null, null,
            group.leagueId(), group.sportGroup(), group.matches().size(), group.liveCount(),
            action, tournament);
    }

    static ScoreRow statusSection(String title, int count, int liveCount) {
        return new ScoreRow(SECTION, null, title, null, null, "", "", count, liveCount, "", "");
    }

    static ScoreRow match(Match match) {
        return new ScoreRow(match.group.equals("F1") ? F1 : MATCH, match, null, null, null, "", "", 0, 0, "", "");
    }

    static ScoreRow empty(String title, String message) {
        return new ScoreRow(EMPTY, null, null, title, message, "", "", 0, 0, "", "");
    }

    static ScoreRow empty(String title, String message, String action) {
        return new ScoreRow(EMPTY, null, action, title, message, "", "", 0, 0, "", "");
    }

    /** Identity of the row's shape: equal keys let the adapter rebind text without a relayout. */
    String key() {
        if (type == MATCH || type == F1) {
            boolean stale = match.live() && System.currentTimeMillis() - match.updated > 120_000;
            return "m" + type + ":" + match.id + ":" + match.state + ":"
                + (match.group.equals("F1") ? Math.min(2, match.results.size()) : 0) + ":" + stale;
        }
        if (type == SECTION) return "s:" + text + ":" + leagueId + ":" + actionLabel + ":" + count + ":" + liveCount;
        if (type == FAILURE) return "f";
        if (type == EMPTY) return "e";
        return "l";
    }
}
