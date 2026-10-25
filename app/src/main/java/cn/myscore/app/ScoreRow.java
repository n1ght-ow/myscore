package cn.myscore.app;

/** One adapter row. Match rows keep their source object so recycling never fabricates data. */
final class ScoreRow {
    static final int LOADING=0,FAILURE=1,SECTION=2,MATCH=3,F1=4,EMPTY=5;
    final int type;
    final Match match;
    final String text,title,message;
    private ScoreRow(int type,Match match,String text,String title,String message){this.type=type;this.match=match;this.text=text;this.title=title;this.message=message;}
    static ScoreRow loading(String text){return new ScoreRow(LOADING,null,text,null,null);}
    static ScoreRow failure(String text){return new ScoreRow(FAILURE,null,text,null,null);}
    static ScoreRow section(String text){return new ScoreRow(SECTION,null,text,null,null);}
    static ScoreRow match(Match match){return new ScoreRow(match.group.equals("F1")?F1:MATCH,match,null,null,null);}
    static ScoreRow empty(String title,String message){return new ScoreRow(EMPTY,null,null,title,message);}
    /** Identity of the row's shape: equal keys let the adapter rebind text without a relayout. */
    String key(){
        if(type==MATCH||type==F1) {
            boolean stale=match.live()&&System.currentTimeMillis()-match.updated>120_000;
            return "m"+type+":"+match.id+":"+(match.group.equals("F1")?Math.min(2,match.results.size()):0)+":"+stale;
        }
        if(type==SECTION)return "s:"+text;
        if(type==FAILURE)return "f";
        if(type==EMPTY)return "e";
        return "l";
    }
}
