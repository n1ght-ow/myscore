package cn.myscore.app;

import java.util.*;

/** Starting formation only. Never presents schematic placement as tracking data. */
final class FootballLayout {
    static List<List<DetailRows.Entry>> lines(MatchDetail.Table table,String formation){
        if(!formation.matches("[1-5](?:-[1-5]){1,4}")||table.people.size()!=11||table.rows.size()!=11)return List.of();
        List<Integer> counts=new ArrayList<>();counts.add(1);int total=0;
        for(String part:formation.split("-")){int n=Integer.parseInt(part);total+=n;counts.add(n);}if(total!=10)return List.of();
        List<DetailRows.Entry> players=new ArrayList<>();Set<String> ids=new HashSet<>();
        for(int i=0;i<table.rows.size();i++){MatchDetail.Person person=table.people.get(i);if(person.position().isBlank()||!ids.add(person.id()))return List.of();players.add(new DetailRows.Entry(table,table.rows.get(i),person));}
        players.sort(Comparator.comparingInt((DetailRows.Entry e)->depth(e.person().position())).thenComparingInt(e->lateral(e.person().position())));
        if(depth(players.get(0).person().position())!=0)return List.of();
        List<List<DetailRows.Entry>> result=new ArrayList<>();int offset=0;
        for(int count:counts){List<DetailRows.Entry> row=new ArrayList<>(players.subList(offset,offset+count));row.sort(Comparator.comparingInt(e->lateral(e.person().position())));result.add(row);offset+=count;}return result;
    }
    private static int depth(String source){String p=DetailParsers.label(source);if(p.equals("门将"))return 0;if(p.contains("中卫")||p.equals("后卫")||p.contains("后卫"))return 1;if(p.contains("后腰"))return 2;if(p.contains("前卫")||p.equals("中场")||p.contains("翼卫"))return 3;if(p.contains("前腰")||p.contains("影锋"))return 4;if(p.contains("边锋")||p.contains("左中锋")||p.contains("右中锋"))return 5;return 6;}
    // Seen from the goal behind this team: right-sided players appear on the left.
    private static int lateral(String source){String p=DetailParsers.label(source);return p.contains("右")?(p.contains("中")?1:0):p.contains("左")?(p.contains("中")?3:4):2;}
    static String value(DetailRows.Entry entry,String column){return DetailRows.cell(entry.values(),entry.table().columns.indexOf(column));}
}
