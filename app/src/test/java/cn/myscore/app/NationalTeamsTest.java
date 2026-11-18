package cn.myscore.app;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public class NationalTeamsTest {
    private static final Competition NATIONAL=Competition.find("national");
    private static final long FETCHED=100;
    private static final String SPAIN_ENGLAND="espn:national:704559:401839031";
    private static final String DENMARK_NORWAY="espn:national:401861194:401861194";

    @Test public void followedTeamsExposeStableNationalIdentity() {
        assertNotNull(NATIONAL);
        assertEquals("national",NATIONAL.id);
        assertEquals("国家队",NATIONAL.name);
        assertEquals("足球",NATIONAL.group);
        assertEquals("soccer/all",NATIONAL.path);
        assertEquals(3,NationalTeams.FOLLOWED.size());
        assertTeam("464","挪威","nor");
        assertTeam("448","英格兰","eng");
        assertTeam("164","西班牙","esp");
        assertEquals("Fallback",NationalTeams.name("999999","Fallback"));
        Match fallback=new Match();fallback.league=NationalTeams.LEAGUE;
        assertEquals("国家队",MatchLabel.of(fallback));
    }

    @Test public void teamScheduleMergesResultsAndFixturesWithoutDuplicatingSpainEngland()throws Exception {
        Team england=followed("448").team();
        JSONObject spainEngland=finished("704559","401839031","2026-11-17T17:30Z","uefa.nations","UEFA Nations League",
            "164","Spain","448","England","2","1");
        JSONObject norwayEngland=finished("704560","401839032","2026-11-15T18:00Z","fifa.friendly","International Friendly",
            "464","Norway","448","England","1","1");
        JSONObject englandNorway=upcoming("704561","401839033","2026-11-19T19:45Z","fifa.friendly","International Friendly",
            "448","England","464","Norway");
        JSONObject results=schedule("448","England",spainEngland,norwayEngland);
        JSONObject fixtures=schedule("448","England",spainEngland,englandNorway);
        TeamSchedule.verifyTeam(results,england);
        TeamSchedule.verifyTeam(fixtures,england);

        List<Match> english=TeamSchedule.parseEspn(england,results,fixtures,FETCHED);
        assertEquals(3,english.size());
        assertTrue(english.stream().allMatch(m->"national".equals(m.league)&&"足球".equals(m.group)&&"ESPN".equals(m.source)));
        Match classic=find(english,SPAIN_ENGLAND);
        assertEquals("欧国联",classic.event);
        assertEquals("西班牙",classic.home);
        assertEquals("英格兰",classic.away);
        assertEquals("164",classic.homeId);
        assertEquals("448",classic.awayId);
        assertEquals("2",classic.homeScore);
        assertEquals("1",classic.awayScore);
        assertEquals("2026-11-18",classic.day().toString());
        assertEquals("01:30",classic.time());
        assertEquals("欧国联",MatchLabel.of(classic));
        assertEquals("挪威",find(english,"espn:national:704560:401839032").home);
        assertEquals("国际友谊赛",find(english,"espn:national:704560:401839032").event);
        Match upcoming=find(english,"espn:national:704561:401839033");
        assertTrue(upcoming.scheduled());
        assertFalse(upcoming.hasScore());
        assertEquals("国际友谊赛",upcoming.event);

        JSONObject spainResults=schedule("164","Spain",finished("704559","401839031","2026-11-17T17:30Z","uefa.nations","UEFA Nations League",
            "164","Spain","448","England","2","1"));
        List<Match> spanish=TeamSchedule.parseEspn(followed("164").team(),spainResults,null,FETCHED);
        assertEquals(1,spanish.size());
        assertEquals(SPAIN_ENGLAND,spanish.get(0).id);

        Repository.Batch view=new Repository.Batch(NATIONAL);
        Map<String,Integer> counts=new LinkedHashMap<>();
        for(Match match:Repository.mergeDays(view,List.of(batch(english),batch(spanish))))counts.merge(match.id,1,Integer::sum);
        assertEquals("",view.error);
        assertEquals(3,counts.size());
        assertEquals(1,counts.get(SPAIN_ENGLAND).intValue());
    }

    @Test public void soccerAllMissesDenmarkNorwayButTeamPageFillsItWithoutOverwritingFinals()throws Exception {
        JSONObject denmarkNorway=finished("401861194","401861194","2026-11-17T19:45Z","uefa.nations","UEFA Nations League",
            "479","Denmark","464","Norway","2","3");
        JSONObject norwayEngland=finished("704560","401839032","2026-11-15T18:00Z","fifa.friendly","International Friendly",
            "464","Norway","448","England","1","1");
        JSONObject staleNorwayEngland=upcoming("704560","401839032","2026-11-15T18:00Z","fifa.friendly","International Friendly",
            "464","Norway","448","England");
        JSONObject teamPage=schedule("464","Norway",denmarkNorway,norwayEngland);
        JSONObject soccerAll=new JSONObject().put("events",new JSONArray().put(staleNorwayEngland));

        List<Match> matches=TeamSchedule.parseEspn(followed("464").team(),FETCHED,teamPage,soccerAll);
        assertEquals(2,matches.size());
        assertEquals(DENMARK_NORWAY,matches.get(0).id);
        assertEquals("espn:national:704560:401839032",matches.get(1).id);

        Match added=find(matches,DENMARK_NORWAY);
        assertEquals("479",added.homeId);
        assertEquals("Denmark",added.home);
        assertEquals("464",added.awayId);
        assertEquals("挪威",added.away);
        assertEquals("2",added.homeScore);
        assertEquals("3",added.awayScore);
        assertEquals("2026-11-18",added.day().toString());
        assertEquals("03:45",added.time());
        assertEquals("欧国联",added.event);
        assertTrue(added.finalised());

        Match duplicate=find(matches,"espn:national:704560:401839032");
        assertEquals("1",duplicate.homeScore);
        assertEquals("1",duplicate.awayScore);
        assertTrue(duplicate.finalised());
    }

    @Test public void nationalSummaryResolvesThroughSoccerAllAndKeepsBeijingDay()throws Exception {
        JSONObject summary=new JSONObject().put("header",finished("704559","401839031","2026-11-17T17:30Z","uefa.nations","UEFA Nations League",
            "164","Spain","448","England","2","1"));
        List<String> urls=new ArrayList<>();
        MatchResolver resolver=new MatchResolver(url->{urls.add(url);return summary.toString();});
        Match previous=new Match();
        previous.id=SPAIN_ENGLAND;previous.league="national";
        previous.url="https://www.espn.com/soccer/match/_/gameId/704559";
        Match resolved=resolver.resolve(previous);
        assertEquals(List.of("https://site.api.espn.com/apis/site/v2/sports/soccer/all/summary?event=704559"),urls);
        assertEquals(SPAIN_ENGLAND,resolved.id);
        assertEquals("西班牙",resolved.home);
        assertEquals("英格兰",resolved.away);
        assertEquals("欧国联",resolved.event);
        assertEquals("2026-11-18",resolved.day().toString());
        assertEquals("01:30",resolved.time());
        assertEquals(previous.url,resolved.url);
        assertEquals("欧国联",MatchLabel.of(resolved));
    }

    @Test public void nationalSummaryDetailUsesTheSameEventIdentity()throws Exception {
        JSONObject summary=new JSONObject().put("header",finished("704559","401839031","2026-11-17T17:30Z","uefa.nations","UEFA Nations League",
            "164","Spain","448","England","2","1"));
        Match previous=new Match();previous.id=SPAIN_ENGLAND;previous.league="national";
        MatchDetail detail=DetailParsers.espn(summary,previous,FETCHED);
        assertEquals(SPAIN_ENGLAND,detail.match.id);
        assertEquals("西班牙",detail.match.home);
        assertEquals("英格兰",detail.match.away);
        assertEquals("欧国联",MatchLabel.of(detail.match));
        assertTrue(detail.facts.stream().anyMatch(f->f.label().equals("赛事")&&f.value().equals("欧国联")));
        assertEquals(FETCHED,detail.fetched);
    }

    private static NationalTeams.Entry followed(String id) {
        for(NationalTeams.Entry entry:NationalTeams.FOLLOWED)if(entry.id().equals(id))return entry;
        throw new AssertionError("missing national team "+id);
    }
    private static void assertTeam(String id,String name,String flag) {
        NationalTeams.Entry entry=followed(id);
        assertEquals(name,entry.name());
        Team team=entry.team();
        assertEquals(NationalTeams.LEAGUE,team.league);
        assertEquals(id,team.id);
        assertEquals(name,team.name);
        assertEquals("https://a.espncdn.com/i/teamlogos/countries/500/"+flag+".png",team.logo);
        assertTrue(team.usable());
    }
    private static Match find(List<Match> matches,String id) {
        for(Match match:matches)if(match.id.equals(id))return match;
        fail("missing match "+id);
        return null;
    }
    private static Repository.Batch batch(List<Match> matches) {
        Repository.Batch batch=new Repository.Batch(NATIONAL);
        batch.matches.addAll(matches);
        return batch;
    }
    private static JSONObject schedule(String teamId,String teamName,JSONObject... events)throws Exception {
        JSONArray list=new JSONArray();for(JSONObject event:events)list.put(event);
        return new JSONObject().put("team",new JSONObject().put("id",teamId).put("displayName",teamName)).put("events",list);
    }
    private static JSONObject finished(String eventId,String competitionId,String date,String slug,String leagueName,
        String homeId,String homeName,String awayId,String awayName,String homeScore,String awayScore)throws Exception {
        return event(eventId,competitionId,date,slug,leagueName,homeId,homeName,awayId,awayName,"STATUS_FULL_TIME","post",homeScore,awayScore);
    }
    private static JSONObject upcoming(String eventId,String competitionId,String date,String slug,String leagueName,
        String homeId,String homeName,String awayId,String awayName)throws Exception {
        return event(eventId,competitionId,date,slug,leagueName,homeId,homeName,awayId,awayName,"STATUS_SCHEDULED","pre",null,null);
    }
    private static JSONObject event(String eventId,String competitionId,String date,String slug,String leagueName,
        String homeId,String homeName,String awayId,String awayName,String statusName,String state,
        String homeScore,String awayScore)throws Exception {
        JSONObject status=new JSONObject().put("displayClock",state.equals("post")?"90:00":"0:00").put("period",state.equals("post")?2:0)
            .put("type",new JSONObject().put("name",statusName).put("state",state));
        JSONArray competitors=new JSONArray()
            .put(competitor("home",homeId,homeName,homeScore))
            .put(competitor("away",awayId,awayName,awayScore));
        JSONObject competition=new JSONObject().put("id",competitionId).put("date",date).put("timeValid",true)
            .put("status",status).put("competitors",competitors);
        return new JSONObject().put("id",eventId).put("date",date)
            .put("league",new JSONObject().put("slug",slug).put("name",leagueName))
            .put("competitions",new JSONArray().put(competition));
    }
    private static JSONObject competitor(String side,String id,String name,String score)throws Exception {
        JSONObject competitor=new JSONObject().put("homeAway",side)
            .put("team",new JSONObject().put("id",id).put("displayName",name));
        if(score!=null)competitor.put("score",new JSONObject().put("displayValue",score));
        return competitor;
    }
}
