package cn.myscore.app;

import android.app.Activity;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * NBA players and CS2 players share one presentation: two stacked team panels, no filters.
 * NBA injury reports stay at the foot of the team they belong to. CS2 keeps per-map tables
 * in the map tab instead of asking the reader to switch the whole page.
 */
final class TeamPanelsView {
    record Sort(String column,boolean descending){}
    private static final String INJURY="伤停";
    private final Ui ui;private final MatchDetail detail;private final LinearLayout body;
    TeamPanelsView(Ui ui,MatchDetail detail,LinearLayout body){this.ui=ui;this.detail=detail;this.body=body;}

    /** Full player page: heading, one hint, then the home and away panels. */
    void show(String sort,boolean descending,Consumer<Sort> changed,int scrollX,IntConsumer savedScroll){
        boolean nba=nba();String category=scope(nba);
        int players=countRows(teamTables(category,detail.match.homeId))+countRows(teamTables(category,detail.match.awayId));boolean anyInjuries=nba&&!injuryTables(null).isEmpty();
        if(players==0&&!anyInjuries){
            body.addView(ui.text("本场数据尚未提供",18,ui.text,true));ui.gap(body,10);
            body.addView(ui.text("来源更新后会显示本场真实数据。",13,ui.muted,false));return;
        }
        String title=nba?"球员数据":category.equals("全场")?"选手总数据":category+" 选手数据";
        body.addView(ui.text(title,16,ui.text,true));ui.gap(body,4);
        TextView hint=ui.text(players>0?"点数值列排序 · 点姓名查看详情":"伤停名单按来源发布",11,ui.muted,false);
        body.addView(hint);ui.gap(body,12);
        List<HorizontalScrollView> scrollers=teams(category,nba,sort,descending,changed,scrollX,"","");
        bind(scrollers,savedScroll);
        hint.post(()->{
            boolean scroll=!scrollers.isEmpty()&&(scrollers.get(0).canScrollHorizontally(1)||scrollers.get(0).canScrollHorizontally(-1));
            hint.setText(players>0?"点数值列排序 · "+(scroll?"横向滑动同步查看两队 · ":"")+"点姓名查看详情":"伤停名单按来源发布");
        });
    }

    /** One scope of two team panels, used by the CS2 map tab for each finished map. */
    void scope(String category,String sort,boolean descending,Consumer<Sort> changed,int scrollX,IntConsumer savedScroll){
        scope(category,sort,descending,changed,scrollX,savedScroll,"","");
    }

    /** The map variant carries each team's half scores without repeating the map total. */
    void scope(String category,String sort,boolean descending,Consumer<Sort> changed,int scrollX,IntConsumer savedScroll,String homeHalf,String awayHalf){
        bind(teams(category,false,sort,descending,changed,scrollX,homeHalf,awayHalf),savedScroll);
    }

    private List<HorizontalScrollView> teams(String category,boolean injuries,String sort,boolean descending,Consumer<Sort> changed,int scrollX,String homeHalf,String awayHalf){
        List<MatchDetail.Table> home=teamTables(category,detail.match.homeId),away=teamTables(category,detail.match.awayId);
        List<String> columns=!home.isEmpty()?home.get(0).columns:!away.isEmpty()?away.get(0).columns:List.of();
        String effective=effectiveSort(columns,sort);
        boolean anyInjuries=injuries&&!injuryTables(null).isEmpty();
        List<HorizontalScrollView> scrollers=new ArrayList<>();
        scrollers.addAll(team(detail.match.homeId,category,effective,descending,changed,scrollX,anyInjuries,homeHalf));
        ui.gap(body,28);
        scrollers.addAll(team(detail.match.awayId,category,effective,descending,changed,scrollX,anyInjuries,awayHalf));
        return scrollers;
    }

