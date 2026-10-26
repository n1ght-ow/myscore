package cn.myscore.app;

import android.app.Activity;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.function.Consumer;

/** Source-backed formation and squad events. Numbers replace portraits so the shape stays the data. */
final class FootballLineupView {
    private final Ui ui;
    private final MatchDetail detail;
    private final LinearLayout body;
    FootballLineupView(Ui ui,MatchDetail detail,LinearLayout body){this.ui=ui;this.detail=detail;this.body=body;}
    void show(String selected,Consumer<String> changed){
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
        body.addView(selector);ui.gap(body,16);
        String team=home?match.home:match.away;
        String formation=detail.facts.stream().filter(f->f.label().equals(team+" 阵型")).map(MatchDetail.Fact::value).findFirst().orElse("");
        MatchDetail.Table starters=find(teamId,"首发");
        LinearLayout title=ui.row();title.addView(ui.text("首发阵容",16,ui.text,true),new LinearLayout.LayoutParams(0,-2,1));title.addView(ui.text(formation.isBlank()?"阵型未提供":formation,16,ui.text,true));body.addView(title);ui.gap(body,12);
        List<List<DetailRows.Entry>> lines=starters==null?List.of():FootballLayout.lines(starters,formation);
        if(lines.isEmpty()){
            body.addView(ui.text("完整首发站位尚未提供",13,ui.muted,false));if(starters!=null)list(starters);
        }else{
            LinearLayout pitch=new LinearLayout(ui.c){
                private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
                @Override protected void onDraw(Canvas canvas){
                    super.onDraw(canvas);ink.setColor(ui.secondary);ink.setAlpha(48);ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(ui.dp(1));
                    float w=getWidth(),h=getHeight(),m=ui.dp(8),box=w*.135f,goal=w*.05f;
                    canvas.drawRect(m,m,w-m,h-m,ink);canvas.drawLine(m,h*.5f,w-m,h*.5f,ink);
                    int lineAlpha=ink.getAlpha();ink.setAlpha(32);canvas.drawCircle(w/2,h*.5f,w*.15f,ink);ink.setAlpha(lineAlpha);ink.setStyle(Paint.Style.FILL);canvas.drawCircle(w/2,h*.5f,ui.dp(1.4f),ink);ink.setStyle(Paint.Style.STROKE);
                    canvas.drawRect(w*.22f,m,w*.78f,m+box,ink);canvas.drawRect(w*.35f,m,w*.65f,m+goal,ink);
                    canvas.drawRect(w*.22f,h-m-box,w*.78f,h-m,ink);canvas.drawRect(w*.35f,h-m-goal,w*.65f,h-m,ink);
                    float spot=ui.dp(1.2f);ink.setStyle(Paint.Style.FILL);canvas.drawCircle(w/2,m+box*.62f,spot,ink);canvas.drawCircle(w/2,h-m-box*.62f,spot,ink);ink.setStyle(Paint.Style.STROKE);
                    canvas.drawArc(w/2-w*.10f,m+box*.62f-w*.10f,w/2+w*.10f,m+box*.62f+w*.10f,25,130,false,ink);
                    canvas.drawArc(w/2-w*.10f,h-m-box*.62f-w*.10f,w/2+w*.10f,h-m-box*.62f+w*.10f,205,130,false,ink);
                    float corner=ui.dp(10);canvas.drawArc(m,m,m+corner*2,m+corner*2,0,90,false,ink);canvas.drawArc(w-m-corner*2,m,w-m,m+corner*2,90,90,false,ink);
                    canvas.drawArc(m,h-m-corner*2,m+corner*2,h-m,270,90,false,ink);canvas.drawArc(w-m-corner*2,h-m-corner*2,w-m,h-m,180,90,false,ink);
                }
            };
            pitch.setOrientation(LinearLayout.VERTICAL);pitch.setWillNotDraw(false);
            GradientDrawable background=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{blend(ui.surface,ui.primary,.05f),ui.surface});
            background.setCornerRadius(ui.dp(Ui.CARD_RADIUS));pitch.setBackground(background);pitch.setClipToOutline(true);pitch.setTag("football-pitch");pitch.setPadding(ui.dp(6),ui.dp(8),ui.dp(6),ui.dp(8));
            float font=ui.c.getResources().getConfiguration().fontScale;
            // Attackers at the top, goalkeeper at the bottom, as on the reference pitch.
            List<List<DetailRows.Entry>> display=new ArrayList<>(lines);Collections.reverse(display);
            for(List<DetailRows.Entry> line:display){
                if(font>=1.5f){for(DetailRows.Entry player:line)pitch.addView(listPlayer(player));}
                else{
                    LinearLayout row=ui.row();row.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);row.setPadding(0,ui.dp(9),0,ui.dp(9));
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
        ui.gap(body,16);body.addView(ui.text("比赛数据：ESPN。首发阵型为示意，不代表平均站位。",11,ui.muted,false));
    }
    private MatchDetail.Table find(String team,String category){return detail.tables.stream().filter(t->t.tab.equals("lineup")&&t.teamId.equals(team)&&t.category.equals(category)).findFirst().orElse(null);}
    private void heading(String title){ui.gap(body,24);body.addView(ui.text(title,16,ui.text,true));ui.gap(body,12);}
    private void list(MatchDetail.Table table){LinearLayout list=ui.column();list.setPadding(ui.dp(12),0,ui.dp(12),0);list.setBackground(ui.panel());for(int i=0;i<table.rows.size();i++){if(i>0)list.addView(ui.divider());list.addView(listPlayer(new DetailRows.Entry(table,table.rows.get(i),i<table.people.size()?table.people.get(i):null)));}body.addView(list);}

    private View pitchPlayer(DetailRows.Entry entry){
        MatchDetail.Person person=entry.person();LinearLayout player=ui.column();player.setGravity(Gravity.CENTER);player.setPadding(ui.dp(1),ui.dp(1),ui.dp(1),ui.dp(1));player.setTag("football-pitch-player");
        FrameLayout holder=new FrameLayout(ui.c);holder.addView(jersey(person,42,46,15),new FrameLayout.LayoutParams(ui.dp(42),ui.dp(46),Gravity.CENTER));player.addView(holder,new LinearLayout.LayoutParams(-1,ui.dp(48)));
        TextView name=ui.text(shortName(person),11,ui.text,true);name.setGravity(Gravity.CENTER);player.addView(name,new LinearLayout.LayoutParams(-1,-2));
        View stats=stats(entry,false);
        if(stats!=null){ui.gap(player,3);player.addView(stats,new LinearLayout.LayoutParams(-1,-2));}
        click(player,entry);return player;
    }
    private View listPlayer(DetailRows.Entry entry){
        LinearLayout row=ui.row();row.setPadding(ui.dp(4),ui.dp(10),ui.dp(4),ui.dp(10));MatchDetail.Person person=entry.person();
        if(person!=null)row.addView(jersey(person,30,34,12),new LinearLayout.LayoutParams(ui.dp(30),ui.dp(34)));
        LinearLayout words=ui.column();LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(0,-2,1);wp.setMarginStart(ui.dp(10));words.addView(ui.text((person==null?DetailRows.cell(entry.values(),0):person.name()),14,ui.text,true));
        View meta=stats(entry,true);
        if(meta!=null){ui.gap(words,4);words.addView(meta,new LinearLayout.LayoutParams(-1,-2));}
        row.addView(words,wp);
        click(row,entry);return row;
    }
    /** Position, goal/card/substitution marks and the paired player; null when there is nothing to show. */
    private View stats(DetailRows.Entry entry,boolean pairing){
        Wrap line=new Wrap(!pairing);line.setGap(ui.dp(6),ui.dp(3));
        if(pairing&&entry.person()!=null){String position=DetailParsers.label(entry.person().position());if(!position.isBlank()&&!position.equals("替补"))line.addView(ui.text(position,10,ui.muted,false));}
        int goals=(int)DetailRows.number(FootballLayout.value(entry,"进球")),yellow=(int)DetailRows.number(FootballLayout.value(entry,"黄牌")),red=(int)DetailRows.number(FootballLayout.value(entry,"红牌"));
        if(goals>0)line.addView(mark("goal",goals>1?String.valueOf(goals):"",false));
        if(yellow>0)line.addView(mark("yellow",yellow>1?String.valueOf(yellow):"",false));
        if(red>0)line.addView(mark("red",red>1?String.valueOf(red):"",false));
        String state=FootballLayout.value(entry,"状态");boolean off=state.equals("已换下"),in=state.equals("替补登场");
        if(off||in)line.addView(mark(off?"out":"in",minute(entry),false));
        if(pairing&&(off||in)){
            String swap=replacement(entry);
            if(!swap.isBlank())line.addView(ui.text(swap,10,ui.muted,false));
        }
        if(line.getChildCount()==0)return null;
        return line;
    }
    private View mark(String kind,String text,boolean outline){
        LinearLayout item=ui.row();
        EventMark icon=new EventMark(kind);item.addView(icon,new LinearLayout.LayoutParams(ui.dp(12),ui.dp(12)));
        if(text!=null&&!text.isBlank()){TextView label=ui.text(text,10,ui.muted,true);label.setPadding(ui.dp(2),0,0,0);item.addView(label);}
        return item;
    }
    private String minute(DetailRows.Entry entry){String sub=FootballLayout.value(entry,"换人");if(FootballMetrics.missing(sub))return "";String first=sub.split(" ",2)[0];return first.matches("[0-9+'’]+")?first:"";}
    private String replacement(DetailRows.Entry entry){String sub=FootballLayout.value(entry,"换人");if(FootballMetrics.missing(sub))return "";int split=sub.indexOf("· ");return split<0?"":sub.substring(split+2).trim();}
    private void legend(){
        LinearLayout legend=ui.column();legend.setTag("football-event-legend");
        Wrap wrap=new Wrap(true);wrap.setGap(ui.dp(12),ui.dp(4));
        for(String[] item:new String[][]{{"goal","进球"},{"yellow","黄牌"},{"red","红牌"},{"out","换下"},{"in","换上"}}){
            LinearLayout pair=ui.row();pair.addView(mark(item[0],"",false),new LinearLayout.LayoutParams(-2,-2));
            TextView label=ui.text(item[1],11,ui.muted,false);label.setPadding(ui.dp(4),0,0,0);pair.addView(label);wrap.addView(pair);
        }
        legend.addView(wrap,new LinearLayout.LayoutParams(-1,-2));body.addView(legend);
    }
    private View jersey(MatchDetail.Person person,int width,int height,int numberSp){
        FrameLayout token=new FrameLayout(ui.c);boolean keeper=person!=null&&DetailParsers.label(person.position()).equals("门将");
        token.addView(new Jersey(keeper),new FrameLayout.LayoutParams(-1,-1));
        TextView number=ui.text(person==null||person.jersey().isBlank()?"—":person.jersey(),numberSp,keeper?ui.primary:ui.text,true);
        // The shirt is a fixed-size graphic; keep the number inside it regardless of system font scale.
        number.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP,numberSp);
        number.setGravity(Gravity.CENTER);number.setIncludeFontPadding(false);token.addView(number,new FrameLayout.LayoutParams(-1,-1));
        token.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return token;
    }
    private String shortName(MatchDetail.Person person){if(person==null)return "";String name=DetailFormat.shortName(person.name());return name.isBlank()?person.name():name;}
    private void click(View view,DetailRows.Entry entry){
        view.setMinimumHeight(ui.dp(48));view.setFocusable(true);ui.clickable(view,Color.TRANSPARENT,8);
        String who=entry.person()==null?DetailRows.cell(entry.values(),0):(entry.person().jersey().isBlank()?"":"球衣号 "+entry.person().jersey()+"，")+entry.person().name()+"，"+DetailParsers.label(entry.person().position());
        String events=FootballMetrics.events(entry);
        view.setContentDescription(who+(events.isBlank()?"":"，"+events));
        view.setOnClickListener(v->{StringBuilder text=new StringBuilder();if(entry.person()!=null&&!entry.person().jersey().isBlank())text.append("球衣号  ").append(entry.person().jersey()).append('\n');for(int i=1;i<entry.table().columns.size();i++){String key=entry.table().columns.get(i);if(key.equals("站位"))continue;text.append(key).append("  ").append(DetailParsers.label(DetailRows.cell(entry.values(),i))).append('\n');}Sheet.message((Activity)ui.c,entry.person()==null?DetailRows.cell(entry.values(),0):entry.person().name(),text.toString());});
    }
    private int blend(int from,int to,float ratio){return Color.rgb(Math.round(Color.red(from)+(Color.red(to)-Color.red(from))*ratio),Math.round(Color.green(from)+(Color.green(to)-Color.green(from))*ratio),Math.round(Color.blue(from)+(Color.blue(to)-Color.blue(from))*ratio));}

