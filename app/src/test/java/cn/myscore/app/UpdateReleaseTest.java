package cn.myscore.app;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class UpdateReleaseTest {
    private static final String HASH="2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";
    private JSONObject asset()throws Exception{return new JSONObject().put("name","MyScore-0.6.1.apk").put("state","uploaded").put("browser_download_url",UpdateRelease.REPOSITORY+"/releases/download/v0.6.1/MyScore-0.6.1.apk").put("digest","sha256:"+HASH).put("size",5);}
    private JSONObject release(JSONObject asset)throws Exception{return new JSONObject().put("tag_name","v0.6.1").put("draft",false).put("prerelease",false).put("body","Changes").put("assets",new JSONArray().put(asset));}
    private interface Attempt {void run()throws Exception;}
    private void rejects(Attempt attempt)throws Exception{try{attempt.run();fail("untrusted or incomplete update should be rejected");}catch(IOException expected){assertFalse(expected.getMessage().isBlank());}}
    @Test public void comparesVersionsNumerically()throws Exception{assertTrue(UpdateRelease.compare("0.10.0","0.9.9")>0);assertTrue(UpdateRelease.compare("1.0.0","0.99.99")>0);assertEquals(0,UpdateRelease.compare("0.6.1","0.6.1"));assertTrue(UpdateRelease.compare("0.5.1","0.6.0")<0);}
    @Test public void rejectsMalformedOrAmbiguousVersions()throws Exception{for(String value:new String[]{"../x","0.6","0.06.1","0.6.1-rc.1","10000.0.0","0.6.1/other"})rejects(()->UpdateRelease.compare(value,"0.6.0"));}
    @Test public void parsesAnExactStableApk()throws Exception{UpdateRelease update=UpdateRelease.parse(release(asset()));assertEquals("0.6.1",update.version);assertEquals(HASH,update.sha256);assertTrue(update.newerThan("0.6.0"));}
    @Test public void ignoresUnrelatedAssets()throws Exception{JSONObject root=release(asset());root.getJSONArray("assets").put(0,new JSONObject().put("name","other.apk"));rejects(()->UpdateRelease.parse(root));}
    @Test public void rejectsDraftAndPreviewReleases()throws Exception{rejects(()->UpdateRelease.parse(release(asset()).put("draft",true)));rejects(()->UpdateRelease.parse(release(asset()).put("prerelease",true)));}
    @Test public void refusesAssetsThatHaveNotFinishedUploading()throws Exception{rejects(()->UpdateRelease.parse(release(asset().put("state","new"))));}
    @Test public void refusesOtherHostsRepositoriesAndTransport()throws Exception{for(String url:new String[]{"http://github.com/n1ght-ow/myscore/releases/download/v0.6.1/MyScore-0.6.1.apk","https://github.com/other/myscore/releases/download/v0.6.1/MyScore-0.6.1.apk","https://evil.invalid/MyScore-0.6.1.apk",UpdateRelease.REPOSITORY+"/releases/download/v0.6.1/MyScore-0.6.1.apk?extra=1"})rejects(()->UpdateRelease.parse(release(asset().put("browser_download_url",url))));}
    @Test public void refusesWrongVersionAssetUrl()throws Exception{rejects(()->UpdateRelease.parse(release(asset().put("browser_download_url",UpdateRelease.REPOSITORY+"/releases/download/v0.5.1/MyScore-0.5.1.apk"))));}
    @Test public void requiresSha256AndBoundedSize()throws Exception{for(String digest:new String[]{"","sha256:123","md5:"+HASH})rejects(()->UpdateRelease.parse(release(asset().put("digest",digest))));for(long size:new long[]{0,-1,UpdateRelease.MAX_BYTES+1})rejects(()->UpdateRelease.parse(release(asset().put("size",size))));}
    @Test public void restoresOnlyRevalidatedMetadata()throws Exception{UpdateRelease original=UpdateRelease.parse(release(asset()));UpdateRelease restored=UpdateRelease.restore(original.saved());assertEquals(original.url,restored.url);assertEquals(original.size,restored.size);rejects(()->UpdateRelease.restore(new JSONObject(original.saved()).put("url","https://evil.invalid/app.apk").toString()));}
    @Test public void verifiesRealFileBytesAndRejectsTampering()throws Exception{UpdateRelease update=UpdateRelease.parse(release(asset()));File file=File.createTempFile("myscore-update-",".apk");try{Files.write(file.toPath(),"hello".getBytes(java.nio.charset.StandardCharsets.UTF_8));update.verifyFile(file);Files.write(file.toPath(),"HELLO".getBytes(java.nio.charset.StandardCharsets.UTF_8));rejects(()->update.verifyFile(file));Files.write(file.toPath(),"shorter than expected".getBytes(java.nio.charset.StandardCharsets.UTF_8));rejects(()->update.verifyFile(file));}finally{Files.deleteIfExists(file.toPath());}}
    @Test public void boundsRemoteNotes()throws Exception{UpdateRelease update=UpdateRelease.parse(release(asset()).put("body","a".repeat(9000)));assertEquals(8000,update.notes.length());}
}
