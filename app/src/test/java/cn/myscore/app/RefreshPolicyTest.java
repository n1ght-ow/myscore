package cn.myscore.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class RefreshPolicyTest {
    @Test public void allActiveSportsShareTheFasterCadence(){for(String group:new String[]{"足球","NBA","NFL","CS2","LOL"}){Match m=new Match();m.group=group;m.state="live";assertEquals(10_000,RefreshPolicy.detail(m));}Match f1=new Match();f1.group="F1";f1.state="live";assertEquals(15_000,RefreshPolicy.detail(f1));}
    @Test public void finishedDetailsUseLongCache(){Match m=new Match();m.state="final";assertEquals(21_600_000,RefreshPolicy.detail(m));}
    @Test public void slowRequestsDoNotAddAnotherWholeInterval(){assertEquals(7000,RefreshPolicy.remaining(10_000,3000));assertEquals(1000,RefreshPolicy.remaining(10_000,20_000));}
    @Test public void failuresBackOffIndependentlyAndRemainBounded(){assertEquals(10_000,RefreshPolicy.retry(10_000,0));assertEquals(20_000,RefreshPolicy.retry(10_000,1));assertEquals(40_000,RefreshPolicy.retry(10_000,2));assertEquals(120_000,RefreshPolicy.retry(10_000,100));}
}
