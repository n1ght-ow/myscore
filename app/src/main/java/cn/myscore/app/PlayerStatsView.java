package cn.myscore.app;

import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.function.Consumer;

/** Category switches keep each remaining sport's own schema instead of tall player cards. */
final class PlayerStatsView {
    record Selection(String team,String category,String sort,boolean descending){}
    private final Ui ui;private final MatchDetail detail;private final LinearLayout body;
    PlayerStatsView(Ui ui,MatchDetail detail,LinearLayout body){this.ui=ui;this.detail=detail;this.body=body;}
    void show(String tab,Selection selected,Consumer<Selection> changed,int scrollX,java.util.function.IntConsumer savedScroll,int limit,Runnable more){
        List<MatchDetail.Table> tables=new ArrayList<>();LinkedHashSet<String> categories=new LinkedHashSet<>();for(MatchDetail.Table t:detail.tables)if(t.tab.equals(tab)){tables.add(t);categories.add(DetailRows.category(t));}
        if(tables.isEmpty()){body.addView(ui.text("Match data is not available",18,ui.text,true));ui.gap(body,10);body.addView(ui.text("Source-backed match data will appear after the next update.",13,ui.muted,false));return;}
        String category=categories.contains(selected.category)?selected.category:categories.iterator().next();String team=selected.team;
        if(categories.size()>1){HorizontalScrollView scroll=new HorizontalScrollView(ui.c);scroll.setHorizontalScrollBarEnabled(false);LinearLayout chips=ui.row();for(String item:categories){String label=EnglishDisplay.text(item);TextView chip=ui.chip(label,item.equals(category),()->changed.accept(new Selection(team,item,"",true)));chip.setContentDescription(label+(item.equals(category)?", selected":""));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,-2);cp.setMarginEnd(ui.dp(8));chips.addView(chip,cp);if(item.equals(category))chip.post(()->scroll.scrollTo(Math.max(0,chip.getLeft()-ui.dp(8)),0));}scroll.addView(chips);body.addView(scroll);ui.gap(body,8);}
        List<MatchDetail.Table> current=new ArrayList<>();for(MatchDetail.Table t:tables)if(DetailRows.category(t).equals(category))current.add(t);
        boolean hasTeams=current.stream().anyMatch(t->!t.teamId.isBlank());
        if(hasTeams){LinearLayout teams=ui.segmentedTrack();teams.setTag("detail-team-filter");String[] ids={"all",detail.match.homeId,detail.match.awayId},names={"Both",detail.match.home,detail.match.away};for(int i=0;i<ids.length;i++){String id=ids[i];String name=names[i];TextView button=ui.tab(name,team.equals(id),()->changed.accept(new Selection(id,category,selected.sort,selected.descending)));button.setTextSize(12);button.setPadding(ui.dp(10),ui.dp(8),ui.dp(10),ui.dp(8));button.setContentDescription(name+(team.equals(id)?", selected":""));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);if(i>0)p.setMarginStart(ui.dp(4));teams.addView(button,p);}HorizontalScrollView teamScroll=new HorizontalScrollView(ui.c);teamScroll.setHorizontalScrollBarEnabled(false);teamScroll.addView(teams);body.addView(teamScroll);ui.gap(body,10);}
        MatchDetail.Table schema=current.get(0);String sorted=selected.sort;List<DetailRows.Entry> allRows=DetailRows.select(tables,category,team,sorted,selected.descending);
        int total=allRows.size();
        TextView count=Ui.singleLine(ui.text("",11,ui.dim,false));body.addView(count);ui.gap(body,6);
        if(total==0){count.setText(players(0));body.addView(ui.text("No player data is available for this team.",14,ui.muted,false));return;}
        int shown=Math.max(1,Math.min(limit,total));boolean truncated=total>shown;
        String countText=truncated?"Showing "+shown+" of "+players(total):players(total);count.setText(countText+" · Tap column to sort");
        List<DetailRows.Entry> rows=truncated?new ArrayList<>(allRows.subList(0,shown)):allRows;
        View table=new DetailTableView(ui,false,true,false,0,detail.match.group.equals("NFL")).build(schema.columns,rows,sorted,selected.descending,label->changed.accept(new Selection(team,category,label,sorted.equals(label)?!selected.descending:true)),savedScroll,scrollX);body.addView(table,new LinearLayout.LayoutParams(-1,-2));
        if(truncated){ui.gap(body,10);body.addView(ui.button("Show more · "+players(total-shown)+" remaining",false,more));}
    }
    private static String players(int count){return count+" "+(count==1?"player":"players");}
}
