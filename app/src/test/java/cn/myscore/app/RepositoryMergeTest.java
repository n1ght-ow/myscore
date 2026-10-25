package cn.myscore.app;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class RepositoryMergeTest {
    private Repository.Batch batch(String error,Match... matches){Repository.Batch b=new Repository.Batch(Competition.find("nba"));b.error=error;b.matches.addAll(List.of(matches));return b;}
    private Match match(String id,String score,long updated){Match m=new Match();m.id=id;m.homeScore=score;m.updated=updated;return m;}
    @Test public void failedDayKeepsCacheWhileSuccessfulDayUpdates()throws Exception{
        Match cached=match("old","1",1),fresh=match("new","2",2);Repository.Batch view=batch("");
        List<Match> merged=Repository.mergeDays(view,List.of(batch("前一天连接失败",cached),batch("",fresh)));
        assertEquals(2,merged.size());assertTrue(merged.contains(cached));assertTrue(merged.contains(fresh));assertEquals("前一天连接失败",view.error);
    }
    @Test public void bothDaysFailWithoutDiscardingPreviousView(){Match old=match("old","1",1);Repository.Batch view=batch("",old);try{Repository.mergeDays(view,List.of(batch("失败"),batch("失败")));fail();}catch(java.io.IOException expected){assertEquals(List.of(old),view.matches);assertFalse(view.error.isEmpty());}}
    @Test public void emptySuccessfulDateDoesNotResurrectRemovedGames()throws Exception{Repository.Batch view=batch("",match("removed","1",1));assertTrue(Repository.mergeDays(view,List.of(batch(""),batch(""))).isEmpty());assertEquals("",view.error);}
    @Test public void newestDuplicateWinsRegardlessOfDateOrder()throws Exception{Match fresh=match("same","3",20),old=match("same","1",10);for(List<Repository.Batch> days:List.of(List.of(batch("",fresh),batch("",old)),List.of(batch("",old),batch("",fresh)))){List<Match> merged=Repository.mergeDays(batch(""),days);assertEquals(1,merged.size());assertSame(fresh,merged.get(0));}}
    @Test public void successfulEmptyDayStillReportsMissingOtherDate()throws Exception{Repository.Batch view=batch("");assertTrue(Repository.mergeDays(view,List.of(batch("未更新"),batch(""))).isEmpty());assertFalse(view.error.isEmpty());}
}
