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
    LolMatchView(Ui ui,MatchDetail detail,LinearLayout body,IntConsumer select){this.ui=ui;this.detail=detail;this.body=body;this.select=select;float scale=Math.max(1,ui.c.getResources().getConfiguration().fontScale);positionWidth=ui.dp(30*scale);kdaWidth=ui.dp(76*scale);csWidth=ui.dp(36*scale);goldWidth=ui.dp(48*scale);spellWidth=ui.dp(40*scale);}
    LolGame game(int selected){for(LolGame g:detail.lolGames)if(g.number==selected)return g;for(LolGame g:detail.lolGames)if(g.live)return g;return detail.lolGames.isEmpty()?null:detail.lolGames.get(detail.lolGames.size()-1);}
    void games(int selected,boolean stats){
        LolGame game=game(selected);
        if(game==null){heading("小局数据尚未提供");body.addView(ui.text("来源尚未返回本场小局的阵容和统计，稍后将自动检查。",14,ui.muted,false));return;}
        selector(game);ui.gap(body,12);
        if(!stats){score(game);heading("目标与经济");objectives(game);heading("位置对位");players(game);}
        else statistics(game);
    }
    private void statistics(LolGame game){
        LinearLayout responsive=new LinearLayout(ui.c){
            private Boolean wide;
            {setOrientation(VERTICAL);setTag("lol-stats-layout");}
            @Override protected void onMeasure(int widthSpec,int heightSpec){
                float scale=Math.max(1,ui.c.getResources().getConfiguration().fontScale);
                boolean next=MeasureSpec.getSize(widthSpec)>=ui.dp(560*scale);
                if(wide==null||wide!=next){wide=next;removeAllViews();if(next)statisticsTable(this,game);else statisticsCards(this,game);}
                super.onMeasure(widthSpec,heightSpec);
            }
        };
        body.addView(responsive,new LinearLayout.LayoutParams(-1,-2));
    }
    private void statisticsCards(LinearLayout host,LolGame game){
        boolean home=!selectedTeam.equals(detail.match.awayId);LinearLayout teams=ui.row();
        String[] ids={detail.match.homeId,detail.match.awayId},names={detail.match.home,detail.match.away};
        for(int i=0;i<2;i++){String id=ids[i];TextView chip=ui.chip(names[i],i==0?home:!home,()->{if(selectTeam!=null)selectTeam.accept(id);});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);if(i>0)p.setMarginStart(ui.dp(8));teams.addView(chip,p);}host.addView(teams);
        ui.gap(host,16);host.addView(ui.text("伤害、视野与出装",15,ui.text,true));ui.gap(host,10);
        LolGame.Team team=home?game.home:game.away;String name=home?detail.match.home:detail.match.away;
        if(team.players.isEmpty())host.addView(ui.text("本局选手统计尚未提供。",14,ui.muted,false));
        host.addView(ui.text("点选手查看装备名称",11,ui.muted,false));ui.gap(host,8);
        LinearLayout roster=ui.column();roster.setBackground(ui.panel());host.addView(roster,new LinearLayout.LayoutParams(-1,-2));
        for(int i=0;i<team.players.size();i++){if(i>0)roster.addView(ui.divider());playerStats(roster,team.players.get(i),name);}
    }
    private void statisticsTable(LinearLayout host,LolGame game){
        float scale=Math.max(1,ui.c.getResources().getConfiguration().fontScale);
        int damageWidth=ui.dp(84*scale),visionWidth=ui.dp(64*scale),itemsWidth=ui.dp(220);
        LinearLayout table=ui.column();table.setTag("lol-stats-table");table.setBackground(ui.panel());table.setPadding(ui.dp(12),0,ui.dp(12),0);
        LinearLayout header=ui.row();header.setMinimumHeight(ui.dp(44));
        header.addView(statsLabel("选手",false),new LinearLayout.LayoutParams(0,-2,1));
        header.addView(statsLabel("对英雄伤害",true),new LinearLayout.LayoutParams(damageWidth,-2));
        header.addView(statsLabel("视野得分",true),new LinearLayout.LayoutParams(visionWidth,-2));
        TextView equipment=statsLabel("最终装备",false);equipment.setPadding(ui.dp(12),0,0,0);header.addView(equipment,new LinearLayout.LayoutParams(itemsWidth,-2));table.addView(header);
        for(int side=0;side<2;side++){
            String teamName=side==0?detail.match.home:detail.match.away;LolGame.Team team=side==0?game.home:game.away;
            LinearLayout teamTitle=ui.row();teamTitle.setPadding(0,ui.dp(8),0,ui.dp(8));
            teamTitle.addView(Logos.view(ui,side==0?detail.match.homeLogo:detail.match.awayLogo,teamName,20),new LinearLayout.LayoutParams(ui.dp(20),ui.dp(20)));
            TextView title=ui.text(teamName,13,ui.text,true);title.setPadding(ui.dp(8),0,0,0);teamTitle.addView(title);table.addView(teamTitle);
            if(team.players.isEmpty())table.addView(ui.text("本局选手统计尚未提供。",13,ui.muted,false));
            for(LolGame.Player player:team.players){
                LinearLayout row=ui.row();row.setTag("lol-stats-player");row.setMinimumHeight(ui.dp(56*scale));row.setPadding(0,ui.dp(8),0,ui.dp(8));
                row.addView(statsIdentity(player,teamName),new LinearLayout.LayoutParams(0,-2,1));
                String damage=DetailFormat.damage(LolGame.value(DetailParsers.obj(player.data,"damageDetail"),"heroDamage"));
                String vision=DetailFormat.number(LolGame.value(DetailParsers.obj(player.data,"visionDetail"),"visionScore"),1);
                TextView damageText=ui.text(damage,13,ui.text,true),visionText=ui.text(vision,13,ui.text,false);
                damageText.setGravity(Gravity.END);visionText.setGravity(Gravity.END);damageText.setPadding(0,0,ui.dp(8),0);visionText.setPadding(0,0,ui.dp(8),0);
                row.addView(damageText,new LinearLayout.LayoutParams(damageWidth,-2));row.addView(visionText,new LinearLayout.LayoutParams(visionWidth,-2));
                LinearLayout items=ui.row();items.setPadding(ui.dp(12),0,0,0);List<String> names=new ArrayList<>();
                JSONArray source=DetailParsers.arr(player.data,"items");
                for(int i=0;i<Math.min(source.length(),7);i++){JSONObject item=DetailParsers.objAt(source,i);String id=item.optString("itemId");if(!id.matches("[1-9][0-9]*"))continue;String name=item.optString("itemName","装备");names.add(name);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ui.dp(24),ui.dp(24));lp.setMarginEnd(ui.dp(4));items.addView(Logos.artwork(ui,LolGame.asset("item",id),name,24),lp);}
                if(names.isEmpty())items.addView(ui.text("尚未提供",11,ui.muted,false));row.addView(items,new LinearLayout.LayoutParams(itemsWidth,-2));
                ui.clickable(row,0,0);row.setFocusable(true);row.setTooltipText(names.isEmpty()?"装备尚未提供":String.join("、",names));
                row.setContentDescription(playerName(player,teamName)+"，对英雄伤害 "+damage+"，视野得分 "+vision+"，"+row.getTooltipText());
                row.setOnClickListener(v->Sheet.message((android.app.Activity)ui.c,playerName(player,teamName)+" · 装备",names.isEmpty()?"装备尚未提供":String.join("\n",names)));
                table.addView(row);table.addView(ui.divider());
            }
        }
        host.addView(table,new LinearLayout.LayoutParams(-1,-2));
    }
    private View statsIdentity(LolGame.Player player,String team){
        LinearLayout row=ui.row();row.addView(Logos.artwork(ui,player.icon(),player.hero(),28),new LinearLayout.LayoutParams(ui.dp(28),ui.dp(28)));
        LinearLayout words=ui.column();words.addView(ui.text(playerName(player,team),14,ui.text,true));
        words.addView(ui.text(LolGame.roleName(player.role())+" · "+player.hero()+(player.level().isBlank()?"":" · "+player.level()),11,ui.muted,false));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMarginStart(ui.dp(8));row.addView(words,lp);return row;
    }
    private TextView statsLabel(String label,boolean numeric){TextView text=ui.text(label,11,ui.muted,false);text.setGravity(numeric?Gravity.END:Gravity.START);if(numeric)text.setPadding(0,0,ui.dp(8),0);return text;}

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
        LinearLayout panel=ui.column();panel.setPadding(ui.dp(12),0,ui.dp(12),0);panel.setBackground(ui.panel());Set<String> roles=new LinkedHashSet<>(List.of("TOP","JUNGLE","MID","BOT","SUPPORT"));for(LolGame.Player p:game.home.players)roles.add(p.role());for(LolGame.Player p:game.away.players)roles.add(p.role());
        panel.setTag("lol-lineup-table");
        int minimum=positionWidth+kdaWidth+csWidth+goldWidth+spellWidth+ui.dp(180*Math.max(1,ui.c.getResources().getConfiguration().fontScale));
        HorizontalScrollView horizontal=new HorizontalScrollView(ui.c){
            @Override protected void onMeasure(int width,int height){panel.setMinimumWidth(Math.max(minimum,MeasureSpec.getSize(width)));super.onMeasure(width,height);}
        };
        horizontal.setTag("lol-lineup");horizontal.setFillViewport(true);horizontal.setHorizontalScrollBarEnabled(true);
        panel.addView(header());
        int index=0;for(String role:roles){if(index++>0)panel.addView(ui.divider());panel.addView(row(game,role,game.home.atRole(role),true));panel.addView(row(game,role,game.away.atRole(role),false));}
        horizontal.addView(panel);body.addView(horizontal,new LinearLayout.LayoutParams(-1,-2));
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
    private void playerStats(LinearLayout host,LolGame.Player player,String team){
        LinearLayout block=ui.column();block.setTag("lol-stats-player-narrow");block.setPadding(ui.dp(12),ui.dp(10),ui.dp(12),ui.dp(10));
        block.addView(statsIdentity(player,team));ui.gap(block,8);
        JSONObject damage=DetailParsers.obj(player.data,"damageDetail"),vision=DetailParsers.obj(player.data,"visionDetail");LinearLayout values=ui.row();
        for(String[] metric:new String[][]{{"对英雄伤害",DetailFormat.damage(LolGame.value(damage,"heroDamage"))},{"视野得分",DetailFormat.number(LolGame.value(vision,"visionScore"),1)}}){LinearLayout field=ui.column();field.addView(ui.text(metric[0],11,ui.muted,false));field.addView(ui.text(metric[1],18,ui.text,true));values.addView(field,new LinearLayout.LayoutParams(0,-2,1));}block.addView(values);ui.gap(block,8);
        JSONArray items=DetailParsers.arr(player.data,"items");LinearLayout equipment=ui.row();List<String> names=new ArrayList<>();
        for(int i=0;i<Math.min(items.length(),7);i++){JSONObject item=DetailParsers.objAt(items,i);String id=item.optString("itemId");if(!id.matches("[1-9][0-9]*"))continue;String name=item.optString("itemName","装备");names.add(name);FrameLayout art=Logos.artwork(ui,LolGame.asset("item",id),name,28);art.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);art.setContentDescription(name);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ui.dp(28),ui.dp(28));p.setMarginEnd(ui.dp(4));equipment.addView(art,p);}
        if(names.isEmpty())block.addView(ui.text("装备尚未提供",12,ui.muted,false));else{block.addView(equipment);ui.clickable(block,android.graphics.Color.TRANSPARENT,0);block.setFocusable(true);block.setContentDescription(playerName(player,team)+"，对英雄伤害 "+DetailFormat.damage(LolGame.value(damage,"heroDamage"))+"，视野得分 "+LolGame.value(vision,"visionScore")+"，点按查看装备");block.setOnClickListener(v->Sheet.message((android.app.Activity)ui.c,playerName(player,team)+" · 装备",String.join("\n",names)));}
        host.addView(block,new LinearLayout.LayoutParams(-1,-2));
    }

    private static String playerName(LolGame.Player player,String team){String name=player.name();return name.regionMatches(true,0,team+" ",0,team.length()+1)?name.substring(team.length()+1):name;}
}
