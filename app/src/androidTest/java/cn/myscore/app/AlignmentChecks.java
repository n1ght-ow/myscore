package cn.myscore.app;

import android.app.Instrumentation;
import android.content.Context;
import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Actual view coordinates, not padding snapshots. Fixtures remain in memory. */
final class AlignmentChecks {
    static int run(Instrumentation test,Context context){
        int[] count={0};
        test.runOnMainSync(()->{
            for(boolean dark:new boolean[]{true,false})for(float scale:new float[]{1,1.3f,2}){
                Configuration cfg=new Configuration(context.getResources().getConfiguration());cfg.fontScale=scale;
                Ui ui=new Ui(context.createConfigurationContext(cfg),dark);
                for(boolean play:new boolean[]{true,false}){
                    Match m=fixture(play);
                    ScoreAdapter phone=new ScoreAdapter(ui,phoneHost());phone.updateRows(List.of(ScoreRow.match(m)));
                    for(int width:new int[]{288,360,560}){View row=phone.getView(0,null,null);SpaceUsageChecks.layout(row,ui.dp(width));checkRow(row,true,count);}
                    for(boolean ledger:new boolean[]{false,true}){
                        PadScoreAdapter pad=new PadScoreAdapter(ui,padHost(),ledger);pad.updateRows(List.of(ScoreRow.match(m)));
                        for(int width:new int[]{ledger?680:320,ledger?1020:480}){View row=pad.getView(0,null,null);SpaceUsageChecks.layout(row,ui.dp(width));checkRow(row,!ledger,count);}
                    }
                }
                LinearLayout left=ui.column(),right=ui.column();left.addView(ui.text("左侧内容",14,ui.text,false));right.addView(ui.text("右侧内容",14,ui.text,false));
                LinearLayout pair=ui.columns(left,right,360,24);
                for(int width:new int[]{700,1100,320}){SpaceUsageChecks.layout(pair,ui.dp(width));boolean across=width>=720*scale+24;require((pair.getOrientation()==LinearLayout.HORIZONTAL)==across,"columns follow actual parent width and font scale");count[0]++;require(across?left.getTop()==right.getTop():right.getTop()>=left.getBottom(),"paired content shares its top edge or stacks without overlap");count[0]++;}
                right.removeAllViews();SpaceUsageChecks.layout(pair,ui.dp(1100));require(right.getVisibility()==View.GONE&&left.getWidth()==pair.getWidth(),"empty second columns leave no dead strip");count[0]++;
                Ui.WatchRow media=ui.watchRow();media.bind(WatchLinks.of(fixture(true)),true,()->{});SpaceUsageChecks.layout(media,ui.dp(288));require(((ViewGroup)media.getChildAt(0)).getChildAt(0).getLeft()==0&&media.getPaddingLeft()==0,"media glyph shares its parent content edge");count[0]++;
            }
        });return count[0];
    }
    private static void checkRow(View root,boolean crestEdge,int[] count){
        List<View> scores=SpaceUsageChecks.findAll(root,"fixture-score");require(scores.size()==2,"both opponents share score geometry");
        View action=root.findViewWithTag("watch-shortcut");if(action==null||action.getVisibility()==View.GONE)action=findCalendar(root);
        require(action!=null,"a trailing action target exists");
        for(View score:scores){require(Math.abs(center(score,root)-center(action,root))<=1,"score and trailing action share a center axis");count[0]++;}
        if(crestEdge){List<View> crests=crests(root);TextView time=findText(root,"00:00");require(time!=null&&crests.size()==2,"crest and start-time anchors exist");for(View crest:crests){require(Math.abs(left(crest,root)-left(time,root))<=1,"crest and start time share a start edge");count[0]++;}}
        inspect(root);count[0]++;
    }
    private static float left(View v,View root){float x=0;while(v!=root){x+=v.getLeft();v=(View)v.getParent();}return x;}
    private static float center(View v,View root){return left(v,root)+v.getWidth()/2f;}
    private static List<View> crests(View v){List<View> list=new ArrayList<>();if(v.getTag() instanceof Logos.Crest)list.add(v);else if(v instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++)list.addAll(crests(g.getChildAt(i)));return list;}
    private static View findCalendar(View v){CharSequence d=v.getContentDescription();if(d!=null&&(d.toString().startsWith("订阅 ")||d.toString().startsWith("取消订阅 ")||d.toString().contains("暂不可订阅")))return v;if(v instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){View found=findCalendar(g.getChildAt(i));if(found!=null)return found;}return null;}
    private static TextView findText(View v,String value){if(v instanceof TextView t&&value.contentEquals(t.getText()))return t;if(v instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){TextView found=findText(g.getChildAt(i),value);if(found!=null)return found;}return null;}
    private static void inspect(View v){if(v.getVisibility()!=View.VISIBLE)return;if(v instanceof TextView t&&t.length()>0){android.text.Layout l=t.getLayout();require(l!=null&&l.getHeight()<=t.getHeight()-t.getCompoundPaddingTop()-t.getCompoundPaddingBottom(),"text remains vertically available: "+t.getText());for(int i=0;i<l.getLineCount();i++)require(l.getLineMax(i)<=l.getWidth()+1&&l.getEllipsisCount(i)==0,"text remains horizontally available: "+t.getText());}if(v instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++)inspect(g.getChildAt(i));}
    private static Match fixture(boolean play){Match m=new Match();m.id="in-memory-alignment";m.league=play?"cs2":"nba";m.group=play?"CS2":"NBA";m.home="Very Long Home Team Name";m.away="Very Long Away Team Name";m.homeId="home";m.awayId="away";m.homeScore="123";m.awayScore="9";m.state="scheduled";m.timeConfirmed=true;m.start=java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();m.source="Source";m.updated=System.currentTimeMillis();return m;}
    private static ScoreAdapter.Host phoneHost(){return new ScoreAdapter.Host(){public void openMatch(Match m){}public void openTeam(Match m,boolean h){}public void toggleSubscription(Match m){}public void openWatch(Match m){}public WatchLinks.Link watchLink(Match m){return m.group.equals("CS2")?WatchLinks.of(m):null;}public void retryScores(){}public void resolveEmptySchedule(){}public void showSourceStatus(){}public boolean isSubscribed(String id){return false;}};}
    private static PadScoreAdapter.Host padHost(){return new PadScoreAdapter.Host(){public void selectMatch(Match m){}public void openMatch(Match m){}public void openTeam(Match m,boolean h){}public void toggleSubscription(Match m){}public void openWatch(Match m){}public WatchLinks.Link watchLink(Match m){return m.group.equals("CS2")?WatchLinks.of(m):null;}public boolean isSubscribed(String id){return false;}public void showSourceStatus(){}public void resolveEmptySchedule(){}};}
    private static void require(boolean okay,String message){if(!okay)throw new AssertionError(message);}
}
