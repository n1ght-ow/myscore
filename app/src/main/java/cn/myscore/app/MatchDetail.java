package cn.myscore.app;

import org.json.*;
import java.util.*;

/** Normalized, source-backed detail, persisted as one identity-scoped cache. */
public final class MatchDetail {
    public Match match;
    public long fetched;
    public String source="",warning="";
    public final List<Fact> facts=new ArrayList<>();
    public final List<Stat> stats=new ArrayList<>();
    public final List<Table> tables=new ArrayList<>();
    public final List<Event> events=new ArrayList<>();
    public MatchDetail(Match m){match=m;source=m.source;}
    public record Fact(String label,String value){}
    public record Stat(String label,String home,String away){}
    public record Event(String time,String title,String text){}
    public static final class Table {
        public String tab,title;
        public final List<String> columns=new ArrayList<>();
        public final List<List<String>> rows=new ArrayList<>();
        public Table(String tab,String title,String...columns){this.tab=tab;this.title=title;this.columns.addAll(Arrays.asList(columns));}
        public void row(String...values){rows.add(Arrays.asList(values));}
    }
    public void fact(String label,String value){if(value!=null&&!value.isBlank())facts.add(new Fact(label,value));}
    public void table(Table t){if(!t.rows.isEmpty())tables.add(t);}
    public JSONObject json()throws JSONException {
        JSONObject root=new JSONObject().put("match",match.json()).put("fetched",fetched).put("source",source).put("warning",warning);
        JSONArray f=new JSONArray(),s=new JSONArray(),ts=new JSONArray(),ev=new JSONArray();
        for(Fact v:facts)f.put(new JSONArray().put(v.label).put(v.value));for(Stat v:stats)s.put(new JSONArray().put(v.label).put(v.home).put(v.away));
        for(Table t:tables){JSONArray rs=new JSONArray();for(List<String> r:t.rows)rs.put(new JSONArray(r));ts.put(new JSONObject().put("tab",t.tab).put("title",t.title).put("columns",new JSONArray(t.columns)).put("rows",rs));}
        for(Event e:events)ev.put(new JSONArray().put(e.time).put(e.title).put(e.text));
        return root.put("facts",f).put("stats",s).put("tables",ts).put("events",ev);
    }
    public static MatchDetail fromJson(JSONObject root)throws JSONException {
        MatchDetail d=new MatchDetail(Match.fromJson(root.getJSONObject("match")));d.fetched=root.optLong("fetched");d.source=root.optString("source");d.warning=root.optString("warning");
        JSONArray a=root.getJSONArray("facts");for(int i=0;i<a.length();i++){JSONArray r=a.getJSONArray(i);d.fact(r.optString(0),r.optString(1));}
        a=root.getJSONArray("stats");for(int i=0;i<a.length();i++){JSONArray r=a.getJSONArray(i);d.stats.add(new Stat(r.optString(0),r.optString(1),r.optString(2)));}
        a=root.getJSONArray("tables");for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);Table t=new Table(o.getString("tab"),o.getString("title"));JSONArray cols=o.getJSONArray("columns");for(int j=0;j<cols.length();j++)t.columns.add(cols.optString(j));JSONArray rows=o.getJSONArray("rows");for(int j=0;j<rows.length();j++){JSONArray r=rows.getJSONArray(j);List<String> row=new ArrayList<>();for(int k=0;k<r.length();k++)row.add(r.optString(k));t.rows.add(row);}d.table(t);}
        a=root.getJSONArray("events");for(int i=0;i<a.length();i++){JSONArray r=a.getJSONArray(i);d.events.add(new Event(r.optString(0),r.optString(1),r.optString(2)));}return d;
    }
}
