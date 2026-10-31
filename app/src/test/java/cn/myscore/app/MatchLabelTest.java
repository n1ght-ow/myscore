package cn.myscore.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class MatchLabelTest {
    private static Match cs2(){Match m=new Match();m.league="cs2";m.group="CS2";return m;}

    @Test public void tierOneEventsUseTheirOwnName() {
        Match m=cs2();m.tierOne=true;m.event="PGL 布加勒斯特大师赛 2026";m.homeId="csgo_tm_1";m.awayId="csgo_tm_2";
        assertEquals("PGL 布加勒斯特大师赛 2026",MatchLabel.of(m));
    }

    @Test public void tierOneSpiritMatchesAlsoUseTheEventName() {
        Match m=cs2();m.tierOne=true;m.event="EPL S24";m.homeId="csgo_tm_7020";m.awayId="csgo_tm_2";
        assertEquals("EPL S24",MatchLabel.of(m));
    }

    @Test public void spiritLowerTierMatchesKeepTheTeamLabel() {
        Match byId=cs2();byId.tierOne=false;byId.event="CCT 2026 欧洲系列赛 第12季";byId.homeId="csgo_tm_7020";
        assertEquals("CS2 · Team Spirit",MatchLabel.of(byId));
        Match byName=cs2();byName.home="Team Spirit";byName.away="NAVI";
        assertEquals("CS2 · Team Spirit",MatchLabel.of(byName));
    }

    @Test public void otherCs2MatchesFallBackToTheSportName() {
        Match m=cs2();m.tierOne=false;m.event="CCT 2026 欧洲系列赛 第12季";m.home="NAVI";m.away="FURIA";
        assertEquals("CS2",MatchLabel.of(m));
        Match academy=cs2();academy.home="Spirit Academy";academy.away="NAVI";
        assertEquals("CS2",MatchLabel.of(academy));
    }

    @Test public void missingEventFallsBackSafely() {
        Match spirit=cs2();spirit.tierOne=true;spirit.homeId="csgo_tm_7020";
        assertEquals("CS2 · Team Spirit",MatchLabel.of(spirit));
        Match other=cs2();other.tierOne=true;other.homeId="csgo_tm_1";
        assertEquals("CS2",MatchLabel.of(other));
    }

    @Test public void otherSportsAndNullKeepTheExistingLabel() {
        Match epl=new Match();epl.league="epl";epl.group="足球";
        assertEquals("英超",MatchLabel.of(epl));
        assertEquals("",MatchLabel.of(null));
    }
}
