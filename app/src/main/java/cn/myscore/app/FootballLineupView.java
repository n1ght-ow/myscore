package cn.myscore.app;

import android.app.Activity;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.function.Consumer;

/** Source-backed formation with selectable player metrics and compact event markers. */
final class FootballLineupView {
    private final Ui ui;
    private final MatchDetail detail;
    private final LinearLayout body;
    private String metric="评分";
    FootballLineupView(Ui ui,MatchDetail detail,LinearLayout body){this.ui=ui;this.detail=detail;this.body=body;}
    void show(String selected,Consumer<String> changed){show(selected,"评分",changed,value->{});}
    void show(String selected,String perspective,Consumer<String> changed,Consumer<String> metricChanged){
        metric=FootballMetrics.valid(perspective);
        Match match=detail.match;boolean home=!selected.equals(match.awayId);String teamId=home?match.homeId:match.awayId;
        LinearLayout selector=ui.row();selector.setTag("football-team-filter");
        String[] ids={match.homeId,match.awayId},names={match.home,match.away},logos={match.homeLogo,match.awayLogo};
        for(int i=0;i<2;i++){
            String id=ids[i];boolean on=i==0?home:!home;LinearLayout choice=ui.row();
            choice.setGravity(Gravity.CENTER);choice.setPadding(ui.dp(8),ui.dp(10),ui.dp(8),ui.dp(10));choice.setMinimumHeight(ui.dp(48));ui.clickable(choice,on?ui.tonal:ui.surface,Ui.CONTROL_RADIUS);
            choice.addView(Logos.view(ui,logos[i],names[i],22));TextView name=ui.text(names[i],12,on?ui.text:ui.muted,on);name.setPadding(ui.dp(8),0,0,0);choice.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            choice.setContentDescription(names[i]+(on?"，已选中":"，查看阵容"));choice.setSelected(on);choice.setFocusable(true);choice.setOnClickListener(v->changed.accept(id));
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);if(i>0)params.setMarginStart(ui.dp(8));selector.addView(choice,params);
        }
        body.addView(selector);ui.gap(body,12);filters(metricChanged);ui.gap(body,16);
        String team=home?match.home:match.away;
        String formation=detail.facts.stream().filter(f->f.label().equals(team+" 阵型")).map(MatchDetail.Fact::value).findFirst().orElse("");
        MatchDetail.Table starters=find(teamId,"首发");
        LinearLayout title=ui.row();title.addView(ui.text("首发阵容",16,ui.text,true),new LinearLayout.LayoutParams(0,-2,1));title.addView(ui.text(formation.isBlank()?"阵型未提供":formation,16,ui.text,true));body.addView(title);ui.gap(body,8);
        TextView context=ui.text(FootballMetrics.caption(metric)+(FootballMetrics.available(starters,metric)?FootballMetrics.PERFORMANCE.contains(metric)?" · 球员比赛数据":" · 球员当前资料":" · 本场数据源暂未提供"),12,ui.muted,false);context.setTag("football-metric-caption");body.addView(context);ui.gap(body,12);
        List<List<DetailRows.Entry>> lines=starters==null?List.of():FootballLayout.lines(starters,formation);
        if(lines.isEmpty()){
            body.addView(ui.text("完整首发站位尚未提供",13,ui.muted,false));if(starters!=null)list(starters);
        }else{
            LinearLayout pitch=new LinearLayout(ui.c){
                private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
                @Override protected void onDraw(Canvas canvas){
                    super.onDraw(canvas);ink.setColor(ui.secondary);ink.setAlpha(75);ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(ui.dp(1));
                    float w=getWidth(),h=getHeight(),m=ui.dp(6);
                    canvas.drawRect(m,m,w-m,h-m,ink);canvas.drawLine(m,h*.5f,w-m,h*.5f,ink);canvas.drawCircle(w/2,h*.5f,w*.15f,ink);
                    canvas.drawRect(w*.23f,m,w*.77f,h*.13f,ink);canvas.drawRect(w*.36f,m,w*.64f,h*.05f,ink);
                    canvas.drawRect(w*.23f,h*.87f,w*.77f,h-m,ink);canvas.drawRect(w*.36f,h*.95f,w*.64f,h-m,ink);
                }
            };
            pitch.setOrientation(LinearLayout.VERTICAL);pitch.setWillNotDraw(false);pitch.setBackground(ui.shape(ui.surface,Ui.CARD_RADIUS));pitch.setClipToOutline(true);pitch.setTag("football-pitch");pitch.setPadding(ui.dp(8),ui.dp(12),ui.dp(8),ui.dp(12));
            float font=ui.c.getResources().getConfiguration().fontScale;
            // Attackers at the top, goalkeeper at the bottom, as on the reference pitch.
            List<List<DetailRows.Entry>> display=new ArrayList<>(lines);Collections.reverse(display);
            for(List<DetailRows.Entry> line:display){
                if(font>=1.5f){for(DetailRows.Entry player:line)pitch.addView(listPlayer(player));}
                else{
                    LinearLayout row=ui.row();row.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);row.setPadding(0,ui.dp(14),0,ui.dp(14));
                    List<DetailRows.Entry> across=new ArrayList<>(line);Collections.reverse(across);
                    for(DetailRows.Entry player:across)row.addView(pitchPlayer(player),new LinearLayout.LayoutParams(0,-2,1));pitch.addView(row);
                }
            }
            body.addView(pitch,new LinearLayout.LayoutParams(-1,-2));ui.gap(body,8);
            body.addView(ui.text(font>=1.5f?"按首发阵型分行 · 点球员查看详情":"首发阵型示意，非平均站位 · 点球员查看详情",11,ui.muted,false));
        }
        ui.gap(body,12);legend();
        heading("替补");MatchDetail.Table bench=find(teamId,"替补");if(bench==null)body.addView(ui.text("替补名单尚未提供",13,ui.muted,false));else list(bench);
        MatchDetail.Table injuries=find(teamId,"伤停");if(injuries!=null){heading("受伤与停赛");list(injuries);}
        ui.gap(body,16);body.addView(ui.text("比赛数据：ESPN。照片：ESPN / 英超官方 / TheSportsDB，备用照片按完整姓名与出生日期匹配。缺失照片显示姓名缩写，未提供的指标显示 —。",11,ui.muted,false));
    }
    private void filters(Consumer<String> changed){
        HorizontalScrollView scroll=new HorizontalScrollView(ui.c);scroll.setHorizontalScrollBarEnabled(false);scroll.setTag("football-metric-filter");
        LinearLayout row=ui.row();boolean performance=FootballMetrics.PERFORMANCE.contains(metric);
        TextView picker=chip("表现 · "+(performance?metric:"评分")+" ▾",performance);picker.setTag("football-performance-picker");
        picker.setContentDescription("表现，选择评分、射门、传球、盘带或防守，当前"+metric);
        picker.setOnClickListener(v->performanceMenu(picker,changed));row.addView(picker);
        for(String profile:FootballMetrics.PROFILES){TextView button=chip(profile,metric.equals(profile));button.setTag("football-filter-"+profile);button.setOnClickListener(v->changed.accept(profile));LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-2,-2);params.setMarginStart(ui.dp(8));row.addView(button,params);}
        scroll.addView(row);body.addView(scroll,new LinearLayout.LayoutParams(-1,-2));
        // Keep the active perspective visible after a body refresh or configuration change.
        scroll.post(()->{if(!performance){View current=row.findViewWithTag("football-filter-"+metric);if(current!=null)scroll.scrollTo(Math.max(0,current.getRight()-scroll.getWidth()),0);}});
    }
    private void performanceMenu(TextView anchor,Consumer<String> changed){
        LinearLayout options=ui.column();options.setPadding(ui.dp(8),ui.dp(8),ui.dp(8),ui.dp(8));options.setBackground(ui.panel());
        ScrollView scroll=new ScrollView(ui.c);scroll.setVerticalScrollBarEnabled(false);scroll.setBackground(ui.panel());scroll.setClipToOutline(true);scroll.addView(options);
        int width=Math.min(ui.dp(240),ui.c.getResources().getDisplayMetrics().widthPixels-ui.dp(32));
        PopupWindow popup=new PopupWindow(scroll,width,-2,true);popup.setBackgroundDrawable(ui.shape(ui.surface,Ui.CARD_RADIUS));popup.setOutsideTouchable(true);popup.setElevation(ui.dp(8));
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        for(String option:FootballMetrics.PERFORMANCE){
            boolean selected=option.equals(metric);LinearLayout item=ui.row();item.setMinimumHeight(ui.dp(48));item.setPadding(ui.dp(14),ui.dp(12),ui.dp(12),ui.dp(12));
            item.addView(ui.text(option,14,ui.text,selected),new LinearLayout.LayoutParams(0,-2,1));
            if(selected){Ui.Icon check=new Ui.Icon(ui.c,"check",ui.text);check.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);item.addView(check,new LinearLayout.LayoutParams(ui.dp(20),ui.dp(20)));}
            item.setSelected(selected);item.setFocusable(true);item.setContentDescription(option+(selected?"，已选中":""));ui.clickable(item,selected?ui.tonal:ui.surface,12);
            item.setOnClickListener(v->{popup.dismiss();changed.accept(option);});options.addView(item);
        }
        anchor.setText("表现 · "+(FootballMetrics.PERFORMANCE.contains(metric)?metric:"评分")+" ▴");
        popup.setOnDismissListener(()->anchor.setText("表现 · "+(FootballMetrics.PERFORMANCE.contains(metric)?metric:"评分")+" ▾"));
        options.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        popup.setHeight(Math.min(options.getMeasuredHeight(),Math.max(ui.dp(100),ui.c.getResources().getDisplayMetrics().heightPixels/2)));
        popup.showAsDropDown(anchor,0,ui.dp(8));
    }
    private TextView chip(String label,boolean selected){TextView v=ui.text(label,13,selected?ui.onAction:ui.text,true);v.setGravity(Gravity.CENTER);v.setPadding(ui.dp(16),ui.dp(10),ui.dp(16),ui.dp(10));v.setMinimumHeight(ui.dp(48));v.setSelected(selected);v.setFocusable(true);ui.clickable(v,selected?ui.action:ui.surface,Ui.CONTROL_RADIUS);v.setContentDescription(label+(selected?"，已选中":""));return v;}
    private MatchDetail.Table find(String team,String category){return detail.tables.stream().filter(t->t.tab.equals("lineup")&&t.teamId.equals(team)&&t.category.equals(category)).findFirst().orElse(null);}
    private void heading(String title){ui.gap(body,24);body.addView(ui.text(title,16,ui.text,true));ui.gap(body,12);}
    private void list(MatchDetail.Table table){LinearLayout list=ui.column();list.setPadding(ui.dp(12),0,ui.dp(12),0);list.setBackground(ui.panel());for(int i=0;i<table.rows.size();i++){if(i>0)list.addView(ui.divider());list.addView(listPlayer(new DetailRows.Entry(table,table.rows.get(i),i<table.people.size()?table.people.get(i):null)));}body.addView(list);}
    private View portrait(MatchDetail.Person person,int size){String photo=DetailParsers.soccerPhoto(person.photo(),person.id());android.content.Context context=ui.c.getApplicationContext();FrameLayout view=Logos.portrait(ui,photo,person.name(),size,()->new FootballProfiles(context).backup(person));view.setBackground(ui.shape(ui.tonal,size));view.setClipToOutline(true);return view;}
    private View pitchPlayer(DetailRows.Entry entry){
        MatchDetail.Person person=entry.person();LinearLayout player=ui.column();player.setGravity(Gravity.CENTER);player.setPadding(ui.dp(2),ui.dp(2),ui.dp(2),ui.dp(2));
        FrameLayout image=new FrameLayout(ui.c){@Override protected void onMeasure(int width,int height){super.onMeasure(MeasureSpec.makeMeasureSpec(Math.min(MeasureSpec.getSize(width),ui.dp(64)),MeasureSpec.EXACTLY),height);}};image.addView(portrait(person,44),new FrameLayout.LayoutParams(ui.dp(44),ui.dp(44),Gravity.TOP|Gravity.CENTER_HORIZONTAL));
        String yellow=FootballLayout.value(entry,"黄牌"),red=FootballLayout.value(entry,"红牌"),goals=FootballLayout.value(entry,"进球");
        if(DetailRows.number(yellow)>0)addMark(image,"yellow",yellow,Gravity.TOP|Gravity.START,0);
        if(DetailRows.number(red)>0)addMark(image,"red",red,Gravity.TOP|Gravity.START,19);
        if(DetailRows.number(goals)>0)addMark(image,"goal",goals,Gravity.TOP|Gravity.END,0);
        String state=FootballLayout.value(entry,"状态");
        if(state.equals("已换下")||state.equals("替补登场"))addMark(image,state.equals("已换下")?"out":"in","1",Gravity.TOP|Gravity.END,25);
        LinearLayout.LayoutParams portraitParams=new LinearLayout.LayoutParams(-1,ui.dp(48));player.addView(image,portraitParams);
        TextView value=metricBadge(entry);player.addView(value,new LinearLayout.LayoutParams(-2,-2));ui.gap(player,4);
        TextView name=ui.text((person.jersey().isBlank()?"":person.jersey()+" ")+DetailFormat.shortName(person.name()),11,ui.text,true);name.setGravity(Gravity.CENTER);player.addView(name,new LinearLayout.LayoutParams(-1,-2));
        String sub=FootballLayout.value(entry,"换人");if(!FootballMetrics.missing(sub)){String minute=sub.split(" ",2)[0];if(minute.matches("[0-9+'’]+")){TextView time=ui.text(minute+(state.equals("已换下")?" 换下":" 换上"),11,ui.muted,false);time.setGravity(Gravity.CENTER);player.addView(time,new LinearLayout.LayoutParams(-1,-2));}}
        click(player,entry);return player;
    }
    private TextView metricBadge(DetailRows.Entry entry){
        String value=FootballMetrics.value(entry,metric);TextView badge=ui.text(value,12,FootballMetrics.missing(value)?ui.muted:ui.text,true);badge.setGravity(Gravity.CENTER);badge.setPadding(ui.dp(6),ui.dp(1),ui.dp(6),ui.dp(1));badge.setMinWidth(ui.dp(30));
        int background=ui.tonal;
        if(metric.equals("评分")&&!FootballMetrics.missing(value)){double rating=DetailRows.number(value);background=rating>=7?ui.primary:rating>0&&rating<6?ui.error:ui.tonal;badge.setTextColor(rating>=7?ui.onPrimary:rating>0&&rating<6?ui.bg:ui.text);}
        badge.setBackground(ui.shape(background,4));badge.setTag("football-player-metric");return badge;
    }
    private void addMark(FrameLayout parent,String kind,String count,int gravity,int top){EventMark mark=new EventMark(kind,count);FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(ui.dp(18),ui.dp(18),gravity);p.topMargin=ui.dp(top);parent.addView(mark,p);}
    private View listPlayer(DetailRows.Entry entry){
        LinearLayout row=ui.row();row.setPadding(ui.dp(4),ui.dp(12),ui.dp(4),ui.dp(12));MatchDetail.Person person=entry.person();if(person!=null)row.addView(portrait(person,40),new LinearLayout.LayoutParams(ui.dp(40),ui.dp(40)));
        LinearLayout words=ui.column();words.setPadding(ui.dp(10),0,0,0);words.addView(ui.text((person==null||person.jersey().isBlank()?"":person.jersey()+"  ")+(person==null?DetailRows.cell(entry.values(),0):person.name()),14,ui.text,true));
        String state=FootballLayout.value(entry,"状态");String caption=String.join(" · ",java.util.stream.Stream.of(person==null?"":DetailParsers.label(person.position()),state,FootballMetrics.events(entry)).filter(s->!FootballMetrics.missing(s)).distinct().toArray(String[]::new));
        if(!caption.isBlank()){ui.gap(words,4);words.addView(ui.text(caption,12,ui.muted,false));}
        if(!entry.table().category.equals("伤停")){ui.gap(words,4);TextView value=ui.text(FootballMetrics.caption(metric)+"  "+FootballMetrics.value(entry,metric),12,ui.text,true);value.setTag("football-list-metric");words.addView(value);}
        row.addView(words,new LinearLayout.LayoutParams(0,-2,1));click(row,entry);return row;
    }
    private void legend(){
        LinearLayout legend=ui.column();legend.setTag("football-event-legend");
        for(String[] line:new String[][]{{"goal","进球","yellow","黄牌","red","红牌"},{"out","换下","in","换上"}}){
            LinearLayout row=ui.row();for(int i=0;i<line.length;i+=2){row.addView(new EventMark(line[i],"1"),new LinearLayout.LayoutParams(ui.dp(16),ui.dp(16)));TextView label=ui.text(line[i+1],11,ui.muted,false);label.setPadding(ui.dp(4),0,ui.dp(12),0);row.addView(label);}legend.addView(row);}
        body.addView(legend);
    }
    private void click(View view,DetailRows.Entry entry){
        view.setMinimumHeight(ui.dp(48));view.setFocusable(true);ui.clickable(view,Color.TRANSPARENT,8);
        view.setContentDescription((entry.person()==null?DetailRows.cell(entry.values(),0):entry.person().name()+"，"+DetailParsers.label(entry.person().position()))+"，"+FootballMetrics.caption(metric)+" "+FootballMetrics.value(entry,metric)+"，"+FootballMetrics.events(entry));
        view.setOnClickListener(v->{StringBuilder text=new StringBuilder();if(entry.person()!=null&&!entry.person().jersey().isBlank())text.append("球衣号  ").append(entry.person().jersey()).append('\n');for(int i=1;i<entry.table().columns.size();i++){String key=entry.table().columns.get(i);if(key.equals("站位"))continue;text.append(key).append("  ").append(DetailParsers.label(DetailRows.cell(entry.values(),i))).append('\n');}Sheet.message((Activity)ui.c,entry.person()==null?DetailRows.cell(entry.values(),0):entry.person().name(),text.toString());});
    }
    /** Native vector marks; semantic labels live on the containing player and legend. */
    private final class EventMark extends View {
        private final String kind,count;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path ball=new Path();
        EventMark(String kind,String count){super(ui.c);this.kind=kind;this.count=count;setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);for(int i=0;i<5;i++){double a=Math.PI*2*i/5-Math.PI/2;float x=10+(float)Math.cos(a)*3.3f,y=10+(float)Math.sin(a)*3.3f;if(i==0)ball.moveTo(x,y);else ball.lineTo(x,y);}ball.close();}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);canvas.save();canvas.scale(getWidth()/20f,getHeight()/20f);paint.setStyle(Paint.Style.FILL);paint.setColor(ui.surface);canvas.drawCircle(10,10,10,paint);
            if(kind.equals("yellow")||kind.equals("red")){paint.setColor(kind.equals("yellow")?Color.rgb(244,201,64):Color.rgb(217,55,66));canvas.drawRoundRect(5,3,15,17,2,2,paint);}
            else if(kind.equals("goal")){paint.setColor(ui.text);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.5f);canvas.drawCircle(10,10,7,paint);paint.setStyle(Paint.Style.FILL);canvas.drawPath(ball,paint);}
            else{paint.setColor(kind.equals("out")?ui.error:ui.primary);paint.setStrokeWidth(2);paint.setStrokeCap(Paint.Cap.ROUND);boolean out=kind.equals("out");canvas.drawLine(4,10,16,10,paint);canvas.drawLine(out?4:16,10,out?8:12,6,paint);canvas.drawLine(out?4:16,10,out?8:12,14,paint);}
            if(DetailRows.number(count)>1){paint.setStyle(Paint.Style.FILL);paint.setColor(ui.action);canvas.drawCircle(15,15,5,paint);paint.setColor(ui.onAction);paint.setTextSize(8);paint.setTypeface(Typeface.DEFAULT_BOLD);paint.setTextAlign(Paint.Align.CENTER);canvas.drawText(DetailFormat.number(count,0),15,18,paint);}canvas.restore();
        }
    }
}
