package cn.myscore.app;

import org.json.*;
import java.util.*;

/** Normalized, source-backed detail, persisted as one identity-scoped cache. */
public final class MatchDetail {
    public Match match;
    public long fetched;
    long lolFetched;
    public String source="",warning="";
    public final List<Fact> facts=new ArrayList<>();
    public final List<Stat> stats=new ArrayList<>();
    public final List<Table> tables=new ArrayList<>();
    public final List<Event> events=new ArrayList<>();
    final List<LolGame> lolGames=new ArrayList<>();
    public MatchDetail(Match m){match=m;source=m.source;}
    public record Fact(String label,String value){}
    public record Stat(String label,String home,String away){}
    public record Event(String time,String title,String text){}
    public record Person(String id,String name,String shortName,String photo,String jersey,String position,String teamId,String team,String teamLogo,boolean starter,boolean absent){
        JSONObject json()throws JSONException{return new JSONObject().put("id",id).put("name",name).put("shortName",shortName).put("photo",photo).put("jersey",jersey).put("position",position).put("teamId",teamId).put("team",team).put("teamLogo",teamLogo).put("starter",starter).put("absent",absent);}
        static Person from(JSONObject o){return new Person(o.optString("id"),o.optString("name"),o.optString("shortName"),o.optString("photo"),o.optString("jersey"),o.optString("position"),o.optString("teamId"),o.optString("team"),o.optString("teamLogo"),o.optBoolean("starter"),o.optBoolean("absent"));}
    }
    public static final class Table {
        public String tab,title;
        public String teamId="",category="";
        public final List<String> columns=new ArrayList<>();
        public final List<List<String>> rows=new ArrayList<>();
        public final List<Person> people=new ArrayList<>();
        public Table(String tab,String title,String...columns){this.tab=tab;this.title=title;this.columns.addAll(Arrays.asList(columns));}
        public void row(String...values){rows.add(Arrays.asList(values));}
    }
    public void fact(String label,String value){if(value!=null&&!value.isBlank())facts.add(new Fact(label,value));}
    public void table(Table t){if(!t.rows.isEmpty())tables.add(t);}
    public JSONObject json()throws JSONException {
        JSONObject root=new JSONObject().put("match",match.json()).put("fetched",fetched).put("source",source).put("warning",warning);
        JSONArray f=new JSONArray(),s=new JSONArray(),ts=new JSONArray(),ev=new JSONArray();
        for(Fact v:facts)f.put(new JSONArray().put(v.label).put(v.value));for(Stat v:stats)s.put(new JSONArray().put(v.label).put(v.home).put(v.away));
        for(Table t:tables){JSONArray rs=new JSONArray(),people=new JSONArray();for(List<String> r:t.rows)rs.put(new JSONArray(r));for(Person person:t.people)people.put(person.json());ts.put(new JSONObject().put("tab",t.tab).put("title",t.title).put("teamId",t.teamId).put("category",t.category).put("columns",new JSONArray(t.columns)).put("rows",rs).put("people",people));}
        for(Event e:events)ev.put(new JSONArray().put(e.time).put(e.title).put(e.text));
        JSONArray games=new JSONArray();for(LolGame game:lolGames)games.put(game.data);
        return root.put("facts",f).put("stats",s).put("tables",ts).put("events",ev).put("lolGames",games).put("lolFetched",lolFetched);
    }
    public static MatchDetail fromJson(JSONObject root)throws JSONException {
        MatchDetail d=new MatchDetail(Match.fromJson(root.getJSONObject("match")));d.fetched=root.optLong("fetched");d.source=root.optString("source");d.warning=root.optString("warning");
        JSONArray a=root.getJSONArray("facts");for(int i=0;i<a.length();i++){JSONArray r=a.getJSONArray(i);d.fact(r.optString(0),r.optString(1));}
        a=root.getJSONArray("stats");for(int i=0;i<a.length();i++){JSONArray r=a.getJSONArray(i);d.stats.add(new Stat(r.optString(0),r.optString(1),r.optString(2)));}
        a=root.getJSONArray("tables");for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);Table t=new Table(o.getString("tab"),o.getString("title"));t.teamId=o.optString("teamId");t.category=o.optString("category");JSONArray cols=o.getJSONArray("columns");for(int j=0;j<cols.length();j++)t.columns.add(cols.optString(j));JSONArray rows=o.getJSONArray("rows");for(int j=0;j<rows.length();j++){JSONArray r=rows.getJSONArray(j);List<String> row=new ArrayList<>();for(int k=0;k<r.length();k++)row.add(r.optString(k));t.rows.add(row);}JSONArray people=o.optJSONArray("people");if(people!=null)for(int j=0;j<people.length();j++)t.people.add(Person.from(people.getJSONObject(j)));d.table(t);}
        a=root.getJSONArray("events");for(int i=0;i<a.length();i++){JSONArray r=a.getJSONArray(i);d.events.add(new Event(r.optString(0),r.optString(1),r.optString(2)));}
        d.lolFetched=root.optLong("lolFetched");a=root.optJSONArray("lolGames");if(a!=null)try{for(int i=0;i<a.length();i++){LolGame game=LolGame.parse(a.getJSONObject(i),d.match);if(game!=null)d.lolGames.add(game);}}catch(Exception e){throw new JSONException("Invalid cached LoL games");}return d;
    }
}
