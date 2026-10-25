package cn.myscore.app;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.Intent;
import android.content.res.Configuration;
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
    @Override public void onDestroy(){operation++;handler.removeCallbacksAndMessages(null);worker.shutdownNow();super.onDestroy();}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)Ui.highRefresh(getWindow());}
    private void build(){
        LinearLayout root=ui.column();setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)->{if(android.os.Build.VERSION.SDK_INT>=30){android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(b.left+ui.pageInset(),b.top,b.right+ui.pageInset(),b.bottom);}else v.setPadding(insets.getSystemWindowInsetLeft()+ui.pageInset(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight()+ui.pageInset(),insets.getSystemWindowInsetBottom());return insets;});root.requestApplyInsets();
        LinearLayout header=ui.row();header.setPadding(ui.dp(4),ui.dp(4),ui.dp(20),ui.dp(4));FrameLayout back=new FrameLayout(this);back.addView(new Ui.Icon(this,"back",ui.text),new FrameLayout.LayoutParams(ui.dp(22),ui.dp(22),Gravity.CENTER));ui.clickable(back,android.graphics.Color.TRANSPARENT,Ui.CONTROL_RADIUS);back.setContentDescription("返回设置");back.setFocusable(true);back.setOnClickListener(v->finish());header.addView(back,new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));header.addView(ui.text("软件更新",18,ui.text,true));root.addView(header);root.addView(ui.divider());
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);LinearLayout body=ui.column();body.setPadding(ui.dp(24),ui.dp(32),ui.dp(24),ui.dp(28));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout product=ui.row();Ui.Icon mark=new Ui.Icon(this,"mark",ui.text);LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(ui.dp(32),ui.dp(32));mp.setMarginEnd(ui.dp(12));product.addView(mark,mp);product.addView(ui.text("MyScore",24,ui.text,true));body.addView(product);ui.gap(body,12);body.addView(ui.text("当前版本 "+BuildConfig.VERSION_NAME,13,ui.muted,false));ui.gap(body,32);
        headline=ui.text("",20,ui.text,true);body.addView(headline);ui.gap(body,10);description=ui.text("",14,ui.muted,false);description.setLineSpacing(ui.dp(3),1);body.addView(description);ui.gap(body,20);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setProgressTintList(android.content.res.ColorStateList.valueOf(ui.primary));progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(ui.tonal));progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(ui.primary));body.addView(progress,new LinearLayout.LayoutParams(-1,ui.dp(4)));ui.gap(body,8);progressText=ui.text("",12,ui.muted,false);body.addView(progressText);ui.gap(body,16);
        action=ui.button("检查更新",true,this::act);body.addView(action,new LinearLayout.LayoutParams(-1,-2));ui.gap(body,10);cancel=ui.button("取消下载",false,this::cancelDownload);body.addView(cancel,new LinearLayout.LayoutParams(-1,-2));ui.gap(body,24);
        notes=ui.text("",14,ui.muted,false);notes.setLineSpacing(ui.dp(4),1);body.addView(notes);ui.gap(body,24);
        TextView link=ui.button("打开发布页",false,()->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(UpdateRelease.REPOSITORY+"/releases/latest")));}catch(android.content.ActivityNotFoundException e){Sheet.message(this,"无法打开发布页","手机没有可用的浏览器，请稍后重试应用内检查。");}});body.addView(link,new LinearLayout.LayoutParams(-1,-2));ui.gap(body,16);body.addView(ui.text("覆盖安装保留设置与日历关联，无需卸载。",12,ui.muted,false));
    }
    private <T> void run(Callable<T> task,Consumer<T> complete){
        long ticket=++operation;working=true;
        worker.submit(()->{try{T result=task.call();handler.post(()->{if(isDestroyed()||ticket!=operation)return;working=false;complete.accept(result);});}catch(Exception e){handler.post(()->{if(isDestroyed()||ticket!=operation)return;working=false;state=State.ERROR;error=e instanceof java.io.IOException&&e.getMessage()!=null?e.getMessage():"操作未完成，请检查网络和系统下载服务后重试";render();});}});
    }
    private void check(boolean restore){
        state=State.CHECKING;release=null;render();
        run(()->{UpdateRelease pending=restore?repository.pending():null;if(pending!=null&&pending.newerThan(BuildConfig.VERSION_NAME)&&repository.downloadId()!=0)return pending;repository.clear();return repository.latest();},result->{release=result;try{state=result.newerThan(BuildConfig.VERSION_NAME)?repository.downloadId()!=0?State.DOWNLOADING:State.AVAILABLE:State.CURRENT;}catch(java.io.IOException e){state=State.ERROR;error="无法比较版本，请在发布页核对";}render();if(state==State.DOWNLOADING)poll();});
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
            Sheet.confirm(this,"允许安装更新","请在系统设置中允许 MyScore 安装应用，返回后再点「安装更新」。","前往设置",()->{try{startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())));}catch(android.content.ActivityNotFoundException e){Sheet.message(this,"无法打开设置","请在手机系统设置中允许 MyScore 安装应用。");}});return;
        }
        state=State.VERIFYING;render();run(()->repository.verify(release),uri->{state=State.READY;render();try{Intent install=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);install.setClipData(ClipData.newRawUri("MyScore update",uri));startActivity(install);}catch(android.content.ActivityNotFoundException|SecurityException e){Sheet.message(this,"未能打开安装界面","请确认系统安装器可用，并允许 MyScore 安装应用后重试。");}});
    }
    private void render(){
        if(headline==null)return;boolean busy=state==State.CHECKING||state==State.VERIFYING||state==State.DOWNLOADING;
        progress.setVisibility(busy?View.VISIBLE:View.GONE);progress.setIndeterminate(state!=State.DOWNLOADING);progress.setProgress(percent);progressText.setVisibility(state==State.DOWNLOADING?View.VISIBLE:View.GONE);progressText.setText(waiting?"等待网络或系统下载调度…":"已下载 "+percent+"%");cancel.setVisibility(state==State.DOWNLOADING?View.VISIBLE:View.GONE);action.setEnabled(!busy);action.setAlpha(busy?0.5f:1f);
        notes.setVisibility(release!=null&&state!=State.CURRENT&&!release.notes.isBlank()?View.VISIBLE:View.GONE);notes.setText(release==null?"":"更新内容\n\n"+release.summary());
        switch(state){
            case CHECKING->{headline.setText(release==null?"正在检查更新":"正在准备下载");description.setText("连接 MyScore 发布渠道…");action.setText("请稍候");}
            case CURRENT->{headline.setText("当前已是最新版本");description.setText("已发布的最新版本 "+release.version);action.setText("重新检查");}
            case AVAILABLE->{headline.setText("发现新版本 "+release.version);description.setText(String.format(Locale.CHINA,"安装包 %.1f MB · 覆盖更新保留数据",release.size/1048576d));action.setText("下载更新");}
            case DOWNLOADING->{headline.setText("正在下载 "+release.version);description.setText("可以离开此页面，系统会继续下载。");action.setText("下载中");}
            case VERIFYING->{headline.setText("正在校验安装包");description.setText("核对文件完整性与应用签名…");action.setText("正在校验");}
            case READY->{headline.setText("更新已准备好");description.setText("版本 "+release.version+" · 点安装更新，在系统界面确认覆盖安装。");action.setText("安装更新");}
            case ERROR->{headline.setText("暂时无法更新");description.setText(error);action.setText(release==null?"重新检查":"重新下载");}
        }
    }
}
