package cn.myscore.app;

import android.app.Activity;
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
        int rowMinimum=ui.dp(48*scale),headerMinimum=ui.dp(48*scale);
        String identity=columns.isEmpty()?"":columns.get(0);
        LinearLayout names=ui.column(),numbers=ui.column(),labels=ui.row();labels.setGravity(Gravity.TOP);
        View nameHeader=header(columns.isEmpty()?"球员":identity,false,false,null);
        ((TextView)((LinearLayout)nameHeader).getChildAt(0)).setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        names.addView(nameHeader,new LinearLayout.LayoutParams(-1,headerMinimum));
        int[] naturalWidths=new int[Math.max(0,columns.size()-1)];
        List<View> identities=new ArrayList<>();List<LinearLayout> metricRows=new ArrayList<>();
        android.graphics.Paint textPaint=ui.text("",13,ui.text,false).getPaint();
        android.graphics.Paint labelPaint=ui.text("",11,ui.muted,true).getPaint();
        for(int i=1;i<columns.size();i++){
            String label=columns.get(i);int index=i;boolean sortable=false;
            if(sort!=null)for(DetailRows.Entry entry:entries)if(!Double.isNaN(DetailRows.number(DetailRows.cell(entry.values(),i)))){sortable=true;break;}
            int minimum=ui.dp(label.equals("说明")?200*scale:label.matches("车队|位置|状态")||label.contains("成绩")?100*scale:label.matches("ADR|KAST|Rating")?64*scale:48*scale);
            float content=labelPaint.measureText(label)+ui.dp(sortable?36:20);
            for(DetailRows.Entry entry:entries){String value=DetailRows.cell(entry.values(),i);if(value.length()<=18)content=Math.max(content,textPaint.measureText(value)+ui.dp(20));}
            naturalWidths[i-1]=Math.max(minimum,(int)Math.ceil(content));
            labels.addView(header(label,label.equals(sorted),descending,sortable?()->sort.accept(columns.get(index)):null),new LinearLayout.LayoutParams(naturalWidths[i-1],headerMinimum));
        }
        numbers.addView(labels);
        int rank=0;
        for(DetailRows.Entry entry:entries){
            names.addView(ui.divider());numbers.addView(ui.divider());
            View name=identity(entry,++rank);identities.add(name);names.addView(name,new LinearLayout.LayoutParams(-1,rowMinimum));
            LinearLayout line=ui.row();
            for(int i=1;i<columns.size();i++){
                String value=DetailRows.cell(entry.values(),i);boolean selected=columns.get(i).equals(sorted);int color=value.equals("—")?ui.muted:ui.text;
                if(identity.equals("地图")&&(i==1||i==2)&&DetailRows.cell(entry.values(),3).equals("已结束"))color=ui.scoreColor(value,DetailRows.cell(entry.values(),i==1?2:1),true);
                TextView number=ui.text(value,13,color,selected);if(selected)number.setBackgroundColor(ui.tonal);
                number.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);number.setPadding(ui.dp(10),ui.dp(6),ui.dp(10),ui.dp(6));number.setContentDescription(columns.get(i)+" "+value);
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
                    int spare=compactColumns?0:Math.max(0,available-rail-ui.dp(1)-total);
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
    private View header(String label,boolean selected,boolean descending,Runnable action){LinearLayout box=ui.row();box.setGravity(Gravity.CENTER);box.setPadding(ui.dp(10),ui.dp(6),ui.dp(10),ui.dp(6));box.setBackgroundColor(selected?ui.tonal:ui.surface);TextView text=ui.text(label,11,selected?ui.text:ui.muted,true);text.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);if(selected){Ui.Icon arrow=new Ui.Icon(ui.c,"chevron",ui.text);arrow.setRotation(descending?90:-90);box.addView(arrow,new LinearLayout.LayoutParams(ui.dp(10),ui.dp(10)));}box.addView(text,new LinearLayout.LayoutParams(0,-2,1));if(action!=null){ui.clickable(box,selected?ui.tonal:ui.surface,0);box.setFocusable(true);box.setContentDescription(label+(selected?descending?"，降序":"，升序":"，点击排序"));box.setOnClickListener(v->action.run());}return box;}
    private View identity(DetailRows.Entry entry,int rank){
        LinearLayout row=ui.row();row.setPadding(ui.dp(10),ui.dp(6),ui.dp(8),ui.dp(6));MatchDetail.Person p=entry.person();
        boolean compact=entry.table().columns.get(0).equals("选手");
        if(!compact&&p!=null&&(!p.photo().isBlank()||!p.teamLogo().isBlank())){FrameLayout portrait=new FrameLayout(ui.c);View avatar=p.photo().startsWith("https://resources.premierleague.com/")?Logos.portrait(ui,p.photo(),p.name(),30,()->new FootballProfiles(ui.c).backup(p)):Logos.artwork(ui,p.photo(),p.name(),30);portrait.addView(avatar);if((nba||!p.photo().isBlank())&&!p.teamLogo().isBlank())portrait.addView(Logos.view(ui,p.teamLogo(),p.team(),14),new FrameLayout.LayoutParams(ui.dp(14),ui.dp(14),Gravity.BOTTOM|Gravity.END));if(nba)portrait.setTag("nba-player-portrait");row.addView(portrait,new LinearLayout.LayoutParams(ui.dp(30),ui.dp(30)));}
        LinearLayout words=ui.column();LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(0,-2,1);wp.setMarginStart(!compact&&p!=null&&(!p.photo().isBlank()||!p.teamLogo().isBlank())?ui.dp(8):0);String name=p==null?DetailRows.cell(entry.values(),0):entry.table().columns.get(0).equals("球员")?p.shortName().matches("^\\p{L}\\..*|^[A-Z]{2}\\..*")?p.shortName():DetailFormat.shortName(p.name()):p.name();TextView title=ui.text(name,12,ui.text,true);words.addView(title);
        boolean nbaPlayer=nba&&p!=null&&entry.table().category.equals("球员");
        String sub=p==null?"":String.join(" · ",java.util.stream.Stream.of(p.jersey(),p.position(),nba?"":p.absent()?"未出场":p.starter()?"首发":showTeam?p.team():"").filter(s->!s.isBlank()).toArray(String[]::new));
        if(nbaPlayer){title.setTag("nba-player-name");row.setTag("nba-player-row");ui.gap(words,4);words.addView(nbaCaption(p,sub));}
        else if(!sub.isBlank()){ui.gap(words,4);TextView caption=ui.text(sub,11,ui.muted,false);words.addView(caption);}row.addView(words,wp);
        ui.clickable(row,ui.surface,0);row.setFocusable(true);row.setContentDescription(p==null?DetailRows.cell(entry.values(),0):p.name()+"，"+(nba?p.team()+"，":"")+(nbaPlayer?role(p)+"，":"")+sub);row.setOnClickListener(v->{StringBuilder detail=new StringBuilder();if(p!=null){if(!p.team().isBlank())detail.append(p.team()).append('\n');if(!p.jersey().isBlank())detail.append("球衣号  ").append(p.jersey()).append('\n');if(!p.position().isBlank())detail.append("位置  ").append(p.position()).append('\n');if(nbaPlayer||p.absent()||p.starter())detail.append(role(p)).append('\n');detail.append('\n');}for(int i=1;i<entry.table().columns.size();i++)detail.append(entry.table().columns.get(i)).append("  ").append(DetailRows.cell(entry.values(),i)).append('\n');Sheet.message((Activity)ui.c,p==null?DetailRows.cell(entry.values(),0):p.name(),detail.toString());});return row;
    }
    private String role(MatchDetail.Person person){return person.absent()?"未出场":person.starter()?"首发":"替补";}
    private View nbaCaption(MatchDetail.Person person,String metadata){
        String label=role(person);boolean starter=person.starter()&&!person.absent();
        TextView badge=ui.text(label,11,starter?ui.onPrimary:ui.muted,true);badge.setTag("nba-player-role");badge.setPadding(ui.dp(5),ui.dp(1),ui.dp(5),ui.dp(1));badge.setBackground(ui.shape(starter?ui.primary:ui.tonal,4));
        TextView meta=ui.text(metadata,11,ui.muted,false);meta.setTag("nba-player-meta");
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
