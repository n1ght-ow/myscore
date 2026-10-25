package cn.myscore.app;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/** A live clock tick must not invalidate the body; real table changes must. */
public final class MatchDetailSignatureTest {
    private static Match match(){
        Match m=new Match();m.id="espn:nba:test:1";m.league="nba";m.group="NBA";m.source="ESPN";
        m.home="Home";m.away="Away";m.homeId="1";m.awayId="2";m.state="live";m.status="第 3 节 · 5:00";
        m.homeScore="88";m.awayScore="80";m.start=1_700_000_000_000L;m.updated=1_700_000_100_000L;m.timeConfirmed=true;
        return m;
    }
    private static MatchDetail detail(String homeScore,String status,long fetched){
        MatchDetail d=new MatchDetail(match());
        d.match.homeScore=homeScore;d.match.status=status;d.fetched=fetched;
        d.fact("赛事","NBA 常规赛");
        d.stats.add(new MatchDetail.Stat("投篮","35-68","27-76"));
        MatchDetail.Table table=new MatchDetail.Table("lineup","球员","球员","时间","得分");
        table.row("A. Player","25","20");
        table.row("B. Player","29","18");
        d.table(table);
        d.events.add(new MatchDetail.Event("Q3 5:00","换人","A. Player"));
        return d;
    }
    @Test public void scoreClockAndFetchTimeDoNotRebuildBody(){
        MatchDetail first=detail("88","第 3 节 · 5:00",1_700_000_100_000L);
        MatchDetail later=detail("91","第 3 节 · 4:20",1_700_000_200_000L);
        assertEquals("header-only updates keep the same body signature",first.bodySignature(),later.bodySignature());
    }
    @Test public void changedStatsOrRowsRebuildBody(){
        MatchDetail first=detail("88","第 3 节 · 5:00",1_700_000_100_000L);
        MatchDetail stats=detail("88","第 3 节 · 5:00",1_700_000_100_000L);
        stats.stats.set(0,new MatchDetail.Stat("投篮","36-68","27-76"));
        assertNotEquals(first.bodySignature(),stats.bodySignature());
        MatchDetail rows=detail("88","第 3 节 · 5:00",1_700_000_100_000L);
        rows.tables.get(0).rows.get(1).set(2,"19");
        assertNotEquals(first.bodySignature(),rows.bodySignature());
    }
    @Test public void persistedDetailKeepsBodySignature() throws Exception {
        MatchDetail detail=detail("88","第 3 节 · 5:00",1_700_000_100_000L);
        MatchDetail copy=MatchDetail.fromJson(detail.json());
        assertEquals("cache reload does not cause a spurious body rebuild",detail.bodySignature(),copy.bodySignature());
    }
}
