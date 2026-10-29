package cn.myscore.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/** Detached view tests: no synthetic fixtures, scores or calendar events enter app storage. */
public final class ShootoutInstrumentation extends Instrumentation {
    private int checks;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle report=new Bundle();Throwable[] failure={null};
        runOnMainSync(()->{try{
            for(boolean dark:new boolean[]{true,false})for(float scale:new float[]{1,1.3f,2}){
                Configuration cfg=new Configuration(getTargetContext().getResources().getConfiguration());cfg.fontScale=scale;
                Ui ui=new Ui(getTargetContext().createConfigurationContext(cfg),dark);
                for(String penalties:new String[]{"4","12",""}){
                    Match m=new Match();m.id="detached-shootout";m.league="efl";m.group="足球";
                    m.home="Manchester City";m.away="Brighton & Hove Albion";m.homeId="382";m.awayId="331";
                    m.state="final";m.homeScore="2";m.awayScore="2";m.scoreDataVersion=1;
                    m.homeShootout=penalties;m.awayShootout=penalties.isEmpty()?"":penalties.equals("4")?"3":"11";
                    m.start=Match.parseTime("2026-10-29T03:30:00+08:00");m.timeConfirmed=true;
                    for(int width:new int[]{288,360,560}){
                        ScoreAdapter phone=new ScoreAdapter(ui,host(ScoreAdapter.Host.class));phone.updateRows(List.of(ScoreRow.match(m)));
                        verify(phone.getView(0,null,null),ui,m,width);
                        for(boolean ledger:new boolean[]{false,true}){
                            PadScoreAdapter pad=new PadScoreAdapter(ui,host(PadScoreAdapter.Host.class),ledger);
                            pad.updateRows(List.of(ScoreRow.match(m)));verify(pad.getView(0,null,null),ui,m,ledger?680:width);
                        }
                        TeamScheduleAdapter teams=new TeamScheduleAdapter(ui,host(TeamScheduleAdapter.Host.class));
                        Team team=new Team();team.id="382";team.name=m.home;
                        teams.updateRows(List.of(TeamScheduleRow.match(m,team)));View row=teams.getView(0,null,null);
                        SpaceUsageChecks.layout(row,ui.dp(width));inspect(row);
                        require(findText(row,m.hasShootout()?"胜":"平")!=null,"team result follows shootout or genuine draw");
                    }
                }
            }
        }catch(Throwable e){failure[0]=e;}});
        report.putString("stream",failure[0]==null?"PASS "+checks+" shootout display/color/alignment checks.\n":"FAIL after "+checks+": "+android.util.Log.getStackTraceString(failure[0]));
        finish(failure[0]==null?Activity.RESULT_OK:Activity.RESULT_CANCELED,report);
    }
    private void verify(View row,Ui ui,Match m,int width){
        SpaceUsageChecks.layout(row,ui.dp(width));List<TextView> scores=new ArrayList<>();collect(row,scores);
        require(scores.size()==2,"two score columns");
        require(scores.get(0).getText().toString().equals(m.displayScore(true))&&scores.get(1).getText().toString().equals(m.displayScore(false)),"both formatted scores");
        require(scores.get(0).getCurrentTextColor()==(m.hasShootout()?ui.primary:ui.text),"winner/draw color");
        require(scores.get(1).getCurrentTextColor()==(m.hasShootout()?ui.error:ui.text),"loser/draw color");
        View play=row.findViewWithTag("watch-shortcut");
        for(View score:scores)require(Math.abs(center(score,row)-center(play,row))<=1,"score and play share axis");
        inspect(row);
    }
    private <T>T host(Class<T> type){return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(proxy,method,args)->{
        if(method.getReturnType()==boolean.class)return false;
        if(method.getName().equals("watchLink"))return WatchLinks.biliVideo("BV1hgsd6gE6a",(Match)args[0]);return null;
    }));}
    private float center(View v,View root){float left=v.getWidth()/2f;while(v!=root){left+=v.getLeft();v=(View)v.getParent();}return left;}
    private void collect(View v,List<TextView> scores){if("fixture-score".equals(v.getTag()))scores.add((TextView)v);if(v instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++)collect(g.getChildAt(i),scores);}
    private TextView findText(View v,String text){if(v instanceof TextView t&&text.contentEquals(t.getText()))return t;if(v instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){TextView result=findText(g.getChildAt(i),text);if(result!=null)return result;}return null;}
    private void inspect(View v){
        if(v.getVisibility()!=View.VISIBLE)return;
        if(v instanceof TextView t&&t.length()>0){android.text.Layout l=t.getLayout();require(l!=null&&l.getHeight()<=t.getHeight()-t.getCompoundPaddingTop()-t.getCompoundPaddingBottom(),"text height fits");for(int i=0;i<l.getLineCount();i++)require(l.getLineMax(i)<=l.getWidth()+1&&l.getEllipsisCount(i)==0,"text width fits: "+t.getText());}
        if(v instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++)inspect(g.getChildAt(i));
    }
    private void require(boolean okay,String message){if(!okay)throw new AssertionError(message);checks++;}
}
