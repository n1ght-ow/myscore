package cn.myscore.app;

import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.function.Consumer;

/** Team/category switches keep each sport's own schema instead of tall player cards. */
final class PlayerStatsView {
    record Selection(String team,String category,String sort,boolean descending){}
    private final Ui ui;private final MatchDetail detail;private final LinearLayout body;
    PlayerStatsView(Ui ui,MatchDetail detail,LinearLayout body){this.ui=ui;this.detail=detail;this.body=body;}
    void show(String tab,Selection selected,Consumer<Selection> changed,int scrollX,java.util.function.IntConsumer savedScroll,int limit,Runnable more){
        List<MatchDetail.Table> tables=new ArrayList<>();LinkedHashSet<String> categories=new LinkedHashSet<>();for(MatchDetail.Table t:detail.tables)if(t.tab.equals(tab)){tables.add(t);categories.add(DetailRows.category(t));}
        if(tables.isEmpty()){body.addView(ui.text("本场数据尚未提供",18,ui.text,true));ui.gap(body,10);body.addView(ui.text("来源更新后会显示本场真实数据。",13,ui.muted,false));return;}
        String category=categories.contains(selected.category)?selected.category:categories.iterator().next();String team=selected.team;
        if(categories.size()>1){HorizontalScrollView scroll=new HorizontalScrollView(ui.c);scroll.setHorizontalScrollBarEnabled(false);LinearLayout chips=ui.row();for(String item:categories){TextView chip=ui.chip(item,item.equals(category),()->changed.accept(new Selection(team,item,"",true)));chip.setContentDescription(item+(item.equals(category)?"，已选中":""));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,-2);cp.setMarginEnd(ui.dp(8));chips.addView(chip,cp);if(item.equals(category))chip.post(()->scroll.scrollTo(Math.max(0,chip.getLeft()-ui.dp(8)),0));}scroll.addView(chips);body.addView(scroll);ui.gap(body,12);}
        List<MatchDetail.Table> current=new ArrayList<>();for(MatchDetail.Table t:tables)if(DetailRows.category(t).equals(category))current.add(t);
        boolean hasTeams=current.stream().anyMatch(t->!t.teamId.isBlank());
        if(hasTeams){LinearLayout teams=ui.row();teams.setTag("detail-team-filter");String[] ids={"all",detail.match.homeId,detail.match.awayId},names={"双方",detail.match.home,detail.match.away};for(int i=0;i<ids.length;i++){String id=ids[i];String name=names[i];TextView button=ui.chip(name,team.equals(id),()->changed.accept(new Selection(id,category,selected.sort,selected.descending)));button.setTextSize(12);button.setPadding(ui.dp(6),ui.dp(10),ui.dp(6),ui.dp(10));button.setContentDescription(name+(team.equals(id)?"，已选中":""));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);if(i>0)p.setMarginStart(ui.dp(8));teams.addView(button,p);}body.addView(teams);ui.gap(body,14);}
        MatchDetail.Table schema=current.get(0);String sort=selected.sort;if(sort.isBlank()&&detail.match.group.equals("NBA")&&schema.columns.contains("得分"))sort="得分";
        String sorted=sort;List<DetailRows.Entry> allRows=DetailRows.select(tables,category,team,sorted,selected.descending);
        int total=allRows.size();
        LinearLayout caption=ui.row();TextView title=ui.text(detail.match.group.equals("NBA")?"球员数据":category,15,ui.text,true);caption.addView(title,new LinearLayout.LayoutParams(0,-2,1));TextView count=ui.text("",11,ui.muted,false);caption.addView(count);body.addView(caption);ui.gap(body,8);
        if(total==0){count.setText("0 人");body.addView(ui.text("这支队伍的本项数据尚未提供。",14,ui.muted,false));return;}
        // CS2 publishes one five-player table per team; keep both on screen as aligned panels.
        if(detail.match.group.equals("CS2")&&hasTeams){count.setText(total+" 人");teams(current,category,team,sorted,selected.descending,changed,scrollX,savedScroll);return;}
        int shown=Math.max(1,Math.min(limit,total));boolean truncated=total>shown;
        count.setText(truncated?"显示 "+shown+" / "+total+" 人":total+" 人");
        List<DetailRows.Entry> rows=truncated?new ArrayList<>(allRows.subList(0,shown)):allRows;
        TextView hint=ui.text("点姓名查看详情",11,ui.muted,false);body.addView(hint);ui.gap(body,8);
        View table=new DetailTableView(ui,detail.match.group.equals("NBA")).build(schema.columns,rows,sorted,selected.descending,label->changed.accept(new Selection(team,category,label,sorted.equals(label)?!selected.descending:true)),savedScroll,scrollX);body.addView(table,new LinearLayout.LayoutParams(-1,-2));
        if(truncated){ui.gap(body,10);body.addView(ui.button("显示更多 · 剩余 "+(total-shown)+" 人",false,more));}
        HorizontalScrollView metrics=table.findViewWithTag("detail-metrics-scroll");boolean sortable=false;for(DetailRows.Entry row:rows)for(int i=1;i<row.values().size();i++)if(!Double.isNaN(DetailRows.number(row.values().get(i))))sortable=true;boolean hasSort=sortable;metrics.post(()->hint.setText((hasSort?"点数值列排序 · ":"")+(metrics.canScrollHorizontally(1)||metrics.canScrollHorizontally(-1)?"横向滑动查看更多 · ":"")+"点姓名查看详情"));
    }
    private void teams(List<MatchDetail.Table> source,String category,String team,String sorted,boolean descending,Consumer<Selection> changed,int scrollX,java.util.function.IntConsumer savedScroll){
        List<MatchDetail.Table> chosen=new ArrayList<>();for(MatchDetail.Table t:source)if(team.equals("all")||team.equals(t.teamId))chosen.add(t);
        TextView hint=ui.text("",11,ui.muted,false);body.addView(hint);ui.gap(body,10);
        List<HorizontalScrollView> scrollers=new ArrayList<>();
        for(int index=0;index<chosen.size();index++){
            MatchDetail.Table table=ratingFirst(chosen.get(index));
            List<DetailRows.Entry> rows=DetailRows.select(List.of(table),category,table.teamId,sorted,descending);
            if(rows.isEmpty())continue;
            if(index>0)ui.gap(body,18);
            body.addView(teamHeader(table,rows.size()));
            ui.gap(body,8);
            View view=new DetailTableView(ui,false,false,true).build(table.columns,rows,sorted,descending,label->changed.accept(new Selection(team,category,label,sorted.equals(label)?!descending:true)),null,scrollX);
            view.setContentDescription(matchName(table.teamId)+" 选手数据");
            body.addView(view,new LinearLayout.LayoutParams(-1,-2));
            HorizontalScrollView scroller=view.findViewWithTag("detail-metrics-scroll");if(scroller!=null)scrollers.add(scroller);
        }
        if(scrollers.isEmpty())return;
        boolean[] moving={false};
        for(HorizontalScrollView scroller:scrollers)scroller.setOnScrollChangeListener((v,x,y,oldX,oldY)->{
            if(savedScroll!=null)savedScroll.accept(x);
            if(moving[0])return;moving[0]=true;
            for(HorizontalScrollView other:scrollers)if(other!=v)other.scrollTo(x,0);
            moving[0]=false;
        });
        hint.post(()->{boolean scroll=scrollers.get(0).canScrollHorizontally(1)||scrollers.get(0).canScrollHorizontally(-1);hint.setText("点数值列排序 · "+(scroll?"横向滑动同时查看两队 · ":"")+"点姓名查看详情");});
    }
    /** Rating leads the numeric columns; names keep a shorter rail so more stats stay visible. */
    private MatchDetail.Table ratingFirst(MatchDetail.Table source){
        int rating=source.columns.indexOf("Rating");
        if(rating<0||source.columns.isEmpty()||!source.columns.get(0).equals("选手"))return source;
        MatchDetail.Table display=new MatchDetail.Table(source.tab,source.title);display.teamId=source.teamId;display.category=source.category;
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
    private View teamHeader(MatchDetail.Table table,int count){
        LinearLayout header=ui.row();header.setTag("cs2-team-header");header.setPadding(ui.dp(4),0,ui.dp(4),0);
        String name=matchName(table.teamId);header.addView(Logos.view(ui,logo(table.teamId),name,22),new LinearLayout.LayoutParams(ui.dp(22),ui.dp(22)));
        TextView title=ui.text(name,14,ui.text,true);title.setPadding(ui.dp(8),0,0,0);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        header.addView(ui.text(count+" 人",11,ui.muted,false));
        header.setContentDescription(name+"，"+count+" 人");
        return header;
    }
    private String matchName(String teamId){return teamId.equals(detail.match.awayId)?detail.match.away:detail.match.home;}
    private String logo(String teamId){return teamId.equals(detail.match.awayId)?detail.match.awayLogo:detail.match.homeLogo;}
}
