package cn.myscore.app;

import org.json.*;
import java.util.*;

/** A single source-reported game. Team identity never depends on blue/red order. */
final class LolGame {
    final JSONObject data;
    final int number;
    final boolean live;
    final Team home,away;
    private LolGame(JSONObject data,Team home,Team away){this.data=data;this.home=home;this.away=away;number=data.optInt("bo");live=data.optString("matchStatus").equals("1");}
    static LolGame parse(JSONObject data,Match match)throws Exception {
        String state=data.optString("matchStatus");
        if(!state.equals("1")&&!state.equals("2"))return null;
        if(data.optInt("bo")<1||data.optInt("bo")>9)throw new Exception("小局编号无效");
        for(String key:new String[]{"teamAId","teamBId"})if(data.has(key)&&!data.optString(key).equals(match.homeId)&&!data.optString(key).equals(match.awayId))throw new Exception("小局战队身份不匹配");
        Team home=null,away=null;JSONArray teams=DetailParsers.arr(data,"teamInfos");
        for(int i=0;i<teams.length();i++){
            JSONObject team=teams.getJSONObject(i);String id=team.optString("teamId");
            if(id.equals(match.homeId)&&home==null)home=new Team(team,id.equals(data.optString("blueTeam")));
            else if(id.equals(match.awayId)&&away==null)away=new Team(team,id.equals(data.optString("blueTeam")));
            else throw new Exception("小局战队重复或不匹配");
        }
        // A live game can exist before its detailed team payload is published.
        if(home==null)home=new Team(new JSONObject().put("teamId",match.homeId),match.homeId.equals(data.optString("blueTeam")));
        if(away==null)away=new Team(new JSONObject().put("teamId",match.awayId),match.awayId.equals(data.optString("blueTeam")));
        return new LolGame(data,home,away);
    }
    String title(){return "第 "+number+" 局";}
    String clock(){
        long seconds=data.optLong("gameTime",-1);
        if(seconds<0&&!live){long start=Match.parseTime(data.optString("matchStartTime")),end=Match.parseTime(data.optString("matchEndTime"));if(start>0&&end>start)seconds=(end-start)/1000;}
        return seconds<0?"—":String.format(Locale.ROOT,"%02d:%02d",seconds/60,seconds%60);
    }
    String winner(Match match){String id=data.optString("matchWin");return id.equals(match.homeId)?match.home:id.equals(match.awayId)?match.away:"";}
    boolean sideKnown(Match match){String id=data.optString("blueTeam");return id.equals(match.homeId)||id.equals(match.awayId);}
    static String value(JSONObject data,String key){return !data.has(key)||data.isNull(key)||data.optString(key).isBlank()?"—":data.optString(key);}
    static String asset(String type,String id){return id.matches("[a-zA-Z0-9_]+")?"https://game.gtimg.cn/images/lol/act/img/"+type+"/"+id+".png":"";}
    static final class Team {
        final JSONObject data;final boolean blue;
        final List<Player> players=new ArrayList<>();
        Team(JSONObject data,boolean blue){this.data=data;this.blue=blue;JSONArray array=DetailParsers.arr(data,"playerInfos");for(int i=0;i<array.length();i++){JSONObject p=DetailParsers.objAt(array,i);if(!p.optString("playerName").isBlank())players.add(new Player(p));}players.sort(Comparator.comparingInt(p->position(p.role())));}
        String value(String key){return LolGame.value(data,key);}
        Player atRole(String role){for(Player p:players)if(p.role().equals(role))return p;return null;}
    }
    static final class Player {
        final JSONObject data;
        Player(JSONObject data){this.data=data;}
        String name(){return data.optString("playerName");}
        String role(){return switch(data.optString("playerLocation")){case "JUN","JUNGLE"->"JUNGLE";case "ADC","BOT"->"BOT";case "SUP","SUPPORT"->"SUPPORT";default->data.optString("playerLocation");};}
        String hero(){return data.optString("heroName",data.optString("heroNameEn","—"));}
        String icon(){return asset("champion",data.optString("heroNameEn"));}
        String level(){return value(DetailParsers.obj(data,"otherDetail"),"level");}
        String kda(){JSONObject b=DetailParsers.obj(data,"battleDetail");return value(b,"kills")+" / "+value(b,"death")+" / "+value(b,"assist");}
    }
    static int position(String role){return switch(role){case "TOP"->0;case "JUNGLE"->1;case "MID"->2;case "BOT"->3;case "SUPPORT"->4;default->5;};}
    static String roleName(String role){return switch(role){case "TOP"->"上单";case "JUNGLE"->"打野";case "MID"->"中单";case "BOT"->"下路";case "SUPPORT"->"辅助";default->role.isBlank()?"位置未提供":role;};}
}
