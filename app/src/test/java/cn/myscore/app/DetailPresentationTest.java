package cn.myscore.app;

import org.json.*;
import org.junit.Test;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class DetailPresentationTest {
    private JSONObject fixture(String name)throws Exception{try(java.io.InputStream in=getClass().getResourceAsStream("/details/"+name+".json")){return new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));}}
    private MatchDetail nba(JSONObject root)throws Exception{Match match=Parsers.espn(new JSONObject().put("events",new JSONArray().put(root.getJSONObject("header"))),Competition.find("nba"),1).get(0);return DetailParsers.espn(root,match,100);}
    private List<MatchDetail.Table> players(MatchDetail detail){return detail.tables.stream().filter(t->t.category.equals("球员")).toList();}
    @Test public void officialsUseReadableRolesInsteadOfSerializedObjects()throws Exception{MatchDetail d=nba(fixture("nba"));List<MatchDetail.Fact> officials=d.facts.stream().filter(f->f.label().equals("裁判")).toList();assertEquals(3,officials.size());assertEquals("Ed Malloy",officials.get(0).value());assertTrue(d.facts.stream().noneMatch(f->f.label().contains("{")||f.value().contains("displayName")));}
    @Test public void stringAndUnknownOfficialRolesHaveSafeFallbacks()throws Exception{JSONObject root=fixture("nba");JSONArray officials=root.getJSONObject("gameInfo").getJSONArray("officials");officials.getJSONObject(0).put("position","Referee");officials.getJSONObject(1).put("position",new JSONObject().put("id",40));MatchDetail d=nba(root);assertEquals(3,d.facts.stream().filter(f->f.label().equals("裁判")).count());}
    @Test public void structuredValuesCannotLeakJsonButZeroSurvives()throws Exception{assertEquals("0",DetailParsers.val(new JSONObject().put("v",0),"v"));assertEquals("Referee",DetailParsers.plain("{\"displayName\":\"Referee\",\"id\":40}"));assertEquals("",DetailParsers.plain(new JSONArray().put(1)));assertEquals("",DetailParsers.plain(new JSONObject().put("unknown",true)));}
    @Test public void explicitJsonNullNeverBecomesTheStringNull()throws Exception{
        JSONObject source=new JSONObject().put("displayName",JSONObject.NULL).put("numeric",0);
        assertEquals("fallback",DetailParsers.text(source,"displayName","fallback"));
        assertEquals("",DetailParsers.text(source,"displayName"));
        assertEquals("0",DetailParsers.text(source,"numeric","fallback"));
        Match match=Match.fromJson(new JSONObject().put("home",JSONObject.NULL).put("homeScore",JSONObject.NULL).put("awayScore","1"));
        assertEquals("",match.home);assertFalse(match.hasScore());
    }
    @Test public void nbaPreservesPortraitNumberPositionAndTeamIdentity()throws Exception{MatchDetail d=nba(fixture("nba"));MatchDetail.Table table=players(d).get(0);MatchDetail.Person p=table.people.get(0);assertEquals("4397189",p.id());assertEquals("25",p.jersey());assertEquals("F",p.position());assertTrue(p.photo().startsWith("https://a.espncdn.com/"));assertEquals(table.teamId,p.teamId());assertTrue(p.starter());assertEquals(table.rows.size(),table.people.size());}
    @Test public void nbaPutsComparableStatsFirstWithoutInventingRatings()throws Exception{MatchDetail.Table table=players(nba(fixture("nba"))).get(0);assertEquals(List.of("球员","时间","得分","篮板","助攻","抢断","盖帽"),table.columns.subList(0,7));assertTrue(table.columns.contains("投篮"));assertTrue(table.columns.contains("正负值"));assertFalse(table.columns.contains("SR"));}
    @Test public void teamFilterUsesIdsEvenWhenTitlesAreEqual()throws Exception{MatchDetail d=nba(fixture("nba"));List<MatchDetail.Table> tables=players(d);for(MatchDetail.Table table:tables)table.title="Same name";List<DetailRows.Entry> rows=DetailRows.select(tables,"球员",d.match.homeId,"得分",true);assertFalse(rows.isEmpty());assertTrue(rows.stream().allMatch(r->r.person().teamId().equals(d.match.homeId)));assertTrue(rows.size()<DetailRows.select(tables,"球员","all","得分",true).size());}
    @Test public void numericSortKeepsMissingValuesLastInBothDirections(){MatchDetail.Table table=new MatchDetail.Table("lineup","players","球员","得分");table.category="球员";table.row("A","10");table.row("B","—");table.row("C","0");assertEquals("A",DetailRows.select(List.of(table),"球员","all","得分",true).get(0).values().get(0));List<DetailRows.Entry> ascending=DetailRows.select(List.of(table),"球员","all","得分",false);assertEquals("C",ascending.get(0).values().get(0));assertEquals("B",ascending.get(2).values().get(0));}
    @Test public void signedPercentAndClockSortValuesKeepMeaning(){assertEquals(-14,DetailRows.number("-14"),0);assertEquals(42.5,DetailRows.number("42.5%"),0);assertEquals(78.518,DetailRows.number("1:18.518"),.001);assertTrue(Double.isNaN(DetailRows.number("—")));}
    @Test public void injuryStatusesUseReadableChinese(){assertEquals("每日观察",DetailParsers.label("Day-To-Day"));assertEquals("每日观察",DetailParsers.label("Day To Day"));assertEquals("缺阵",DetailParsers.label("Out"));assertEquals("出战存疑",DetailParsers.label("Doubtful"));}
    @Test public void rowMetadataAndAllStatsSurviveCache()throws Exception{MatchDetail d=nba(fixture("nba")),restored=MatchDetail.fromJson(d.json());MatchDetail.Table table=players(d).get(0),copy=players(restored).get(0);assertEquals(table.columns,copy.columns);assertEquals(table.rows,copy.rows);assertEquals(table.people,copy.people);assertEquals(table.teamId,copy.teamId);}
    @Test public void oldTablesWithoutMetadataRemainReadable()throws Exception{MatchDetail d=nba(fixture("nba"));JSONObject root=d.json();for(int i=0;i<root.getJSONArray("tables").length();i++){JSONObject table=root.getJSONArray("tables").getJSONObject(i);table.remove("people");table.remove("teamId");table.remove("category");}assertFalse(MatchDetail.fromJson(root).tables.isEmpty());}
    @Test public void oldCs2HalfTablesRequestAFreshReadForSides(){
        Match match=new Match();match.group="CS2";
        MatchDetail old=new MatchDetail(match);MatchDetail.Table halves=new MatchDetail.Table("events","Mirage · 攻守半场","球队","上半场","下半场","加时","总分");halves.row("Aurora","7","6","—","13");old.table(halves);assertTrue(DetailRepository.needsSideRefresh(match,old));
        MatchDetail preview=new MatchDetail(match);MatchDetail.Table noStart=new MatchDetail.Table("events","Mirage · T / CT","球队","T 方","CT 方","加时","总分");noStart.row("Aurora","6","7","—","13");preview.table(noStart);assertTrue(DetailRepository.needsSideRefresh(match,preview));
        MatchDetail sides=new MatchDetail(match);MatchDetail.Table current=new MatchDetail.Table("events","Mirage · T / CT","球队","首开方","T 方","CT 方","加时","总分");current.row("Aurora","CT","6","7","—","13");sides.table(current);assertFalse(DetailRepository.needsSideRefresh(match,sides));
        MatchDetail empty=new MatchDetail(match);MatchDetail.Table nuke=new MatchDetail.Table("events","Nuke · 攻守半场","球队","上半场","下半场","加时","总分");nuke.row("Aurora","—","—","—","0");empty.table(nuke);assertFalse(DetailRepository.needsSideRefresh(match,empty));
    }
}
