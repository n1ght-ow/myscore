package cn.myscore.app;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class MatchResolverTest {
    @Test public void worldsReadsAreSharedWithinOneSyncButRefetchedOnTheNext()throws Exception {
        int[] calls={0};
        MatchResolver resolver=new MatchResolver(url->{
            calls[0]++;
            String date=calls[0]==1?"2026-10-28 20:00:00":"2026-10-29 21:00:00";
            JSONArray matches=new JSONArray();
            for(int id=1;id<=2;id++)matches.put(new JSONObject().put("GameName","2026全球总决赛")
                .put("bMatchId",String.valueOf(id)).put("bMatchName","T1 vs GEN")
                .put("MatchDate",date).put("MatchStatus","1"));
            return new JSONObject().put("status","0").put("msg",matches).toString();
        });
        Match first=match("qq:lol:1","lol"),second=match("qq:lol:2","lol");
        resolver.beginSync();
        long original=resolver.resolve(first).start;
        assertEquals(original,resolver.resolve(second).start);
        assertEquals(1,calls[0]);
        resolver.beginSync();
        assertNotEquals(original,resolver.resolve(first).start);
        assertEquals(2,calls[0]);
    }

    @Test public void f1SeasonSnapshotDoesNotHideReschedulingAcrossSyncs()throws Exception {
        int[] calls={0};
        MatchResolver resolver=new MatchResolver(url->{
            calls[0]++;
            JSONObject session=new JSONObject().put("id","11")
                .put("date",calls[0]==1?"2026-10-28T12:00Z":"2026-10-28T14:00Z")
                .put("type",new JSONObject().put("abbreviation","Race"))
                .put("status",new JSONObject().put("type",new JSONObject().put("state","pre")));
            JSONObject event=new JSONObject().put("id","10").put("name","Grand Prix")
                .put("competitions",new JSONArray().put(session));
            return new JSONObject().put("events",new JSONArray().put(event)).toString();
        });
        Match previous=match("espn:f1:10:11","f1");previous.start=Match.parseTime("2026-10-28T12:00Z");
        resolver.beginSync();
        long original=resolver.resolve(previous).start;
        resolver.resolve(previous);assertEquals(1,calls[0]);
        resolver.beginSync();
        assertEquals(original+2*60*60_000L,resolver.resolve(previous).start);
        assertEquals(2,calls[0]);
    }

    @Test public void nonSpiritCs2DetailResolvesWithoutASpiritFilter() throws Exception {
        JSONObject match=new JSONObject()
            .put("mc_info",new JSONObject().put("id","csgo_mc_1").put("plan_ts","1791652200").put("format","3").put("tt_stage_desc","淘汰赛 半决赛")
                .put("t1_info",new JSONObject().put("id","hltv_team_1").put("disp_name","NAVI").put("logo",""))
                .put("t2_info",new JSONObject().put("id","hltv_team_2").put("disp_name","FURIA").put("logo","")))
            .put("tt_info",new JSONObject().put("disp_name","PGL 布加勒斯特大师赛 2026").put("grade","2").put("grade_label","S级赛事"))
            .put("global_state",new JSONObject().put("status","2").put("t1_score","2").put("t2_score","0"))
            .put("bouts_state",new JSONArray());
        JSONObject root=new JSONObject().put("success",true).put("data",new JSONObject().put("match",match));
        MatchResolver resolver=new MatchResolver(url->root.toString());
        Match previous=match("five:cs2:csgo_mc_1","cs2");
        Match resolved=resolver.resolve(previous);
        assertEquals("NAVI",resolved.home);assertEquals("FURIA",resolved.away);
        assertEquals("2 : 0",resolved.score());assertTrue(resolved.tierOne);
        assertEquals("PGL 布加勒斯特大师赛 2026",resolved.event);
    }

    private Match match(String id,String league) {
        Match match=new Match();match.id=id;match.league=league;return match;
    }
}
