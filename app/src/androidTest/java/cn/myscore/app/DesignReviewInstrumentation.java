package cn.myscore.app;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Emulator review for the LOL pairing, CS2 team panels and photo-free football formation. */
public final class DesignReviewInstrumentation extends Instrumentation {
    private Activity activity;private int checks;private String label="review";
    @Override public void onCreate(Bundle args){super.onCreate(args);if(args!=null)label=args.getString("label","review");start();}
    @Override public void onStart(){
        Bundle report=new Bundle();int outcome=Activity.RESULT_OK;Context context=getTargetContext();
        String old=context.getSharedPreferences("settings",0).getString("theme","dark");
        try{
            require(android.os.Build.HARDWARE.equals("ranchu")||android.os.Build.HARDWARE.equals("goldfish"),"emulator only");
            for(String theme:List.of("dark","light")){
                context.getSharedPreferences("settings",0).edit().putString("theme",theme).commit();
                football(theme);
                cs2(theme);
                lol(theme);
            }
            report.putString("stream","\nPASS: "+checks+" design review checks.\n");
        }catch(Throwable e){outcome=Activity.RESULT_CANCELED;report.putString("stream","\nFAIL after "+checks+": "+e.toString()+"\n"+android.util.Log.getStackTraceString(e));}
        finally{context.getSharedPreferences("settings",0).edit().putString("theme",old).commit();}
        finish(outcome,report);
    }
    private void football(String theme)throws Exception{
        open("espn:epl:401878767:401878767","足球","epl","lineup",null,null);
        View pitch=findTag("football-pitch");require(pitch!=null,"football formation renders");
        require(findAll(pitch,"football-pitch-player").size()==11,"eleven numbered players");
        require(findTag("football-event-legend")!=null,"event legend renders");
        require(countImages(pitch)==0,"formation no longer depends on photos");
        require(findTag("football-performance-picker")==null,"perspective filter row removed");
        require(findTag("football-player-metric")==null&&findTag("football-list-metric")==null,"no per-player metric values");
        scroll(240);capture(theme+"-football-pitch");
        scroll(760);capture(theme+"-football-lower");
        scroll(1320);capture(theme+"-football-bench");
        main(()->{ViewGroup filter=(ViewGroup)findTag("football-team-filter");filter.getChildAt(1).performClick();});settle();
        require(hasDescription(findTag("football-pitch"),"进球"),"goal marker reaches the away formation");
        scroll(300);capture(theme+"-football-away");
    }
    private void cs2(String theme)throws Exception{
        MatchDetail detail=open("five:cs2:csgo_mc_2398756","CS2","cs2","lineup",null,null);
        require(findTag("detail-team-filter")==null,"CS2 team switch row removed");
        require(findText("双方")==null,"CS2 no all-teams switch");
        require(findText("全场")==null&&findText("Mirage")==null,"CS2 no map or scope switch");
        require(findText("选手总数据")!=null,"CS2 overall player section is named once");
        List<View> headers=findAll(activity.getWindow().getDecorView(),"team-panel-header");
        require(headers.size()==2,"two CS2 team panels");
        require(headers.get(0).getContentDescription()!=null&&headers.get(0).getContentDescription().toString().contains(detail.match.home),"first panel is the home team");
        require(headers.get(1).getContentDescription()!=null&&headers.get(1).getContentDescription().toString().contains(detail.match.away),"second panel is the away team");
        List<View> tables=findAll(activity.getWindow().getDecorView(),"detail-data-table");
        require(tables.size()==2,"two stacked player tables");
        require(findTag("team-panel-injury")==null,"CS2 has no injury block");
        for(View table:tables){LinearLayout numbers=(LinearLayout)((HorizontalScrollView)table.findViewWithTag("detail-metrics-scroll")).getChildAt(0);LinearLayout labels=(LinearLayout)numbers.getChildAt(0);View first=labels.getChildAt(0);require(first instanceof ViewGroup&&((ViewGroup)first).getChildAt(0) instanceof TextView&&"Rating".contentEquals(((TextView)((ViewGroup)first).getChildAt(0)).getText()),"Rating leads the numeric columns");}
        List<HorizontalScrollView> scrollers=new ArrayList<>();for(View table:tables)scrollers.add((HorizontalScrollView)table.findViewWithTag("detail-metrics-scroll"));
        main(()->scrollers.get(0).scrollTo(ui().dp(90),0));settle();
        require(Math.abs(scrollers.get(1).getScrollX()-scrollers.get(0).getScrollX())<=1,"team tables scroll together");
        main(()->{for(HorizontalScrollView scroller:scrollers)scroller.scrollTo(0,0);((ScrollView)get("scroll")).scrollTo(0,0);});settle();
        capture(theme+"-cs2-first");
        scroll(650);capture(theme+"-cs2-second");
        main(()->{View view=findText("Rating");require(view!=null,"CS2 rating header");while(!view.isClickable()&&view.getParent() instanceof View)view=(View)view.getParent();view.performClick();});settle();
        require(field("sortColumn").equals("Rating"),"CS2 numeric sort remains interactive");
        require((boolean)field("descending"),"new CS2 sort column starts descending");
        capture(theme+"-cs2-sorted");
        main(()->{try{set("sortColumn","");set("tab","events");render(false);((ScrollView)get("scroll")).scrollTo(0,0);}catch(Exception e){throw new AssertionError(e);}});settle();
        List<String> scope=new ArrayList<>();
        for(MatchDetail.Table table:detail.tables)if(table.tab.equals("lineup")&&!table.category.equals("全场")&&!table.category.equals("伤停")&&!table.rows.isEmpty()&&!scope.contains(table.category))scope.add(table.category);
        require(!scope.isEmpty(),"real per-map player tables exist");
        require(findTag("cs2-map-filter")!=null,"map switch row exists");
        List<View> chips=findAll(activity.getWindow().getDecorView(),"cs2-map-chip");
        require(chips.size()==scope.size(),"one map button per map with data");
        require(chips.get(0).getContentDescription()!=null&&chips.get(0).getContentDescription().toString().contains("已选中"),"first map starts selected");
        require(findTag("cs2-map-title")==null,"selected map name is not repeated below its switch");
        require(findAll(activity.getWindow().getDecorView(),"team-panel-header").size()==2,"selected map shows both teams");
        require(findAll(activity.getWindow().getDecorView(),"detail-data-table").size()==2,"selected map keeps only the two player tables");
        List<View> bands=findAll(activity.getWindow().getDecorView(),"team-map-score");
        require(bands.size()==2&&findAll(activity.getWindow().getDecorView(),"team-map-score-total").size()==2,"one score band and total per team");
        require(findText("上半场")==null&&findText("下半场")==null,"T and CT labels replace first and second half");
        String homeBand=bands.get(0).getContentDescription().toString(),awayBand=bands.get(1).getContentDescription().toString();
        require(homeBand.startsWith(detail.match.home+"，CT 方 7")&&homeBand.contains("T 方 6")&&homeBand.contains("总分 13"),"home opens CT, score band leads with CT");
        require(awayBand.startsWith(detail.match.away+"，T 方 5")&&awayBand.contains("CT 方 4")&&awayBand.contains("总分 9"),"away opens T, score band leads with T");
        View missing=findTag("map-missing");require(missing instanceof TextView&&((TextView)missing).getText().toString().contains("Nuke")&&((TextView)missing).getText().toString().contains("暂无逐图数据"),"map with no data is called out once");
        require(findText("Nuke")==null,"map without data has no button");
        capture(theme+"-cs2-maps-top");
        main(()->{View chip=findText("Anubis");require(chip!=null,"Anubis button");while(!chip.isClickable()&&chip.getParent() instanceof View)chip=(View)chip.getParent();chip.performClick();});settle();
        require(field("mapFilter").equals("Anubis"),"map button changes the selected map");
        require(findTag("cs2-map-title")==null,"changing map does not add a duplicate title");
        require(findAll(activity.getWindow().getDecorView(),"team-panel-header").size()==2&&findAll(activity.getWindow().getDecorView(),"detail-data-table").size()==2,"selected Anubis data replaces the previous map");
        List<View> anubis=findAll(activity.getWindow().getDecorView(),"team-map-score");
        require(anubis.get(0).getContentDescription().toString().startsWith(detail.match.home+"，CT 方 9")&&anubis.get(0).getContentDescription().toString().contains("T 方 4"),"Anubis home opens CT, score band switches");
        require(anubis.get(1).getContentDescription().toString().startsWith(detail.match.away+"，T 方 3")&&anubis.get(1).getContentDescription().toString().contains("CT 方 3"),"Anubis away opens T, score band switches");
        main(()->{View view=findText("Rating");require(view!=null,"map rating header");while(!view.isClickable()&&view.getParent() instanceof View)view=(View)view.getParent();view.performClick();});settle();
        require(field("sortColumn").equals("Rating"),"map panels share the numeric sort");
        require((boolean)field("descending"),"map panel sort starts descending");
        scroll(240);capture(theme+"-cs2-maps");
        scroll(1400);capture(theme+"-cs2-maps-lower");
        scroll(2400);capture(theme+"-cs2-maps-second");
        cs2Matrix(detail,theme.equals("dark"));
        main(()->{try{set("sortColumn","");set("tab","overview");render(false);((ScrollView)get("scroll")).scrollTo(0,0);}catch(Exception e){throw new AssertionError(e);}});settle();
        require(findText("比赛信息")!=null,"CS2 overview keeps match facts");
        require(findTag("detail-data-table")==null,"CS2 overview does not repeat score or player tables");
        require(findText("来源与更新时间")!=null,"CS2 overview keeps the source action");
        capture(theme+"-cs2-overview");
        main(()->{try{set("tab","lineup");render(false);((ScrollView)get("scroll")).scrollTo(0,0);}catch(Exception e){throw new AssertionError(e);}});settle();
    }
    private void cs2Matrix(MatchDetail detail,boolean dark){
        String map="";for(MatchDetail.Table table:detail.tables)if(table.tab.equals("lineup")&&!table.category.equals("全场")&&!table.category.equals("伤停")&&!table.rows.isEmpty()){map=table.category;break;}
        require(!map.isBlank(),"CS2 matrix has a real map scope");String mapScope=map;
        Map<String,String> aurora=new LinkedHashMap<>();aurora.put("CT 方","7");aurora.put("T 方","6");aurora.put("加时","—");aurora.put("总分","13");
        Map<String,String> spirit=new LinkedHashMap<>();spirit.put("T 方","5");spirit.put("CT 方","4");spirit.put("加时","—");spirit.put("总分","9");
        for(int width:new int[]{320,375,640})for(float scale:new float[]{1f,1.3f,2f})main(()->{
            Configuration config=new Configuration(activity.getResources().getConfiguration());config.fontScale=scale;config.screenWidthDp=width;
            Ui ui=new Ui(activity.createConfigurationContext(config),dark);
            LinearLayout body=ui.column();body.setPadding(ui.dp(16),ui.dp(12),ui.dp(16),ui.dp(24));
            new TeamPanelsView(ui,detail,body).show("",false,s->{},0,x->{});
            measure(body,ui.dp(width));inspect(body);
            require(findAll(body,"team-panel-header").size()==2,"CS2 matrix keeps two team panels");
            LinearLayout maps=ui.column();maps.setPadding(ui.dp(16),ui.dp(12),ui.dp(16),ui.dp(24));
            new TeamPanelsView(ui,detail,maps).scope(mapScope,"",false,null,0,null,aurora,spirit);
            measure(maps,ui.dp(width));inspect(maps);
            require(findAll(maps,"team-panel-header").size()==2,"map matrix keeps two team panels");
            require(findAll(maps,"team-map-score").size()==2,"map matrix keeps both T/CT score bands");
        });
    }
    private void measure(View view,int width){view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));view.layout(0,0,view.getMeasuredWidth(),view.getMeasuredHeight());}
    private void inspect(View view){
        if(view.getVisibility()!=View.VISIBLE)return;
        if(view instanceof TextView text&&text.length()>0&&text.getWidth()>0){android.text.Layout layout=text.getLayout();require(layout!=null,"native text layout");require(layout.getHeight()<=text.getHeight()-text.getCompoundPaddingTop()-text.getCompoundPaddingBottom(),"text height: "+text.getText());for(int i=0;i<layout.getLineCount();i++)require(layout.getEllipsisCount(i)==0&&layout.getLineMax(i)<=layout.getWidth()+1,"text width: "+text.getText());}
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View child=group.getChildAt(i);if(!(group instanceof HorizontalScrollView))require(child.getLeft()>=0&&child.getRight()<=group.getWidth()+1,"child fits parent");inspect(child);}
    }
    private void lol(String theme)throws Exception{
        open("qq:lol:13608","LOL","lol","lineup",null,null);
        View away=findTag("lol-kda-away"),home=findTag("lol-kda-home");require(away!=null&&home!=null,"paired KDA rows");
        require(home.getWidth()==away.getWidth()&&home.getWidth()>0,"same KDA column width");
        require(findAll(activity.getWindow().getDecorView(),"lol-table-header").size()==1,"one table header for all roles");
        require(findAll(activity.getWindow().getDecorView(),"lol-role-label").size()==5,"five role labels split the pairs");
        scroll(560);capture(theme+"-lol-pairing");
        scroll(900);capture(theme+"-lol-pairing-lower");
    }
    private MatchDetail open(String id,String group,String league,String tab,String category,String team)throws Exception{
        MatchDetail cached=new DetailRepository(getTargetContext()).cached(request(id,group,league));require(cached!=null,"real cached "+id);
        activity=startActivitySync(new Intent(getTargetContext(),DetailActivity.class).putExtra("match",cached.match.json().toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        waitForIdleSync();for(int i=0;i<80&&field("detail")==null;i++)Thread.sleep(100);require(field("detail")!=null,"detail loads");
        main(()->{try{set("tab",tab);if(category!=null)set("categoryFilter",category);if(team!=null)set("teamFilter",team);render(false);((ScrollView)get("scroll")).scrollTo(0,0);}catch(Exception e){throw new AssertionError(e);}});
        settle();return cached;
    }
    private Match request(String id,String group,String league){Match m=new Match();m.id=id;m.group=group;m.league=league;return m;}
    private void scroll(int dp)throws Exception{main(()->((ScrollView)get("scroll")).scrollTo(0,ui().dp(dp)));settle();}
    private Ui ui(){return new Ui(activity,Ui.isDark(activity));}
    private int countImages(View view){int count=view instanceof ImageView?1:0;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)count+=countImages(group.getChildAt(i));return count;}
    private boolean hasDescription(View view,String text){CharSequence description=view.getContentDescription();if(description!=null&&description.toString().contains(text))return true;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)if(hasDescription(group.getChildAt(i),text))return true;return false;}
    private List<View> findAll(View view,String tag){List<View> result=new ArrayList<>();if(tag.equals(view.getTag()))result.add(view);if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)result.addAll(findAll(group.getChildAt(i),tag));return result;}
    private View findTag(String tag){return activity.getWindow().getDecorView().findViewWithTag(tag);}
    private View findText(String value){return find(activity.getWindow().getDecorView(),value);}
    private View find(View view,String value){if(view instanceof TextView&&value.contentEquals(((TextView)view).getText()))return view;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),value);if(found!=null)return found;}return null;}
    private void capture(String name)throws Exception{settle();Bitmap bitmap=getUiAutomation().takeScreenshot();require(bitmap!=null,"screenshot");File folder=new File(getTargetContext().getExternalFilesDir(null),"design-review/"+label);folder.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(folder,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
    private Object field(String name)throws Exception{Field f=DetailActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
    private Object get(String name){try{return field(name);}catch(Exception e){throw new AssertionError(e);}}
    private void set(String name,Object value){try{Field f=DetailActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,value);}catch(Exception e){throw new AssertionError(e);}}
    private void render(boolean preserve){try{Method m=DetailActivity.class.getDeclaredMethod("render",boolean.class);m.setAccessible(true);m.invoke(activity,preserve);}catch(Exception e){throw new AssertionError(e);}}
    private void settle()throws Exception{waitForIdleSync();Thread.sleep(450);waitForIdleSync();}
    private void require(boolean okay,String message){if(!okay)throw new AssertionError(message);checks++;}
    private void main(Runnable action){Throwable[] error={null};runOnMainSync(()->{try{action.run();}catch(Throwable t){error[0]=t;}});if(error[0]!=null)throw new AssertionError(error[0]);}
}
