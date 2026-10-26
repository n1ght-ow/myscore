package cn.myscore.app;

import android.app.Activity;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.function.Consumer;

/** Frozen identity rail and horizontally scrollable numeric columns. */
final class DetailTableView {
    private final Ui ui;
    private final boolean nba;
    DetailTableView(Ui ui){this(ui,false);}
    DetailTableView(Ui ui,boolean nba){this.ui=ui;this.nba=nba;}
    View build(List<String> columns,List<DetailRows.Entry> entries,String sorted,boolean descending,Consumer<String> sort,java.util.function.IntConsumer savedScroll,int scrollX){
        LinearLayout shell=ui.row();shell.setGravity(Gravity.TOP);shell.setBackground(ui.panel());shell.setClipToOutline(true);shell.setTag("detail-data-table");
        float scale=Math.max(1,ui.c.getResources().getConfiguration().fontScale);int rowHeight=ui.dp(56*scale),headerHeight=ui.dp(48*scale);
        int available=ui.dp(ui.contentWidthDp()-32);String identity=columns.isEmpty()?"":columns.get(0);int rail=identity.equals("选手")?Math.min(ui.dp(132*scale),(int)(available*.34f)):identity.equals("球队")?ui.dp(130):identity.equals("场次")||identity.equals("地图")?ui.dp(100):Math.min(ui.dp(164),Math.max(ui.dp(120),(int)(available*.38f)));int cell=ui.dp(48*scale);
        LinearLayout names=ui.column(),numbers=ui.column();names.addView(header(columns.isEmpty()?"球员":columns.get(0),false,false,null),new LinearLayout.LayoutParams(-1,headerHeight));
        LinearLayout labels=ui.row();labels.setGravity(Gravity.TOP);
        int[] widths=new int[Math.max(0,columns.size()-1)];
        boolean periods=identity.equals("球队")&&columns.size()<=7&&columns.subList(1,columns.size()).stream().allMatch(s->s.matches("Q[1-4]|OT[0-9]+"));
        for(int i=1;i<columns.size();i++){String label=columns.get(i);int index=i;boolean sortable=sort!=null&&entries.stream().anyMatch(row->!Double.isNaN(DetailRows.number(DetailRows.cell(row.values(),index))));widths[i-1]=periods?Math.max(ui.dp(36),(available-rail-ui.dp(1))/(columns.size()-1)):label.equals("说明")?ui.dp(200*scale):label.equals("车队")||label.equals("位置")||label.equals("状态")||label.contains("成绩")||identity.equals("车手")&&label.matches("Q[1-3]")?Math.max(cell,ui.dp(100*scale)):label.equals("ADR")||label.equals("KAST")||label.equals("Rating")?ui.dp(70*scale):Math.max(cell,ui.dp(Math.min(90,Math.max(48,label.length()*9))*scale));labels.addView(header(label,label.equals(sorted),descending,sortable?()->sort.accept(label):null),new LinearLayout.LayoutParams(widths[i-1],headerHeight));}numbers.addView(labels);
        int total=0;for(int width:widths)total+=width;int spare=Math.max(0,available-rail-ui.dp(1)-total);for(int i=0;i<widths.length;i++){widths[i]+=spare/widths.length;labels.getChildAt(i).getLayoutParams().width=widths[i];}
        headerHeight=Math.max(headerHeight,naturalHeight(names.getChildAt(0),rail));
        for(int i=0;i<widths.length;i++)headerHeight=Math.max(headerHeight,naturalHeight(labels.getChildAt(i),widths[i]));
        names.getChildAt(0).getLayoutParams().height=headerHeight;
        for(int i=0;i<widths.length;i++)labels.getChildAt(i).getLayoutParams().height=headerHeight;
        int tableHeight=headerHeight;
        int rank=0;for(DetailRows.Entry entry:entries){
            names.addView(ui.divider());numbers.addView(ui.divider());
            View name=identity(entry,++rank);LinearLayout line=ui.row();
            int height=Math.max(rowHeight,naturalHeight(name,rail));
            for(int i=1;i<columns.size();i++){String value=DetailRows.cell(entry.values(),i);boolean selected=columns.get(i).equals(sorted);int color=value.equals("—")?ui.muted:ui.text;if(identity.equals("地图")&&(i==1||i==2)&&DetailRows.cell(entry.values(),3).equals("已结束"))color=ui.scoreColor(value,DetailRows.cell(entry.values(),i==1?2:1),true);TextView number=ui.text(value,13,color,selected);if(selected)number.setBackgroundColor(ui.tonal);number.setGravity(Gravity.CENTER);number.setPadding(ui.dp(4),ui.dp(6),ui.dp(4),ui.dp(6));number.setContentDescription(columns.get(i)+" "+value);height=Math.max(height,naturalHeight(number,widths[i-1]));line.addView(number,new LinearLayout.LayoutParams(widths[i-1],-2));}
            names.addView(name,new LinearLayout.LayoutParams(-1,height));
            for(int i=0;i<line.getChildCount();i++)line.getChildAt(i).getLayoutParams().height=height;
            numbers.addView(line);tableHeight+=height+ui.dp(1);
        }
        shell.addView(names,new LinearLayout.LayoutParams(rail,-2));View rule=new View(ui.c);rule.setBackgroundColor(ui.line);shell.addView(rule,new LinearLayout.LayoutParams(ui.dp(1),tableHeight));HorizontalScrollView horizontal=new HorizontalScrollView(ui.c);horizontal.setTag("detail-metrics-scroll");horizontal.setHorizontalScrollBarEnabled(true);horizontal.setFillViewport(true);horizontal.addView(numbers);shell.addView(horizontal,new LinearLayout.LayoutParams(0,-2,1));horizontal.post(()->horizontal.scrollTo(scrollX,0));if(savedScroll!=null)horizontal.setOnScrollChangeListener((v,x,y,ox,oy)->savedScroll.accept(x));return shell;
    }
    private int naturalHeight(View view,int width){view.measure(View.MeasureSpec.makeMeasureSpec(Math.max(1,width),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));return view.getMeasuredHeight();}
    private View header(String label,boolean selected,boolean descending,Runnable action){LinearLayout box=ui.row();box.setGravity(Gravity.CENTER);box.setPadding(ui.dp(4),ui.dp(6),ui.dp(4),ui.dp(6));box.setBackgroundColor(selected?ui.tonal:ui.surface);TextView text=ui.text(label,11,selected?ui.text:ui.muted,true);text.setGravity(Gravity.CENTER);box.addView(text,new LinearLayout.LayoutParams(0,-2,1));if(selected){Ui.Icon arrow=new Ui.Icon(ui.c,"chevron",ui.text);arrow.setRotation(descending?90:-90);box.addView(arrow,new LinearLayout.LayoutParams(ui.dp(10),ui.dp(10)));}if(action!=null){ui.clickable(box,selected?ui.tonal:ui.surface,0);box.setFocusable(true);box.setContentDescription(label+(selected?descending?"，降序":"，升序":"，点击排序"));box.setOnClickListener(v->action.run());}return box;}
    private View identity(DetailRows.Entry entry,int rank){
        LinearLayout row=ui.row();row.setPadding(ui.dp(10),ui.dp(6),ui.dp(8),ui.dp(6));MatchDetail.Person p=entry.person();
        boolean compact=entry.table().columns.get(0).equals("选手");
        if(!compact&&p!=null&&(!p.photo().isBlank()||!p.teamLogo().isBlank())){FrameLayout portrait=new FrameLayout(ui.c);portrait.addView(Logos.artwork(ui,p.photo(),p.name(),30));if((nba||!p.photo().isBlank())&&!p.teamLogo().isBlank())portrait.addView(Logos.view(ui,p.teamLogo(),p.team(),14),new FrameLayout.LayoutParams(ui.dp(14),ui.dp(14),Gravity.BOTTOM|Gravity.END));if(nba)portrait.setTag("nba-player-portrait");row.addView(portrait,new LinearLayout.LayoutParams(ui.dp(30),ui.dp(30)));}
        LinearLayout words=ui.column();LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(0,-2,1);wp.setMarginStart(!compact&&p!=null&&(!p.photo().isBlank()||!p.teamLogo().isBlank())?ui.dp(8):0);String name=p==null?DetailRows.cell(entry.values(),0):entry.table().columns.get(0).equals("球员")?p.shortName().matches("^\\p{L}\\..*|^[A-Z]{2}\\..*")?p.shortName():DetailFormat.shortName(p.name()):p.name();TextView title=ui.text(name,12,ui.text,true);words.addView(title);
        boolean nbaPlayer=nba&&p!=null&&entry.table().category.equals("球员");
        String sub=p==null?"":String.join(" · ",java.util.stream.Stream.of(p.jersey(),p.position(),nba?"":p.absent()?"未出场":p.starter()?"首发":p.team()).filter(s->!s.isBlank()).toArray(String[]::new));
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
    View simple(MatchDetail.Table table){List<DetailRows.Entry> rows=new ArrayList<>();for(int i=0;i<table.rows.size();i++)rows.add(new DetailRows.Entry(table,table.rows.get(i),i<table.people.size()?table.people.get(i):null));return build(table.columns,rows,"",false,null,null,0);}
}
