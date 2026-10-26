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
        boolean hasTeams=tables.stream().anyMatch(t->DetailRows.category(t).equals(category)&&!t.teamId.isBlank());
        if(hasTeams){LinearLayout teams=ui.row();teams.setTag("detail-team-filter");String[] ids={"all",detail.match.homeId,detail.match.awayId},names={"双方",detail.match.home,detail.match.away};for(int i=0;i<ids.length;i++){String id=ids[i];String name=names[i];TextView button=ui.chip(name,team.equals(id),()->changed.accept(new Selection(id,category,selected.sort,selected.descending)));button.setTextSize(12);button.setPadding(ui.dp(6),ui.dp(10),ui.dp(6),ui.dp(10));button.setContentDescription(name+(team.equals(id)?"，已选中":""));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);if(i>0)p.setMarginStart(ui.dp(8));teams.addView(button,p);}body.addView(teams);ui.gap(body,14);}
        MatchDetail.Table schema=tables.stream().filter(t->DetailRows.category(t).equals(category)).findFirst().orElseThrow();String sort=selected.sort;if(sort.isBlank()&&detail.match.group.equals("NBA")&&schema.columns.contains("得分"))sort="得分";
        String sorted=sort;List<DetailRows.Entry> allRows=DetailRows.select(tables,category,team,sorted,selected.descending);
        int total=allRows.size(),shown=Math.max(1,Math.min(limit,total));boolean truncated=total>shown;
        List<DetailRows.Entry> rows=truncated?new ArrayList<>(allRows.subList(0,shown)):allRows;
        LinearLayout caption=ui.row();TextView title=ui.text(detail.match.group.equals("NBA")?"球员数据":category,15,ui.text,true);caption.addView(title,new LinearLayout.LayoutParams(0,-2,1));caption.addView(ui.text(truncated?"显示 "+shown+" / "+total+" 人":total+" 人",11,ui.muted,false));body.addView(caption);ui.gap(body,8);
        if(rows.isEmpty()){body.addView(ui.text("这支队伍的本项数据尚未提供。",14,ui.muted,false));return;}
        TextView hint=ui.text("点姓名查看详情",11,ui.muted,false);body.addView(hint);ui.gap(body,8);
        View table=new DetailTableView(ui,detail.match.group.equals("NBA")).build(schema.columns,rows,sorted,selected.descending,label->changed.accept(new Selection(team,category,label,sorted.equals(label)?!selected.descending:true)),savedScroll,scrollX);body.addView(table,new LinearLayout.LayoutParams(-1,-2));
        if(truncated){ui.gap(body,10);body.addView(ui.button("显示更多 · 剩余 "+(total-shown)+" 人",false,more));}
        HorizontalScrollView metrics=table.findViewWithTag("detail-metrics-scroll");boolean sortable=false;for(DetailRows.Entry row:rows)for(int i=1;i<row.values().size();i++)if(!Double.isNaN(DetailRows.number(row.values().get(i))))sortable=true;boolean hasSort=sortable;metrics.post(()->hint.setText((hasSort?"点数值列排序 · ":"")+(metrics.canScrollHorizontally(1)||metrics.canScrollHorizontally(-1)?"横向滑动查看更多 · ":"")+"点姓名查看详情"));
    }
}
