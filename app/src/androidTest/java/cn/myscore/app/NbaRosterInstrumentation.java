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

/** Real cached NBA rows: visible identity, role badges, scrolling, sorting and text alignment. */
public final class NbaRosterInstrumentation extends Instrumentation {
    private Activity activity;private int checks;private String label="phone";private boolean matrix=true;
    @Override public void onCreate(Bundle args){super.onCreate(args);if(args!=null){label=args.getString("label","phone");matrix=!"false".equals(args.getString("matrix"));}start();}
    @Override public void onStart(){
        Bundle report=new Bundle();int outcome=Activity.RESULT_OK;Context context=getTargetContext();String old=context.getSharedPreferences("settings",0).getString("theme","dark");
        try{
            require(android.os.Build.HARDWARE.equals("ranchu")||android.os.Build.HARDWARE.equals("goldfish"),"emulator only");
            Match request=new Match();request.id="espn:nba:401909848:401909848";request.group="NBA";request.league="nba";
            MatchDetail real=new DetailRepository(context).cached(request);require(real!=null,"real cached NBA detail");
            for(String theme:List.of("dark","light")){
                context.getSharedPreferences("settings",0).edit().putString("theme",theme).commit();
                activity=startActivitySync(new Intent(context,DetailActivity.class).putExtra("match",real.match.json().toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));settle();
                for(int i=0;i<80&&field("detail")==null;i++){Thread.sleep(100);waitForIdleSync();}require(field("detail")!=null,"detail loaded");
                click("球员");settle();main(()->{set("playerLimit",400);render(false);});settle();
                main(()->checkRoles(activity.getWindow().getDecorView(),real.match));
                main(()->((ScrollView)get("scroll")).scrollTo(0,new Ui(activity,theme.equals("dark")).dp(300)));capture(theme+"-roles");
                main(()->{ViewGroup filter=activity.getWindow().getDecorView().findViewWithTag("detail-team-filter");filter.getChildAt(1).performClick();});settle();
                require(field("teamFilter").equals(real.match.homeId),"team filter keeps exact ID");
                main(()->{set("teamFilter","all");set("playerLimit",400);render(false);});settle();
                View table=activity.getWindow().getDecorView().findViewWithTag("detail-data-table");
                main(()->{View column=find(table,"时间");while(!column.isClickable()&&column.getParent() instanceof View)column=(View)column.getParent();column.performClick();});settle();require(field("sortColumn").equals("时间"),"time sort remains interactive");
                main(()->{HorizontalScrollView metrics=activity.getWindow().getDecorView().findViewWithTag("detail-metrics-scroll");metrics.scrollTo(new Ui(activity,theme.equals("dark")).dp(120),0);});settle();require((int)field("metricsX")>0,"numeric columns remain horizontally scrollable");
                main(()->{set("sortColumn","得分");set("metricsX",0);set("playerLimit",400);render(false);((ScrollView)get("scroll")).scrollTo(0,new Ui(activity,theme.equals("dark")).dp(400));});settle();capture(theme+"-table");
                main(()->{View row=all(activity.getWindow().getDecorView(),"nba-player-row").get(0);row.performClick();});settle();
                String sheet=getUiAutomation().getRootInActiveWindow().getText()==null?"":getUiAutomation().getRootInActiveWindow().getText().toString();
                require(!getUiAutomation().getRootInActiveWindow().findAccessibilityNodeInfosByText("球衣号").isEmpty(),"player detail retains jersey");capture(theme+"-player");sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);settle();
                if(matrix)layoutMatrix(real,theme.equals("dark"));
            }
            report.putString("stream","\nPASS: "+checks+" NBA roster native checks ("+label+").\n");
        }catch(Throwable e){outcome=Activity.RESULT_CANCELED;report.putString("stream","\nFAIL after "+checks+": "+e+"\n"+android.util.Log.getStackTraceString(e));}
        finally{context.getSharedPreferences("settings",0).edit().putString("theme",old).commit();}
        finish(outcome,report);
    }
    private void checkRoles(View root,Match match){
        List<View> rows=all(root,"nba-player-row");require(!rows.isEmpty(),"NBA identity rows");boolean starter=false,bench=false;
        for(View row:rows){
            TextView status=row.findViewWithTag("nba-player-role"),meta=row.findViewWithTag("nba-player-meta");String text=status.getText().toString();starter|=text.equals("首发");bench|=text.equals("替补");require(List.of("首发","替补","未出场").contains(text),"explicit playing role");
            require(!meta.getText().toString().contains(match.home)&&!meta.getText().toString().contains(match.away),"identity metadata omits team name");
            FrameLayout portrait=row.findViewWithTag("nba-player-portrait");require(portrait!=null&&portrait.getChildCount()==2,"headshot retains team crest");
        }
        require(starter&&bench,"starters and bench are both explicitly labeled");
    }
    private void layoutMatrix(MatchDetail real,boolean dark){
        List<MatchDetail.Table> tables=real.tables.stream().filter(t->t.category.equals("球员")).toList();List<DetailRows.Entry> entries=DetailRows.select(tables,"球员","all","得分",true);
        for(int width:new int[]{320,375,640})for(float scale:new float[]{1f,1.3f,2f})main(()->{
            Configuration config=new Configuration(activity.getResources().getConfiguration());config.fontScale=scale;config.screenWidthDp=width;Ui ui=new Ui(activity.createConfigurationContext(config),dark);
            View table=new DetailTableView(ui,true).build(tables.get(0).columns,entries,"得分",true,null,null,0);table.measure(View.MeasureSpec.makeMeasureSpec(ui.dp(width-32),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));table.layout(0,0,table.getMeasuredWidth(),table.getMeasuredHeight());inspect(table);checkRoles(table,real.match);
            LinearLayout names=(LinearLayout)((ViewGroup)table).getChildAt(0);ViewGroup numbers=(ViewGroup)((HorizontalScrollView)((ViewGroup)table).getChildAt(2)).getChildAt(0);
            for(int i=0;i<names.getChildCount();i++)require(names.getChildAt(i).getHeight()==numbers.getChildAt(i).getHeight(),"identity and numeric heights align");
        });
    }
    private void inspect(View view){
        if(view.getVisibility()!=View.VISIBLE)return;
        if(view instanceof TextView text&&text.length()>0&&text.getWidth()>0){android.text.Layout layout=text.getLayout();require(layout!=null,"native text layout");require(layout.getHeight()<=text.getHeight()-text.getCompoundPaddingTop()-text.getCompoundPaddingBottom(),"text height: "+text.getText());for(int i=0;i<layout.getLineCount();i++)require(layout.getEllipsisCount(i)==0&&layout.getLineMax(i)<=layout.getWidth()+1,"text width: "+text.getText());}
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View child=group.getChildAt(i);if(!(group instanceof HorizontalScrollView))require(child.getLeft()>=0&&child.getRight()<=group.getWidth()+1,"child fits parent");inspect(child);}
    }
    private List<View> all(View view,String tag){List<View> result=new ArrayList<>();if(tag.equals(view.getTag()))result.add(view);if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)result.addAll(all(group.getChildAt(i),tag));return result;}
    private View find(View view,String text){if(view instanceof TextView t&&text.contentEquals(t.getText()))return view;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),text);if(found!=null)return found;}return null;}
    private void click(String text){main(()->{View view=find(activity.getWindow().getDecorView(),text);require(view!=null,"control "+text);while(!view.isClickable()&&view.getParent() instanceof View)view=(View)view.getParent();view.performClick();});}
    private void capture(String name)throws Exception{settle();main(()->inspect(activity.getWindow().getDecorView()));Bitmap bitmap=getUiAutomation().takeScreenshot();require(bitmap!=null,"screenshot");File dir=new File(getTargetContext().getExternalFilesDir(null),"nba-roster-review/"+label);dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
    private void require(boolean okay,String why){if(!okay)throw new AssertionError(why);checks++;}
    private Object field(String name)throws Exception{Field f=DetailActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
    private Object get(String name){try{return field(name);}catch(Exception e){throw new AssertionError(e);}}
    private void set(String name,Object value){try{Field f=DetailActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,value);}catch(Exception e){throw new AssertionError(e);}}
    private void render(boolean preserve){try{Method method=DetailActivity.class.getDeclaredMethod("render",boolean.class);method.setAccessible(true);method.invoke(activity,preserve);}catch(Exception e){throw new AssertionError(e);}}
    private void main(Runnable action){Throwable[] error={null};runOnMainSync(()->{try{action.run();}catch(Throwable t){error[0]=t;}});if(error[0]!=null)throw new AssertionError(error[0]);}
    private void settle()throws Exception{waitForIdleSync();Thread.sleep(350);waitForIdleSync();}
}