    private List<HorizontalScrollView> team(String teamId,String category,String sort,boolean descending,Consumer<Sort> changed,int scrollX,boolean anyInjuries,String halfLine){
        List<MatchDetail.Table> tables=displayTables(teamTables(category,teamId));
        List<DetailRows.Entry> rows=DetailRows.select(tables,category,teamId,sort,descending);
        LinearLayout section=ui.column();section.setTag("team-panel");body.addView(section);
        LinearLayout header=ui.row();header.setTag("team-panel-header");header.setPadding(ui.dp(4),0,ui.dp(4),0);
        String name=teamName(teamId);
        header.addView(Logos.view(ui,teamLogo(teamId),name,22),new LinearLayout.LayoutParams(ui.dp(22),ui.dp(22)));
        TextView title=ui.text(name,14,ui.text,true);title.setPadding(ui.dp(8),0,0,0);
        header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        header.addView(ui.text(rows.size()+" 人",11,ui.muted,false));
        header.setContentDescription(name+"，"+rows.size()+" 人");
        section.addView(header);ui.gap(section,8);
        if(halfLine!=null&&!halfLine.isBlank()){
            TextView halves=ui.text(halfLine,11,ui.muted,false);halves.setTag("team-half-line");
            section.addView(halves);ui.gap(section,6);
        }
        List<HorizontalScrollView> scrollers=new ArrayList<>();
        if(rows.isEmpty()){
            section.addView(ui.text("来源未提供这支球队的数据。",13,ui.muted,false));
        }else{
            View table=new DetailTableView(ui,nba(),false,!nba()).build(tables.get(0).columns,rows,sort,descending,
                changed==null?null:label->changed.accept(new Sort(label,label.equals(sort)?!descending:true)),null,scrollX);
            table.setContentDescription(name+(nba()?" 球员数据":" 选手数据"));
            section.addView(table,new LinearLayout.LayoutParams(-1,-2));
            HorizontalScrollView scroller=table.findViewWithTag("detail-metrics-scroll");
            if(scroller!=null)scrollers.add(scroller);
        }
        if(anyInjuries){ui.gap(section,16);injuries(section,teamId);}
        return scrollers;
    }

    /** The injury block is the last child of its team panel, never a separate filter page. */
    private void injuries(LinearLayout section,String teamId){
        List<MatchDetail.Table> tables=injuryTables(teamId);
        List<DetailRows.Entry> entries=DetailRows.select(tables,INJURY,teamId,"",true);
        LinearLayout caption=ui.row();caption.setTag("team-panel-injury-header");caption.setPadding(ui.dp(4),0,ui.dp(4),0);
        caption.addView(ui.text(INJURY,13,ui.text,true),new LinearLayout.LayoutParams(0,-2,1));
        if(!entries.isEmpty())caption.addView(ui.text(entries.size()+" 人",11,ui.muted,false));
        caption.setContentDescription(INJURY+"，"+(entries.isEmpty()?"来源未提供伤停名单":entries.size()+" 人"));
        section.addView(caption);
        if(entries.isEmpty()){ui.gap(section,6);section.addView(ui.text("来源未提供伤停名单",12,ui.muted,false));return;}
        for(int i=0;i<entries.size();i++){
            if(i>0)section.addView(ui.divider());
            section.addView(injuryRow(entries.get(i),teamId));
        }
    }

