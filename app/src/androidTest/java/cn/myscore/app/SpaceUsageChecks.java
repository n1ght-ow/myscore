package cn.myscore.app;

import android.app.Instrumentation;
import android.content.Context;
import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Measured pane geometry; fixture objects stay in memory and never enter application caches. */
final class SpaceUsageChecks {
    static int run(Instrumentation test,Context context){
        int[] checks={0};
        test.runOnMainSync(()->{
            for(float scale:new float[]{1f,1.3f,2f})for(boolean dark:new boolean[]{true,false}){
                Configuration config=new Configuration(context.getResources().getConfiguration());config.screenWidthDp=1219;config.smallestScreenWidthDp=813;config.fontScale=scale;
                Ui ui=new Ui(context.createConfigurationContext(config),dark);
                MatchDetail.Table cs=new MatchDetail.Table("lineup","Map","选手","Rating","K","D","A","ADR","KAST");
                cs.row("Long Player Name","1.27","19","13","7","124.9","93.3%");cs.row("Player","0.89","9","14","2","63.2","46.7%");
                View table=new DetailTableView(ui,false,false,true,360).simple(cs);
                for(int width:new int[]{747,320,1020}){
                    layout(table,ui.dp(width));HorizontalScrollView scroll=table.findViewWithTag("detail-metrics-scroll");
                    require(Math.abs(scroll.getChildAt(0).getWidth()-scroll.getWidth())<=1||scroll.getChildAt(0).getWidth()>scroll.getWidth(),"table has no trailing empty numeric band");checks[0]++;
                    LinearLayout shell=(LinearLayout)table,names=(LinearLayout)shell.getChildAt(0),numbers=(LinearLayout)scroll.getChildAt(0);
                    require(names.getChildAt(2).getHeight()==numbers.getChildAt(2).getHeight(),"identity and metrics share the same row height");checks[0]++;
                    inspect(table);checks[0]++;
                }
                MatchDetail.Table nfl=new MatchDetail.Table("lineup","Passing","球员","C/ATT","YDS","AVG","TD","INT","SACKS","QBR","RTG");
                nfl.row("J. Burrow","22/30","292","9.7","4","0","1-8","95.3","138.2");
                View packed=new DetailTableView(ui,false,true,false,1120,true).simple(nfl);layout(packed,ui.dp(747));
                HorizontalScrollView metrics=packed.findViewWithTag("detail-metrics-scroll");LinearLayout cells=(LinearLayout)((LinearLayout)metrics.getChildAt(0)).getChildAt(0);
                for(int i=0;i<cells.getChildCount();i++){require(cells.getChildAt(i).getWidth()<=ui.dp(100*scale),"NFL columns stay proportional to their values");checks[0]++;}
                if(scale==1f){require(metrics.getChildAt(0).getWidth()<=metrics.getWidth(),"all normal-size passing columns fit in the tablet pane");checks[0]++;}
                inspect(packed);checks[0]++;
                int[] actions={0};Ui.WatchRow watch=ui.watchRow();Match match=new Match();match.league="cs2";
                watch.bind(WatchLinks.of(match),true,()->actions[0]++);layout(watch,ui.dp(360));
                require(watch.getChildCount()==3,"media row has one leading icon and no trailing duplicate icon");checks[0]++;
                watch.performClick();require(actions[0]==1,"the remaining media action still opens its destination");checks[0]++;
                MatchDetail lol=lolFixture();LinearLayout body=ui.column();new LolMatchView(ui,lol,body,n->{}).games(1,true);
                layout(body,ui.dp(747));View stats=body.findViewWithTag("lol-stats-table");
                if(scale<=1.3f){require(stats!=null&&findAll(stats,"lol-stats-player").size()==10,"wide statistics show both teams together");checks[0]++;inspect(stats);checks[0]++;}
                layout(body,ui.dp(320));require(body.findViewWithTag("lol-stats-table")==null,"phone-width statistics retain the readable card layout");checks[0]++;inspect(body);checks[0]++;
                layout(body,ui.dp(747));if(scale<=1.3f){require(findAll(body,"lol-stats-player").size()==10,"resizing back does not duplicate player rows");checks[0]++;}
            }
        });
        return checks[0];
    }
    private static MatchDetail lolFixture(){
        Match match=new Match();match.group="LOL";match.league="lol";match.homeId="1";match.awayId="2";match.home="Home";match.away="Away";MatchDetail detail=new MatchDetail(match);
        try{
            JSONArray teams=new JSONArray();
            for(String id:List.of("1","2")){
                JSONArray players=new JSONArray();int index=0;
                for(String role:List.of("TOP","JUNGLE","MID","BOT","SUPPORT"))players.put(new JSONObject().put("playerName","Player "+(++index)).put("playerLocation",role).put("heroName","测试英雄").put("damageDetail",new JSONObject().put("heroDamage",12345)).put("visionDetail",new JSONObject().put("visionScore",33.9)));
                teams.put(new JSONObject().put("teamId",id).put("playerInfos",players));
            }
            detail.lolGames.add(LolGame.parse(new JSONObject().put("bo",1).put("matchStatus","2").put("teamInfos",teams),match));
        }catch(Exception e){throw new AssertionError(e);}
        return detail;
    }
    static void layout(View view,int width){view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));view.layout(0,0,view.getMeasuredWidth(),view.getMeasuredHeight());}
    static List<View> findAll(View view,String tag){List<View> found=new ArrayList<>();if(tag.equals(view.getTag()))found.add(view);if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)found.addAll(findAll(group.getChildAt(i),tag));return found;}
    private static void inspect(View view){
        if(view.getVisibility()!=View.VISIBLE)return;
        if(view instanceof TextView text&&text.length()>0){android.text.Layout layout=text.getLayout();require(layout!=null&&layout.getHeight()<=text.getHeight()-text.getCompoundPaddingTop()-text.getCompoundPaddingBottom(),"text is not vertically clipped: "+text.getText());for(int i=0;i<layout.getLineCount();i++)require(layout.getEllipsisCount(i)==0&&layout.getLineMax(i)<=layout.getWidth()+1,"text fits its column: "+text.getText());}
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)inspect(group.getChildAt(i));
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
