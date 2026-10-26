package cn.myscore.app;

import org.json.*;
import java.util.*;

/** Each adapter maps native source fields; unavailable values stay unavailable. */
public final class DetailParsers {
    private DetailParsers(){}
    static JSONObject obj(JSONObject o,String k){JSONObject r=o.optJSONObject(k);return r==null?new JSONObject():r;}
    static JSONArray arr(JSONObject o,String k){JSONArray r=o.optJSONArray(k);return r==null?new JSONArray():r;}
    static String val(JSONObject o,String k){return plain(o.opt(k));}
    static String plain(Object value){
        if(value==null||value==JSONObject.NULL||value instanceof JSONArray)return "";
        if(value instanceof JSONObject){JSONObject o=(JSONObject)value;for(String key:new String[]{"displayValue","displayName","name","value"}){Object v=o.opt(key);if(v!=null&&v!=JSONObject.NULL&&!(v instanceof JSONObject)&&!(v instanceof JSONArray))return String.valueOf(v);}return "";}
        String text=String.valueOf(value);if(text.trim().startsWith("{"))try{return plain(new JSONObject(text));}catch(JSONException ignored){return "";}return text;
    }
    static String show(String s){return s.isBlank()?"—":s;}
    public static MatchDetail espn(JSONObject root,Match previous,long now)throws Exception {
        Competition c=Competition.find(previous.league);if(c==null)throw new Exception("未知赛事");
        JSONObject header=obj(root,"header");List<Match> ms=Parsers.espn(new JSONObject().put("events",new JSONArray().put(header)),c,now);Match actual=null;
        for(Match m:ms)if(m.id.equals(previous.id))actual=m;if(actual==null)throw new Exception("比赛详情身份不匹配");
        MatchDetail d=new MatchDetail(actual);d.fetched=now;
        JSONObject gi=obj(root,"gameInfo");d.fact("场地",obj(gi,"venue").optString("fullName"));d.fact("观众",val(gi,"attendance"));d.fact("赛事",c.name);
        JSONArray officials=arr(gi,"officials");for(int i=0;i<officials.length();i++){JSONObject o=officials.optJSONObject(i);if(o!=null){String role=val(o,"position");d.fact(role.isBlank()?"裁判":label(role),val(o,"displayName").isBlank()?val(o,"fullName"):val(o,"displayName"));}}
        JSONArray comp=arr(header,"competitions");JSONObject co=comp.optJSONObject(0);if(co!=null){JSONArray notes=arr(co,"notes");for(int i=0;i<notes.length();i++)d.fact("说明",objAt(notes,i).optString("headline"));lineScores(d,co);}
        teamStats(d,arr(obj(root,"boxscore"),"teams"));
        if(c.group.equals("足球"))d.stats.sort(Comparator.comparingInt(s->switch(s.label()){case "控球率 %"->0;case "射门"->1;case "射正"->2;case "角球"->3;case "成功传球"->4;case "传球"->5;default->6;}));
        if(c.group.equals("足球"))soccerRoster(d,arr(root,"rosters"),arr(root,"keyEvents"));else players(d,arr(obj(root,"boxscore"),"players"));
        JSONArray events=arr(root,c.group.equals("足球")?"keyEvents":c.id.equals("nfl")?"scoringPlays":"plays");
        for(int i=events.length()-1;i>=0;i--){JSONObject e=events.optJSONObject(i);if(e==null)continue;String clock=obj(e,"clock").optString("displayValue");int period=obj(e,"period").optInt("number");String time=(c.group.equals("足球")?"":period>0?"Q"+period+" ":"")+clock;String type=obj(e,"type").optString("text");String text=e.optString("text");if(text.isEmpty())continue;d.events.add(new MatchDetail.Event(time,label(type),text));}
        JSONArray injuries=arr(root,"injuries");for(int i=0;i<injuries.length();i++){JSONObject team=objAt(injuries,i);MatchDetail.Table t=new MatchDetail.Table("lineup",obj(team,"team").optString("displayName")+" · 伤停","球员","状态","说明");t.teamId=obj(team,"team").optString("id");t.category="伤停";JSONArray list=arr(team,"injuries");for(int j=0;j<list.length();j++){JSONObject r=objAt(list,j);t.row(obj(r,"athlete").optString("displayName"),label(r.optString("status")),r.optString("shortComment"));t.people.add(person(r,obj(team,"team"),d.match));}d.table(t);}
        if(c.group.equals("NBA"))for(MatchDetail.Table table:d.tables)if(table.category.equals("球员"))orderColumns(table,List.of("球员","时间","得分","篮板","助攻","抢断","盖帽","失误","投篮","三分","罚球","前场板","后场板","犯规","正负值"));
        return d;
    }
    private static void orderColumns(MatchDetail.Table table,List<String> preferred){List<String> columns=new ArrayList<>(table.columns);List<Integer> order=new ArrayList<>();for(String label:preferred){int index=columns.indexOf(label);if(index>=0&&!order.contains(index))order.add(index);}for(int i=0;i<columns.size();i++)if(!order.contains(i))order.add(i);table.columns.clear();for(int index:order)table.columns.add(columns.get(index));for(int i=0;i<table.rows.size();i++){List<String> before=table.rows.get(i),row=new ArrayList<>();for(int index:order)row.add(index<before.size()?before.get(index):"—");table.rows.set(i,row);}}
    private static void lineScores(MatchDetail d,JSONObject co){
        JSONObject h=null,a=null;JSONArray teams=arr(co,"competitors");for(int i=0;i<teams.length();i++){JSONObject t=objAt(teams,i);if(t.optString("homeAway").equals("home"))h=t;else if(t.optString("homeAway").equals("away"))a=t;}
        if(h==null||a==null||d.match.group.equals("足球"))return;JSONArray hs=arr(h,"linescores"),as=arr(a,"linescores");if(hs.length()==0&&as.length()==0)return;
        MatchDetail.Table t=new MatchDetail.Table("overview","分节比分","球队");for(int i=0;i<Math.max(hs.length(),as.length());i++)t.columns.add(i<4?"Q"+(i+1):"OT"+(i-3));
        for(JSONObject team:new JSONObject[]{h,a}){List<String> row=new ArrayList<>();row.add(obj(team,"team").optString("shortDisplayName",obj(team,"team").optString("displayName")));JSONArray scores=arr(team,"linescores");for(int i=0;i<t.columns.size()-1;i++){JSONObject s=objAt(scores,i);row.add(show(s.optString("displayValue",val(s,"value"))));}t.rows.add(row);}d.table(t);
    }
    private static void teamStats(MatchDetail d,JSONArray teams){
        JSONObject h=null,a=null;for(int i=0;i<teams.length();i++){JSONObject t=objAt(teams,i);String id=obj(t,"team").optString("id");if(id.equals(d.match.homeId))h=t;if(id.equals(d.match.awayId))a=t;}
        if(h==null||a==null)return;Map<String,JSONObject> away=new LinkedHashMap<>();JSONArray aa=arr(a,"statistics");for(int i=0;i<aa.length();i++){JSONObject s=objAt(aa,i);away.put(s.optString("name"),s);}
        JSONArray hh=arr(h,"statistics");for(int i=0;i<hh.length();i++){JSONObject s=objAt(hh,i);String key=s.optString("name"),v=val(s,"displayValue");JSONObject other=away.get(key);String av=other==null?"":val(other,"displayValue");String title=key.equals("possessionPct")?"控球率 %":key.equals("possessionTime")?"控球时间":label(s.optString("label",key));if(!v.isEmpty()||!av.isEmpty())d.stats.add(new MatchDetail.Stat(title,show(v),show(av)));}
    }
    private static void soccerRoster(MatchDetail d,JSONArray rosters,JSONArray events){
        for(int i=0;i<rosters.length();i++){JSONObject r=objAt(rosters,i);String team=obj(r,"team").optString("displayName");String formation=r.optString("formation");d.fact(team+" 阵型",formation);
            for(boolean starter:new boolean[]{true,false}){MatchDetail.Table t=new MatchDetail.Table("lineup",team+" · "+(starter?"首发":"替补"),"球员","位置","状态","站位","进球","黄牌","红牌","换人","评分","射门","射正","传球","成功传球","成功盘带","抢断","犯规","助攻","乌龙球","国籍","年龄");t.teamId=obj(r,"team").optString("id");t.category=starter?"首发":"替补";JSONArray list=arr(r,"roster");for(int j=0;j<list.length();j++){JSONObject p=objAt(list,j);if(p.optBoolean("starter")!=starter)continue;JSONObject athlete=obj(p,"athlete");String name=athlete.optString("displayName");if(name.isBlank())continue;t.row((p.optString("jersey").isBlank()?"":p.optString("jersey")+"  ")+name,label(obj(p,"position").optString("displayName")),p.optBoolean("subbedOut")?"已换下":p.optBoolean("subbedIn")?"替补登场":starter?"首发":"替补",p.optString("formationPlace"),rosterStat(p,"totalGoals"),rosterStat(p,"yellowCards"),rosterStat(p,"redCards"),substitution(events,athlete.optString("id")),rosterStat(p,"rating"),rosterStat(p,"totalShots"),rosterStat(p,"shotsOnTarget"),rosterStat(p,"totalPasses"),rosterStat(p,"accuratePasses"),rosterStat(p,"successfulDribbles"),rosterStat(p,"totalTackles"),rosterStat(p,"foulsCommitted"),rosterStat(p,"goalAssists"),rosterStat(p,"ownGoals"),show(val(athlete,"citizenship")),show(val(athlete,"age")));t.people.add(person(p,obj(r,"team"),d.match));}d.table(t);}
        }
    }
    private static String rosterStat(JSONObject player,String name){JSONArray stats=arr(player,"stats");for(int i=0;i<stats.length();i++){JSONObject stat=objAt(stats,i);if(stat.optString("name").equals(name))return show(val(stat,"displayValue").isBlank()?val(stat,"value"):val(stat,"displayValue"));}return "—";}
    private static String substitution(JSONArray events,String player){
        if(player.isBlank())return "";
        for(int i=0;i<events.length();i++){JSONObject event=objAt(events,i);if(!obj(event,"type").optString("text").equals("Substitution"))continue;JSONArray people=arr(event,"participants");if(people.length()!=2)continue;JSONObject in=obj(objAt(people,0),"athlete"),out=obj(objAt(people,1),"athlete");String clock=obj(event,"clock").optString("displayValue");if(in.optString("id").equals(player))return clock+" 换上 · 换下 "+out.optString("displayName");if(out.optString("id").equals(player))return clock+" 换下 · 换上 "+in.optString("displayName");}return "";
    }
    static String soccerPhoto(String photo,String id){
        if(photo.startsWith("https://")&&!photo.contains("/teamlogos/")&&!photo.contains("/jersey"))return photo;
        return id.matches("[0-9]+")?"https://a.espncdn.com/i/headshots/soccer/players/full/"+id+".png":"";
    }
    private static void players(MatchDetail d,JSONArray teams){
        for(int i=0;i<teams.length();i++){JSONObject r=objAt(teams,i);String team=obj(r,"team").optString("displayName");JSONArray groups=arr(r,"statistics");for(int j=0;j<groups.length();j++){JSONObject g=objAt(groups,j);JSONArray columns=arr(g,"labels");if(columns.length()==0)continue;MatchDetail.Table t=new MatchDetail.Table("lineup",team+(g.optString("name").isEmpty()?"":" · "+label(g.optString("name"))),"球员");t.teamId=obj(r,"team").optString("id");t.category=g.optString("name").isBlank()?"球员":label(g.optString("name"));for(int k=0;k<columns.length();k++)t.columns.add(label(columns.optString(k)));JSONArray athletes=arr(g,"athletes");for(int k=0;k<athletes.length();k++){JSONObject p=objAt(athletes,k);String name=obj(p,"athlete").optString("displayName");if(name.isBlank())continue;List<String> row=new ArrayList<>();row.add(name+(p.optBoolean("starter")?" · 首发":"")+(p.optBoolean("didNotPlay")?" · 未出场":""));JSONArray values=arr(p,"stats");for(int v=0;v<columns.length();v++)row.add(show(values.optString(v)));t.rows.add(row);t.people.add(person(p,obj(r,"team"),d.match));}d.table(t);}
        }
    }
    private static MatchDetail.Person person(JSONObject p,JSONObject team,Match match){JSONObject a=obj(p,"athlete"),position=obj(p,"position");String role=position.optString("displayName",obj(a,"position").optString("abbreviation"));String photo=obj(a,"headshot").optString("href");if(match.group.equals("足球"))photo=soccerPhoto(photo,a.optString("id"));String shortName=a.optString("shortName",DetailFormat.shortName(a.optString("displayName")));if((match.group.equals("NBA")||match.group.equals("NFL"))&&!a.optString("firstName").isBlank()&&!a.optString("lastName").isBlank())shortName=a.optString("firstName").substring(0,1)+". "+a.optString("lastName");return new MatchDetail.Person(a.optString("id"),a.optString("displayName"),shortName,photo,p.optString("jersey",a.optString("jersey")),label(role),team.optString("id"),team.optString("displayName"),team.optString("logo",team.optString("id").equals(match.homeId)?match.homeLogo:team.optString("id").equals(match.awayId)?match.awayLogo:""),p.optBoolean("starter"),p.optBoolean("didNotPlay"));}
    public static MatchDetail five(JSONObject root,Match previous,long now)throws Exception {
        JSONObject match=obj(obj(root,"data"),"match");if(!root.optBoolean("success"))throw new Exception("CS2 详情读取失败");Match actual=DomesticEsports.fiveMatch(match,now,true,false);if(!actual.id.equals(previous.id))throw new Exception("CS2 比赛身份不匹配");MatchDetail d=new MatchDetail(actual);d.fetched=now;JSONObject mc=obj(match,"mc_info");d.fact("赛事",obj(match,"tt_info").optString("disp_name"));d.fact("赛制",mc.optString("format").isBlank()?"":"BO"+mc.optString("format"));d.fact("阶段",mc.optString("tt_stage_desc"));
        JSONObject global=obj(match,"global_state");csPlayers(d,global,"全场",actual.home,actual.away,"t1_player_stats","t2_player_stats");
        MatchDetail.Table maps=new MatchDetail.Table("overview","地图比分","地图",actual.home,actual.away,"状态");JSONArray bouts=arr(match,"bouts_state");
        for(int i=0;i<bouts.length();i++){JSONObject b=objAt(bouts,i);String name=b.optString("map_name");if(name.isBlank())continue;String status=b.optString("status");boolean done=status.equals("2");JSONObject hs=obj(b,"t1_stats"),as=obj(b,"t2_stats");maps.row((i+1)+"  "+name,done?show(val(hs,"all_score")):"—",done?show(val(as,"all_score")):"—",done?"已结束":status.equals("1")?"进行中":"未开始");if(done){csPlayers(d,b,name,actual.home,actual.away,"t1_pr_stats","t2_pr_stats");MatchDetail.Table halves=new MatchDetail.Table("events",name+" · 攻守半场","球队","上半场","下半场","加时","总分");halves.row(actual.home,show(val(hs,"fh_score")),show(val(hs,"sh_score")),show(val(hs,"ot_score")),show(val(hs,"all_score")));halves.row(actual.away,show(val(as,"fh_score")),show(val(as,"sh_score")),show(val(as,"ot_score")),show(val(as,"all_score")));d.table(halves);}}
        d.table(maps);d.warning="地图比分仅展示已结束小局；选手统计按来源更新。";return d;
    }
    private static void csPlayers(MatchDetail d,JSONObject source,String map,String home,String away,String hk,String ak){
        for(int i=0;i<2;i++){MatchDetail.Table t=new MatchDetail.Table("lineup",(i==0?home:away)+" · "+map,"选手","K","D","A","ADR","KAST","Rating");t.teamId=i==0?d.match.homeId:d.match.awayId;t.category=map;JSONArray a=arr(source,i==0?hk:ak);for(int j=0;j<a.length();j++){JSONObject p=objAt(a,j);if(p.optString("name").isBlank())continue;String name=p.optString("name"),team=i==0?home:away;t.row(name,show(val(p,"kill")),show(val(p,"death")),show(val(p,"assist")),DetailFormat.number(show(val(p,"adr")),2),DetailFormat.number(show(val(p,"kast")),1),DetailFormat.number(show(val(p,"rating")),2));t.people.add(new MatchDetail.Person(p.optString("id"),name,name,DetailSources.https(p.optString("avatar")),"","",t.teamId,team,"",false,false));}d.table(t);}
    }
    static JSONObject objAt(JSONArray a,int i){JSONObject o=a.optJSONObject(i);return o==null?new JSONObject():o;}
    public static MatchDetail lol(JSONObject base,JSONObject rich,Match previous,long now)throws Exception {
        if(!previous.id.equals("qq:lol:"+base.optString("bMatchId")))throw new Exception("LOL 比赛身份不匹配");
        String tournament=base.optString("GameName");if(!tournament.contains("全球总决赛")&&!Rules.isWorlds(tournament,""))throw new Exception("该比赛不是 Worlds");
        Match m=Match.fromJson(previous.json());m.homeId=base.optString("TeamA");m.awayId=base.optString("TeamB");m.home=base.optString("TeamNameA",m.home);m.away=base.optString("TeamNameB",m.away);
        m.state=switch(base.optString("MatchStatus")){case "1"->"scheduled";case "2"->"live";case "3"->"final";default->"unknown";};m.status=Rules.stateLabel(m.state);m.homeScore=m.scheduled()?"":val(base,"ScoreA");m.awayScore=m.scheduled()?"":val(base,"ScoreB");
        try{m.updated=java.time.LocalDateTime.parse(base.optString("lastUpdateTime"),java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).atZone(Match.ZONE).toInstant().toEpochMilli();}catch(RuntimeException ignored){}
        try{m.start=java.time.LocalDateTime.parse(base.optString("MatchTime"),java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).atZone(Match.ZONE).toInstant().toEpochMilli();m.timeConfirmed=true;}catch(RuntimeException ignored){}
        JSONArray icons=arr(base,"TeamIcon");for(int i=0;i<icons.length();i++){JSONObject icon=objAt(icons,i);if(icon.optString("TeamId").equals(m.homeId))m.homeLogo=DetailSources.https(icon.optString("TeamIcon"));if(icon.optString("TeamId").equals(m.awayId))m.awayLogo=DetailSources.https(icon.optString("TeamIcon"));}
        m.url="https://lpl.qq.com/es/stats.shtml?bmid="+base.optString("bMatchId");m.detail=tournament+" · "+base.optString("GameModeName")+" · "+base.optString("GameTypeName");MatchDetail d=new MatchDetail(m);d.fetched=now;d.fact("赛事",tournament);d.fact("赛制",base.optString("GameModeName"));d.fact("阶段",base.optString("GameTypeName"));d.fact("轮次",base.optString("GameProcName"));
        JSONObject data=obj(rich,"data");if(rich.optBoolean("success")){
            if(!data.optString("matchId").equals(base.optString("bMatchId"))||!data.optString("teamAId").equals(m.homeId)||!data.optString("teamBId").equals(m.awayId))throw new Exception("LOL 小局身份或战队方向不匹配");
            if(data.optString("matchStatus").equals("1")||data.optString("matchStatus").equals("2")){
                m.state=data.optString("matchStatus").equals("1")?"live":"final";m.status=Rules.stateLabel(m.state);
                m.homeScore=val(data,"teamAScore");m.awayScore=val(data,"teamBScore");
            }
            d.source="英雄联盟赛事官网 · 腾竞数据";JSONArray games=arr(data,"matchInfos");MatchDetail.Table summary=new MatchDetail.Table("overview","已结束小局","局次","胜方","时长");
            Set<Integer> gameNumbers=new HashSet<>();for(int i=0;i<games.length();i++){LolGame game=LolGame.parse(objAt(games,i),m);if(game!=null){if(!gameNumbers.add(game.number))throw new Exception("小局编号重复");d.lolGames.add(game);}}
            d.lolGames.sort(Comparator.comparingInt(game->game.number));
            if(!d.lolGames.isEmpty())d.lolFetched=now;
            for(int i=0;i<games.length();i++){JSONObject game=objAt(games,i);if(!game.optString("matchStatus").equals("2"))continue;String win=game.optString("matchWin");if(!win.equals(m.homeId)&&!win.equals(m.awayId))continue;String gameName="第 "+game.optString("bo",String.valueOf(i+1))+" 局";long start=Match.parseTime(game.optString("matchStartTime")),end=Match.parseTime(game.optString("matchEndTime"));String duration=end>start&&start>0?((end-start)/60000)+":"+String.format(Locale.ROOT,"%02d",(end-start)/1000%60):"—";summary.row(gameName,win.equals(m.homeId)?m.home:m.away,duration);d.events.add(new MatchDetail.Event(gameName,win.equals(m.homeId)?m.home+" 获胜":m.away+" 获胜","用时 "+duration));
                MatchDetail.Table totals=new MatchDetail.Table("stats",gameName+" · 团队数据","球队","击杀","经济","推塔","小龙","大龙","巢虫");JSONArray teams=arr(game,"teamInfos");
                for(int j=0;j<teams.length();j++){JSONObject team=objAt(teams,j);String tid=team.optString("teamId");if(!tid.equals(m.homeId)&&!tid.equals(m.awayId))continue;String name=tid.equals(m.homeId)?m.home:m.away;totals.row(name,show(val(team,"kills")),show(val(team,"golds")),show(val(team,"turretAmount")),show(val(team,"dragonAmount")),show(val(team,"baronAmount")),show(val(team,"voidGrubAmount")));
                    MatchDetail.Table players=new MatchDetail.Table("lineup",gameName+" · "+name+(game.optString("blueTeam").equals(tid)?" · 蓝方":" · 红方"),"选手","位置","英雄","K / D / A","补刀","伤害","经济");
                    MatchDetail.Table gear=new MatchDetail.Table("stats",gameName+" · "+name+" 出装","选手","装备");JSONArray ps=arr(team,"playerInfos");for(int k=0;k<ps.length();k++){JSONObject p=objAt(ps,k);if(p.optString("playerName").isBlank())continue;JSONObject battle=obj(p,"battleDetail");players.row(p.optString("playerName"),role(p.optString("playerLocation")),p.optString("heroName",p.optString("heroNameEn")),show(val(battle,"kills"))+" / "+show(val(battle,"death"))+" / "+show(val(battle,"assist")),show(val(p,"minionKilled")),show(val(obj(p,"damageDetail"),"heroDamage")),show(val(obj(p,"otherDetail"),"golds")));JSONArray items=arr(p,"items");List<String> names=new ArrayList<>();for(int it=0;it<items.length();it++){String item=objAt(items,it).optString("itemName");if(!item.isBlank())names.add(item);}if(!names.isEmpty())gear.row(p.optString("playerName"),String.join("、",names));}d.table(players);d.table(gear);
                }d.table(totals);
            }d.table(summary);
        }
        return d;
    }
    private static String role(String r){return switch(r){case "TOP"->"上单";case "JUN","JUNGLE"->"打野";case "MID"->"中单";case "ADC","BOT"->"下路";case "SUP","SUPPORT"->"辅助";default->r;};}
    public static MatchDetail f1(JSONObject event,JSONObject session,JSONObject core,Map<String,JSONObject> statistics,Match previous,long now)throws Exception {
        List<Match> all=Parsers.espn(new JSONObject().put("events",new JSONArray().put(event)),Competition.find("f1"),now);Match actual=null;for(Match m:all)if(m.id.equals(previous.id))actual=m;if(actual==null||!core.optString("id").equals(session.optString("id")))throw new Exception("F1 场次身份不匹配");
        MatchDetail d=new MatchDetail(actual);d.fetched=now;d.fact("大奖赛",event.optString("name"));d.fact("场次",obj(session,"type").optString("abbreviation"));d.fact("赛道",obj(core,"venue").optString("fullName"));
        Map<String,String> names=new HashMap<>();JSONArray boardDrivers=arr(session,"competitors");for(int i=0;i<boardDrivers.length();i++){JSONObject p=objAt(boardDrivers,i);names.put(p.optString("id"),obj(p,"athlete").optString("displayName"));}
        List<JSONObject> drivers=new ArrayList<>();JSONArray cs=arr(core,"competitors");for(int i=0;i<cs.length();i++)drivers.add(objAt(cs,i));drivers.sort(Comparator.comparingInt(p->p.optInt("order",999)));
        MatchDetail.Table lineup=new MatchDetail.Table("lineup","本场车手与车队","车手","车队","车号");MatchDetail.Table results=new MatchDetail.Table("stats","场次成绩","车手","名次","车队","圈数","成绩 / 用时","积分","Q1","Q2","Q3");
        for(JSONObject p:drivers){String id=p.optString("id"),name=names.getOrDefault(id,"");if(name.isBlank())continue;JSONObject car=obj(p,"vehicle");Map<String,String> values=new HashMap<>();JSONObject stats=statistics.get(id);if(stats!=null){JSONArray cats=arr(obj(stats,"splits"),"categories");for(int j=0;j<cats.length();j++){JSONArray ss=arr(objAt(cats,j),"stats");for(int k=0;k<ss.length();k++){JSONObject s=objAt(ss,k);values.put(s.optString("name"),val(s,"displayValue"));}}}
            String place=show(values.getOrDefault("place",p.has("order")?p.optString("order"):""));
            lineup.row(name,show(car.optString("manufacturer")),show(val(car,"number")));
            if(!actual.scheduled())results.row(name,place,show(car.optString("manufacturer")),show(values.getOrDefault("lapsCompleted","")),show(values.getOrDefault("totalTime","")),show(values.getOrDefault("championshipPts","")),show(values.getOrDefault("qual1TimeMS","")),show(values.getOrDefault("qual2TimeMS","")),show(values.getOrDefault("qual3TimeMS","")));
        }
        // Qualifying splits and points are meaningful only when this session publishes them.
        for(int i=results.columns.size()-1;i>=1;i--){String column=results.columns.get(i);final int index=i;if((column.matches("Q[1-3]")||column.equals("积分"))&&results.rows.stream().allMatch(row->DetailRows.cell(row,index).equals("—"))){results.columns.remove(i);for(int j=0;j<results.rows.size();j++){List<String> row=new ArrayList<>(results.rows.get(j));row.remove(i);results.rows.set(j,row);}}}
        d.table(lineup);d.table(results);
        MatchDetail.Table weekend=new MatchDetail.Table("overview","比赛周末 · 北京时间","场次","开始时间","状态");JSONArray sessions=arr(event,"competitions");for(int i=0;i<sessions.length();i++){JSONObject co=objAt(sessions,i);long time=Match.parseTime(co.optString("date"));String date=time>0?java.time.format.DateTimeFormatter.ofPattern("M/d HH:mm",Locale.CHINA).withZone(Match.ZONE).format(java.time.Instant.ofEpochMilli(time)):"待定";JSONObject ty=obj(obj(co,"status"),"type");weekend.row(obj(co,"type").optString("abbreviation"),date,Rules.stateLabel(Rules.state(ty.optString("name"),ty.optString("state"))));}d.table(weekend);return d;
    }
    private static final Map<String,String> LABELS=new HashMap<>();
    static {
        String[][] pairs={{"Possession","控球率 %"},{"SHOTS","射门"},{"ON GOAL","射正"},{"Fouls","犯规"},{"Yellow Cards","黄牌"},{"Red Cards","红牌"},{"Offsides","越位"},{"Corner Kicks","角球"},{"Saves","扑救"},{"Accurate Passes","成功传球"},{"Passes","传球"},{"Crosses","传中"},{"Accurate Crosses","成功传中"},{"Long Balls","长传"},{"Accurate Long Balls","成功长传"},{"Blocked Shots","封堵射门"},{"Effective Tackles","成功抢断"},{"Tackles","抢断"},{"Interceptions","拦截"},{"Clearances","解围"},{"Effective Clearances","成功解围"},{"Rebounds","篮板"},{"Assists","助攻"},{"Steals","抢断"},{"Blocks","盖帽"},{"Turnovers","失误"},{"Total Turnovers","总失误"},{"Field Goal %","投篮命中率 %"},{"Three Point %","三分命中率 %"},{"Free Throw %","罚球命中率 %"},{"Points in Paint","内线得分"},{"Fast Break Points","快攻得分"},{"Largest Lead","最大领先"},{"Lead Changes","领先交替"},{"Total Yards","总推进码数"},{"Net Passing Yards","净传球码数"},{"Rushing Yards","冲球码数"},{"Possession","控球率 / 时间"},{"1st Downs","首攻"},{"3rd down efficiency","三档成功率"},{"4th down efficiency","四档成功率"},{"Goal","进球"},{"Yellow Card","黄牌"},{"Red Card","红牌"},{"Substitution","换人"},{"Kickoff","开球"},{"Halftime","中场"},{"End Regular Time","常规时间结束"},{"Goalkeeper","门将"},{"Center Back","中卫"},{"Center Left Defender","左中卫"},{"Center Right Defender","右中卫"},{"Left Back","左后卫"},{"Right Back","右后卫"},{"Central Midfielder","中场"},{"Left Midfielder","左前卫"},{"Right Midfielder","右前卫"},{"Striker","前锋"},{"passing","传球"},{"rushing","冲球"},{"receiving","接球"},{"defensive","防守"},{"kicking","踢球"},{"punting","弃踢"},{"fumbles","掉球"},{"kickReturns","开球回攻"},{"puntReturns","弃踢回攻"},{"MIN","时间"},{"PTS","得分"},{"REB","篮板"},{"AST","助攻"},{"TO","失误"},{"STL","抢断"},{"BLK","盖帽"},{"PF","犯规"}};
        for(String[] p:pairs)LABELS.put(p[0],p[1]);
        LABELS.put("Attacking Midfielder","前腰");LABELS.put("Defensive Midfielder","后腰");LABELS.put("Center Forward","中锋");LABELS.put("Left Forward","左边锋");LABELS.put("Right Forward","右边锋");LABELS.put("Center Midfielder","中前卫");
        LABELS.put("Forward","前锋");LABELS.put("Midfielder","中场");LABELS.put("Defender","后卫");LABELS.put("Winger","边锋");LABELS.put("Left Wing","左边锋");LABELS.put("Right Wing","右边锋");LABELS.put("Left Winger","左边锋");LABELS.put("Right Winger","右边锋");LABELS.put("Second Striker","影锋");LABELS.put("Sweeper","清道夫");LABELS.put("Left Wing Back","左翼卫");LABELS.put("Right Wing Back","右翼卫");
        LABELS.put("Referee","裁判");LABELS.put("Head Referee","主裁判");LABELS.put("Assistant Referee","助理裁判");LABELS.put("Umpire","裁判");LABELS.put("FG","投篮");LABELS.put("3PT","三分");LABELS.put("FT","罚球");LABELS.put("OREB","前场板");LABELS.put("DREB","后场板");LABELS.put("+/-","正负值");
        LABELS.put("Offensive Rebounds","进攻篮板");LABELS.put("Defensive Rebounds","防守篮板");LABELS.put("interceptions","抄截");
        LABELS.put("Attacking Midfielder Left","左前腰");LABELS.put("Attacking Midfielder Right","右前腰");LABELS.put("Attacking Midfielder Center","前腰");LABELS.put("Left Attacking Midfielder","左前腰");LABELS.put("Right Attacking Midfielder","右前腰");
        LABELS.put("Defensive Midfielder Left","左后腰");LABELS.put("Defensive Midfielder Right","右后腰");LABELS.put("Left Defensive Midfielder","左后腰");LABELS.put("Right Defensive Midfielder","右后腰");LABELS.put("Left Center Back","左中卫");LABELS.put("Right Center Back","右中卫");
    }
    public static String label(String value){
        if(LABELS.containsKey(value))return LABELS.get(value);
        return switch(value){
            case "Substitute"->"替补";case "Center Defender"->"中卫";case "Center Left Midfielder"->"左中前卫";case "Center Right Midfielder"->"右中前卫";
            case "Center Defensive Midfielder","Defensive Midfielder Center"->"后腰";
            case "Left Defender"->"左后卫";case "Right Defender"->"右后卫";
            case "Center Left Forward"->"左中锋";case "Center Right Forward"->"右中锋";
            case "Left Center Midfielder"->"左中前卫";case "Right Center Midfielder"->"右中前卫";
            case "Left Centre Back"->"左中卫";case "Right Centre Back"->"右中卫";case "Centre Back"->"中卫";
            case "Out"->"缺阵";case "Doubtful"->"出战存疑";case "Questionable"->"待定";case "Injured"->"受伤";case "Suspended"->"停赛";case "Day-To-Day","Day To Day"->"每日观察";
            default->value;
        };
    }
}
