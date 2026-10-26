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
        check(Arrays.asList(installed.requestedPermissions).contains("android.permission.FOREGROUND_SERVICE_DATA_SYNC"),"background update transfer declares data-sync service permission");
        android.content.pm.ProviderInfo provider=manager.getProviderInfo(new android.content.ComponentName(context,UpdateProvider.class),0);
        check(!provider.exported&&provider.grantUriPermissions,"installer provider is private and grants access only through explicit URIs");
        UpdateRepository updates=new UpdateRepository(context);java.util.Map<String,?> original=new java.util.HashMap<>(updates.preferences.getAll());
        try {
            updates.preferences.edit().clear().putLong("download_id",Long.MAX_VALUE).commit();
            rejected(updates::progress,"legacy system tasks return a retryable error without querying the stalled system provider");
            updates.preferences.edit().clear().putString("transfer_token","new-task").putInt("status",android.app.DownloadManager.STATUS_PENDING).putLong("bytes",0).commit();
            updates.changed("cancelled-task",android.app.DownloadManager.STATUS_SUCCESSFUL,100,"");
            check(updates.preferences.getInt("status",0)==android.app.DownloadManager.STATUS_PENDING&&updates.preferences.getLong("bytes",-1)==0,"late progress from a cancelled transfer cannot overwrite a new download");
            try{context.getContentResolver().openInputStream(android.net.Uri.parse("content://"+context.getPackageName()+".updates/apk/9.9.9"));throw new AssertionError("unverified APK was exposed");}catch(java.io.FileNotFoundException expected){check(true,"installer provider refuses files before validation");}
        } finally {
            android.content.SharedPreferences.Editor editor=updates.preferences.edit().clear();
            for(java.util.Map.Entry<String,?> entry:original.entrySet()){Object value=entry.getValue();if(value instanceof String)editor.putString(entry.getKey(),(String)value);else if(value instanceof Long)editor.putLong(entry.getKey(),(Long)value);else if(value instanceof Integer)editor.putInt(entry.getKey(),(Integer)value);else if(value instanceof Boolean)editor.putBoolean(entry.getKey(),(Boolean)value);}
            editor.commit();
        }
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
