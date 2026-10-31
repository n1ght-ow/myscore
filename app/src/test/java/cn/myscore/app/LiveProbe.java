package cn.myscore.app;

import org.json.JSONObject;
import org.json.JSONArray;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Explicit opt-in live schema validation, separate from deterministic unit tests. */
public final class LiveProbe {
    public static void main(String[] args) throws Exception {
        LocalDate from=Match.today(System.currentTimeMillis());JSONArray report=new JSONArray();int errors=0;
        for(Competition c:Competition.ALL) {
            JSONObject record=new JSONObject().put("competition",c.id);
            try {
                String url;
                if(!c.esports())url="https://site.api.espn.com/apis/site/v2/sports/"+c.path+"/scoreboard?dates="+(c.id.equals("f1")?from.minusDays(7).format(DateTimeFormatter.BASIC_ISO_DATE)+"-"+from.plusDays(2).format(DateTimeFormatter.BASIC_ISO_DATE):from.format(DateTimeFormatter.BASIC_ISO_DATE))+"&limit=1000";
                else url=c.id.equals("lol")?DomesticEsports.LOL:"";
                List<Match> matches;
                if(c.id.equals("cs2"))matches=DomesticEsports.cs2(from,from,System.currentTimeMillis());
                else {JSONObject root=new JSONObject(Repository.get(url));matches=c.id.equals("lol")?DomesticEsports.worlds(root,System.currentTimeMillis()):Parsers.espn(root,c,System.currentTimeMillis());}
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
