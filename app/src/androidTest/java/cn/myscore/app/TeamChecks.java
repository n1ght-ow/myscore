package cn.myscore.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

/**
 * Emulator checks for the team-schedule feature: real sources, the recycled schedule page, and the
 * crest shortcut on a real score card. No synthetic match is written to app storage.
 */
final class TeamChecks {
    private static int checks;
    private static Instrumentation test;
    private static Context context;

    static int run(Instrumentation instrumentation,Context c)throws Exception {
        test=instrumentation;context=c;checks=0;
        wiring();
        Team arsenal=team("epl","359","Arsenal");
        TeamRepository repository=new TeamRepository(c);
        TeamRepository.Result epl=repository.fetch(arsenal,true);
        check(epl.error.isEmpty(),"live ESPN team schedule loads: "+epl.error);
        check(!epl.matches.isEmpty(),"live ESPN team schedule returns matches");
        check(epl.matches.stream().allMatch(m->"359".equals(m.homeId)||"359".equals(m.awayId)),"ESPN schedule contains only the requested team");
        check(epl.matches.stream().anyMatch(Match::finalised),"ESPN schedule includes completed matches");
        check(epl.matches.stream().anyMatch(Match::scheduled),"ESPN schedule includes upcoming matches");
        check(epl.source.contains("ESPN"),"team schedule names its source");

        Team spirit=team("cs2","csgo_tm_7020","Team Spirit");
        TeamRepository.Result cs2=fetchWithRetry(repository,spirit);
        check(cs2.error.isEmpty(),"live 5EPlay Spirit team schedule loads: "+cs2.error);
        check(cs2.matches.size()>=20,"5EPlay team schedule returns recent matches: "+cs2.matches.size());
        check(cs2.matches.stream().allMatch(m->"csgo_tm_7020".equals(m.homeId)||"csgo_tm_7020".equals(m.awayId)),"5EPlay schedule keeps the requested team identity");
        check(cs2.matches.stream().anyMatch(Match::finalised),"5EPlay team schedule includes completed matches");

        Match recent=cs2.matches.stream().filter(Match::finalised).findFirst().orElseThrow();
        String opponentId=recent.homeId.equals("csgo_tm_7020")?recent.awayId:recent.homeId;
        String opponentName=recent.homeId.equals("csgo_tm_7020")?recent.away:recent.home;
        Team opponent=team("cs2",opponentId,opponentName);
        TeamRepository.Result opponentSchedule=fetchWithRetry(repository,opponent);
        check(opponentSchedule.error.isEmpty(),"a CS2 opponent's schedule loads: "+opponentSchedule.error);
        check(!opponentSchedule.matches.isEmpty(),"a CS2 opponent's schedule returns matches");
        check(opponentSchedule.matches.stream().allMatch(m->opponent.id.equals(m.homeId)||opponent.id.equals(m.awayId)),"opponent schedule keeps the requested team identity");

        java.time.LocalDate day=Match.today(System.currentTimeMillis());
        List<Match> worlds=new Repository(c).fetch(day,day,"LOL",true).batches.stream().filter(b->b.competition.id.equals("lol")).findFirst().orElseThrow().matches;
        check(!worlds.isEmpty(),"Worlds feed returns matches for a team page");
        Match sample=worlds.stream().filter(m->!m.homeId.isBlank()).findFirst().orElseThrow();
        Team lol=team("lol",sample.homeId,sample.home);
        TeamRepository.Result lolSchedule=repository.fetch(lol,true);
        check(lolSchedule.error.isEmpty(),"live Worlds team schedule loads: "+lolSchedule.error);
        check(!lolSchedule.matches.isEmpty(),"Worlds team schedule returns the team's matches");
        check(lolSchedule.matches.stream().allMatch(m->lol.id.equals(m.homeId)||lol.id.equals(m.awayId)),"Worlds team schedule contains only the requested team");

        context.getSharedPreferences("settings",0).edit().putString("theme","dark").commit();
        Activity dark=open(arsenal);
        waitForResult(dark,30_000);
        checkText(dark,"球队赛程");
        checkText(dark,"Arsenal");
        View subscribe=findDescriptionStart(dark.getWindow().getDecorView(),"订阅对阵");
        check(subscribe!=null,"upcoming team fixtures expose a subscription action");
        check(subscribe.getWidth()>=new Ui(context,true).dp(48)-1&&subscribe.getHeight()>=new Ui(context,true).dp(48)-1,"icon-only subscription retains a 48dp touch target");
        check(find(dark.getWindow().getDecorView(),"订阅")==null,"team schedule has no visible subscribe label");
        check(find(dark.getWindow().getDecorView(),"提前 5 分钟提醒")==null,"team schedule omits repeated reminder copy");
        int before=new CalendarSync(context).linkedEvents();
        test.runOnMainSync(subscribe::performClick);
        long subscribeUntil=android.os.SystemClock.uptimeMillis()+10_000;
        while(new CalendarSync(context).linkedEvents()==before&&android.os.SystemClock.uptimeMillis()<subscribeUntil){Thread.sleep(80);test.waitForIdleSync();}
        check(new CalendarSync(context).linkedEvents()==before+1,"team schedule action creates a calendar subscription");
        test.waitForIdleSync();Thread.sleep(2400);test.waitForIdleSync();
        View cancel=findDescriptionStart(dark.getWindow().getDecorView(),"取消订阅对阵");
        check(cancel!=null,"team schedule reflects the subscribed state");
        capture(dark,"team-subscribed-dark");
        test.runOnMainSync(cancel::performClick);test.waitForIdleSync();
        test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);test.waitForIdleSync();
        check(new CalendarSync(context).linkedEvents()==before+1,"dismissing cancellation preserves the subscription");

