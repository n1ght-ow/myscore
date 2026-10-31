package cn.myscore.app;

public final class Competition {
    public final String id, name, group, path;
    private Competition(String id, String name, String group, String path) {
        this.id=id; this.name=name; this.group=group; this.path=path;
    }
    public static final Competition[] ALL = {
        new Competition("epl", "英超", "足球", "soccer/eng.1"),
        new Competition("ucl", "欧冠", "足球", "soccer/uefa.champions"),
        new Competition("efl", "Carabao Cup", "足球", "soccer/eng.league_cup"),
        new Competition("fa", "FA Cup", "足球", "soccer/eng.fa"),
        new Competition("nba", "NBA", "NBA", "basketball/nba"),
        new Competition("nfl", "NFL", "NFL", "football/nfl"),
        new Competition("f1", "F1", "F1", "racing/f1"),
        new Competition("cs2", "CS2", "CS2", ""),
        new Competition("lol", "LOL · Worlds", "LOL", "")
    };
    public boolean esports() { return path.isEmpty(); }
    public static Competition find(String id) {
        for(Competition c:ALL) if(c.id.equals(id)) return c;
        return null;
    }
}
