package cn.myscore.app;

/** One row of a team schedule. Scores are presented in this team's order, never home-first. */
final class TeamScheduleRow {
    static final int SECTION=0,MATCH=1,NOTE=2;
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
        String detail=m.detail.isBlank()?competition==null?m.group:competition.name:m.detail;
        String state=m.live()&&!m.status.isBlank()?m.status:Rules.stateLabel(m.state);
        // 5EPlay's team feed and esports series have no home venue; do not label them 主/客场.
        String side=m.group.equals("CS2")||m.group.equals("LOL")?"":home?"主场":"客场";
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
}
