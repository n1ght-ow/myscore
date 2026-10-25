package cn.myscore.app;

import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;

public class LolGameTest {
    private JSONObject fixture(String name)throws Exception {try(java.io.InputStream in=getClass().getResourceAsStream("/details/"+name+".json")){return new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));}}
    private MatchDetail detail(JSONObject rich)throws Exception {Match match=new Match();match.id="qq:lol:13608";match.group="LOL";match.league="lol";return DetailParsers.lol(fixture("lol-base"),rich,match,100);}
    @Test public void pairsByTeamAndRoleRegardlessOfPayloadOrder()throws Exception {
        MatchDetail d=detail(fixture("lol-rich"));LolGame game=d.lolGames.get(0);
        assertEquals("KC Canna",game.away.atRole("TOP").name());assertEquals("C9 Thanatos",game.home.atRole("TOP").name());
        assertTrue(game.away.blue);assertFalse(game.home.blue);assertEquals("35:02",game.clock());assertFalse(game.live);
    }
    @Test public void acceptsLiveSnapshotWithoutInventingWinner()throws Exception {
        JSONObject rich=fixture("lol-rich"),data=rich.getJSONObject("data");data.put("matchStatus",1);JSONObject game=data.getJSONArray("matchInfos").getJSONObject(0);game.put("matchStatus",1).put("matchWin",JSONObject.NULL).put("gameTime",434);
        MatchDetail d=detail(rich);assertTrue(d.match.live());assertTrue(d.lolGames.get(0).live);assertEquals("07:14",d.lolGames.get(0).clock());assertEquals("",d.lolGames.get(0).winner(d.match));
    }
    @Test public void unknownFieldsStayUnknownAndZeroIsReal()throws Exception {
        JSONObject rich=fixture("lol-rich"),team=rich.getJSONObject("data").getJSONArray("matchInfos").getJSONObject(0).getJSONArray("teamInfos").getJSONObject(0);team.remove("golds");team.put("kills",0);
        LolGame game=detail(rich).lolGames.get(0);assertEquals("—",game.away.value("golds"));assertEquals("0",game.away.value("kills"));assertNull(game.away.atRole("unknown"));
    }
    @Test public void sourceHeroLevelItemsAndCacheRemainAssociated()throws Exception {
        JSONObject rich=fixture("lol-rich"),p=rich.getJSONObject("data").getJSONArray("matchInfos").getJSONObject(0).getJSONArray("teamInfos").getJSONObject(0).getJSONArray("playerInfos").getJSONObject(0);p.put("heroNameEn","Rumble");
        MatchDetail cached=MatchDetail.fromJson(detail(rich).json());LolGame.Player top=cached.lolGames.get(0).away.atRole("TOP");assertEquals("18",top.level());assertTrue(top.icon().endsWith("/champion/Rumble.png"));assertEquals(6,top.data.getJSONArray("items").length());assertEquals(1,cached.lolGames.size());
    }
    @Test public void pregameDoesNotShowPlaceholderStatistics()throws Exception {
        JSONObject rich=fixture("lol-rich");JSONArray games=rich.getJSONObject("data").getJSONArray("matchInfos");for(int i=0;i<games.length();i++)games.getJSONObject(i).put("matchStatus",0);assertTrue(detail(rich).lolGames.isEmpty());
    }
    @Test(expected=Exception.class) public void foreignTeamCannotEnterAGame()throws Exception {
        JSONObject rich=fixture("lol-rich");rich.getJSONObject("data").getJSONArray("matchInfos").getJSONObject(0).getJSONArray("teamInfos").getJSONObject(0).put("teamId",999999);detail(rich);
    }
    @Test(expected=Exception.class) public void duplicateGameNumbersAreRejected()throws Exception {
        JSONObject rich=fixture("lol-rich");JSONArray games=rich.getJSONObject("data").getJSONArray("matchInfos");games.put(new JSONObject(games.getJSONObject(0).toString()));detail(rich);
    }
    @Test public void oldCacheWithoutGamesStillLoads()throws Exception {JSONObject cached=detail(fixture("lol-rich")).json();cached.remove("lolGames");assertTrue(MatchDetail.fromJson(cached).lolGames.isEmpty());}
    @Test public void unavailableGameRefreshKeepsItsOriginalTimestamp()throws Exception {MatchDetail old=detail(fixture("lol-rich")),next=detail(new JSONObject());next.fetched=200;DetailRepository.retainGames(old,next);assertEquals(1,next.lolGames.size());assertEquals(100,next.lolFetched);assertTrue(next.warning.contains("上次"));}
    @Test public void cachedGamesNeverCrossMatchIdentity()throws Exception {MatchDetail old=detail(fixture("lol-rich")),next=detail(new JSONObject());next.match.id="qq:lol:999";DetailRepository.retainGames(old,next);assertTrue(next.lolGames.isEmpty());}
    @Test public void liveClockIsNotCalculatedFromWallTime()throws Exception {
        JSONObject rich=fixture("lol-rich"),game=rich.getJSONObject("data").getJSONArray("matchInfos").getJSONObject(0);game.put("matchStatus",1);game.remove("gameTime");assertEquals("—",detail(rich).lolGames.get(0).clock());
    }
    @Test public void artworkPathsRejectUntrustedComponents(){assertEquals("",LolGame.asset("champion","../other"));assertTrue(LolGame.asset("item","3157").startsWith("https://game.gtimg.cn/"));}
    @Test public void unknownBlueTeamNeverAssignsSides()throws Exception {JSONObject rich=fixture("lol-rich"),game=rich.getJSONObject("data").getJSONArray("matchInfos").getJSONObject(0);for(Object value:new Object[]{"",JSONObject.NULL,"99999"}){game.put("blueTeam",value);MatchDetail d=detail(rich);assertFalse(d.lolGames.get(0).sideKnown(d.match));}}
}
