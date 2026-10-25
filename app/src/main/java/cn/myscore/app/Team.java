package cn.myscore.app;

import org.json.JSONObject;

/**
 * A team identity taken from the match that was tapped. The source id is required: a name-only
 * lookup could show another club with the same short name, so an unknown identity stays unusable.
 */
public final class Team {
    public String league="", id="", name="", logo="";

    public boolean usable() {
        return !league.isBlank() && !id.isBlank() && !name.isBlank() && Competition.find(league)!=null;
    }
    public String competitionName() {
        Competition c=Competition.find(league);
        return c==null?league:c.name;
    }
    public JSONObject json() {
        JSONObject o=new JSONObject();
        try{o.put("league",league).put("id",id).put("name",name).put("logo",logo);}
        catch(org.json.JSONException e){throw new IllegalStateException(e);}
        return o;
    }
    public static Team fromJson(JSONObject o) {
        Team t=new Team();
        t.league=o.optString("league");t.id=o.optString("id");t.name=o.optString("name");t.logo=o.optString("logo");
        return t;
    }
    /** Returns null when the card has no source team id; the crest then keeps the card's own action. */
    public static Team of(Match match,boolean home) {
        if(match==null)return null;
        Team t=new Team();
        t.league=match.league;t.id=home?match.homeId:match.awayId;t.name=home?match.home:match.away;t.logo=home?match.homeLogo:match.awayLogo;
        return t.usable()?t:null;
    }
}
