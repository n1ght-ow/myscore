package cn.myscore.app;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public final class ScoreListTest {
    @Test public void tracksPixelsAcrossUnequalAdjacentRowsInBothDirections() {
        ScoreList.ScrollTracker tracker=new ScoreList.ScrollTracker();
        assertEquals(0,tracker.update(2,-180,200,800));
        assertEquals(35,tracker.update(3,-15,350,800));
        assertEquals(-35,tracker.update(2,-180,200,800));
        assertEquals(-20,tracker.update(2,-160,200,800));
        assertEquals(10,tracker.update(2,-170,200,800));
    }

    @Test public void repeatedCallbacksDoNotMoveTheHeader() {
        ScoreList.ScrollTracker tracker=new ScoreList.ScrollTracker();
        tracker.update(4,-10,300,800);
        assertEquals(0,tracker.update(4,-10,300,800));
        assertEquals(0,tracker.update(4,-10,500,800));
        assertEquals(15,tracker.update(4,-25,500,800));
    }

    @Test public void fastJumpsKeepDirectionDespiteDifferentRowHeights() {
        ScoreList.ScrollTracker tracker=new ScoreList.ScrollTracker();
        tracker.update(10,-200,400,800);
        assertEquals(800,tracker.update(13,-5,40,800));
        assertEquals(800,tracker.update(20,-10,50,800));
        assertEquals(-800,tracker.update(15,-600,700,800));
        assertEquals(-800,tracker.update(2,-20,900,800));
    }

    @Test public void dataReplacementStartsANewBaseline() {
        ScoreList.ScrollTracker tracker=new ScoreList.ScrollTracker();
        tracker.update(30,-100,300,800);
        tracker.reset();
        assertEquals(0,tracker.update(0,150,40,800));
        assertEquals(20,tracker.update(0,130,40,800));
    }
}
