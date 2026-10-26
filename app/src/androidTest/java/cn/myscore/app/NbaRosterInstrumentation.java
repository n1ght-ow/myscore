package cn.myscore.app;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Real cached NBA rows: two stacked team panels, injuries at each panel's foot, sorting and text fit. */
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
                open(real);
                click("球员");settle();
                main(()->checkPanels(real));
                main(()->((ScrollView)get("scroll")).scrollTo(0,0));settle();capture(theme+"-panels");
                main(()->checkRoles(activity.getWindow().getDecorView(),real.match));
                main(()->scrollTo(all(activity.getWindow().getDecorView(),"team-panel-injury-header").get(1)));capture(theme+"-injuries");
                main(()->all(activity.getWindow().getDecorView(),"team-panel-injury").get(0).performClick());settle();
                android.view.accessibility.AccessibilityNodeInfo sheet=getUiAutomation().getRootInActiveWindow();
                require(sheet!=null&&!sheet.findAccessibilityNodeInfosByText("球队").isEmpty(),"injury sheet names the team");
                require(!sheet.findAccessibilityNodeInfosByText("状态").isEmpty(),"injury sheet names the status");
                require(!sheet.findAccessibilityNodeInfosByText("每日观察").isEmpty(),"injury status is localized");
                capture(theme+"-injury-sheet");sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);settle();
                main(()->scrollTo(all(activity.getWindow().getDecorView(),"team-panel-header").get(1)));settle();
                main(()->{View table=all(activity.getWindow().getDecorView(),"detail-data-table").get(0);clickIn(table,"时间");});settle();
                require(field("sortColumn").equals("时间"),"time sort remains interactive");
                require((boolean)field("descending"),"new sort column starts descending");
                main(()->checkFirstRows(real,"时间",true));
                main(()->{View table=all(activity.getWindow().getDecorView(),"detail-data-table").get(0);clickIn(table,"时间");});settle();
                require(!(boolean)field("descending"),"same column toggles ascending");
                main(()->checkFirstRows(real,"时间",false));
                main(()->{View table=all(activity.getWindow().getDecorView(),"detail-data-table").get(0);HorizontalScrollView metrics=(HorizontalScrollView)table.findViewWithTag("detail-metrics-scroll");int[] before=new int[2];((ViewGroup)table).getChildAt(0).getLocationOnScreen(before);metrics.scrollTo(new Ui(activity,Ui.isDark(activity)).dp(120),0);});settle();
                require((int)field("metricsX")>0,"numeric columns remain horizontally scrollable");
                main(()->{List<View> tables=all(activity.getWindow().getDecorView(),"detail-data-table");View first=tables.get(0),second=tables.get(1);HorizontalScrollView a=(HorizontalScrollView)first.findViewWithTag("detail-metrics-scroll"),b=(HorizontalScrollView)second.findViewWithTag("detail-metrics-scroll");require(Math.abs(a.getScrollX()-b.getScrollX())<=1,"both team tables scroll together");require(a.getScrollX()>0,"numeric rail moved");});
                main(()->{View table=all(activity.getWindow().getDecorView(),"detail-data-table").get(0);HorizontalScrollView metrics=(HorizontalScrollView)table.findViewWithTag("detail-metrics-scroll");int[] before=new int[2];((ViewGroup)table).getChildAt(0).getLocationOnScreen(before);metrics.scrollTo(0,0);int[] after=new int[2];((ViewGroup)table).getChildAt(0).getLocationOnScreen(after);require(before[0]==after[0],"NBA names remain frozen while numeric columns scroll");});settle();
                main(()->{set("sortColumn","得分");set("descending",true);set("metricsX",0);render(true);((ScrollView)get("scroll")).scrollTo(0,new Ui(activity,Ui.isDark(activity)).dp(360));});settle();capture(theme+"-table");
                main(()->{View row=all(activity.getWindow().getDecorView(),"nba-player-row").get(0);row.performClick();});settle();
                require(!getUiAutomation().getRootInActiveWindow().findAccessibilityNodeInfosByText("球衣号").isEmpty(),"player detail retains jersey");capture(theme+"-player");sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);settle();
                if(matrix)panelMatrix(real,theme.equals("dark"));
            }
            report.putString("stream","\nPASS: "+checks+" NBA two-panel native checks ("+label+").\n");
        }catch(Throwable e){outcome=Activity.RESULT_CANCELED;report.putString("stream","\nFAIL after "+checks+": "+e+"\n"+android.util.Log.getStackTraceString(e));}
        finally{context.getSharedPreferences("settings",0).edit().putString("theme",old).commit();}
        finish(outcome,report);
    }
    private void open(MatchDetail real)throws Exception{
        activity=startActivitySync(new Intent(getTargetContext(),DetailActivity.class).putExtra("match",real.match.json().toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));settle();
        for(int i=0;i<80&&field("detail")==null;i++){Thread.sleep(100);waitForIdleSync();}require(field("detail")!=null,"detail loaded");
    }
    private void checkPanels(MatchDetail real){
        View root=activity.getWindow().getDecorView();
        require(root.findViewWithTag("detail-team-filter")==null,"team switch row removed");
        require(findExact(root,"双方")==null,"no all-teams switch");
        View injuryHeading=findExact(root,"伤停");require(injuryHeading==null||!injuryHeading.isClickable(),"injuries are a section, not a switch");
        List<View> headers=all(root,"team-panel-header");require(headers.size()==2,"exactly two team panels");
        require(headers.get(0).getContentDescription()!=null&&headers.get(0).getContentDescription().toString().contains(real.match.home),"home panel first");
        require(headers.get(1).getContentDescription()!=null&&headers.get(1).getContentDescription().toString().contains(real.match.away),"away panel second");
        List<View> sections=all(root,"team-panel");require(sections.size()==2,"two panel containers");
        require(headers.get(0).getParent()==sections.get(0)&&headers.get(1).getParent()==sections.get(1),"headers own their panels");
        List<View> tables=all(root,"detail-data-table");require(tables.size()==2,"two team tables");
        List<MatchDetail.Table> homeTables=real.tables.stream().filter(t->t.category.equals("球员")&&t.teamId.equals(real.match.homeId)).toList();
        List<MatchDetail.Table> awayTables=real.tables.stream().filter(t->t.category.equals("球员")&&t.teamId.equals(real.match.awayId)).toList();
        require(rows(homeTables)==all(tables.get(0),"nba-player-row").size(),"home panel lists every home player");
        require(rows(awayTables)==all(tables.get(1),"nba-player-row").size(),"away panel lists every away player");
        List<View> injuryRows=all(root,"team-panel-injury");List<MatchDetail.Table> injuries=real.tables.stream().filter(t->t.category.equals("伤停")).toList();
        require(injuryRows.size()==rows(injuries),"all source injury rows render");
        String injuredTeam=injuries.isEmpty()?"":injuries.get(0).teamId;ViewGroup injuredSection=(ViewGroup)(injuredTeam.equals(real.match.homeId)?sections.get(0):sections.get(1)),otherSection=(ViewGroup)(injuredSection==sections.get(0)?sections.get(1):sections.get(0));
        for(View row:injuryRows)require(isInside(row,injuredSection),"injury row belongs to the team it describes");
        require(findExact(otherSection,"来源未提供伤停名单")!=null,"missing injury report stays explicit");
        List<View> injuryHeaders=all(root,"team-panel-injury-header");require(injuryHeaders.size()==2,"each panel owns an injury block");
        View injuredHeader=isInside(injuryHeaders.get(0),injuredSection)?injuryHeaders.get(0):injuryHeaders.get(1);
        int tableIndex=injuredSection.indexOfChild(injuredTeam.equals(real.match.homeId)?tables.get(0):tables.get(1)),headerIndex=injuredSection.indexOfChild(injuredHeader);
        require(tableIndex>=0&&headerIndex>tableIndex,"injuries sit below the player table");
        View firstInjury=injuryRows.get(0);require(injuredSection.indexOfChild(firstInjury)>headerIndex,"first injury row follows its heading");
        require(injuredHeader.getContentDescription()!=null&&injuredHeader.getContentDescription().toString().contains(rows(injuries)+" 人"),"injury count matches rows");
    }
    private void checkFirstRows(MatchDetail real,String column,boolean descending){
        List<View> tables=all(activity.getWindow().getDecorView(),"detail-data-table");
        for(int side=0;side<2;side++){
            String teamId=side==0?real.match.homeId:real.match.awayId;
            List<MatchDetail.Table> teamTables=real.tables.stream().filter(t->t.category.equals("球员")&&t.teamId.equals(teamId)).toList();
            List<DetailRows.Entry> ordered=DetailRows.select(teamTables,"球员",teamId,column,descending);
            String expected=ordered.isEmpty()?"":name(ordered.get(0));
            List<View> names=all(tables.get(side),"nba-player-name");require(!names.isEmpty(),"panel renders player names");
            require(((TextView)names.get(0)).getText().toString().equals(expected),"panel "+side+" follows shared "+column+(descending?" descending":" ascending")+" sort");
        }
    }
    private void checkRoles(View root,Match match){
        List<View> rows=all(root,"nba-player-row");require(!rows.isEmpty(),"NBA identity rows");boolean starter=false,bench=false;
        for(View row:rows){
            TextView status=row.findViewWithTag("nba-player-role"),meta=row.findViewWithTag("nba-player-meta");require(status!=null&&meta!=null,"role badge and metadata");
            String text=status.getText().toString();starter|=text.equals("首发");bench|=text.equals("替补");require(List.of("首发","替补","未出场").contains(text),"explicit playing role");
            require(!meta.getText().toString().contains(match.home)&&!meta.getText().toString().contains(match.away),"identity metadata omits team name");
            FrameLayout portrait=row.findViewWithTag("nba-player-portrait");require(portrait!=null&&portrait.getChildCount()==2,"headshot retains team crest");
        }
        require(starter&&bench,"starters and bench are both explicitly labeled");
    }
    private void panelMatrix(MatchDetail real,boolean dark){
        for(int width:new int[]{320,375,640})for(float scale:new float[]{1f,1.3f,2f})main(()->{
            Configuration config=new Configuration(activity.getResources().getConfiguration());config.fontScale=scale;config.screenWidthDp=width;
            Ui ui=new Ui(activity.createConfigurationContext(config),dark);
            LinearLayout body=ui.column();body.setPadding(ui.dp(16),ui.dp(12),ui.dp(16),ui.dp(24));
            new TeamPanelsView(ui,real,body).show("得分",true,s->{},0,x->{});
            body.measure(View.MeasureSpec.makeMeasureSpec(ui.dp(width),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
            body.layout(0,0,body.getMeasuredWidth(),body.getMeasuredHeight());inspect(body);
            require(all(body,"team-panel-header").size()==2,"matrix keeps two panels");
            require(all(body,"team-panel-injury").size()==rows(real.tables.stream().filter(t->t.category.equals("伤停")).toList()),"matrix keeps injury rows");
        });
    }
    private void inspect(View view){
        if(view.getVisibility()!=View.VISIBLE)return;
        if(view instanceof TextView text&&text.length()>0&&text.getWidth()>0){android.text.Layout layout=text.getLayout();require(layout!=null,"native text layout");require(layout.getHeight()<=text.getHeight()-text.getCompoundPaddingTop()-text.getCompoundPaddingBottom(),"text height: "+text.getText());for(int i=0;i<layout.getLineCount();i++)require(layout.getEllipsisCount(i)==0&&layout.getLineMax(i)<=layout.getWidth()+1,"text width: "+text.getText());}
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View child=group.getChildAt(i);if(!(group instanceof HorizontalScrollView))require(child.getLeft()>=0&&child.getRight()<=group.getWidth()+1,"child fits parent");inspect(child);}
    }
    private String name(DetailRows.Entry entry){MatchDetail.Person p=entry.person();if(p==null)return entry.values().get(0);String shortName=p.shortName().matches("^\\p{L}\\..*|^[A-Z]{2}\\..*")?p.shortName():DetailFormat.shortName(p.name());return shortName;}
    private boolean isInside(View child,View ancestor){for(View view=child;view!=null;view=view.getParent() instanceof View?(View)view.getParent():null)if(view==ancestor)return true;return false;}
    private int rows(List<MatchDetail.Table> tables){int count=0;for(MatchDetail.Table table:tables)count+=table.rows.size();return count;}
    private void click(String text){main(()->{View view=findExact(activity.getWindow().getDecorView(),text);require(view!=null,"control "+text);while(!view.isClickable()&&view.getParent() instanceof View)view=(View)view.getParent();view.performClick();});}
    private void clickIn(View root,String text){View view=findExact(root,text);require(view!=null,"control "+text);while(!view.isClickable()&&view.getParent() instanceof View)view=(View)view.getParent();view.performClick();}
    private List<View> all(View view,String tag){List<View> result=new ArrayList<>();if(tag.equals(view.getTag()))result.add(view);if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)result.addAll(all(group.getChildAt(i),tag));return result;}
    private View findExact(View view,String text){if(view instanceof TextView t&&text.contentEquals(t.getText()))return view;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=findExact(group.getChildAt(i),text);if(found!=null)return found;}return null;}
    private void scrollTo(View target){Rect bounds=new Rect();target.getDrawingRect(bounds);ScrollView scroll=(ScrollView)get("scroll");scroll.offsetDescendantRectToMyCoords(target,bounds);scroll.scrollTo(0,Math.max(0,bounds.top-new Ui(activity,Ui.isDark(activity)).dp(64)));}
    private void capture(String name)throws Exception{settle();main(()->inspect(activity.getWindow().getDecorView()));Bitmap bitmap=getUiAutomation().takeScreenshot();require(bitmap!=null,"screenshot");File dir=new File(getTargetContext().getExternalFilesDir(null),"nba-roster-review/"+label);dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
    private void require(boolean okay,String why){if(!okay)throw new AssertionError(why);checks++;}
    private Object field(String name)throws Exception{Field f=DetailActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
    private Object get(String name){try{return field(name);}catch(Exception e){throw new AssertionError(e);}}
    private void set(String name,Object value){try{Field f=DetailActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,value);}catch(Exception e){throw new AssertionError(e);}}
    private void render(boolean preserve){try{Method method=DetailActivity.class.getDeclaredMethod("render",boolean.class);method.setAccessible(true);method.invoke(activity,preserve);}catch(Exception e){throw new AssertionError(e);}}
    private void main(Runnable action){Throwable[] error={null};runOnMainSync(()->{try{action.run();}catch(Throwable t){error[0]=t;}});if(error[0]!=null)throw new AssertionError(error[0]);}
    private void settle()throws Exception{waitForIdleSync();Thread.sleep(350);waitForIdleSync();}
}
