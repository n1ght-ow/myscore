package cn.myscore.app;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.function.Consumer;

/** Source-backed pitch, bench and availability; no invented ratings or locations. */
final class FootballLineupView {
    private final Ui ui;private final MatchDetail detail;private final LinearLayout body;
    FootballLineupView(Ui ui,MatchDetail detail,LinearLayout body){this.ui=ui;this.detail=detail;this.body=body;}
    void show(String selected,Consumer<String> changed){
        Match match=detail.match;boolean home=!selected.equals(match.awayId);String teamId=home?match.homeId:match.awayId;
        LinearLayout selector=ui.row();selector.setTag("football-team-filter");
        String[] ids={match.homeId,match.awayId},names={match.home,match.away},logos={match.homeLogo,match.awayLogo};
        for(int i=0;i<2;i++){String id=ids[i];boolean on=i==0?home:!home;LinearLayout choice=ui.row();choice.setGravity(Gravity.CENTER);choice.setPadding(ui.dp(8),ui.dp(10),ui.dp(8),ui.dp(10));choice.setMinimumHeight(ui.dp(48));ui.clickable(choice,on?ui.tonal:ui.surface,Ui.CONTROL_RADIUS);choice.addView(Logos.view(ui,logos[i],names[i],22));TextView name=ui.text(names[i],12,on?ui.text:ui.muted,on);name.setPadding(ui.dp(8),0,0,0);choice.addView(name,new LinearLayout.LayoutParams(0,-2,1));choice.setContentDescription(names[i]+(on?"，已选中":"，查看阵容"));choice.setSelected(on);choice.setFocusable(true);choice.setOnClickListener(v->changed.accept(id));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);if(i>0)p.setMarginStart(ui.dp(8));selector.addView(choice,p);}body.addView(selector);ui.gap(body,20);
        String team=home?match.home:match.away;String formation=detail.facts.stream().filter(f->f.label().equals(team+" 阵型")).map(MatchDetail.Fact::value).findFirst().orElse("");
        LinearLayout title=ui.row();title.addView(ui.text("首发阵容",16,ui.text,true),new LinearLayout.LayoutParams(0,-2,1));title.addView(ui.text(formation.isBlank()?"阵型未提供":formation,16,ui.text,true));body.addView(title);ui.gap(body,10);
        MatchDetail.Table starters=find(teamId,"首发");
        List<List<DetailRows.Entry>> lines=starters==null?List.of():FootballLayout.lines(starters,formation);
        if(lines.isEmpty()){
            body.addView(ui.text("完整首发站位尚未提供",13,ui.muted,false));if(starters!=null)list(starters);
        }else{
            LinearLayout pitch=new LinearLayout(ui.c){final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);@Override protected void onDraw(Canvas canvas){super.onDraw(canvas);ink.setColor(ui.secondary);ink.setAlpha(80);ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(ui.dp(1));float w=getWidth(),h=getHeight(),m=ui.dp(6);canvas.drawRect(m,m,w-m,h-m,ink);canvas.drawRect(w*.23f,m,w*.77f,h*.16f,ink);canvas.drawRect(w*.37f,m,w*.63f,h*.06f,ink);canvas.drawLine(m,h-m,w-m,h-m,ink);canvas.drawCircle(w/2,h-m,w*.16f,ink);}};
            pitch.setOrientation(LinearLayout.VERTICAL);pitch.setWillNotDraw(false);pitch.setBackground(ui.shape(ui.surface,Ui.CARD_RADIUS));pitch.setClipToOutline(true);pitch.setTag("football-pitch");pitch.setPadding(ui.dp(8),ui.dp(8),ui.dp(8),ui.dp(24));
            float font=ui.c.getResources().getConfiguration().fontScale;
            for(List<DetailRows.Entry> line:lines){
                if(font>=1.5f){ // Large type keeps names and touch targets readable in an ordered list.
                    for(DetailRows.Entry player:line)pitch.addView(listPlayer(player));
                }else{LinearLayout row=ui.row();row.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);row.setPadding(0,ui.dp(12),0,ui.dp(12));for(DetailRows.Entry player:line)row.addView(pitchPlayer(player),new LinearLayout.LayoutParams(0,-2,1));pitch.addView(row);}
            }body.addView(pitch,new LinearLayout.LayoutParams(-1,-2));
            ui.gap(body,8);body.addView(ui.text(font>=1.5f?"按首发阵型分行 · 点球员查看详情":"首发阵型示意，非平均站位 · 点球员查看详情",11,ui.muted,false));
        }
        heading("替补");MatchDetail.Table bench=find(teamId,"替补");if(bench==null)body.addView(ui.text("替补名单尚未提供",13,ui.muted,false));else list(bench);
        MatchDetail.Table injuries=find(teamId,"伤停");if(injuries!=null){heading("受伤与停赛");list(injuries);}
        ui.gap(body,16);body.addView(ui.text("球员照片由 ESPN 提供；缺失时显示姓名缩写。",11,ui.muted,false));
    }
    private MatchDetail.Table find(String team,String category){return detail.tables.stream().filter(t->t.tab.equals("lineup")&&t.teamId.equals(team)&&t.category.equals(category)).findFirst().orElse(null);}
    private void heading(String title){ui.gap(body,24);body.addView(ui.text(title,16,ui.text,true));ui.gap(body,12);}
    private void list(MatchDetail.Table table){LinearLayout list=ui.column();list.setPadding(ui.dp(14),0,ui.dp(14),0);list.setBackground(ui.panel());for(int i=0;i<table.rows.size();i++){if(i>0)list.addView(ui.divider());DetailRows.Entry entry=new DetailRows.Entry(table,table.rows.get(i),i<table.people.size()?table.people.get(i):null);list.addView(listPlayer(entry));}body.addView(list);}
    private View portrait(MatchDetail.Person person,int size){String photo=DetailParsers.soccerPhoto(person.photo(),person.id());FrameLayout view=Logos.artwork(ui,photo,person.name(),size);view.setBackground(ui.shape(ui.tonal,size));view.setClipToOutline(true);return view;}
    private View pitchPlayer(DetailRows.Entry entry){
        MatchDetail.Person p=entry.person();LinearLayout player=ui.column();player.setGravity(Gravity.CENTER);player.setPadding(ui.dp(2),0,ui.dp(2),0);FrameLayout image=new FrameLayout(ui.c);image.addView(portrait(p,38),new FrameLayout.LayoutParams(ui.dp(38),ui.dp(38),Gravity.CENTER));TextView number=ui.text(p.jersey().isBlank()?"—":p.jersey(),11,ui.onAction,true);number.setGravity(Gravity.CENTER);number.setMinWidth(ui.dp(20));number.setPadding(ui.dp(3),0,ui.dp(3),0);number.setBackground(ui.shape(ui.action,4));image.addView(number,new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL));player.addView(image,new LinearLayout.LayoutParams(-1,ui.dp(52)));TextView name=ui.text(DetailFormat.shortName(p.name()),11,ui.text,true);name.setGravity(Gravity.CENTER);player.addView(name,new LinearLayout.LayoutParams(-1,-2));String marks=marks(entry);if(FootballLayout.value(entry,"状态").equals("已换下"))marks=marks.isBlank()?"已换下":marks+" · 已换下";if(!marks.isBlank()){TextView status=ui.text(marks,10,marks.contains("红牌")?ui.error:ui.muted,true);status.setGravity(Gravity.CENTER);player.addView(status,new LinearLayout.LayoutParams(-1,-2));}click(player,entry);return player;
    }
    private View listPlayer(DetailRows.Entry entry){
        LinearLayout row=ui.row();row.setPadding(0,ui.dp(12),0,ui.dp(12));MatchDetail.Person p=entry.person();
        if(p!=null){row.addView(portrait(p,40),new LinearLayout.LayoutParams(ui.dp(40),ui.dp(40)));}
        LinearLayout words=ui.column();words.setPadding(ui.dp(12),0,0,0);words.addView(ui.text((p==null?"":p.jersey().isBlank()?"":p.jersey()+"  ")+(p==null?DetailRows.cell(entry.values(),0):p.name()),14,ui.text,true));String status=FootballLayout.value(entry,"状态");String substitution=FootballLayout.value(entry,"换人");String caption=String.join(" · ",java.util.stream.Stream.of(p==null?"":DetailParsers.label(p.position()),substitution.equals("—")?status:substitution,marks(entry)).filter(s->!s.isBlank()&&!s.equals("—")&&!s.equals("替补")).toArray(String[]::new));if(!caption.isBlank()){ui.gap(words,4);words.addView(ui.text(DetailParsers.label(caption),12,status.equals("替补登场")?ui.primary:ui.muted,false));}row.addView(words,new LinearLayout.LayoutParams(0,-2,1));click(row,entry);return row;
    }
    private String marks(DetailRows.Entry entry){List<String> marks=new ArrayList<>();for(String key:List.of("进球","黄牌","红牌")){String value=FootballLayout.value(entry,key);if(DetailRows.number(value)>0)marks.add(key+" "+DetailFormat.number(value,0));}return String.join(" · ",marks);}
    private void click(View view,DetailRows.Entry entry){view.setMinimumHeight(ui.dp(48));view.setFocusable(true);view.setContentDescription(entry.person()==null?DetailRows.cell(entry.values(),0):entry.person().name()+"，"+DetailParsers.label(entry.person().position()));view.setOnClickListener(v->{StringBuilder text=new StringBuilder();for(int i=1;i<entry.table().columns.size();i++){String key=entry.table().columns.get(i);if(key.equals("站位"))continue;text.append(key).append("  ").append(DetailParsers.label(DetailRows.cell(entry.values(),i))).append('\n');}Sheet.message((Activity)ui.c,entry.person()==null?DetailRows.cell(entry.values(),0):entry.person().name(),text.toString());});}
}
