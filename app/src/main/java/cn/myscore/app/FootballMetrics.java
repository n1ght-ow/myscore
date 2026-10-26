package cn.myscore.app;

import java.util.*;

/** Source-backed match events only; missing values are never filled or estimated. */
final class FootballMetrics {
    static boolean missing(String value){return value==null||value.isBlank()||value.equals("—");}
    static String events(DetailRows.Entry entry){
        List<String> result=new ArrayList<>();for(String key:List.of("进球","助攻","乌龙球","黄牌","红牌")){
            String value=FootballLayout.value(entry,key);if(DetailRows.number(value)>0)result.add(key+" "+DetailFormat.number(value,0));
        }
        String sub=FootballLayout.value(entry,"换人"),state=FootballLayout.value(entry,"状态");
        if(!missing(sub))result.add(sub);else if(state.equals("已换下")||state.equals("替补登场"))result.add(state);
        return String.join(" · ",result);
    }
}
