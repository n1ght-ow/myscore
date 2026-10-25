package cn.myscore.app;

import java.util.*;

/** Source rows with stable athlete identity, filters, and missing-last sorting. */
final class DetailRows {
    record Entry(MatchDetail.Table table,List<String> values,MatchDetail.Person person){}
    static String category(MatchDetail.Table table){return table.category.isBlank()?table.title:table.category;}
    static List<Entry> select(List<MatchDetail.Table> tables,String category,String team,String sort,boolean descending){
        List<Entry> rows=new ArrayList<>();
        for(MatchDetail.Table t:tables)if(category(t).equals(category)&&(team.equals("all")||team.equals(t.teamId)))for(int i=0;i<t.rows.size();i++)rows.add(new Entry(t,t.rows.get(i),i<t.people.size()?t.people.get(i):null));
        if(!sort.isBlank())rows.sort((a,b)->{boolean absentA=a.person!=null&&a.person.absent(),absentB=b.person!=null&&b.person.absent();if(absentA!=absentB)return absentA?1:-1;int ai=a.table.columns.indexOf(sort),bi=b.table.columns.indexOf(sort);double av=ai<0?Double.NaN:number(cell(a.values,ai)),bv=bi<0?Double.NaN:number(cell(b.values,bi));if(Double.isNaN(av)||Double.isNaN(bv))return Double.isNaN(av)?Double.isNaN(bv)?0:1:-1;return descending?Double.compare(bv,av):Double.compare(av,bv);});
        return rows;
    }
    static String cell(List<String> values,int index){return index>=0&&index<values.size()&&!values.get(index).isBlank()?values.get(index):"—";}
    static double number(String text){try{String value=text.replace("%","").trim();if(value.contains(":")){double seconds=0;for(String part:value.split(":"))seconds=seconds*60+Double.parseDouble(part);return seconds;}java.util.regex.Matcher m=java.util.regex.Pattern.compile("^[+-]?[0-9]+(?:\\.[0-9]+)?").matcher(value);return m.find()?Double.parseDouble(m.group()):Double.NaN;}catch(RuntimeException e){return Double.NaN;}}
}
