package cn.myscore.app;

import java.util.List;

final class NationalTeams {
    static final String LEAGUE="national";
    static final String NATIONS_PATH="soccer/uefa.nations";
    static final int SCHEDULE_SCHEMA=1;
    record Entry(String id,String name,String flag) {
        Team team() {
            Team team=new Team();
            team.league=LEAGUE;team.id=id;team.name=name;
            team.logo="https://a.espncdn.com/i/teamlogos/countries/500/"+flag+".png";
            return team;
        }
    }
    static final List<Entry> FOLLOWED=List.of(new Entry("464","挪威","nor"),new Entry("448","英格兰","eng"),new Entry("164","西班牙","esp"));

    private NationalTeams() {}

    static boolean follows(Team team) {
        return LEAGUE.equals(team.league)&&FOLLOWED.stream().anyMatch(entry->entry.id().equals(team.id));
    }

    static String name(String id,String fallback) {
        for(Entry entry:FOLLOWED)if(entry.id().equals(id))return entry.name();
        return fallback;
    }
}
