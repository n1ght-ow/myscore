package cn.myscore.app;

import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.function.IntConsumer;

/** Native per-game match centre; neutral UI with real champion/item artwork. */
final class LolMatchView {
    private final Ui ui;private final MatchDetail detail;private final LinearLayout body;
    private final IntConsumer select;
    LolMatchView(Ui ui,MatchDetail detail,LinearLayout body,IntConsumer select){this.ui=ui;this.detail=detail;this.body=body;this.select=select;}
    LolGame game(int selected){for(LolGame g:detail.lolGames)if(g.number==selected)return g;for(LolGame g:detail.lolGames)if(g.live)return g;return detail.lolGames.isEmpty()?null:detail.lolGames.get(detail.lolGames.size()-1);}
    void games(int selected,boolean stats){
        LolGame game=game(selected);
        if(game==null){heading("小局数据尚未提供");body.addView(ui.text("来源尚未返回本场小局的阵容和统计，稍后将自动检查。",14,ui.muted,false));return;}
        selector(game);ui.gap(body,12);score(game);
        if(!stats){heading("目标与经济");objectives(game);heading("双方阵容");players(game);}
        else {heading("选手数据与出装");for(LolGame.Team team:new LolGame.Team[]{game.home,game.away}){String name=team==game.home?detail.match.home:detail.match.away;if(team.players.isEmpty()){body.addView(ui.text(name+" 本局选手统计尚未提供。",14,ui.muted,false));ui.gap(body,12);}for(LolGame.Player player:team.players)playerStats(player,name);}}
    }
    void overview(){
        heading("小局比分");
        if(detail.lolGames.isEmpty()){body.addView(ui.text("本场小局数据尚未发布。",14,ui.muted,false));return;}
        for(LolGame game:detail.lolGames){
            LinearLayout line=ui.row();line.setGravity(Gravity.CENTER_VERTICAL);line.setPadding(ui.dp(14),ui.dp(14),ui.dp(14),ui.dp(14));ui.clickable(line,ui.surface,Ui.CARD_RADIUS);line.setMinimumHeight(ui.dp(64));line.setFocusable(true);line.setContentDescription("查看"+game.title());
            LinearLayout label=ui.column();label.addView(ui.text(game.title(),14,ui.text,true));ui.gap(label,5);label.addView(ui.text((game.live?"进行中":"已结束")+" · "+game.clock(),12,ui.muted,false));line.addView(label,new LinearLayout.LayoutParams(0,-2,1));
            String winner=game.winner(detail.match);LinearLayout result=ui.column();result.setGravity(Gravity.END);result.addView(ui.text(game.home.value("kills")+" : "+game.away.value("kills"),20,ui.text,true));if(!winner.isBlank()){ui.gap(result,5);result.addView(ui.text(winner+" 获胜",12,ui.muted,false));}line.addView(result);Ui.Icon arrow=new Ui.Icon(ui.c,"chevron",ui.muted);LinearLayout.LayoutParams icon=new LinearLayout.LayoutParams(ui.dp(18),ui.dp(18));icon.setMarginStart(ui.dp(10));line.addView(arrow,icon);line.setOnClickListener(v->select.accept(game.number));body.addView(line,new LinearLayout.LayoutParams(-1,-2));ui.gap(body,8);
        }
        LolGame latest=game(-1);heading("参赛选手 · "+latest.title());players(latest);
    }
    private void selector(LolGame selected){
        HorizontalScrollView scroller=new HorizontalScrollView(ui.c);scroller.setHorizontalScrollBarEnabled(false);LinearLayout row=ui.row();
        for(LolGame game:detail.lolGames){boolean chosen=game.number==selected.number;TextView chip=ui.chip(game.title()+(game.live?" · 进行中":""),chosen,()->select.accept(game.number));chip.setContentDescription(game.title()+(chosen?"，已选中":""));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.setMarginEnd(ui.dp(8));row.addView(chip,p);if(chosen)chip.post(()->scroller.smoothScrollTo(Math.max(0,chip.getLeft()-ui.dp(8)),0));}scroller.addView(row);body.addView(scroller,new LinearLayout.LayoutParams(-1,-2));
    }
    private void score(LolGame game){
        LinearLayout panel=ui.column();panel.setPadding(ui.dp(14),ui.dp(12),ui.dp(14),ui.dp(12));panel.setBackground(ui.panel());LinearLayout line=ui.row();line.setGravity(Gravity.CENTER);
        line.addView(team(detail.match.home,detail.match.homeLogo,game.home.blue,game.sideKnown(detail.match)),new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout center=ui.column();center.setGravity(Gravity.CENTER);center.addView(ui.text("击杀",12,ui.muted,false));ui.gap(center,6);center.addView(ui.score(game.home.value("kills"),game.away.value("kills"),true,24),new LinearLayout.LayoutParams(-1,-2));ui.gap(center,8);TextView time=ui.text(game.clock(),14,ui.text,true);time.setContentDescription("来源比赛用时 "+game.clock());center.addView(time);ui.gap(center,5);center.addView(ui.text(game.live?"进行中 · 来源计时":"已结束",11,ui.muted,false));for(int ci=0;ci<center.getChildCount();ci++)if(center.getChildAt(ci) instanceof TextView)((TextView)center.getChildAt(ci)).setGravity(Gravity.CENTER);line.addView(center,new LinearLayout.LayoutParams(0,-2,1.2f));line.addView(team(detail.match.away,detail.match.awayLogo,game.away.blue,game.sideKnown(detail.match)),new LinearLayout.LayoutParams(0,-2,1));panel.addView(line);body.addView(panel,new LinearLayout.LayoutParams(-1,-2));
    }
    private LinearLayout team(String name,String logo,boolean blue,boolean sideKnown){LinearLayout side=ui.column();side.setGravity(Gravity.CENTER);side.addView(Logos.view(ui,logo,name,30));ui.gap(side,8);TextView text=ui.text(name,13,ui.text,true);text.setGravity(Gravity.CENTER);side.addView(text);if(sideKnown){ui.gap(side,4);TextView label=ui.text(blue?"蓝方":"红方",11,ui.muted,false);label.setGravity(Gravity.CENTER);side.addView(label);}return side;}
    private void heading(String text){ui.gap(body,16);body.addView(ui.text(text,15,ui.text,true));ui.gap(body,10);}
    private void objectives(LolGame game){
        LinearLayout panel=ui.column();panel.setPadding(ui.dp(14),ui.dp(14),ui.dp(14),ui.dp(2));panel.setBackground(ui.panel());
        String[][] metrics={{"golds","经济"},{"turretAmount","推塔"},{"dragonAmount","小龙"},{"baronAmount","大龙"},{"riftHeraldAmount","先锋"},{"voidGrubAmount","巢虫"},{"elderDragonAmount","远古龙"},{"atakhanAmount","厄塔汗"}};
        for(int i=0;i<metrics.length;i+=4){LinearLayout row=ui.row();row.setGravity(Gravity.TOP);for(int j=i;j<Math.min(i+4,metrics.length);j++){LinearLayout field=ui.column();field.setPadding(ui.dp(3),0,ui.dp(3),ui.dp(14));TextView label=ui.text(metrics[j][1],12,ui.muted,false);label.setGravity(Gravity.CENTER);field.addView(label);ui.gap(field,7);String home=game.home.value(metrics[j][0]),away=game.away.value(metrics[j][0]);boolean gold=metrics[j][0].equals("golds");TextView value=ui.text((gold?compactGold(home):home)+" : "+(gold?compactGold(away):away),13,ui.text,true);value.setGravity(Gravity.CENTER);value.setContentDescription(detail.match.home+" "+home+"，"+detail.match.away+" "+away);value.setTooltipText(home+" : "+away);field.addView(value);row.addView(field,new LinearLayout.LayoutParams(0,-2,1));}panel.addView(row);}body.addView(panel,new LinearLayout.LayoutParams(-1,-2));
    }
    private static String compactGold(String value){try{return String.format(Locale.ROOT,"%.1fk",Double.parseDouble(value)/1000);}catch(NumberFormatException e){return value;}}
    private void players(LolGame game){
        LinearLayout panel=ui.column();panel.setTag("lol-lineup");panel.setPadding(ui.dp(12),0,ui.dp(12),0);panel.setBackground(ui.panel());Set<String> roles=new LinkedHashSet<>(List.of("TOP","JUNGLE","MID","BOT","SUPPORT"));for(LolGame.Player p:game.home.players)roles.add(p.role());for(LolGame.Player p:game.away.players)roles.add(p.role());
        int index=0;for(String role:roles){if(index++>0)panel.addView(ui.divider());LinearLayout pair=ui.column();pair.setPadding(0,ui.dp(12),0,ui.dp(14));TextView roleText=ui.text(LolGame.roleName(role),11,ui.muted,true);roleText.setGravity(Gravity.CENTER);pair.addView(roleText);ui.gap(pair,10);LinearLayout sides=ui.row();sides.setGravity(Gravity.TOP);sides.addView(player(game.home.atRole(role),false),new LinearLayout.LayoutParams(0,-2,1));View gap=new View(ui.c);sides.addView(gap,new LinearLayout.LayoutParams(ui.dp(18),1));sides.addView(player(game.away.atRole(role),true),new LinearLayout.LayoutParams(0,-2,1));pair.addView(sides);panel.addView(pair);}body.addView(panel,new LinearLayout.LayoutParams(-1,-2));
    }
    private LinearLayout player(LolGame.Player player,boolean right){
        LinearLayout block=ui.column();block.setGravity(right?Gravity.END:Gravity.START);
        if(player==null){block.addView(ui.text("暂未提供",13,ui.muted,false));return block;}
        TextView name=ui.text(player.name(),13,ui.text,true);name.setGravity(right?Gravity.END:Gravity.START);block.addView(name,new LinearLayout.LayoutParams(-1,-2));ui.gap(block,8);
        LinearLayout hero=ui.row();hero.setGravity(right?Gravity.END:Gravity.START);FrameLayout portrait=new FrameLayout(ui.c);portrait.addView(Logos.artwork(ui,player.icon(),player.hero(),36));TextView level=ui.text(player.level(),11,ui.onPrimary,true);level.setGravity(Gravity.CENTER);level.setBackground(ui.shape(ui.primary,10));FrameLayout.LayoutParams badge=new FrameLayout.LayoutParams(-2,ui.dp(18),Gravity.BOTTOM|Gravity.END);level.setMinWidth(ui.dp(20));portrait.addView(level,badge);TextView champion=ui.text(player.hero(),12,ui.muted,false);champion.setGravity(right?Gravity.END:Gravity.START);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,-2,1);if(right){hp.setMarginEnd(ui.dp(7));hero.addView(champion,hp);hero.addView(portrait,new LinearLayout.LayoutParams(ui.dp(36),ui.dp(36)));}else{hero.addView(portrait,new LinearLayout.LayoutParams(ui.dp(36),ui.dp(36)));hp.setMarginStart(ui.dp(7));hero.addView(champion,hp);}block.addView(hero,new LinearLayout.LayoutParams(-1,-2));ui.gap(block,9);
        block.addView(ui.text(player.kda(),13,ui.text,true));ui.gap(block,5);TextView farming=ui.text("补刀 "+LolGame.value(player.data,"minionKilled")+" · 经济 "+LolGame.value(DetailParsers.obj(player.data,"otherDetail"),"golds"),11,ui.muted,false);farming.setGravity(right?Gravity.END:Gravity.START);block.addView(farming);
        LinearLayout spells=ui.row();spells.setGravity(right?Gravity.END:Gravity.START);for(String key:List.of("spell1Name","spell2Name")){String id=player.data.optString(key);if(!id.isBlank()){LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(ui.dp(20),ui.dp(20));sp.setMarginEnd(ui.dp(4));spells.addView(Logos.artwork(ui,LolGame.asset("spell",id),"技能",20),sp);}}if(spells.getChildCount()>0){ui.gap(block,7);block.addView(spells);}
        return block;
    }
    private void playerStats(LolGame.Player player,String team){
        LinearLayout block=ui.column();block.setPadding(ui.dp(14),ui.dp(14),ui.dp(14),ui.dp(14));block.setBackground(ui.panel());block.addView(ui.text(player.name()+" · "+team,14,ui.text,true));ui.gap(block,6);block.addView(ui.text(player.hero()+" · "+LolGame.roleName(player.role())+" · "+player.kda(),13,ui.muted,false));ui.gap(block,10);
        JSONObject damage=DetailParsers.obj(player.data,"damageDetail"),vision=DetailParsers.obj(player.data,"visionDetail");block.addView(ui.text("伤害 "+LolGame.value(damage,"heroDamage")+"   视野 "+LolGame.value(vision,"visionScore"),13,ui.text,false));ui.gap(block,10);
        JSONArray items=DetailParsers.arr(player.data,"items");LinearLayout equipment=ui.row();List<String> names=new ArrayList<>();for(int i=0;i<Math.min(items.length(),7);i++){JSONObject item=DetailParsers.objAt(items,i);String id=item.optString("itemId");if(!id.matches("[1-9][0-9]*"))continue;String name=item.optString("itemName","装备");names.add(name);FrameLayout art=Logos.artwork(ui,LolGame.asset("item",id),name,28);art.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);art.setContentDescription(name);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ui.dp(28),ui.dp(28));p.setMarginEnd(ui.dp(4));equipment.addView(art,p);}block.addView(equipment);ui.gap(block,7);block.addView(ui.text(names.isEmpty()?"装备尚未提供":String.join("、",names),12,ui.muted,false));body.addView(block,new LinearLayout.LayoutParams(-1,-2));ui.gap(body,8);
    }
}