    private View injuryRow(DetailRows.Entry entry,String teamId){
        MatchDetail.Person person=entry.person();
        String name=injuryName(entry),team=teamName(teamId);
        int statusIndex=entry.table().columns.indexOf("状态"),noteIndex=entry.table().columns.indexOf("说明");
        String status=DetailParsers.label(DetailRows.cell(entry.values(),statusIndex<0?1:statusIndex)).trim();
        String rawNote=DetailRows.cell(entry.values(),noteIndex<0?2:noteIndex);
        String note=rawNote.equals("—")?"":rawNote;
        LinearLayout row=ui.row();row.setGravity(Gravity.TOP);row.setTag("team-panel-injury");
        row.setMinimumHeight(ui.dp(48));row.setPadding(ui.dp(4),ui.dp(10),ui.dp(4),ui.dp(10));
        LinearLayout words=ui.column();
        words.addView(ui.text(name,13,ui.text,true));
        if(!note.isBlank()){ui.gap(words,3);words.addView(ui.text(note,12,ui.muted,false));}
        row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        if(!status.isBlank()&&!status.equals("—")){
            TextView badge=ui.text(status,11,ui.text,true);badge.setTag("team-panel-injury-status");
            badge.setGravity(Gravity.CENTER);badge.setPadding(ui.dp(6),ui.dp(2),ui.dp(6),ui.dp(2));
            badge.setBackground(ui.shape(ui.tonal,4));
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-2,-2);params.setMarginStart(ui.dp(8));
            row.addView(badge,params);
        }
        String description=name+"，"+status+(note.isBlank()?"":"，"+note);
        row.setContentDescription(description);row.setFocusable(true);
        ui.clickable(row,ui.bg,0);
        String fullName=person==null||person.name().isBlank()?name:person.name();
        row.setOnClickListener(v->{if(!(ui.c instanceof Activity))return;StringBuilder text=new StringBuilder("球队  ").append(team);if(!status.isBlank()&&!status.equals("—"))text.append('\n').append("状态  ").append(status);if(!note.isBlank())text.append('\n').append("说明  ").append(note);Sheet.message((Activity)ui.c,fullName,text.toString());});
        return row;
    }

    private String injuryName(DetailRows.Entry entry){
        MatchDetail.Person person=entry.person();
        if(person==null)return DetailRows.cell(entry.values(),0);
        String shortName=person.shortName();
        return shortName==null||shortName.isBlank()?DetailFormat.shortName(person.name()):shortName;
    }

    /** CS2 rating leads the numeric rail so the strongest column stays visible first. */
    private List<MatchDetail.Table> displayTables(List<MatchDetail.Table> source){
        if(nba())return source;
        List<MatchDetail.Table> tables=new ArrayList<>(source.size());
        for(MatchDetail.Table table:source)tables.add(ratingFirst(table));
        return tables;
    }
    private MatchDetail.Table ratingFirst(MatchDetail.Table source){
        int rating=source.columns.indexOf("Rating");
        if(rating<0||source.columns.isEmpty()||!source.columns.get(0).equals("选手"))return source;
        MatchDetail.Table display=new MatchDetail.Table(source.tab,source.title);
        display.teamId=source.teamId;display.category=source.category;
        display.columns.add(source.columns.get(0));display.columns.add("Rating");
        for(int i=1;i<source.columns.size();i++)if(i!=rating)display.columns.add(source.columns.get(i));
        for(List<String> row:source.rows){
            if(row.size()<=rating)return source;
            List<String> values=new ArrayList<>();values.add(row.get(0));values.add(row.get(rating));
            for(int i=1;i<row.size();i++)if(i!=rating)values.add(row.get(i));
            display.rows.add(values);
        }
        display.people.addAll(source.people);return display;
    }

    private String effectiveSort(List<String> columns,String sort){
        String value=sort==null?"":sort;
        if(!value.isBlank()&&columns.contains(value))return value;
        return nba()&&columns.contains("得分")?"得分":"";
    }

    private String scope(boolean nba){
        String preferred=nba?"球员":"全场",first="";
        for(MatchDetail.Table table:detail.tables){
            if(!table.tab.equals("lineup")||table.category.isBlank()||table.category.equals(INJURY))continue;
            if(table.category.equals(preferred))return preferred;
            if(first.isEmpty())first=table.category;
        }
        return nba&&!first.isEmpty()?first:preferred;
    }

    private List<MatchDetail.Table> teamTables(String category,String teamId){
        List<MatchDetail.Table> tables=new ArrayList<>();
        for(MatchDetail.Table table:detail.tables)if(table.tab.equals("lineup")&&table.category.equals(category)&&(teamId==null||table.teamId.equals(teamId)))tables.add(table);
        return tables;
    }
    private List<MatchDetail.Table> injuryTables(String teamId){
        List<MatchDetail.Table> tables=new ArrayList<>();
        for(MatchDetail.Table table:detail.tables)if(table.tab.equals("lineup")&&table.category.equals(INJURY)&&(teamId==null||table.teamId.equals(teamId)))tables.add(table);
        return tables;
    }
    private int countRows(List<MatchDetail.Table> tables){int count=0;for(MatchDetail.Table table:tables)count+=table.rows.size();return count;}
    private boolean nba(){return detail.match.group.equals("NBA");}
    private String teamName(String teamId){return teamId.equals(detail.match.awayId)?detail.match.away:detail.match.home;}
    private String teamLogo(String teamId){return teamId.equals(detail.match.awayId)?detail.match.awayLogo:detail.match.homeLogo;}

    private void bind(List<HorizontalScrollView> scrollers,IntConsumer savedScroll){
        if(scrollers.isEmpty())return;boolean[] moving={false};
        for(HorizontalScrollView scroller:scrollers)scroller.setOnScrollChangeListener((v,x,y,oldX,oldY)->{
            if(savedScroll!=null)savedScroll.accept(x);
            if(moving[0])return;moving[0]=true;
            for(HorizontalScrollView other:scrollers)if(other!=v)other.scrollTo(x,0);
            moving[0]=false;
        });
    }
}
