package cn.myscore.app;

import java.util.*;

/** Display perspectives use published player fields, never locally calculated ratings. */
final class FootballMetrics {
    static final List<String> PERFORMANCE=List.of("评分","射门","传球","盘带","防守");
    static final List<String> PROFILES=List.of("国籍","年龄","跑动","身价");
    static String valid(String metric){return PERFORMANCE.contains(metric)||PROFILES.contains(metric)?metric:"评分";}
    static List<String> columns(String metric){return switch(valid(metric)){
        case "射门"->List.of("射门","射正");case "传球"->List.of("传球","成功传球");
        case "盘带"->List.of("成功盘带");case "防守"->List.of("抢断","犯规");
        case "跑动"->List.of("跑动距离");default->List.of(valid(metric));
    };}
    static String value(DetailRows.Entry entry,String metric){
        List<String> values=new ArrayList<>();for(String column:columns(metric))values.add(FootballLayout.value(entry,column));
        return values.stream().allMatch(FootballMetrics::missing)?"—":String.join(" / ",values);
    }
    static boolean missing(String value){return value==null||value.isBlank()||value.equals("—");}
    static boolean available(MatchDetail.Table table,String metric){
        if(table==null)return false;
        for(List<String> row:table.rows)for(String column:columns(metric))
            if(!missing(DetailRows.cell(row,table.columns.indexOf(column))))return true;
        return false;
    }
    static String caption(String metric){return String.join(" / ",columns(metric));}
    static String events(DetailRows.Entry entry){
        List<String> result=new ArrayList<>();for(String key:List.of("进球","助攻","乌龙球","黄牌","红牌")){
            String value=FootballLayout.value(entry,key);if(DetailRows.number(value)>0)result.add(key+" "+DetailFormat.number(value,0));
        }
        String sub=FootballLayout.value(entry,"换人"),state=FootballLayout.value(entry,"状态");
        if(!missing(sub))result.add(sub);else if(state.equals("已换下")||state.equals("替补登场"))result.add(state);
        return String.join(" · ",result);
    }
}
