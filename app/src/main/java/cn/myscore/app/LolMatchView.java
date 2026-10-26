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
    private final int positionWidth,kdaWidth,csWidth,goldWidth,spellWidth;
    private String selectedTeam="";
    private java.util.function.Consumer<String> selectTeam;
    LolMatchView(Ui ui,MatchDetail detail,LinearLayout body,IntConsumer select){this.ui=ui;this.detail=detail;this.body=body;this.select=select;positionWidth=ui.dp(30);kdaWidth=ui.dp(64);csWidth=ui.dp(32);goldWidth=ui.dp(44);spellWidth=ui.dp(40);}
    LolGame game(int selected){for(LolGame g:detail.lolGames)if(g.number==selected)return g;for(LolGame g:detail.lolGames)if(g.live)return g;return detail.lolGames.isEmpty()?null:detail.lolGames.get(detail.lolGames.size()-1);}
    void games(int selected,boolean stats){
        LolGame game=game(selected);
        if(game==null){heading("小局数据尚未提供");body.addView(ui.text("来源尚未返回本场小局的阵容和统计，稍后将自动检查。",14,ui.muted,false));return;}
        selector(game);ui.gap(body,12);
        if(!stats){score(game);heading("目标与经济");objectives(game);heading("位置对位");players(game);}
        else {
            boolean home=!selectedTeam.equals(detail.match.awayId);LinearLayout teams=ui.row();
            String[] ids={detail.match.homeId,detail.match.awayId},names={detail.match.home,detail.match.away};
            for(int i=0;i<2;i++){String id=ids[i];TextView chip=ui.chip(names[i],i==0?home:!home,()->{if(selectTeam!=null)selectTeam.accept(id);});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);if(i>0)p.setMarginStart(ui.dp(8));teams.addView(chip,p);}body.addView(teams);
            heading("伤害、视野与出装");body.addView(ui.text("对英雄伤害 · 本局最终装备",12,ui.muted,false));ui.gap(body,12);
            LolGame.Team team=home?game.home:game.away;String name=home?detail.match.home:detail.match.away;
            if(team.players.isEmpty())body.addView(ui.text("本局选手统计尚未提供。",14,ui.muted,false));
            for(LolGame.Player player:team.players)playerStats(player,name);
        }
    }
    void teamSelection(String team,java.util.function.Consumer<String> changed){selectedTeam=team;selectTeam=changed;}
    void overview(){
        heading("小局比分");
        if(detail.lolGames.isEmpty()){body.addView(ui.text("本场小局数据尚未发布。",14,ui.muted,false));return;}
        for(LolGame game:detail.lolGames){
            LinearLayout line=ui.row();line.setGravity(Gravity.CENTER_VERTICAL);line.setPadding(ui.dp(14),ui.dp(14),ui.dp(14),ui.dp(14));ui.clickable(line,ui.surface,Ui.CARD_RADIUS);line.setMinimumHeight(ui.dp(64));line.setFocusable(true);line.setContentDescription("查看"+game.title());
            LinearLayout label=ui.column();label.addView(ui.text(game.title(),14,ui.text,true));ui.gap(label,5);label.addView(ui.text((game.live?"进行中":"已结束")+" · "+game.clock(),12,ui.muted,false));line.addView(label,new LinearLayout.LayoutParams(0,-2,1));
            String winner=game.winner(detail.match);TextView result=ui.text(winner.isBlank()?"查看战况":winner+" 获胜",14,winner.isBlank()?ui.text:ui.primary,true);result.setGravity(Gravity.END);line.addView(result,new LinearLayout.LayoutParams(0,-2,1));Ui.Icon arrow=new Ui.Icon(ui.c,"chevron",ui.muted);LinearLayout.LayoutParams icon=new LinearLayout.LayoutParams(ui.dp(18),ui.dp(18));icon.setMarginStart(ui.dp(10));line.addView(arrow,icon);line.setOnClickListener(v->select.accept(game.number));body.addView(line,new LinearLayout.LayoutParams(-1,-2));ui.gap(body,8);
        }
    }
    private void selector(LolGame selected){
        HorizontalScrollView scroller=new HorizontalScrollView(ui.c);scroller.setHorizontalScrollBarEnabled(false);LinearLayout row=ui.row();
        for(LolGame game:detail.lolGames){boolean chosen=game.number==selected.number;TextView chip=ui.chip(game.title()+(game.live?" · 进行中":""),chosen,()->select.accept(game.number));chip.setContentDescription(game.title()+(chosen?"，已选中":""));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.setMarginEnd(ui.dp(8));row.addView(chip,p);if(chosen)chip.post(()->scroller.smoothScrollTo(Math.max(0,chip.getLeft()-ui.dp(8)),0));}scroller.addView(row);body.addView(scroller,new LinearLayout.LayoutParams(-1,-2));
    }
    private void score(LolGame game){
        LinearLayout panel=ui.column();panel.setPadding(ui.dp(14),ui.dp(12),ui.dp(14),ui.dp(12));panel.setBackground(ui.panel());LinearLayout line=ui.row();line.setGravity(Gravity.CENTER);
        line.addView(team(detail.match.home,detail.match.homeLogo,game.home.blue,game.sideKnown(detail.match)),new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout center=ui.column();center.setGravity(Gravity.CENTER);center.addView(ui.text("击杀",12,ui.muted,false));ui.gap(center,6);Ui.ScoreText kills=ui.score(game.home.value("kills"),game.away.value("kills"),true,24);String winner=game.data.optString("matchWin");boolean decided=!game.live&&(winner.equals(detail.match.homeId)||winner.equals(detail.match.awayId));kills.setResultColors(decided?(winner.equals(detail.match.homeId)?ui.primary:ui.error):ui.text,decided?(winner.equals(detail.match.awayId)?ui.primary:ui.error):ui.text);center.addView(kills,new LinearLayout.LayoutParams(-1,-2));ui.gap(center,8);TextView time=ui.text(game.clock(),14,ui.text,true);time.setContentDescription("来源比赛用时 "+game.clock());center.addView(time);ui.gap(center,5);center.addView(ui.text(game.live?"进行中 · 来源计时":"已结束",11,ui.muted,false));for(int ci=0;ci<center.getChildCount();ci++)if(center.getChildAt(ci) instanceof TextView)((TextView)center.getChildAt(ci)).setGravity(Gravity.CENTER);line.addView(center,new LinearLayout.LayoutParams(0,-2,1.2f));line.addView(team(detail.match.away,detail.match.awayLogo,game.away.blue,game.sideKnown(detail.match)),new LinearLayout.LayoutParams(0,-2,1));panel.addView(line);body.addView(panel,new LinearLayout.LayoutParams(-1,-2));
    }
    private LinearLayout team(String name,String logo,boolean blue,boolean sideKnown){LinearLayout side=ui.column();side.setGravity(Gravity.CENTER);side.addView(Logos.view(ui,logo,name,30));ui.gap(side,8);TextView text=ui.text(name,13,ui.text,true);text.setGravity(Gravity.CENTER);side.addView(text);if(sideKnown){ui.gap(side,4);TextView label=ui.text(blue?"蓝方":"红方",11,ui.muted,false);label.setGravity(Gravity.CENTER);side.addView(label);}return side;}
    private void heading(String text){ui.gap(body,16);body.addView(ui.text(text,15,ui.text,true));ui.gap(body,10);}
    private void objectives(LolGame game){
        LinearLayout panel=ui.column();panel.setPadding(ui.dp(16),ui.dp(14),ui.dp(16),ui.dp(6));panel.setBackground(ui.panel());
        LinearLayout economy=ui.row();String h=game.home.value("golds"),a=game.away.value("golds");TextView left=ui.text(compactGold(h),18,ui.text,true),label=ui.text("经济",12,ui.muted,false),right=ui.text(compactGold(a),18,ui.text,true);left.setTag("lol-economy-home");right.setTag("lol-economy-away");label.setGravity(Gravity.CENTER);right.setGravity(Gravity.END);economy.addView(left,new LinearLayout.LayoutParams(0,-2,1));economy.addView(label,new LinearLayout.LayoutParams(0,-2,1));economy.addView(right,new LinearLayout.LayoutParams(0,-2,1));panel.addView(economy);ui.gap(panel,14);panel.addView(ui.divider());ui.gap(panel,8);
        String[][] metrics={{"turretAmount","推塔"},{"dragonAmount","小龙"},{"baronAmount","大龙"},{"riftHeraldAmount","先锋"},{"voidGrubAmount","巢虫"},{"elderDragonAmount","远古龙"},{"atakhanAmount","厄塔汗"}};
        List<String[]> available=new ArrayList<>();for(String[] metric:metrics)if(!game.home.value(metric[0]).equals("—")||!game.away.value(metric[0]).equals("—"))available.add(metric);
        int perRow=ui.c.getResources().getConfiguration().fontScale>=1.5f?1:2;
        for(int i=0;i<available.size();i+=perRow){LinearLayout row=ui.row();row.setGravity(Gravity.TOP);for(int j=i;j<Math.min(i+perRow,available.size());j++){String[] metric=available.get(j);LinearLayout field=ui.row();field.setPadding(0,ui.dp(10),ui.dp(12),ui.dp(10));TextView title=ui.text(metric[1],12,ui.muted,false),value=ui.text(game.home.value(metric[0])+" : "+game.away.value(metric[0]),14,ui.text,true);value.setGravity(Gravity.END);field.addView(title,new LinearLayout.LayoutParams(0,-2,1));field.addView(value,new LinearLayout.LayoutParams(0,-2,1));row.addView(field,new LinearLayout.LayoutParams(0,-2,1));}panel.addView(row);}body.addView(panel,new LinearLayout.LayoutParams(-1,-2));
    }
    private static String compactGold(String value){try{return String.format(Locale.ROOT,"%.1fk",Double.parseDouble(value)/1000);}catch(NumberFormatException e){return value;}}
    private void players(LolGame game){
        LinearLayout panel=ui.column();panel.setTag("lol-lineup");panel.setPadding(ui.dp(12),0,ui.dp(12),0);panel.setBackground(ui.panel());Set<String> roles=new LinkedHashSet<>(List.of("TOP","JUNGLE","MID","BOT","SUPPORT"));for(LolGame.Player p:game.home.players)roles.add(p.role());for(LolGame.Player p:game.away.players)roles.add(p.role());
        panel.addView(header());
        int index=0;for(String role:roles){if(index++>0)panel.addView(ui.divider());panel.addView(row(game,role,game.home.atRole(role),true));panel.addView(row(game,role,game.away.atRole(role),false));}body.addView(panel,new LinearLayout.LayoutParams(-1,-2));
    }
    /** A single header labels the columns once; each role contributes a home/away row pair. */
    private View header(){
        LinearLayout header=ui.row();header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(ui.dp(8),ui.dp(7),ui.dp(8),ui.dp(7));header.setBackground(ui.shape(ui.tonal,6));header.setTag("lol-table-header");
        header.addView(headerLabel("位置"),new LinearLayout.LayoutParams(positionWidth,-2));
        header.addView(headerLabel("选手"),new LinearLayout.LayoutParams(0,-2,1));
        header.addView(headerLabel("KDA"),new LinearLayout.LayoutParams(kdaWidth,-2));
        header.addView(headerLabel("补刀"),new LinearLayout.LayoutParams(csWidth,-2));
        header.addView(headerLabel("经济"),new LinearLayout.LayoutParams(goldWidth,-2));
        header.addView(headerLabel("技能"),new LinearLayout.LayoutParams(spellWidth,-2));
        return header;
    }
    private TextView headerLabel(String text){TextView value=ui.text(text,10,ui.muted,true);value.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);return value;}
    private View row(LolGame game,String role,LolGame.Player player,boolean home){
        LinearLayout row=ui.row();row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(ui.dp(8),ui.dp(5),ui.dp(8),ui.dp(5));
        TextView position=ui.text(home?LolGame.roleName(role):"",10,ui.muted,true);position.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        if(home)position.setTag("lol-role-label");
        row.addView(position,new LinearLayout.LayoutParams(positionWidth,-2));
        row.addView(identity(player,home?detail.match.home:detail.match.away),new LinearLayout.LayoutParams(0,-2,1));
        TextView kda=value(player==null?"—":player.kda(),true);kda.setTag(home?"lol-kda-home":"lol-kda-away");row.addView(kda,new LinearLayout.LayoutParams(kdaWidth,-2));
        row.addView(value(stat(player,"minionKilled"),false),new LinearLayout.LayoutParams(csWidth,-2));
        row.addView(value(gold(player),false),new LinearLayout.LayoutParams(goldWidth,-2));
        row.addView(spellIcons(player),new LinearLayout.LayoutParams(spellWidth,-2));
        return row;
    }
    private TextView value(String text,boolean strong){TextView value=ui.text(text,strong?12:11,text.equals("—")?ui.muted:ui.text,strong);value.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);return value;}
    private View identity(LolGame.Player player,String team){
        LinearLayout side=ui.row();side.setGravity(Gravity.CENTER_VERTICAL);
        if(player==null){side.addView(ui.text("暂未提供",11,ui.muted,false),new LinearLayout.LayoutParams(-1,-2));return side;}
        FrameLayout icon=new FrameLayout(ui.c);icon.addView(Logos.artwork(ui,player.icon(),player.hero(),24));side.addView(icon,new LinearLayout.LayoutParams(ui.dp(24),ui.dp(24)));
        LinearLayout words=ui.column();LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(0,-2,1);wp.setMarginStart(ui.dp(6));words.addView(ui.text(playerName(player,team),12,ui.text,true),new LinearLayout.LayoutParams(-1,-2));String champion=player.hero()+(player.level().isBlank()?"":" · "+player.level());words.addView(ui.text(champion,10,ui.muted,false),new LinearLayout.LayoutParams(-1,-2));
        side.addView(words,wp);return side;
    }
    private View spellIcons(LolGame.Player player){
        LinearLayout icons=ui.row();icons.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        if(player!=null)for(String key:List.of("spell1Name","spell2Name")){String id=player.data.optString(key);if(id.isBlank())continue;LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ui.dp(16),ui.dp(16));lp.setMarginEnd(ui.dp(3));icons.addView(Logos.artwork(ui,LolGame.asset("spell",id),"技能",16),lp);}
        return icons;
    }
    private static String stat(LolGame.Player player,String key){return player==null?"—":LolGame.value(player.data,key);}
    private static String gold(LolGame.Player player){return player==null?"—":compactGold(LolGame.value(DetailParsers.obj(player.data,"otherDetail"),"golds"));}
    private void playerStats(LolGame.Player player,String team){
        LinearLayout block=ui.column();block.setPadding(ui.dp(16),ui.dp(14),ui.dp(16),ui.dp(14));block.setBackground(ui.panel());LinearLayout identity=ui.row();identity.addView(Logos.artwork(ui,player.icon(),player.hero(),36));LinearLayout words=ui.column();words.setPadding(ui.dp(10),0,0,0);words.addView(ui.text(playerName(player,team),15,ui.text,true));words.addView(ui.text(LolGame.roleName(player.role())+" · "+player.hero(),12,ui.muted,false));identity.addView(words,new LinearLayout.LayoutParams(0,-2,1));block.addView(identity);ui.gap(block,14);
        JSONObject damage=DetailParsers.obj(player.data,"damageDetail"),vision=DetailParsers.obj(player.data,"visionDetail");LinearLayout values=ui.row();for(String[] metric:new String[][]{{"对英雄伤害",DetailFormat.damage(LolGame.value(damage,"heroDamage"))},{"视野得分",DetailFormat.number(LolGame.value(vision,"visionScore"),1)}}){LinearLayout field=ui.column();field.addView(ui.text(metric[0],11,ui.muted,false));field.addView(ui.text(metric[1],20,ui.text,true));values.addView(field,new LinearLayout.LayoutParams(0,-2,1));}block.addView(values);ui.gap(block,14);
        JSONArray items=DetailParsers.arr(player.data,"items");LinearLayout equipment=ui.row();List<String> names=new ArrayList<>();for(int i=0;i<Math.min(items.length(),7);i++){JSONObject item=DetailParsers.objAt(items,i);String id=item.optString("itemId");if(!id.matches("[1-9][0-9]*"))continue;String name=item.optString("itemName","装备");names.add(name);FrameLayout art=Logos.artwork(ui,LolGame.asset("item",id),name,28);art.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);art.setContentDescription(name);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ui.dp(28),ui.dp(28));p.setMarginEnd(ui.dp(4));equipment.addView(art,p);}block.addView(equipment);if(names.isEmpty())block.addView(ui.text("装备尚未提供",12,ui.muted,false));else{ui.gap(block,6);block.addView(ui.text("装备 · 点按查看名称",11,ui.muted,false));ui.clickable(block,ui.surface,Ui.CARD_RADIUS);block.setFocusable(true);block.setOnClickListener(v->Sheet.message((android.app.Activity)ui.c,playerName(player,team)+" · 装备",String.join("\n",names)));}body.addView(block,new LinearLayout.LayoutParams(-1,-2));ui.gap(body,10);
    }
    private static String playerName(LolGame.Player player,String team){String name=player.name();return name.regionMatches(true,0,team+" ",0,team.length()+1)?name.substring(team.length()+1):name;}
}
