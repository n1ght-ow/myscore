package cn.myscore.app;

import android.app.Instrumentation;
import android.content.Context;
import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;

/** Measure actual Android text layouts with long content; no synthetic scores enter app storage. */
final class LayoutChecks {
    static int run(Instrumentation test,Context context){
        int[] checks={0};
        test.runOnMainSync(()->{
            for(int width:new int[]{320,360})for(float scale:new float[]{1f,1.3f,2f})for(boolean dark:new boolean[]{false,true}){
                Configuration config=new Configuration(context.getResources().getConfiguration());config.fontScale=scale;config.screenWidthDp=width;
                Ui ui=new Ui(context.createConfigurationContext(config),dark);
                TextView copy=ui.text("中文字符 Égj Ångström，完整文字与换行\n第二行的下缘也应完整显示",14,ui.text,false);layout(copy,ui.dp(width-32));inspect(copy);checks[0]++;
                MatchDetail.Table source=new MatchDetail.Table("lineup","layout regression","球员","非常长的统计表头","说明");
                source.row("很长的球员姓名 Giannis Antetokounmpo Alexander-Walker","123–456","第一行\n第二行\n第三行\n第四行\n第五行");
                source.row("另一位长姓名选手","—","来源没有更多字段，不以零填充。");
                View table=new DetailTableView(ui).simple(source);layout(table,ui.dp(width-32));inspect(table);checks[0]++;
                LinearLayout names=(LinearLayout)((ViewGroup)table).getChildAt(0);
                ViewGroup metrics=(ViewGroup)((ViewGroup)((ViewGroup)table).getChildAt(2)).getChildAt(0);
                for(int i=0;i<names.getChildCount();i++){require(names.getChildAt(i).getHeight()==metrics.getChildAt(i).getHeight(),"identity and statistics rows align");checks[0]++;}
                ScoreAdapter adapter=new ScoreAdapter(ui,new ScoreAdapter.Host(){public void openMatch(Match m){}public void openTeam(Match m,boolean home){}public void toggleSubscription(Match m){}public void retryScores(){}public void showSourceStatus(){}public boolean isSubscribed(String id){return false;}});
                Match m=new Match();m.group="NBA";m.league="nba";m.home="这是非常长的球队名称 Los Angeles Basketball Association";m.away="另一支长名称球队 Minnesota Timberwolves";m.state="live";m.status="第 4 节 · 加时比赛进行中 · 来源计时 12:34";m.homeScore="123";m.awayScore="124";m.updated=System.currentTimeMillis();
                adapter.updateRows(List.of(ScoreRow.match(m)));View row=adapter.getView(0,null,null);layout(row,ui.dp(width-32));inspect(row);checks[0]++;
                Match teamMatch=new Match();teamMatch.group="NBA";teamMatch.league="nba";teamMatch.home="这是非常长的球队名称 Los Angeles Basketball Association";teamMatch.away="另一支长名称球队 Minnesota Timberwolves";teamMatch.homeId="13";teamMatch.awayId="9";teamMatch.state="live";teamMatch.status="第 4 节 · 12:34";teamMatch.homeScore="123";teamMatch.awayScore="124";teamMatch.updated=System.currentTimeMillis();
                teamMatch.detail="Crypto.com Arena · 常规赛";
                TeamScheduleAdapter teamAdapter=new TeamScheduleAdapter(ui,match->{});
                Team home=new Team();home.league="nba";home.id="13";home.name=teamMatch.home;
                teamAdapter.updateRows(List.of(TeamScheduleRow.match(teamMatch,home)));
                View teamRow=teamAdapter.getView(0,null,null);layout(teamRow,ui.dp(width-32));inspect(teamRow);checks[0]++;
                Ui.ScoreText score=ui.score("123","124",true,36);layout(score,ui.dp(100));inspect(score);checks[0]++;
            }
        });
        return checks[0];
    }
    private static void layout(View view,int width){view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));view.layout(0,0,width,view.getMeasuredHeight());}
    private static void inspect(View view){
        if(view instanceof TextView){TextView text=(TextView)view;if(text.getVisibility()!=View.VISIBLE||text.getText().length()==0)return;android.text.Layout layout=text.getLayout();require(layout!=null,"text has a layout");require(layout.getHeight()<=text.getHeight()-text.getCompoundPaddingTop()-text.getCompoundPaddingBottom(),"text height fits: "+text.getText());require(layout.getLineEnd(layout.getLineCount()-1)==text.length(),"all text lines are displayed: "+text.getText());for(int i=0;i<layout.getLineCount();i++){require(layout.getEllipsisCount(i)==0,"text is not ellipsized");require(layout.getLineMax(i)<=layout.getWidth()+1,"visible text width fits: "+text.getText());}}
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)inspect(group.getChildAt(i));}
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
