package cn.myscore.app;

import org.json.JSONObject;
import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

public class UpdateTransferTest {
    private static final String HASH="2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";
    private UpdateRelease release()throws Exception{return UpdateRelease.restore(new JSONObject().put("version","9.9.9").put("url",UpdateRelease.REPOSITORY+"/releases/download/v9.9.9/MyScore-9.9.9.apk").put("digest","sha256:"+HASH).put("size",5).toString());}
    private static final class Response extends HttpURLConnection {
        final int code;final String location,body;final long length;boolean disconnected;
        Response(int code,String location,String body,long length)throws Exception{super(new URL("https://github.com"));this.code=code;this.location=location;this.body=body;this.length=length;}
        @Override public void connect(){}
        @Override public void disconnect(){disconnected=true;}
        @Override public boolean usingProxy(){return false;}
        @Override public int getResponseCode(){return code;}
        @Override public String getHeaderField(String name){return "Location".equals(name)?location:null;}
        @Override public long getContentLengthLong(){return length;}
        @Override public InputStream getInputStream(){return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));}
    }
    private interface Attempt {void run(File file)throws Exception;}
    private void rejected(Attempt attempt)throws Exception{
        File file=File.createTempFile("myscore-transfer-",".part");
        try{try{attempt.run(file);fail("Unsafe or incomplete transfer accepted");}catch(IOException expected){assertFalse(expected.getMessage().isBlank());}assertFalse("Failed partial file must be removed",file.exists());}
        finally{Files.deleteIfExists(file.toPath());}
    }
    @Test public void followsGithubRedirectAndVerifiesCompleteBytes()throws Exception{
        File file=File.createTempFile("myscore-transfer-",".part");Response redirect=new Response(302,"https://release-assets.githubusercontent.com/github-production-release-asset/1/app.apk?token=temporary","",0),body=new Response(200,null,"hello",5);
        ArrayDeque<Response> responses=new ArrayDeque<>(List.of(redirect,body));List<String> hosts=new ArrayList<>();
        try{UpdateTransfer.download(release(),file,bytes->{},()->false,url->{hosts.add(url.getHost());return responses.remove();});assertEquals(List.of("github.com","release-assets.githubusercontent.com"),hosts);assertEquals("hello",new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));assertTrue(redirect.disconnected&&body.disconnected);}
        finally{Files.deleteIfExists(file.toPath());}
    }
    @Test public void acceptsChunkedTransferOnlyAfterHashVerification()throws Exception{File file=File.createTempFile("myscore-transfer-",".part");try{UpdateTransfer.download(release(),file,bytes->{},()->false,url->{try{return new Response(200,null,"hello",-1);}catch(Exception e){throw new IOException(e);}});assertEquals(5,file.length());}finally{Files.deleteIfExists(file.toPath());}}
    @Test public void refusesHttpsDowngradeAndOtherRedirectDestinations()throws Exception{
        for(String target:List.of("http://release-assets.githubusercontent.com/app.apk","https://evil.invalid/app.apk","https://github.com.evil.invalid/app.apk","https://user@github.com/app.apk","https://github.com:8443/app.apk")){
            Response redirect=new Response(302,target,"",0);int[] calls={0};rejected(file->UpdateTransfer.download(release(),file,bytes->{},()->false,url->{calls[0]++;return redirect;}));assertEquals("Untrusted redirect must never be opened",1,calls[0]);
        }
    }
    @Test public void refusesRedirectWithoutLocation()throws Exception{Response response=new Response(302,null,"",0);rejected(file->UpdateTransfer.download(release(),file,bytes->{},()->false,url->response));}
    @Test public void boundsRedirectLoops()throws Exception{Response response=new Response(308,"https://github.com/loop","",0);int[] requests={0};rejected(file->UpdateTransfer.download(release(),file,bytes->{},()->false,url->{requests[0]++;return response;}));assertTrue(requests[0]<=6);}
    @Test public void refusesServerErrorPages()throws Exception{Response response=new Response(503,null,"hello",5);rejected(file->UpdateTransfer.download(release(),file,bytes->{},()->false,url->response));}
    @Test public void refusesUnexpectedDeclaredLength()throws Exception{Response response=new Response(200,null,"hello",6);rejected(file->UpdateTransfer.download(release(),file,bytes->{},()->false,url->response));}
    @Test public void refusesOversizedUnknownLengthBody()throws Exception{Response response=new Response(200,null,"hello!",-1);rejected(file->UpdateTransfer.download(release(),file,bytes->{},()->false,url->response));}
    @Test public void refusesTruncatedAndTamperedBodies()throws Exception{for(String value:List.of("hell","HELLO")){Response response=new Response(200,null,value,-1);rejected(file->UpdateTransfer.download(release(),file,bytes->{},()->false,url->response));}}
    @Test public void cancellationDoesNotExposeAnInstallablePartialFile()throws Exception{Response response=new Response(200,null,"hello",5);AtomicBoolean cancelled=new AtomicBoolean();rejected(file->UpdateTransfer.download(release(),file,bytes->{if(bytes>0)cancelled.set(true);},cancelled::get,url->response));}
}
