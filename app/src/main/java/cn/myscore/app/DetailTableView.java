package cn.myscore.app;

import android.app.Activity;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.function.Consumer;

/** Frozen identity rail and horizontally scrollable numeric columns. */
final class DetailTableView {
    private final Ui ui;
    private final boolean nba,showTeam,narrow,compactColumns;
    private final int widthDp;
    DetailTableView(Ui ui){this(ui,false,true,false,0);}
    DetailTableView(Ui ui,boolean nba,boolean showTeam,boolean narrow,int widthDp){this(ui,nba,showTeam,narrow,widthDp,false);}
    DetailTableView(Ui ui,boolean nba,boolean showTeam,boolean narrow,int widthDp,boolean compactColumns){this.ui=ui;this.nba=nba;this.showTeam=showTeam;this.narrow=narrow;this.widthDp=widthDp;this.compactColumns=compactColumns;}
    View build(List<String> columns,List<DetailRows.Entry> entries,String sorted,boolean descending,Consumer<String> sort,java.util.function.IntConsumer savedScroll,int scrollX){
        float scale=Math.max(1,ui.c.getResources().getConfiguration().fontScale);
        int rowMinimum=ui.dp(44*scale),headerMinimum=ui.dp((sort!=null?42:38)*scale);
        int headerTint=ui.tonal;
        int sortTint=Ui.alpha(ui.primary,ui.dark?28:18);
        int zebraTint=Ui.alpha(ui.tonal,ui.dark?85:70);
        String identity=columns.isEmpty()?"":columns.get(0);
        boolean football=football(columns);
        LinearLayout names=ui.column(),numbers=ui.column(),labels=ui.row();labels.setGravity(Gravity.TOP);
        labels.setBackgroundColor(headerTint);
        View nameHeader=header(columns.isEmpty()?"Player":EnglishDisplay.text(identity),false,false,null);
        ((LinearLayout)nameHeader).setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        nameHeader.setPadding(ui.dp(12),ui.dp(6),ui.dp(6),ui.dp(6));
        nameHeader.setBackgroundColor(headerTint);
        ((TextView)((LinearLayout)nameHeader).getChildAt(0)).setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        names.addView(nameHeader,new LinearLayout.LayoutParams(-1,headerMinimum));
        int[] naturalWidths=new int[Math.max(0,columns.size()-1)];
        List<View> identities=new ArrayList<>();List<LinearLayout> metricRows=new ArrayList<>();
        android.graphics.Paint textPaint=ui.text("",13,ui.text,false).getPaint();
        android.graphics.Paint labelPaint=ui.text("",11,ui.muted,true).getPaint();
        for(int i=1;i<columns.size();i++){
            String label=columns.get(i),shownLabel=column(label,football);int index=i;boolean sortable=false;
            if(sort!=null)for(DetailRows.Entry entry:entries)if(!Double.isNaN(DetailRows.number(DetailRows.cell(entry.values(),i)))){sortable=true;break;}
            boolean selected=label.equals(sorted);
            int minimum=ui.dp(label.equals("说明")?176*scale:label.matches("车队|位置|状态")||label.contains("成绩")||label.contains("差距")||label.contains("计时")?84*scale:label.matches("ADR|KAST|Rating|时间|投篮|三分|罚球|C/ATT|QBR|RTG")?50*scale:36*scale);
            float content=labelPaint.measureText(shownLabel)+ui.dp(selected?25:14)+2;
            for(DetailRows.Entry entry:entries){String value=EnglishDisplay.text(DetailRows.cell(entry.values(),i));if(value.length()<=18)content=Math.max(content,textPaint.measureText(value)+ui.dp(14)+2);}
            naturalWidths[i-1]=Math.max(minimum,(int)Math.ceil(content));
            labels.addView(header(shownLabel,selected,descending,sortable?()->sort.accept(columns.get(index)):null),new LinearLayout.LayoutParams(naturalWidths[i-1],headerMinimum));
        }
        numbers.addView(labels);
        int rank=0;
        for(DetailRows.Entry entry:entries){
            names.addView(ui.divider());numbers.addView(ui.divider());
            boolean even=(rank%2==1);
            View name=identity(entry,++rank);
            if(even)name.setBackgroundColor(zebraTint);
            identities.add(name);names.addView(name,new LinearLayout.LayoutParams(-1,rowMinimum));
            LinearLayout line=ui.row();
            if(even)line.setBackgroundColor(zebraTint);
            for(int i=1;i<columns.size();i++){
                String value=DetailRows.cell(entry.values(),i),shownValue=EnglishDisplay.text(value);boolean selected=columns.get(i).equals(sorted);int color=shownValue.equals("—")?ui.muted:selected?ui.primary:ui.text;
                if(identity.equals("地图")&&(i==1||i==2)&&DetailRows.cell(entry.values(),3).equals("已结束"))color=ui.scoreColor(value,DetailRows.cell(entry.values(),i==1?2:1),true);
                TextView number=ui.text(shownValue,13,color,selected);if(!columns.get(i).equals("说明"))Ui.singleLine(number);if(selected)number.setBackgroundColor(sortTint);
                number.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);number.setPadding(ui.dp(7),ui.dp(5),ui.dp(7),ui.dp(5));number.setContentDescription(column(columns.get(i),football)+" "+shownValue);
                line.addView(number,new LinearLayout.LayoutParams(naturalWidths[i-1],rowMinimum));
            }
            metricRows.add(line);numbers.addView(line);
        }
        View rule=new View(ui.c);rule.setBackgroundColor(ui.line);
        HorizontalScrollView horizontal=new HorizontalScrollView(ui.c);horizontal.setTag("detail-metrics-scroll");horizontal.setHorizontalScrollBarEnabled(true);horizontal.setFillViewport(false);horizontal.addView(numbers);
        LinearLayout shell=new LinearLayout(ui.c){
            private int measuredAvailable=-1;
            private int tableWidth;
            @Override protected void onMeasure(int widthSpec,int heightSpec){
                int available=MeasureSpec.getMode(widthSpec)==MeasureSpec.UNSPECIFIED?ui.dp((widthDp>0?widthDp:ui.contentWidthDp())-32):MeasureSpec.getSize(widthSpec);
                available=Math.max(ui.dp(48),available);
                if(available!=measuredAvailable){
                    measuredAvailable=available;
                    int rail=identity.equals("选手")?Math.min(ui.dp((narrow?96:132)*scale),(int)(available*(narrow?.30f:.34f))):identity.equals("球队")?ui.dp(130):identity.equals("场次")||identity.equals("地图")?ui.dp(100):Math.min(ui.dp(164*scale),Math.max(ui.dp(120),(int)(available*.30f)));
                    rail=Math.min(rail,available-ui.dp(48));
                    int[] widths=naturalWidths.clone();int total=0;for(int width:widths)total+=width;
                    if(compactColumns)rail=Math.max(rail,Math.min(available-ui.dp(1)-total,ui.dp(320*scale)));
                    else if(available>rail+ui.dp(1)+total)rail=Math.min(available-ui.dp(1)-total,Math.max(rail,Math.min(ui.dp(220*scale),(int)(available*0.44f))));
                    int spare=compactColumns||widths.length==0?0:Math.max(0,available-rail-ui.dp(1)-total);
                    for(int i=0;i<widths.length;i++)widths[i]+=spare/widths.length+(i<spare%widths.length?1:0);
                    total=0;for(int width:widths)total+=width;
                    tableWidth=compactColumns?Math.min(available,rail+ui.dp(1)+total):available;
                    names.getLayoutParams().width=rail;numbers.getLayoutParams().width=total;
                    int headerHeight=Math.max(headerMinimum,naturalHeight(nameHeader,rail));
                    for(int i=0;i<widths.length;i++){labels.getChildAt(i).getLayoutParams().width=widths[i];headerHeight=Math.max(headerHeight,naturalHeight(labels.getChildAt(i),widths[i]));}
                    nameHeader.getLayoutParams().height=headerHeight;
                    for(int i=0;i<widths.length;i++)labels.getChildAt(i).getLayoutParams().height=headerHeight;
                    int tableHeight=headerHeight;
                    for(int row=0;row<identities.size();row++){
                        View name=identities.get(row);LinearLayout line=metricRows.get(row);int height=Math.max(rowMinimum,naturalHeight(name,rail));
                        for(int i=0;i<widths.length;i++){View number=line.getChildAt(i);number.getLayoutParams().width=widths[i];height=Math.max(height,naturalHeight(number,widths[i]));}
                        name.getLayoutParams().height=height;for(int i=0;i<widths.length;i++)line.getChildAt(i).getLayoutParams().height=height;
                        tableHeight+=height+ui.dp(1);
                    }
                    rule.getLayoutParams().height=tableHeight;
                }
                super.onMeasure(MeasureSpec.makeMeasureSpec(tableWidth,MeasureSpec.EXACTLY),heightSpec);
            }
        };
        shell.setOrientation(LinearLayout.HORIZONTAL);shell.setGravity(Gravity.TOP);shell.setBackground(ui.panel());shell.setClipToOutline(true);shell.setTag("detail-data-table");
        shell.addView(names,new LinearLayout.LayoutParams(ui.dp(120),-2));shell.addView(rule,new LinearLayout.LayoutParams(ui.dp(1),1));shell.addView(horizontal,new LinearLayout.LayoutParams(0,-2,1));
        horizontal.post(()->horizontal.scrollTo(scrollX,0));if(savedScroll!=null)horizontal.setOnScrollChangeListener((v,x,y,ox,oy)->savedScroll.accept(x));
        if(compactColumns){FrameLayout frame=new FrameLayout(ui.c);frame.addView(shell,new FrameLayout.LayoutParams(-2,-2,Gravity.START));return frame;}
        return shell;
    }
    private int naturalHeight(View view,int width){view.measure(View.MeasureSpec.makeMeasureSpec(Math.max(1,width),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));return view.getMeasuredHeight();}
    private View header(String label,boolean selected,boolean descending,Runnable action){LinearLayout box=ui.row();box.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);box.setPadding(ui.dp(7),ui.dp(6),ui.dp(7),ui.dp(6));int bg=selected?Ui.alpha(ui.primary,ui.dark?40:28):Color.TRANSPARENT;box.setBackgroundColor(bg);TextView text=Ui.singleLine(ui.text(label,11,selected?ui.primary:ui.muted,true));text.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);box.addView(text,new LinearLayout.LayoutParams(-2,-2));if(selected){Ui.Icon arrow=new Ui.Icon(ui.c,"chevron",ui.primary);arrow.setRotation(descending?90:-90);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(ui.dp(9),ui.dp(9));ap.setMarginStart(ui.dp(2));box.addView(arrow,ap);}if(action!=null){ui.clickable(box,bg,0);box.setFocusable(true);box.setContentDescription(label+(selected?descending?", descending":", ascending":", tap to sort"));box.setOnClickListener(v->action.run());}return box;}
    private View identity(DetailRows.Entry entry,int rank){
        LinearLayout row=ui.row();row.setPadding(ui.dp(10),ui.dp(5),ui.dp(6),ui.dp(5));MatchDetail.Person p=entry.person();
        boolean compact=entry.table().columns.get(0).equals("选手");
        if(!compact&&p!=null&&(!p.photo().isBlank()||!p.teamLogo().isBlank())){FrameLayout portrait=new FrameLayout(ui.c);View avatar=p.photo().startsWith("https://resources.premierleague.com/")?Logos.portrait(ui,p.photo(),p.name(),28,()->new FootballProfiles(ui.c).backup(p)):Logos.artwork(ui,p.photo(),p.name(),28);portrait.addView(avatar);if((nba||!p.photo().isBlank())&&!p.teamLogo().isBlank())portrait.addView(Logos.view(ui,p.teamLogo(),p.team(),13),new FrameLayout.LayoutParams(ui.dp(13),ui.dp(13),Gravity.BOTTOM|Gravity.END));if(nba)portrait.setTag("nba-player-portrait");row.addView(portrait,new LinearLayout.LayoutParams(ui.dp(28),ui.dp(28)));}
        LinearLayout words=ui.column();LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(0,-2,1);wp.setMarginStart(!compact&&p!=null&&(!p.photo().isBlank()||!p.teamLogo().isBlank())?ui.dp(8):0);String name=p==null?DetailRows.cell(entry.values(),0):entry.table().columns.get(0).equals("球员")?p.shortName().matches("^\\p{L}\\..*|^[A-Z]{2}\\..*")?p.shortName():DetailFormat.shortName(p.name()):p.name();TextView title=Ui.singleLine(ui.text(name,12,ui.text,true));words.addView(title);
        boolean nbaPlayer=nba&&p!=null&&entry.table().category.equals("球员");
        String sub=p==null?"":String.join(" · ",java.util.stream.Stream.of(p.jersey(),EnglishDisplay.text(p.position()),nba?"":p.absent()?"Did not play":p.starter()?"Starter":showTeam?p.team():"").filter(s->!s.isBlank()).toArray(String[]::new));
        if(nbaPlayer){title.setTag("nba-player-name");row.setTag("nba-player-row");ui.gap(words,2);words.addView(nbaCaption(p,sub));}
        else if(!sub.isBlank()){ui.gap(words,2);TextView caption=Ui.singleLine(ui.text(sub,11,ui.muted,false));words.addView(caption);}row.addView(words,wp);
        ui.clickable(row,Color.TRANSPARENT,0);row.setFocusable(true);row.setContentDescription(EnglishDisplay.text(p==null?DetailRows.cell(entry.values(),0):p.name()+", "+(nba?p.team()+", ":"")+(nbaPlayer?role(p)+", ":"")+sub));row.setOnClickListener(v->{StringBuilder detail=new StringBuilder();if(p!=null){if(!p.team().isBlank())detail.append(p.team()).append('\n');if(!p.jersey().isBlank())detail.append("Jersey  ").append(p.jersey()).append('\n');if(!p.position().isBlank())detail.append("Position  ").append(EnglishDisplay.text(p.position())).append('\n');if(nbaPlayer||p.absent()||p.starter())detail.append(role(p)).append('\n');detail.append('\n');}boolean football=football(entry.table().columns);for(int i=1;i<entry.table().columns.size();i++)detail.append(column(entry.table().columns.get(i),football)).append("  ").append(EnglishDisplay.text(DetailRows.cell(entry.values(),i))).append('\n');Sheet.message((Activity)ui.c,p==null?EnglishDisplay.text(DetailRows.cell(entry.values(),0)):p.name(),detail.toString());});return row;
    }
    private String role(MatchDetail.Person person){return person.absent()?"Did not play":person.starter()?"Starter":"Bench";}
    private static boolean football(List<String> columns){return columns.contains("评分")&&columns.contains("换人");}
    private static String column(String key,boolean football){
        if(football){
            if(key.equals("进球"))return "Goals";
            if(key.equals("助攻"))return "Assists";
            if(key.equals("抢断"))return "Tackles";
            if(key.equals("传球"))return "Passes";
        }
        return EnglishDisplay.text(key);
    }
    private View nbaCaption(MatchDetail.Person person,String metadata){
        String label=role(person);boolean starter=person.starter()&&!person.absent();
        TextView badge=Ui.singleLine(ui.text(label,10,starter?ui.primary:ui.muted,true));badge.setTag("nba-player-role");badge.setPadding(ui.dp(6),ui.dp(1.5f),ui.dp(6),ui.dp(1.5f));badge.setBackground(ui.shape(starter?ui.primarySoft:ui.tonal,6));
        TextView meta=Ui.singleLine(ui.text(metadata,11,ui.muted,false));meta.setTag("nba-player-meta");
        LinearLayout caption=new LinearLayout(ui.c){
            @Override protected void onMeasure(int width,int height){
                int needed=(int)Math.ceil(badge.getPaint().measureText(label)+meta.getPaint().measureText(metadata))+badge.getPaddingLeft()+badge.getPaddingRight()+ui.dp(4);
                boolean across=MeasureSpec.getSize(width)>=needed;
                int direction=across?HORIZONTAL:VERTICAL;if(getOrientation()!=direction)setOrientation(direction);setGravity(across?Gravity.CENTER_VERTICAL:Gravity.START);
                LinearLayout.LayoutParams params=(LinearLayout.LayoutParams)meta.getLayoutParams();params.setMarginStart(across?ui.dp(4):0);params.topMargin=across?0:ui.dp(3);
                super.onMeasure(width,height);
            }
        };
        caption.addView(badge,new LinearLayout.LayoutParams(-2,-2));if(!metadata.isBlank())caption.addView(meta,new LinearLayout.LayoutParams(-2,-2));
        // Keep a measurable metadata child for rows whose source omits jersey and position.
        else {meta.setVisibility(View.GONE);caption.addView(meta,new LinearLayout.LayoutParams(-2,-2));}
        return caption;
    }
    View simple(MatchDetail.Table table){return simple(table,null,0);}
    View simple(MatchDetail.Table table,java.util.function.IntConsumer savedScroll,int scrollX){
        List<DetailRows.Entry> rows=new ArrayList<>();
        for(int i=0;i<table.rows.size();i++)rows.add(new DetailRows.Entry(table,table.rows.get(i),i<table.people.size()?table.people.get(i):null));
        return build(table.columns,rows,"",false,null,savedScroll,scrollX);
    }
}
