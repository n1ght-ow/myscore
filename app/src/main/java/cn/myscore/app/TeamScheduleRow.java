package cn.myscore.app;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** One row of a team schedule. Scores are presented in this team's order, never home-first. */
final class TeamScheduleRow {
    static final int SECTION=0,MATCH=1,NOTE=2;
    private static final DateTimeFormatter MONTH_DAY_WEEK = DateTimeFormatter.ofPattern("MMM d EEE", Locale.ENGLISH);
    private static final DateTimeFormatter YEAR_MONTH_DAY_WEEK = DateTimeFormatter.ofPattern("MMM d, yyyy EEE", Locale.ENGLISH);
    final int type;
    final String key,title,message;
    final Match match;
    final String date,time,opponent,detail,state,score,result,side;
    final boolean home;

    private TeamScheduleRow(int type,String key,String title,String message,Match match,String date,String time,String opponent,String detail,String state,String score,String result,String side,boolean home) {
        this.type=type;this.key=key;this.title=title;this.message=message;this.match=match;
        this.date=date;this.time=time;this.opponent=opponent;this.detail=detail;this.state=state;this.score=score;this.result=result;this.side=side;this.home=home;
    }
    static TeamScheduleRow section(String title) {
        return new TeamScheduleRow(SECTION,"s:"+title,title,null,null,null,null,null,null,null,null,null,"",false);
    }
    static TeamScheduleRow note(String title,String message) {
        return new TeamScheduleRow(NOTE,"n:"+title,title,message,null,null,null,null,null,null,null,null,"",false);
    }
    static TeamScheduleRow match(Match m,Team team) {
        boolean home=team.id.equals(m.homeId)||(!team.id.equals(m.awayId)&&team.name.equals(m.home));
        String opponent=home?m.away:m.home;
        if(opponent.isBlank())opponent="对阵待定";
        String date=m.day()==null?"日期待定":Formats.date(m.day(),m.day().getYear()==Match.today(System.currentTimeMillis()).getYear()?Formats.MONTH_DAY_WEEK:Formats.YEAR_MONTH_DAY_WEEK,"日期待定");
        String time=m.timeConfirmed?m.time():"待定";
        Competition competition=Competition.find(m.league);
        String detail=NationalTeams.LEAGUE.equals(m.league)?MatchLabel.of(m):m.detail.isBlank()?competition==null?m.group:competition.name:m.detail;
        String state=m.live()&&!m.status.isBlank()?m.status:Rules.stateLabel(m.state);
        String side=m.group.equals("CS2")||NationalTeams.LEAGUE.equals(m.league)?"":home?"主场":"客场";
        String score="",result="";
        if(m.hasScore()) {
            String own=m.displayScore(home),other=m.displayScore(!home);
            score=own+"–"+other;
            if(m.finalised()&&m.homeScore.matches("\\d+")&&m.awayScore.matches("\\d+"))result=m.outcome(home)>0?"胜":m.outcome(home)<0?"负":"平";
        }
        return new TeamScheduleRow(MATCH,"m:"+m.id,null,null,m,date,time,opponent,detail,state,score,result,side,home);
    }
    /** Only a completed, numeric score becomes a win/draw/loss label. */
    static String result(String own,String other) {
        try {
            int a=Integer.parseInt(own.trim()),b=Integer.parseInt(other.trim());
            return a>b?"胜":a<b?"负":"平";
        } catch(NumberFormatException e) {
            return "";
        }
    }

    String displayOpponent() {
        return switch(opponent) {
            case "对阵待定", "待定", "TBD" -> "Opponent TBD";
            default -> opponent;
        };
    }

    String displayDate() {
        if(match==null||match.day()==null)return "Date TBD";
        LocalDate day=match.day();
        return day.format(day.getYear()==Match.today(System.currentTimeMillis()).getYear()?MONTH_DAY_WEEK:YEAR_MONTH_DAY_WEEK);
    }

    String displayTime() {
        if(match==null||!match.timeConfirmed||match.start<=0)return "TBD";
        String value=match.time();
        return value.isBlank()||"时间待定".equals(value)?"TBD":value;
    }

    String displaySide() {
        return switch(side) {
            case "主场" -> "Home";
            case "客场" -> "Away";
            default -> side;
        };
    }

    String displayDetail() {
        return switch(detail) {
            case "英超" -> "Premier League";
            case "欧冠" -> "Champions League";
            case "国家队" -> "National team";
            case "足球" -> "Football";
            default -> detail;
        };
    }

    String displayState() {
        return switch(state) {
            case "进行中" -> "Live";
            case "未开始" -> "Scheduled";
            case "已结束" -> "Final";
            case "已取消" -> "Cancelled";
            case "延期 / 暂停" -> "Postponed";
            case "状态待确认" -> "Status TBD";
            case "时间待定" -> "Time TBD";
            default -> state;
        };
    }

    String displayResult() {
        return switch(result) {
            case "胜" -> "W";
            case "负" -> "L";
            case "平" -> "D";
            default -> result;
        };
    }
}
