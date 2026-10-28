package cn.myscore.app;

import android.app.Activity;
import android.app.Dialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

/** Native route review; existing source caches only, no calendar writes or synthetic disk data. */
public final class UiAuditInstrumentation extends Instrumentation {
    private int checks;private String label="audit";private final List<String> evidence=new ArrayList<>();
    @Override public void onCreate(Bundle args){super.onCreate(args);if(args!=null)label=args.getString("label","audit");start();}
    @Override public void runOnMainSync(Runnable action){Throwable[] failure={null};super.runOnMainSync(()->{try{action.run();}catch(Throwable e){failure[0]=e;}});if(failure[0]!=null)throw new AssertionError(failure[0]);}
    @Override public void onStart(){Bundle report=new Bundle();int outcome=Activity.RESULT_OK;try{
        checks+=AlignmentChecks.run(this,getTargetContext());
        Activity main=open(MainActivity.class,new Intent(getTargetContext(),MainActivity.class));settle();
        capture(main.getWindow(),"schedule");review(main);
        runOnMainSync(()->call(main,"select",new Class<?>[]{int.class,String.class,int.class},1,"全部",(int)get(main,"dayOffset")));settle();
        require(find(main.getWindow().getDecorView(),"接收日历").size()==1,"settings calendar action appears once");
        require(find(main.getWindow().getDecorView(),"已订阅的比赛").size()==1,"settings subscriptions action appears once");capture(main.getWindow(),"settings");review(main);
        sheet(main,"date",()->Sheet.date(main,Match.today(System.currentTimeMillis()),date->{}));
        sheet(main,"choice",()->Sheet.choose(main,"外观",null,List.of(new Sheet.Item("深色","",true),new Sheet.Item("浅色","",false),new Sheet.Item("跟随系统","",false)),0,index->{}));
        sheet(main,"confirmation",()->Sheet.confirm(main,"取消订阅","这条确认不会执行日历写入。","取消订阅",()->{}));
        Map<String,MatchDetail> samples=samples();
        for(MatchDetail detail:samples.values()){
            Activity page=open(DetailActivity.class,new Intent(main,DetailActivity.class).putExtra("match",detail.match.json().toString()));
            runOnMainSync(()->{set(page,"active",false);((Handler)get(page,"handler")).removeCallbacksAndMessages(null);set(page,"match",detail.match);set(page,"detail",detail);});
            List<String> tabs=detail.match.group.equals("CS2")?List.of("overview","lineup","events"):detail.match.group.equals("LOL")||detail.match.group.equals("F1")?List.of("overview","lineup","stats"):List.of("overview","lineup","stats","events");
            for(String tab:tabs){runOnMainSync(()->{set(page,"tab",tab);set(page,"rendered","");call(page,"render",new Class<?>[]{boolean.class},false);});settle();review(page);capture(page.getWindow(),detail.match.group+"-"+tab);
                if(tab.equals("lineup")&&detail.match.group.equals("足球")){View player=page.getWindow().getDecorView().findViewWithTag("football-pitch-player");if(player!=null)sheet(page,"足球-player",player::performClick);}
                if(tab.equals("lineup")){View table=page.getWindow().getDecorView().findViewWithTag("detail-data-table");if(table instanceof ViewGroup g&&g.getChildAt(0) instanceof ViewGroup names&&names.getChildCount()>2){View person=names.getChildAt(2);sheet(page,detail.match.group+"-player",person::performClick);}}
            }
            runOnMainSync(page::finish);settle();current=main;
            Team team=Team.of(detail.match,true);if(team!=null){Activity teamPage=open(TeamActivity.class,new Intent(main,TeamActivity.class).putExtra("team",team.json().toString()));settle();review(teamPage);capture(teamPage.getWindow(),detail.match.group+"-team");runOnMainSync(teamPage::finish);settle();current=main;}
        }
        Activity update=open(UpdateActivity.class,new Intent(main,UpdateActivity.class));settle();review(update);capture(update.getWindow(),"updater");runOnMainSync(update::finish);
        File folder=folder();java.nio.file.Files.write(new File(folder,"coverage.txt").toPath(),("config="+main.getResources().getConfiguration()+"\n"+String.join("\n",evidence)).getBytes(StandardCharsets.UTF_8));
        report.putString("stream","\nPASS: "+checks+" native UI audit checks; "+evidence.size()+" captured routes.\n"+String.join("\n",evidence)+"\n");
    }catch(Throwable e){outcome=Activity.RESULT_CANCELED;report.putString("stream","\nFAIL after "+checks+": "+android.util.Log.getStackTraceString(e)+"\nCaptured: "+evidence+"\n");}finish(outcome,report);}
    private Map<String,MatchDetail> samples()throws Exception{
        Map<String,MatchDetail> best=new LinkedHashMap<>();File[] files=new File(getTargetContext().getCacheDir(),"details-v4").listFiles();if(files!=null)for(File file:files){if(!file.getName().endsWith(".json"))continue;try{MatchDetail raw=MatchDetail.fromJson(new JSONObject(new String(java.nio.file.Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8)));MatchDetail detail=new DetailRepository(getTargetContext()).cached(raw.match);if(detail==null)continue;MatchDetail previous=best.get(detail.match.group);if(previous==null||quality(detail)>quality(previous))best.put(detail.match.group,detail);}catch(Exception ignored){}}
        if(!best.containsKey("F1")){
            java.time.LocalDate raceDay=Match.today(System.currentTimeMillis()).minusDays(4);
            Repository.Snapshot race=new Repository(getTargetContext()).fetch(raceDay,raceDay,"F1",false);
            for(Match m:race.matches())if(m.group.equals("F1")){try{best.put("F1",new DetailRepository(getTargetContext()).fetch(m,false));break;}catch(Exception ignored){}}
        }
        require(best.keySet().containsAll(List.of("足球","NBA","NFL","CS2","LOL")),"real caches cover the five populated sports");return best;
    }
    private int quality(MatchDetail d){return d.tables.stream().mapToInt(t->t.rows.size()).sum()*10+d.events.size()+d.stats.size()+d.lolGames.size()*100;}
    private Activity open(Class<? extends Activity> type,Intent intent)throws Exception{
        ActivityMonitor monitor=addMonitor(type.getName(),null,false);
        if(type==MainActivity.class){try(android.os.ParcelFileDescriptor fd=getUiAutomation().executeShellCommand("am start -W -n cn.myscore.app/.MainActivity -f 0x10008000");java.io.FileInputStream in=new java.io.FileInputStream(fd.getFileDescriptor())){in.readAllBytes();}}
        else {Activity foreground=foreground();runOnMainSync(()->foreground.startActivity(intent));}
        Activity page=waitForMonitorWithTimeout(monitor,10000);removeMonitor(monitor);require(page!=null,"route opens "+type.getSimpleName());current=page;settle();return page;
    }
    private Activity current;private Activity foreground(){return current;}
    private void sheet(Activity page,String name,Runnable open)throws Exception{
        current=page;runOnMainSync(open);settle();Field f=Sheet.class.getDeclaredField("OPEN");f.setAccessible(true);@SuppressWarnings("unchecked") Map<Activity,List<Dialog>> all=(Map<Activity,List<Dialog>>)f.get(null);List<Dialog> dialogs=all.get(page);require(dialogs!=null&&!dialogs.isEmpty(),"sheet opens "+name);Dialog dialog=dialogs.get(dialogs.size()-1);runOnMainSync(()->inspect(dialog.getWindow().getDecorView()));capture(dialog.getWindow(),name);runOnMainSync(dialog::dismiss);settle();
    }
    private void review(Activity page){current=page;runOnMainSync(()->inspect(page.getWindow().getDecorView()));}
    private void inspect(View view){if(view.getVisibility()!=View.VISIBLE)return;if(view instanceof TextView text&&text.length()>0&&text.getWidth()>0){android.text.Layout l=text.getLayout();require(l!=null&&l.getHeight()<=text.getHeight()-text.getCompoundPaddingTop()-text.getCompoundPaddingBottom()+1,"text height: "+text.getText());for(int i=0;i<l.getLineCount();i++)require(l.getEllipsisCount(i)==0&&l.getLineMax(i)<=l.getWidth()+1,"text width: "+text.getText());}if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)inspect(group.getChildAt(i));}
    private void capture(Window window,String name)throws Exception{
        settle();View decor=window.getDecorView();Bitmap bitmap=Bitmap.createBitmap(decor.getWidth(),decor.getHeight(),Bitmap.Config.ARGB_8888);CountDownLatch done=new CountDownLatch(1);int[] result={-1};runOnMainSync(()->PixelCopy.request(window,bitmap,value->{result[0]=value;done.countDown();},new Handler(Looper.getMainLooper())));require(done.await(5,TimeUnit.SECONDS)&&result[0]==PixelCopy.SUCCESS,"native window capture "+name);try(FileOutputStream out=new FileOutputStream(new File(folder(),name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();evidence.add(name);
    }
    private File folder(){File folder=new File(getTargetContext().getExternalFilesDir(null),"ui-audit/"+label);folder.mkdirs();return folder;}
    private void settle()throws Exception{waitForIdleSync();Thread.sleep(650);waitForIdleSync();}
    private static Object get(Activity page,String name){try{Field f=page.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(page);}catch(Exception e){throw new AssertionError(e);}}
    private static void set(Activity page,String name,Object value){try{Field f=page.getClass().getDeclaredField(name);f.setAccessible(true);f.set(page,value);}catch(Exception e){throw new AssertionError(e);}}
    private static void call(Activity page,String name,Class<?>[] types,Object... args){try{Method m=page.getClass().getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(page,args);}catch(Exception e){throw new AssertionError(e);}}
    private static List<View> find(View v,String text){List<View> found=new ArrayList<>();if(v instanceof TextView t&&text.contentEquals(t.getText()))found.add(v);if(v instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++)found.addAll(find(g.getChildAt(i),text));return found;}
    private void require(boolean okay,String description){if(!okay)throw new AssertionError(description);checks++;}
}
