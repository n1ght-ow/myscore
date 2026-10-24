package cn.myscore.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.io.IOException;
import java.util.Arrays;

/** Uses the real installed signing information; no mock version is rendered in the UI. */
final class UpdateChecks {
    private static int checks;
    static int run(Instrumentation test,Context context)throws Exception {
        checks=0;PackageManager manager=context.getPackageManager();PackageInfo installed=manager.getPackageInfo(context.getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES|PackageManager.GET_PERMISSIONS);
        check(Arrays.asList(installed.requestedPermissions).contains("android.permission.REQUEST_INSTALL_PACKAGES"),"self-update install permission declared");
        PackageInfo previous=new PackageInfo();previous.packageName=installed.packageName;previous.versionCode=(int)installed.getLongVersionCode()-1;previous.signingInfo=installed.signingInfo;
        UpdateRepository.validateArchive(installed,previous,installed.versionName);check(true,"real signed APK is accepted over an older installed version");
        rejected(()->UpdateRepository.validateArchive(installed,installed,installed.versionName),"same version is not an upgrade");
        rejected(()->UpdateRepository.validateArchive(installed,previous,"0.0.0"),"archive version must match release metadata");
        PackageInfo wrong=new PackageInfo();wrong.packageName="other.application";wrong.versionName=installed.versionName;wrong.versionCode=(int)installed.getLongVersionCode();wrong.signingInfo=installed.signingInfo;
        rejected(()->UpdateRepository.validateArchive(wrong,previous,installed.versionName),"foreign package is refused");
        wrong.packageName=installed.packageName;wrong.signingInfo=null;rejected(()->UpdateRepository.validateArchive(wrong,previous,installed.versionName),"unsigned archive is refused");
        Activity activity=test.startActivitySync(new Intent(context,UpdateActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));test.waitForIdleSync();
        check(find(activity.getWindow().getDecorView(),"软件更新"),"native updater page opens");check(find(activity.getWindow().getDecorView(),"当前版本 "+BuildConfig.VERSION_NAME),"updater shows installed version");
        test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);test.waitForIdleSync();return checks;
    }
    private interface Attempt {void run()throws Exception;}
    private static void rejected(Attempt attempt,String message)throws Exception{try{attempt.run();throw new AssertionError(message);}catch(IOException expected){check(true,message);}}
    private static void check(boolean result,String text){if(!result)throw new AssertionError(text);checks++;android.util.Log.i("MyScoreUpdateTests","PASS "+text);}
    private static boolean find(View root,String text){if(root instanceof TextView&&text.contentEquals(((TextView)root).getText()))return true;if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++)if(find(group.getChildAt(i),text))return true;}return false;}
}
