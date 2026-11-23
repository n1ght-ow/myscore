package cn.myscore.app;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** User-initiated check, persistent background download, explicit system install. */
public final class UpdateActivity extends Activity {
    private enum State { CHECKING,CURRENT,AVAILABLE,DOWNLOADING,VERIFYING,READY,ERROR }
    private Ui ui;private UpdateRepository repository;private UpdateRelease release;
    private TextView headline,description,notes,action,cancel,progressText;private ProgressBar progress;
    private LinearLayout notesCard;
    private State state=State.CHECKING;private String error="";private boolean active,working;
    private long operation;private int percent;private boolean waiting;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Runnable pollAgain=this::poll;
    @Override public void onCreate(Bundle saved){
        boolean dark=Ui.isDark(this);
        setTheme(dark?R.style.AppThemeDark:R.style.AppTheme);super.onCreate(saved);ui=new Ui(this,dark);repository=new UpdateRepository(this);
        getWindow().setStatusBarColor(ui.bg);getWindow().setNavigationBarColor(ui.bg);getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(ui.bg));if(android.os.Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        build();check(true);
    }
    @Override public void onResume(){super.onResume();active=true;if(state==State.DOWNLOADING)poll();}
    @Override public void onPause(){active=false;handler.removeCallbacks(pollAgain);super.onPause();}
    @Override public void onDestroy(){active=false;Sheet.dismissAll(this);operation++;handler.removeCallbacksAndMessages(null);worker.shutdownNow();super.onDestroy();}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)Ui.highRefresh(getWindow());}
    private void build(){
        LinearLayout root=ui.column();setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)->{if(android.os.Build.VERSION.SDK_INT>=30){android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(b.left+ui.pageInset(),b.top,b.right+ui.pageInset(),b.bottom);}else v.setPadding(insets.getSystemWindowInsetLeft()+ui.pageInset(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight()+ui.pageInset(),insets.getSystemWindowInsetBottom());return insets;});root.requestApplyInsets();
        LinearLayout header=ui.row();header.setMinimumHeight(ui.dp(48));header.setPadding(ui.dp(4),ui.dp(2),ui.dp(16),ui.dp(2));
        FrameLayout back=new FrameLayout(this);back.addView(new Ui.Icon(this,"back",ui.text),new FrameLayout.LayoutParams(ui.dp(22),ui.dp(22),Gravity.CENTER));
        ui.clickable(back,android.graphics.Color.TRANSPARENT,Ui.CONTROL_RADIUS);back.setContentDescription("Back to settings");back.setFocusable(true);back.setOnClickListener(v->finish());
        header.addView(back,new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));header.addView(ui.text("Update",17,ui.text,true));
        root.addView(header);root.addView(ui.divider());
        boolean isPad=Ui.isPad(this);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body=ui.column();body.setPadding(ui.dp(16),ui.dp(18),ui.dp(16),ui.dp(24));
        if(isPad){int side=Math.max(ui.dp(24),(getResources().getDisplayMetrics().widthPixels-ui.dp(560))/2);body.setPadding(side,ui.dp(28),side,ui.dp(28));}
        scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout mainCard=ui.column();mainCard.setBackground(ui.panel());mainCard.setPadding(ui.dp(20),ui.dp(20),ui.dp(20),ui.dp(20));
        LinearLayout product=ui.row();
        Ui.Icon mark=new Ui.Icon(this,"mark",ui.text);LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(ui.dp(28),ui.dp(28));mp.setMarginEnd(ui.dp(10));product.addView(mark,mp);
        product.addView(ui.text("N1ghtScore",20,ui.text,true),new LinearLayout.LayoutParams(0,-2,1));
        TextView version=ui.text("Version "+BuildConfig.VERSION_NAME,12,ui.muted,false);
        version.setBackground(ui.shape(ui.tonal,12));version.setPadding(ui.dp(10),ui.dp(4),ui.dp(10),ui.dp(4));
        LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(-2,-2);vp.setMarginStart(ui.dp(8));product.addView(version,vp);
        mainCard.addView(product);ui.gap(mainCard,14);mainCard.addView(ui.divider());ui.gap(mainCard,16);

        headline=ui.text("",18,ui.text,true);mainCard.addView(headline);ui.gap(mainCard,6);
        description=ui.text("",13,ui.muted,false);description.setLineSpacing(ui.dp(3),1);mainCard.addView(description);ui.gap(mainCard,16);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(ui.primary));
        progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(ui.tonal));
        progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(ui.primary));
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,ui.dp(6));pp.bottomMargin=ui.dp(8);mainCard.addView(progress,pp);
        progressText=ui.text("",12,ui.muted,false);
        LinearLayout.LayoutParams ptp=new LinearLayout.LayoutParams(-1,-2);ptp.bottomMargin=ui.dp(14);mainCard.addView(progressText,ptp);

        LinearLayout actions=isPad?ui.row():ui.column();
        action=ui.button("Check for updates",true,this::act);
        cancel=ui.button("Cancel download",false,this::cancelDownload);
        if(isPad){
            action.setMinWidth(ui.dp(180));cancel.setMinWidth(ui.dp(140));
            actions.addView(action,new LinearLayout.LayoutParams(-2,-2));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,-2);cp.setMarginStart(ui.dp(10));actions.addView(cancel,cp);
        }else{
            actions.addView(action,new LinearLayout.LayoutParams(-1,-2));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=ui.dp(10);actions.addView(cancel,cp);
        }
        mainCard.addView(actions);
        body.addView(mainCard,new LinearLayout.LayoutParams(-1,-2));
        ui.gap(body,12);

        notesCard=ui.column();notesCard.setBackground(ui.panel());notesCard.setPadding(ui.dp(18),ui.dp(16),ui.dp(18),ui.dp(16));
        notes=ui.text("",14,ui.muted,false);notes.setLineSpacing(ui.dp(4),1);notesCard.addView(notes);
        LinearLayout.LayoutParams ncp=new LinearLayout.LayoutParams(-1,-2);ncp.bottomMargin=ui.dp(12);
        body.addView(notesCard,ncp);

        TextView link=ui.button("Open release page",false,()->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(UpdateRelease.REPOSITORY+"/releases/latest")));}catch(android.content.ActivityNotFoundException e){Sheet.message(this,"Couldn't open release page","No browser is available. Try the in-app update check again later.");}});
        if(isPad){link.setMinWidth(ui.dp(180));body.addView(link,new LinearLayout.LayoutParams(-2,-2));}
        else body.addView(link,new LinearLayout.LayoutParams(-1,-2));
        ui.gap(body,12);body.addView(ui.text("Installing over the current app keeps settings and calendar links. No uninstall needed.",12,ui.muted,false));
    }
    private <T> void run(Callable<T> task,Consumer<T> complete){
        long ticket=++operation;working=true;
        worker.submit(()->{try{T result=task.call();handler.post(()->{if(isDestroyed()||ticket!=operation)return;working=false;complete.accept(result);});}catch(Exception e){handler.post(()->{if(isDestroyed()||ticket!=operation)return;working=false;state=State.ERROR;error=e instanceof java.io.IOException&&e.getMessage()!=null?e.getMessage():"Action didn't finish. Check your network and system download service, then retry.";render();});}});
    }
    private void check(boolean restore){
        state=State.CHECKING;release=null;render();
        run(()->{UpdateRelease pending=restore?repository.pending():null;if(pending!=null&&pending.newerThan(BuildConfig.VERSION_NAME)&&repository.downloadId()!=0)return pending;repository.clear();return repository.latest();},result->{release=result;try{state=result.newerThan(BuildConfig.VERSION_NAME)?repository.downloadId()!=0?State.DOWNLOADING:State.AVAILABLE:State.CURRENT;}catch(java.io.IOException e){state=State.ERROR;error="Couldn't compare versions. Check the release page.";}render();if(state==State.DOWNLOADING)poll();});
    }
    private void act(){
        if(working||state==State.DOWNLOADING||state==State.VERIFYING)return;
        if(state==State.READY){install();return;}
        if(state==State.AVAILABLE||state==State.ERROR&&release!=null){startDownload();return;}
        check(false);
    }
    private void startDownload(){state=State.CHECKING;render();UpdateRelease selected=release;run(()->repository.start(selected),id->{state=State.DOWNLOADING;percent=0;waiting=false;render();poll();});}
    private void cancelDownload(){handler.removeCallbacks(pollAgain);state=State.CHECKING;render();run(()->{repository.clear();return true;},ignored->{state=State.AVAILABLE;render();});}
    private void poll(){
        if(!active||working||state!=State.DOWNLOADING)return;
        run(repository::progress,value->{
            if(value.status()==DownloadManager.STATUS_SUCCESSFUL){state=State.VERIFYING;render();run(()->repository.verify(release),uri->{state=State.READY;render();});return;}
            waiting=value.status()==DownloadManager.STATUS_PAUSED||value.status()==DownloadManager.STATUS_PENDING;
            long total=value.total()>0?value.total():release.size;percent=(int)Math.min(100,Math.max(0,value.downloaded()*100/Math.max(1,total)));render();if(active)handler.postDelayed(pollAgain,1000);
        });
    }
    private void install(){
        if(!getPackageManager().canRequestPackageInstalls()){
            Sheet.confirm(this,"Allow app installs","Allow N1ghtScore to install apps in system settings, then return and tap “Install update”.","Open settings",()->{try{startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())));}catch(android.content.ActivityNotFoundException e){Sheet.message(this,"Couldn't open settings","Allow N1ghtScore to install apps in your device settings.");}});return;
        }
        state=State.VERIFYING;render();run(()->repository.verify(release),uri->{state=State.READY;render();try{Intent install=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);install.setClipData(ClipData.newRawUri("N1ghtScore update",uri));startActivity(install);}catch(android.content.ActivityNotFoundException|SecurityException e){Sheet.message(this,"Couldn't open installer","Make sure the system installer is available and N1ghtScore is allowed to install apps, then retry.");}});
    }
    private void render(){
        if(headline==null)return;boolean busy=state==State.CHECKING||state==State.VERIFYING||state==State.DOWNLOADING;
        progress.setVisibility(busy?View.VISIBLE:View.GONE);progress.setIndeterminate(state!=State.DOWNLOADING);progress.setProgress(percent);progressText.setVisibility(state==State.DOWNLOADING?View.VISIBLE:View.GONE);progressText.setText(waiting?"Connecting to the download server…":"Downloaded "+percent+"%");cancel.setVisibility(state==State.DOWNLOADING?View.VISIBLE:View.GONE);action.setEnabled(!busy);action.setAlpha(busy?0.5f:1f);
        String summary=release==null?"":release.summary();
        if(EnglishDisplay.hasHan(summary))summary="The original notes are on the release page.";
        notes.setVisibility(state!=State.CURRENT&&!summary.isBlank()?View.VISIBLE:View.GONE);
        notes.setText(summary.isBlank()?"":"What's new\n\n"+summary);
        if(notesCard!=null)notesCard.setVisibility(notes.getVisibility());
        switch(state){
            case CHECKING->{headline.setText(release==null?"Checking for updates":"Preparing download");description.setText("Connecting to N1ghtScore releases…");action.setText("Please wait");}
            case CURRENT->{headline.setText("You're up to date");description.setText("No newer release is available.");action.setText("Check again");}
            case AVAILABLE->{headline.setText("Version "+release.version+" is available");description.setText(String.format(Locale.US,"Download %.1f MB · Installing over the current app keeps data",release.size/1048576d));action.setText("Download update");}
            case DOWNLOADING->{headline.setText("Downloading "+release.version);description.setText("You can leave this screen. N1ghtScore will keep downloading.");action.setText("Downloading");}
            case VERIFYING->{headline.setText("Verifying update");description.setText("Checking file integrity and app signature…");action.setText("Verifying");}
            case READY->{headline.setText("Update ready");description.setText("Version "+release.version+" · Tap Install update, then confirm the over-install in the system dialog.");action.setText("Install update");}
            case ERROR->{headline.setText("Update unavailable right now");description.setText(error);action.setText(release==null?"Check again":"Download again");}
        }
    }
}
