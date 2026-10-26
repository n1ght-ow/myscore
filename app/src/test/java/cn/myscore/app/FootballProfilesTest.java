package cn.myscore.app;

import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class FootballProfilesTest {
    private JSONObject athlete(String name,String dob)throws Exception{return new JSONObject().put("displayName",name).put("dateOfBirth",dob);}
    private JSONObject player(String first,String last,String dob,int code)throws Exception{return new JSONObject().put("first_name",first).put("second_name",last).put("birth_date",dob).put("code",code);}
    @Test public void fullIdentityMatchesAccentsAndExtraPublishedMiddleNames()throws Exception{
        JSONObject person=athlete("Rúben Dias","1997-05-14T07:00Z"),candidate=player("Ruben","Santos Gato Alves Dias","1997-05-14",164742);
        assertSame(candidate,FootballProfiles.match(person,new JSONArray().put(candidate)));
    }
    @Test public void sameNameWithDifferentBirthdayCannotReceivePhoto()throws Exception{
        JSONObject person=athlete("John Smith","1990-01-01");assertNull(FootballProfiles.match(person,new JSONArray().put(player("John","Smith","1991-01-01",42))));
        assertNull(FootballProfiles.match(athlete("John Smith",""),new JSONArray().put(player("John","Smith","1990-01-01",42))));
    }
    @Test public void ambiguousIdentityAndSurnameOnlyAreRejected()throws Exception{
        JSONObject person=athlete("John Smith","1990-01-01"),candidate=player("John","Smith","1990-01-01",42);
        assertNull(FootballProfiles.match(person,new JSONArray().put(candidate).put(candidate)));
        assertNull(FootballProfiles.match(athlete("Smith","1990-01-01"),new JSONArray().put(candidate)));
    }
    @Test public void photoUsesMatchedOfficialCodeInsteadOfEspnId()throws Exception{
        JSONObject person=athlete("Erling Haaland","2000-07-21T07:00Z").put("id","253989");JSONObject fpl=new JSONObject().put("elements",new JSONArray().put(player("Erling","Haaland","2000-07-21",223094)));
        assertEquals("https://resources.premierleague.com/premierleague/photos/players/110x140/p223094.png",FootballProfiles.photo(person,fpl));
        assertEquals("",FootballProfiles.photo(person,new JSONObject()));
    }
    @Test public void realEspnPhotoIsPreferredAndJerseyImageIsRejected()throws Exception{
        JSONObject person=athlete("Player Name","1990-01-01").put("headshot",new JSONObject().put("href","https://a.espncdn.com/i/headshots/soccer/players/full/42.png"));
        assertEquals("https://a.espncdn.com/i/headshots/soccer/players/full/42.png",FootballProfiles.photo(person,new JSONObject()));
        person.put("headshot",new JSONObject().put("href","https://a.espncdn.com/jersey.png"));assertEquals("",FootballProfiles.photo(person,new JSONObject()));
    }
    @Test public void backupRequiresExactBirthdayNameSportAndTrustedPhotoHost()throws Exception{
        JSONObject source=athlete("Zion Suzuki","2002-08-21T07:00Z"),record=new JSONObject().put("strPlayer","Zion Suzuki").put("dateBorn","2002-08-21").put("strSport","Soccer").put("strCutout","https://r2.thesportsdb.com/images/media/player/cutout/photo.png");
        JSONObject response=new JSONObject().put("player",new JSONArray().put(record));
        assertFalse(FootballProfiles.backupPhoto(source,response).isBlank());record.put("dateBorn","2002-08-22");assertEquals("",FootballProfiles.backupPhoto(source,response));
        record.put("dateBorn","2002-08-21").put("strCutout","https://untrusted.example/player.png");assertEquals("",FootballProfiles.backupPhoto(source,response));
    }
}
