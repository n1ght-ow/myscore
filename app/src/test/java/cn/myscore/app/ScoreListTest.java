package cn.myscore.app;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public final class ScoreListTest {
    @Test public void tracksPixelsAcrossUnequalAdjacentRowsInBothDirections() {
        ScoreList.ScrollTracker tracker=new ScoreList.ScrollTracker();
        assertEquals(0,tracker.update(2,-180,200,800,true));
        assertEquals(35,tracker.update(3,-15,350,800,true));
        assertEquals(-35,tracker.update(2,-180,200,800,true));
        assertEquals(-20,tracker.update(2,-160,200,800,true));
        assertEquals(10,tracker.update(2,-170,200,800,true));
    }

    @Test public void repeatedCallbacksDoNotMoveTheHeader() {
        ScoreList.ScrollTracker tracker=new ScoreList.ScrollTracker();
        tracker.update(4,-10,300,800,true);
        assertEquals(0,tracker.update(4,-10,300,800,true));
        assertEquals(0,tracker.update(4,-10,500,800,true));
        assertEquals(15,tracker.update(4,-25,500,800,true));
    }

    @Test public void fastJumpsKeepDirectionDespiteDifferentRowHeights() {
        ScoreList.ScrollTracker tracker=new ScoreList.ScrollTracker();
        tracker.update(10,-200,400,800,true);
        assertEquals(800,tracker.update(13,-5,40,800,true));
        assertEquals(800,tracker.update(20,-10,50,800,true));
        assertEquals(-800,tracker.update(15,-600,700,800,true));
        assertEquals(-800,tracker.update(2,-20,900,800,true));
    }

    @Test public void dataReplacementStartsANewBaseline() {
        ScoreList.ScrollTracker tracker=new ScoreList.ScrollTracker();
        tracker.update(30,-100,300,800,true);
        tracker.reset();
        assertEquals(0,tracker.update(0,150,40,800,true));
        assertEquals(20,tracker.update(0,130,40,800,true));
    }

    @Test public void switchingToShortAndEmptyListsDoesNotMoveTheHeader() {
        ScoreList.ScrollTracker tracker=new ScoreList.ScrollTracker();
        tracker.update(4,-100,300,800,true);
        tracker.reset();
        assertEquals(0,tracker.update(4,-100,300,800,false));
        assertEquals(0,tracker.update(0,150,50,800,false));
        assertEquals(0,tracker.update(0,150,250,800,false));
        assertEquals(0,tracker.update(0,145,250,800,false));
        assertEquals(0,tracker.update(0,150,50,800,false));
    }

    @Test public void layoutDuringAFlingUpdatesTheBaselineWithoutMovingTheHeader() {
        ScoreList.ScrollTracker tracker=new ScoreList.ScrollTracker();
        tracker.update(3,-50,300,800,true);
        assertEquals(20,tracker.update(3,-70,300,800,true));
        assertEquals(0,tracker.update(0,150,50,800,false));
        assertEquals(0,tracker.update(0,130,50,800,false));
        assertEquals(10,tracker.update(0,120,50,800,true));
        assertEquals(-5,tracker.update(0,125,50,800,true));
    }
}
