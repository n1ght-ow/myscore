package cn.myscore.app;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class ResultNotifyTest {
    @Test public void formatsFinishedScoreShootoutEsportsAndEventTitles() {
        Match epl = new Match();
        epl.id = "espn:epl:1:1";
        epl.league = "epl";
        epl.group = "足球";
        epl.home = "Arsenal";
        epl.away = "Chelsea";
        epl.homeScore = "2";
        epl.awayScore = "1";
        epl.state = "scheduled";
        Competition eplComp = Competition.find("epl");
        assertEquals("英超 · Arsenal vs Chelsea", ResultNotify.calendarTitle(eplComp, epl));

        epl.state = "final";
        assertEquals("英超 · Arsenal 2 - 1 Chelsea", ResultNotify.calendarTitle(eplComp, epl));
        assertEquals("英超 · 比赛已结束", ResultNotify.title(epl));
        assertEquals("Arsenal 2 : 1 Chelsea", ResultNotify.body(epl));

        Match pens = Match.fromJson(epl.json());
        pens.league = "efl";
        pens.homeScore = "1";
        pens.awayScore = "1";
        pens.homeShootout = "5";
        pens.awayShootout = "4";
        assertEquals("Arsenal 1(5) : 1(4) Chelsea", ResultNotify.body(pens));

        Match cs2 = new Match();
        cs2.id = "five:cs2:csgo_mc_1";
        cs2.league = "cs2";
        cs2.group = "CS2";
        cs2.tierOne = true;
        cs2.event = "IEM Cologne";
        cs2.home = "Spirit";
        cs2.away = "Vitality";
        cs2.homeScore = "2";
        cs2.awayScore = "1";
        cs2.state = "final";
        cs2.results.addAll(List.of("Mirage 13-9", "Nuke 10-13", "Dust2 13-7"));
        assertEquals("IEM Cologne · 比赛已结束", ResultNotify.title(cs2));
        assertEquals("Spirit 2 : 1 Vitality（Mirage 13-9 · Nuke 10-13 · Dust2 13-7）", ResultNotify.body(cs2));
        cs2.tierOne = false;
        cs2.homeId = "csgo_tm_7020";
        assertEquals("CS2 · Team Spirit · 比赛已结束", ResultNotify.title(cs2));

        Match f1 = new Match();
        f1.id = "espn:f1:1:2";
        f1.league = "f1";
        f1.group = "F1";
        f1.title = "拉斯维加斯大奖赛 · 正赛";
        f1.state = "final";
        f1.results.addAll(List.of("1 VER", "2 NOR", "3 LEC", "4 PIA"));
        assertEquals("F1 · 比赛已结束", ResultNotify.title(f1));
        assertEquals("拉斯维加斯大奖赛 · 正赛 · 1 VER / 2 NOR / 3 LEC", ResultNotify.body(f1));
        assertEquals("F1 · 拉斯维加斯大奖赛 · 正赛 · 1 VER", ResultNotify.calendarTitle(Competition.find("f1"), f1));
    }

    @Test public void computesFinishWindowWakeupAcrossSportsAndPollingPhases() {
        long now = 1_700_000_000_000L;
        assertEquals(-1L, CalendarJob.nextFinishCheckDelay(List.of(), now));

        Match finished = new Match();
        finished.group = "足球";
        finished.state = "final";
        finished.start = now - 3600_000L;
        assertEquals(-1L, CalendarJob.nextFinishCheckDelay(List.of(finished), now));

        // Football kickoff in 60 minutes -> wakes at 60m + 105m = 165m
        Match football = new Match();
        football.group = "足球";
        football.state = "scheduled";
        football.start = now + 60 * 60_000L;
        assertEquals(165 * 60_000L, CalendarJob.nextFinishCheckDelay(List.of(football), now));

        // Esports BO1 starting in 10 minutes -> 10m + 45m = 55m (beats football's 165m)
        Match bo1 = new Match();
        bo1.group = "CS2";
        bo1.detail = "BO1";
        bo1.state = "scheduled";
        bo1.start = now + 10 * 60_000L;
        assertEquals(55 * 60_000L, CalendarJob.nextFinishCheckDelay(List.of(football, bo1), now));

        // Match already 110 minutes past kickoff and still live -> 8-minute finish-window check
        Match stoppageTime = new Match();
        stoppageTime.group = "足球";
        stoppageTime.state = "live";
        stoppageTime.start = now - 110 * 60_000L;
        assertEquals(8 * 60_000L, CalendarJob.nextFinishCheckDelay(List.of(football, stoppageTime), now));

        // BO5 esports offset vs F1 qualifying offset
        Match bo5 = new Match();
        bo5.group = "LOL";
        bo5.detail = "BO5 决赛";
        assertEquals(165 * 60_000L, CalendarJob.expectedFinishOffsetMs(bo5));

        Match quali = new Match();
        quali.group = "F1";
        quali.title = "排位赛";
        assertEquals(65 * 60_000L, CalendarJob.expectedFinishOffsetMs(quali));
    }
}
