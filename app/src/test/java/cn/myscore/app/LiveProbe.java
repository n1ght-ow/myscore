package cn.myscore.app;

import org.json.JSONObject;
import org.json.JSONArray;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Explicit opt-in live schema validation, separate from deterministic unit tests. */
public final class LiveProbe {
    public static void main(String[] args) throws Exception {
        LocalDate from=Match.today(System.currentTimeMillis());JSONArray report=new JSONArray();int errors=0;
        for(Competition c:Competition.ALL) {
            JSONObject record=new JSONObject().put("competition",c.id);
            try {
                List<Match> matches;
                if(c.id.equals("national")) {
                    Map<String,Match> unique=new LinkedHashMap<>();
                    for(NationalTeams.Entry entry:NationalTeams.FOLLOWED) {
                        TeamSchedule.Result result=TeamSchedule.fetch(entry.team(),System.currentTimeMillis());
                        if(!result.error.isEmpty())throw new Exception(entry.name()+"："+result.error);
                        for(Match match:result.matches)unique.putIfAbsent(match.id,match);
                    }
                    matches=new ArrayList<>(unique.values());
                } else if(c.id.equals("cs2")) {
                    matches=DomesticEsports.cs2(from,from,System.currentTimeMillis());
                } else {
                    String url="https://site.api.espn.com/apis/site/v2/sports/"+c.path+"/scoreboard?dates="+(c.id.equals("f1")?from.minusDays(7).format(DateTimeFormatter.BASIC_ISO_DATE)+"-"+from.plusDays(2).format(DateTimeFormatter.BASIC_ISO_DATE):from.format(DateTimeFormatter.BASIC_ISO_DATE))+"&limit=1000";
                    matches=Parsers.espn(new JSONObject(Repository.get(url)),c,System.currentTimeMillis());
                }
                record.put("ok",true).put("matches",matches.size());
                if(!matches.isEmpty())record.put("sample",matches.get(0).json());
                System.out.println(c.name+": OK "+matches.size());
            }catch(Exception e){errors++;record.put("ok",false).put("error",e.toString());System.out.println(c.name+": FAILED "+e.getMessage());}
            report.put(record);
        }
        Path target=java.nio.file.Paths.get("../artifacts/live-source-report.json");Files.createDirectories(target.toAbsolutePath().getParent());
        Files.write(target,new JSONObject().put("checkedAt",java.time.Instant.now().toString()).put("explicitJavaProxyConfigured",System.getProperty("https.proxyHost")!=null).put("sources",report).toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if(errors>0)throw new IllegalStateException(errors+" source(s) could not be validated");
    }
}