        checkContains(dark,"ESPN · 球队赛程 · 共");
        check(findTeamCard(dark.getWindow().getDecorView())!=null,"schedule list renders source match cards");
        View finishedChip=findClickableStart(dark.getWindow().getDecorView(),"已结束");
        check(finishedChip!=null,"results filter is available when both fixtures and results exist");
        test.runOnMainSync(finishedChip::performClick);test.waitForIdleSync();Thread.sleep(350);test.waitForIdleSync();
        check(findSection(dark.getWindow().getDecorView(),"接下来 ·")==null,"results filter hides the upcoming section");
        check(findSection(dark.getWindow().getDecorView(),"已结束 ·")!=null,"results filter keeps completed matches");
        View allChip=findClickableStart(dark.getWindow().getDecorView(),"全部");
        check(allChip!=null,"mixed schedule filter is available");
        test.runOnMainSync(allChip::performClick);test.waitForIdleSync();Thread.sleep(350);test.waitForIdleSync();
        check(findSection(dark.getWindow().getDecorView(),"接下来 ·")!=null,"mixed schedule restores upcoming fixtures");
        capture(dark,"team-epl-dark");
        openFirstMatch(dark);
        test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);test.waitForIdleSync();Thread.sleep(400);test.waitForIdleSync();
        test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);test.waitForIdleSync();

        context.getSharedPreferences("settings",0).edit().putString("theme","light").commit();
        Activity light=open(arsenal);
        waitForResult(light,30_000);
        checkText(light,"Arsenal");
        capture(light,"team-epl-light");
        test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);test.waitForIdleSync();

        context.getSharedPreferences("settings",0).edit().putString("theme","dark").commit();
        Activity cs2Page=open(spirit);
        waitForResult(cs2Page,30_000);
        checkText(cs2Page,"Team Spirit");
        checkContains(cs2Page,"5EPlay");
        capture(cs2Page,"team-cs2-dark");
        test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);test.waitForIdleSync();

        Activity lolPage=open(lol);
        waitForResult(lolPage,30_000);
        checkText(lolPage,lol.name);
        checkContains(lolPage,"Worlds");
        capture(lolPage,"team-lol-dark");
        test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);test.waitForIdleSync();

        crestShortcut();
        return checks;
    }

    private static void wiring()throws Exception {
        test.runOnMainSync(()->{
            Ui ui=new Ui(context,true);
            final Match[] opened=new Match[1];final boolean[] home={false};
            ScoreAdapter adapter=new ScoreAdapter(ui,new ScoreAdapter.Host(){
                public void openMatch(Match m){}
                public void openTeam(Match m,boolean isHome){opened[0]=m;home[0]=isHome;}
                public void toggleSubscription(Match m){}
                public void retryScores(){}
                public void showSourceStatus(){}
                public boolean isSubscribed(String id){return false;}
            });
            Match m=new Match();m.id="espn:nba:team-test";m.league="nba";m.group="NBA";m.home="Lakers";m.away="Warriors";
            m.homeId="13";m.awayId="9";m.state="final";m.status="已结束";m.homeScore="110";m.awayScore="105";m.homeLogo="";m.awayLogo="";m.updated=System.currentTimeMillis();
            adapter.updateRows(List.of(ScoreRow.match(m)));
            View row=adapter.getView(0,null,null);
            int width=ui.dp(328);
            row.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
            row.layout(0,0,width,row.getMeasuredHeight());
            View crest=findDescription(row,"查看 Lakers 的赛程");
            check(crest!=null,"home crest is an explicit team shortcut");
            check(crest.getWidth()>=ui.dp(48)-1&&crest.getHeight()>=ui.dp(48)-1,"crest shortcut keeps a 48dp touch target");
            crest.performClick();
            check(opened[0]==m&&home[0],"home crest opens the home team schedule");
            View away=findDescription(row,"查看 Warriors 的赛程");check(away!=null,"away crest is an explicit team shortcut");
            away.performClick();check(opened[0]==m&&!home[0],"away crest opens the away team schedule");
            Match unknown=Match.fromJson(m.json());unknown.homeId="";
            adapter.updateRows(List.of(ScoreRow.match(unknown)));
            View card=adapter.getView(0,null,null);
            check(findDescription(card,"查看 Lakers 的赛程")==null,"a missing team id leaves the crest to the card action");
        });
    }

    private static TeamRepository.Result fetchWithRetry(TeamRepository repository,Team team)throws Exception {
        TeamRepository.Result result=repository.fetch(team,true);
        for(int attempt=0;attempt<2&&!result.error.isEmpty();attempt++){Thread.sleep(1500);result=repository.fetch(team,true);}
        return result;
    }
    private static Activity open(Team team)throws Exception {
        return test.startActivitySync(new Intent(context,TeamActivity.class).putExtra("team",team.json().toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
    private static void waitForResult(Activity activity,long timeout)throws Exception {
        long until=android.os.SystemClock.uptimeMillis()+timeout;
        while(field(activity)==null&&android.os.SystemClock.uptimeMillis()<until){Thread.sleep(80);test.waitForIdleSync();}
        test.waitForIdleSync();Thread.sleep(250);test.waitForIdleSync();
        check(field(activity)!=null,"team schedule data arrives on device");
    }
    private static Object field(Activity activity)throws Exception {
        java.lang.reflect.Field field=TeamActivity.class.getDeclaredField("result");field.setAccessible(true);return field.get(activity);
    }
    private static void checkText(Activity activity,String text){test.waitForIdleSync();check(find(activity.getWindow().getDecorView(),text)!=null,"team schedule shows: "+text);}
    private static void checkContains(Activity activity,String text){test.waitForIdleSync();check(findContaining(activity.getWindow().getDecorView(),text)!=null,"team schedule contains: "+text);}
    private static void openFirstMatch(Activity activity)throws Exception {
        View card=findTeamCard(activity.getWindow().getDecorView());
        check(card!=null,"a source match card is clickable");
        Instrumentation.ActivityMonitor monitor=test.addMonitor(DetailActivity.class.getName(),null,false);
        test.runOnMainSync(card::performClick);
        Activity detail=test.waitForMonitorWithTimeout(monitor,10_000);test.removeMonitor(monitor);
        check(detail!=null,"team schedule opens the selected match detail");
        long until=android.os.SystemClock.uptimeMillis()+20_000;
        while(find(detail.getWindow().getDecorView(),"总览")==null&&android.os.SystemClock.uptimeMillis()<until){Thread.sleep(100);test.waitForIdleSync();}
        check(find(detail.getWindow().getDecorView(),"总览")!=null,"match detail keeps its native tabs");
        capture(detail,"team-match-detail");
        View detailCrest=findDescriptionStart(detail.getWindow().getDecorView(),"查看 ");
        check(detailCrest!=null,"match detail exposes the same crest shortcut");
        if(detailCrest!=null) {
            Instrumentation.ActivityMonitor teamMonitor=test.addMonitor(TeamActivity.class.getName(),null,false);
            test.runOnMainSync(detailCrest::performClick);
            Activity opened=test.waitForMonitorWithTimeout(teamMonitor,10_000);test.removeMonitor(teamMonitor);
            check(opened!=null,"detail crest opens the opponent schedule");
            if(opened!=null){test.waitForIdleSync();check(find(opened.getWindow().getDecorView(),"球队赛程")!=null,"detail crest lands on the schedule page");test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);test.waitForIdleSync();}
        }
    }
    private static View findTeamCard(View root) {
        if(root.getContentDescription()!=null&&root.getContentDescription().toString().contains("对阵")&&root.isClickable())return root;
        if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){View found=findTeamCard(group.getChildAt(i));if(found!=null)return found;}}
        return null;
    }
    private static void crestShortcut()throws Exception {
        Activity main=test.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        test.waitForIdleSync();
        View crest=null;
        long until=android.os.SystemClock.uptimeMillis()+30_000;
        while(crest==null&&android.os.SystemClock.uptimeMillis()<until){Thread.sleep(200);test.waitForIdleSync();crest=findDescriptionStart(main.getWindow().getDecorView(),"查看 ");}
        check(crest!=null,"the score list exposes crest shortcuts");
        Instrumentation.ActivityMonitor monitor=test.addMonitor(TeamActivity.class.getName(),null,false);
        View target=crest;
        test.runOnMainSync(target::performClick);
        Activity opened=test.waitForMonitorWithTimeout(monitor,10_000);test.removeMonitor(monitor);
        check(opened!=null,"tapping a crest opens the team schedule page");
        if(opened!=null){test.waitForIdleSync();check(find(opened.getWindow().getDecorView(),"球队赛程")!=null,"crest shortcut lands on the schedule title");capture(opened,"crest-shortcut");test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);test.waitForIdleSync();}
    }
    private static View find(View root,String text){if(root instanceof TextView&&text.contentEquals(((TextView)root).getText()))return root;if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),text);if(found!=null)return found;}}return null;}
    private static View findContaining(View root,String text){if(root instanceof TextView&&((TextView)root).getText().toString().contains(text))return root;if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){View found=findContaining(group.getChildAt(i),text);if(found!=null)return found;}}return null;}
    private static View findDescription(View root,String text){if(root==null)return null;CharSequence d=root.getContentDescription();if(d!=null&&text.contentEquals(d)&&root.isClickable())return root;if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){View found=findDescription(group.getChildAt(i),text);if(found!=null)return found;}}return null;}
    private static View findDescriptionStart(View root,String text){if(root==null)return null;CharSequence d=root.getContentDescription();if(d!=null&&d.toString().startsWith(text)&&root.isClickable())return root;if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){View found=findDescriptionStart(group.getChildAt(i),text);if(found!=null)return found;}}return null;}
    private static View findClickableStart(View root,String text){if(root instanceof TextView&&((TextView)root).getText().toString().startsWith(text)&&root.isClickable())return root;if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){View found=findClickableStart(group.getChildAt(i),text);if(found!=null)return found;}}return null;}
    private static View findSection(View root,String text){if(root instanceof TextView&&!root.isClickable()&&((TextView)root).getText().toString().startsWith(text))return root;if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){View found=findSection(group.getChildAt(i),text);if(found!=null)return found;}}return null;}
    private static void capture(Activity activity,String name)throws Exception {
        test.waitForIdleSync();Thread.sleep(500);
        Bitmap bitmap=test.getUiAutomation().takeScreenshot();check(bitmap!=null,"native team capture "+name);
        File dir=new File(context.getExternalFilesDir(null),"review-team-1.1");dir.mkdirs();
        try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
    }
    private static Team team(String league,String id,String name){Team t=new Team();t.league=league;t.id=id;t.name=name;return t;}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;android.util.Log.i("MyScoreTeamTests","PASS "+message);}
}