    /** Jersey silhouette: the squad number replaces the portrait. */
    private final class Jersey extends View {
        private final boolean keeper;private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path shirt=new Path();
        Jersey(boolean keeper){super(ui.c);this.keeper=keeper;setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);float w=getWidth(),h=getHeight(),pad=Math.max(1,ui.dp(.8f));
            shirt.reset();
            shirt.moveTo(w*.30f,pad);shirt.lineTo(w*.42f,pad);shirt.quadTo(w*.50f,h*.10f,w*.58f,pad);shirt.lineTo(w*.70f,pad);
            shirt.lineTo(w*.97f-pad,h*.19f);shirt.lineTo(w*.86f,h*.36f);shirt.lineTo(w*.79f,h*.30f);shirt.lineTo(w*.79f,h-pad);
            shirt.quadTo(w*.79f,h-h*.03f,w*.72f,h-h*.03f);shirt.lineTo(w*.28f,h-h*.03f);shirt.quadTo(w*.21f,h-h*.03f,w*.21f,h-pad);
            shirt.lineTo(w*.21f,h*.30f);shirt.lineTo(w*.14f,h*.36f);shirt.lineTo(w*.03f+pad,h*.19f);shirt.close();
            ink.setStyle(Paint.Style.FILL);ink.setColor(keeper?blend(ui.tonal,ui.primary,.20f):ui.tonal);canvas.drawPath(shirt,ink);
            ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(ui.dp(1));ink.setColor(keeper?ui.primary:ui.line);canvas.drawPath(shirt,ink);
        }
    }
    /** Small centred or left-aligned rows that wrap instead of clipping at large sizes. */
    private final class Wrap extends ViewGroup {
        private final boolean centered;private int gapX,gapY;private int[] lineOf=new int[0],lineWidth=new int[0],lineHeight=new int[0];private int lineCount;
        Wrap(boolean centered){super(ui.c);this.centered=centered;setClipChildren(false);}
        void setGap(int x,int y){gapX=x;gapY=y;}
        @Override protected void onMeasure(int widthSpec,int heightSpec){
            int width=MeasureSpec.getSize(widthSpec);if(MeasureSpec.getMode(widthSpec)==MeasureSpec.UNSPECIFIED)width=ui.c.getResources().getDisplayMetrics().widthPixels;
            int available=Math.max(0,width-getPaddingLeft()-getPaddingRight());
            int count=getChildCount();ensure(count);
            int used=0,height=getPaddingTop()+getPaddingBottom();
            for(int i=0;i<count;i++){
                View child=getChildAt(i);child.measure(MeasureSpec.makeMeasureSpec(available,MeasureSpec.AT_MOST),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
                int cw=Math.min(child.getMeasuredWidth(),available),ch=child.getMeasuredHeight();
                if(used>0&&used+gapX+cw>available){height+=lineHeight[lineCount-1]+gapY;used=0;}
                if(used==0){lineOf[i]=lineCount;lineWidth[lineCount]=cw;lineHeight[lineCount]=ch;lineCount++;}
                else{lineOf[i]=lineCount-1;lineWidth[lineCount-1]+=gapX+cw;lineHeight[lineCount-1]=Math.max(lineHeight[lineCount-1],ch);}
                used+=used==0?cw:gapX+cw;
            }
            if(lineCount>0)height+=lineHeight[lineCount-1];
            setMeasuredDimension(width,height);
        }
        @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
            int available=Math.max(0,getWidth()-getPaddingLeft()-getPaddingRight()),y=getPaddingTop(),index=0;
            for(int line=0;line<lineCount;line++){
                int x=centered?getPaddingLeft()+Math.max(0,(available-lineWidth[line])/2):getPaddingLeft(),first=index;
                while(index<getChildCount()&&lineOf[index]==line){View child=getChildAt(index);child.layout(x,y+Math.max(0,(lineHeight[line]-child.getMeasuredHeight())/2),x+child.getMeasuredWidth(),y+Math.max(0,(lineHeight[line]-child.getMeasuredHeight())/2)+child.getMeasuredHeight());x+=child.getMeasuredWidth()+gapX;index++;}
                if(index==first)break;
                y+=lineHeight[line]+gapY;
            }
        }
        private void ensure(int count){if(lineOf.length<count){lineOf=new int[count];lineWidth=new int[count];lineHeight=new int[count];}lineCount=0;}
    }
    /** Native vector marks; semantic labels live on the containing player and legend. */
    private final class EventMark extends View {
        private final String kind;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path ball=new Path();
        EventMark(String kind){super(ui.c);this.kind=kind;setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);for(int i=0;i<5;i++){double a=Math.PI*2*i/5-Math.PI/2;float x=10+(float)Math.cos(a)*3.4f,y=10+(float)Math.sin(a)*3.4f;if(i==0)ball.moveTo(x,y);else ball.lineTo(x,y);}ball.close();}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);canvas.save();canvas.scale(getWidth()/20f,getHeight()/20f);paint.setStyle(Paint.Style.FILL);
            if(kind.equals("yellow")||kind.equals("red")){paint.setColor(kind.equals("yellow")?Color.rgb(244,201,64):Color.rgb(217,55,66));canvas.drawRoundRect(5,2,15,18,2.2f,2.2f,paint);}
            else if(kind.equals("goal")){paint.setColor(ui.text);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.6f);paint.setStrokeCap(Paint.Cap.ROUND);canvas.drawCircle(10,10,7,paint);paint.setStyle(Paint.Style.FILL);canvas.drawPath(ball,paint);}
            else{paint.setColor(kind.equals("out")?ui.error:ui.primary);paint.setStrokeWidth(2.2f);paint.setStrokeCap(Paint.Cap.ROUND);boolean out=kind.equals("out");canvas.drawLine(10,4,10,16,paint);float tip=out?16:4;canvas.drawLine(10,tip,6,out?12:8,paint);canvas.drawLine(10,tip,14,out?12:8,paint);}
            canvas.restore();
        }
    }
}
