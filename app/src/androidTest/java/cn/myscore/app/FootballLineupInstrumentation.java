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

/** Emulator-only review with real cached football data, including menu and layout boundaries. */
public final class FootballLineupInstrumentation extends Instrumentation {
    private Activity activity;private int checks;private String label="phone";private boolean matrix=true;
    @Override public void onCreate(Bundle args){super.onCreate(args);if(args!=null){label=args.getString("label","phone");matrix=!"false".equals(args.getString("matrix"));}start();}
    @Override public void onStart(){
        Bundle report=new Bundle();int outcome=Activity.RESULT_OK;Context context=getTargetContext();
        String old=context.getSharedPreferences("settings",0).getString("theme","dark");
        try{
            require(android.os.Build.HARDWARE.equals("ranchu")||android.os.Build.HARDWARE.equals("goldfish"),"emulator only");
            Match requested=new Match();requested.id="espn:epl:401879256:401879256";requested.group="足球";requested.league="epl";
            MatchDetail cached=new DetailRepository(context).cached(requested);require(cached!=null,"real cached football detail");
            // Refresh through the production parser to validate real per-player fields as well as cache migration.
            MatchDetail real=new DetailRepository(context).fetch(cached.match,false);
            for(String theme:List.of("dark","light")){
                context.getSharedPreferences("settings",0).edit().putString("theme",theme).commit();
                activity=startActivitySync(new Intent(context,DetailActivity.class).putExtra("match",real.match.json().toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
                waitForIdleSync();for(int i=0;i<80&&field("detail")==null;i++)Thread.sleep(100);
                require(field("detail")!=null,"detail loads");
                main(()->{set("tab","lineup");render(false);});
                require(findTag("football-pitch")!=null,"eleven-player pitch");
                require(findAll(findTag("football-pitch"),"football-pitch-player").size()==11,"numbered starter tokens");
                require(noImages(findTag("football-pitch")),"formation renders without photos");
                require(findTag("football-event-legend")!=null,"event legend");
                main(()->((ScrollView)get("scroll")).scrollTo(0,new Ui(activity,theme.equals("dark")).dp(200)));settle();
                capture(theme+"-formation");
                String away=real.match.awayId;
                main(()->{ViewGroup filter=(ViewGroup)findTag("football-team-filter");filter.getChildAt(1).performClick();});settle();
                require(field("teamFilter").equals(away),"team switch applies");capture(theme+"-away");
                main(()->{((ScrollView)get("scroll")).scrollTo(0,new Ui(activity,theme.equals("dark")).dp(420));});settle();capture(theme+"-pitch");
                main(()->{View player=firstClickable(findTag("football-pitch"));require(player!=null,"player target");player.performClick();});settle();capture(theme+"-player");sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);settle();
                if(matrix)layoutMatrix(real,theme.equals("dark"));
            }
            report.putString("stream","\nPASS: "+checks+" football native checks.\n");
        }catch(Throwable e){outcome=Activity.RESULT_CANCELED;report.putString("stream","\nFAIL after "+checks+": "+e.toString()+"\n"+android.util.Log.getStackTraceString(e));}
        finally{context.getSharedPreferences("settings",0).edit().putString("theme",old).commit();}
        finish(outcome,report);
    }
    private void layoutMatrix(MatchDetail detail,boolean dark){
        for(int width:new int[]{320,375,640})for(float scale:new float[]{1f,1.3f,2f})main(()->{
            Configuration config=new Configuration(activity.getResources().getConfiguration());config.fontScale=scale;config.screenWidthDp=width;
            Context c=activity.createConfigurationContext(config);Ui ui=new Ui(c,dark);LinearLayout body=ui.column();body.setPadding(ui.dp(16),0,ui.dp(16),0);
            new FootballLineupView(ui,detail,body).show(detail.match.homeId,id->{});
            body.measure(View.MeasureSpec.makeMeasureSpec(ui.dp(width),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));body.layout(0,0,body.getMeasuredWidth(),body.getMeasuredHeight());
            inspect(body);require(body.findViewWithTag("football-event-legend")!=null,"event legend in layout matrix");
        });
    }
    private void inspect(View v){
        if(v.getVisibility()!=View.VISIBLE)return;
        if(v instanceof TextView text&&text.length()>0&&text.getWidth()>0){android.text.Layout layout=text.getLayout();require(layout!=null,"native text layout");require(layout.getHeight()<=text.getHeight()-text.getCompoundPaddingTop()-text.getCompoundPaddingBottom(),"text height: "+text.getText());for(int i=0;i<layout.getLineCount();i++)require(layout.getEllipsisCount(i)==0&&layout.getLineMax(i)<=layout.getWidth()+1,"text width: "+text.getText());}
        if(v instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View child=group.getChildAt(i);if(!(group instanceof HorizontalScrollView))require(child.getLeft()>=0&&child.getRight()<=group.getWidth()+1,"child stays in parent: "+child.getClass().getSimpleName());inspect(child);}
    }
    private void capture(String name)throws Exception{settle();main(()->inspect(activity.getWindow().getDecorView()));Bitmap bitmap=getUiAutomation().takeScreenshot();require(bitmap!=null,"screenshot");File folder=new File(getTargetContext().getExternalFilesDir(null),"football-review/"+label);folder.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(folder,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
    private View firstClickable(View view){if(view.isClickable())return view;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=firstClickable(group.getChildAt(i));if(found!=null)return found;}return null;}
    private List<View> findAll(View view,String tag){List<View> result=new ArrayList<>();if(tag.equals(view.getTag()))result.add(view);if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)result.addAll(findAll(group.getChildAt(i),tag));return result;}
    private boolean noImages(View view){if(view instanceof android.widget.ImageView)return false;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)if(!noImages(group.getChildAt(i)))return false;return true;}
    private View findTag(String tag){return activity.getWindow().getDecorView().findViewWithTag(tag);}
    private Object field(String name)throws Exception{Field f=DetailActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
    private Object get(String name){try{return field(name);}catch(Exception e){throw new AssertionError(e);}}
    private void set(String name,Object value){try{Field f=DetailActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,value);}catch(Exception e){throw new AssertionError(e);}}
    private void render(boolean preserve){try{Method m=DetailActivity.class.getDeclaredMethod("render",boolean.class);m.setAccessible(true);m.invoke(activity,preserve);}catch(Exception e){throw new AssertionError(e);}}
    private void settle()throws Exception{waitForIdleSync();Thread.sleep(400);waitForIdleSync();}
    private void require(boolean okay,String message){if(!okay)throw new AssertionError(message);checks++;}
    private void main(Runnable action){Throwable[] error={null};runOnMainSync(()->{try{action.run();}catch(Throwable t){error[0]=t;}});if(error[0]!=null)throw new AssertionError(error[0]);}
}
