package cn.myscore.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Locale;

/** Only stable, checksummed APK assets from this project's public releases. */
final class UpdateRelease {
    static final String REPOSITORY="https://github.com/n1ght-ow/myscore";
    static final String LATEST_API="https://api.github.com/repos/n1ght-ow/myscore/releases/latest";
    static final long MAX_BYTES=100L*1024*1024;
    final String version,url,assetUrl,sha256,notes;
    final long size;
    private UpdateRelease(String version,String url,String assetUrl,String digest,long size,String notes)throws IOException {
        parts(version);
        if(!url.equals(REPOSITORY+"/releases/download/v"+version+"/MyScore-"+version+".apk"))throw new IOException("更新文件地址不属于 MyScore 发布渠道");
        if(!assetUrl.isEmpty()&&!assetUrl.matches("https://api\\.github\\.com/repos/n1ght-ow/myscore/releases/assets/[1-9][0-9]{0,18}"))throw new IOException("更新附件接口不属于 MyScore 发布渠道");
        if(!digest.matches("sha256:[a-fA-F0-9]{64}"))throw new IOException("发布文件缺少有效的校验信息，请稍后重试");
        if(size<=0||size>MAX_BYTES)throw new IOException("更新文件大小异常");
        this.version=version;this.url=url;this.assetUrl=assetUrl;this.sha256=digest.substring(7).toLowerCase(Locale.ROOT);this.size=size;
        this.notes=notes==null?"":notes.substring(0,Math.min(notes.length(),8000));
    }
    static UpdateRelease parse(JSONObject root)throws IOException {
        if(root.optBoolean("draft")||root.optBoolean("prerelease"))throw new IOException("当前没有可用的正式更新");
        String tag=root.optString("tag_name");
        if(!tag.startsWith("v"))throw new IOException("无法识别发布版本");
        String version=tag.substring(1);parts(version);JSONArray assets=root.optJSONArray("assets");
        if(assets!=null)for(int i=0;i<assets.length();i++){
            JSONObject asset=assets.optJSONObject(i);
            if(asset!=null&&asset.optString("name").equals("MyScore-"+version+".apk")&&asset.optString("state").equals("uploaded"))
                return new UpdateRelease(version,asset.optString("browser_download_url"),asset.optString("url"),asset.optString("digest"),asset.optLong("size"),root.optString("body"));
        }
        throw new IOException("新版本尚未上传安装包，请稍后重试");
    }
    static UpdateRelease restore(String json)throws Exception {
        JSONObject value=new JSONObject(json);
        return new UpdateRelease(value.getString("version"),value.getString("url"),value.optString("assetUrl"),value.getString("digest"),value.getLong("size"),value.optString("notes"));
    }
    String saved()throws Exception {return new JSONObject().put("version",version).put("url",url).put("assetUrl",assetUrl).put("digest","sha256:"+sha256).put("size",size).put("notes",notes).toString();}
    boolean newerThan(String installed)throws IOException{return compare(version,installed)>0;}
    String summary(){
        StringBuilder bullets=new StringBuilder();int count=0;
        for(String line:notes.split("\\r?\\n")){String text=line.trim();if((text.startsWith("- ")||text.startsWith("* "))&&count++<8)bullets.append("• ").append(text.substring(2).replace("**","").replace("`","")).append("\n\n");}
        if(bullets.length()>0)return bullets.toString().trim();
        String plain=notes.replaceAll("(?m)^#{1,6}\\s+","").replace("**","").replace("`","").trim();return plain.substring(0,Math.min(plain.length(),1200));
    }
    static int compare(String left,String right)throws IOException {
        int[] a=parts(left),b=parts(right);for(int i=0;i<3;i++){int difference=Integer.compare(a[i],b[i]);if(difference!=0)return difference;}return 0;
    }
    private static int[] parts(String value)throws IOException {
        if(value==null||!value.matches("(?:0|[1-9][0-9]{0,3})\\.(?:0|[1-9][0-9]{0,3})(?:\\.(?:0|[1-9][0-9]{0,3}))?"))throw new IOException("无法识别发布版本");
        String[] tokens=value.split("\\.");return new int[]{Integer.parseInt(tokens[0]),Integer.parseInt(tokens[1]),tokens.length==3?Integer.parseInt(tokens[2]):0};
    }
    void verifyFile(File file)throws Exception {
        if(!file.isFile()||file.length()!=size)throw new IOException("安装包不完整，请重新下载");
        MessageDigest digest=MessageDigest.getInstance("SHA-256");long total=0;
        try(FileInputStream input=new FileInputStream(file)){byte[] buffer=new byte[32768];int count;while((count=input.read(buffer))!=-1){total+=count;if(total>size)throw new IOException("安装包大小异常");digest.update(buffer,0,count);}}
        StringBuilder actual=new StringBuilder();for(byte value:digest.digest())actual.append(String.format(Locale.ROOT,"%02x",value));
        if(total!=size||!sha256.equals(actual.toString()))throw new IOException("安装包校验未通过，请重新下载");
    }
}
