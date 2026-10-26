package cn.myscore.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import java.util.*;

/** Live probe: refresh the real CS2 cache and require the source-published T/CT side fields. */
public final class Cs2SidesRefreshInstrumentation extends Instrumentation {
    private int checks;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle report=new Bundle();int outcome=Activity.RESULT_OK;
        try{
            require(android.os.Build.HARDWARE.equals("ranchu")||android.os.Build.HARDWARE.equals("goldfish"),"emulator only");
            Match request=new Match();request.id="five:cs2:csgo_mc_2398756";request.league="cs2";request.group="CS2";
            MatchDetail detail=new DetailRepository(getTargetContext()).fetch(request,true);
            require(detail!=null&&detail.match.id.equals(request.id),"live CS2 detail cached");
            MatchDetail.Table mirage=detail.tables.stream().filter(t->t.title.equals("Mirage · T / CT")).findFirst().orElseThrow(()->new AssertionError("Mirage T/CT table"));
            require(mirage.columns.equals(List.of("球队","首开方","T 方","CT 方","加时","总分")),"T/CT columns with opening side");
            require(mirage.rows.get(0).equals(List.of("Aurora","CT","6","7","—","13")),"Aurora opens CT, T 6 / CT 7 / 13");
            require(mirage.rows.get(1).equals(List.of("Spirit","T","5","4","—","9")),"Spirit opens T, T 5 / CT 4 / 9");
            report.putString("stream","\nPASS: "+checks+" live CS2 side checks.\n");
        }catch(Throwable e){outcome=Activity.RESULT_CANCELED;report.putString("stream","\nFAIL after "+checks+": "+e+"\n"+android.util.Log.getStackTraceString(e));}
        finish(outcome,report);
    }
    private void require(boolean okay,String why){if(!okay)throw new AssertionError(why);checks++;}
}
