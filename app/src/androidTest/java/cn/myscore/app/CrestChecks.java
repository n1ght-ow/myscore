package cn.myscore.app;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;

/** Pixel regressions for transparent, optically fitted team marks. Fixtures stay in memory. */
final class CrestChecks {
    private static int checks;
    static int run(Instrumentation test,Context context)throws Exception {
        checks=0;
        test.runOnMainSync(()->{
            for(boolean dark:new boolean[]{false,true}){
                Ui ui=new Ui(context,dark);
                for(int size:new int[]{14,28,44}){
                    Logos.Crest crest=Logos.crest(ui,size,false);
                    check(crest.box.getBackground()==null,"team mark has no backing in either theme at "+size+"dp");
                }
                check(Logos.crest(ui,36,true).box.getBackground()!=null,"champion artwork keeps its separate presentation");
            }
        });
        Bitmap compact=mark(64,64,new Rect(16,8,48,56),Color.RED,false);
        Bitmap padded=mark(128,128,new Rect(48,40,80,88),Color.RED,false);
        Bitmap a=Logos.prepareCrest(compact,96,Color.WHITE),b=Logos.prepareCrest(padded,96,Color.WHITE);
        check(a.sameAs(b),"source transparent padding does not change displayed size or position");
        Rect bounds=bounds(a);
        check(Math.abs(bounds.exactCenterX()-48)<=1&&Math.abs(bounds.exactCenterY()-48)<=1,"visible mark is centred by its alpha bounds");
        check(Math.abs(bounds.width()/(float)bounds.height()-2f/3)<.03,"tall crest preserves its aspect ratio");
        check(Color.alpha(a.getPixel(0,0))==0&&Color.alpha(a.getPixel(95,95))==0,"normalised corners remain transparent");
        check(a.getPixel(48,48)==Color.RED,"coloured crest keeps source colour");a.recycle();b.recycle();
        Bitmap wide=Logos.prepareCrest(mark(128,64,new Rect(8,24,120,40),Color.RED,false),96,Color.WHITE);
        Rect wideBounds=bounds(wide);check(wideBounds.width()>wideBounds.height()*6,"wide crest is fitted without stretching or cutting off wings");wide.recycle();
        Bitmap dense=Logos.prepareCrest(mark(64,64,new Rect(8,8,56,56),Color.RED,false),96,Color.WHITE);
        Bitmap open=Logos.prepareCrest(mark(64,64,new Rect(8,8,56,56),Color.RED,true),96,Color.WHITE);
        check(bounds(open).width()>bounds(dense).width(),"open silhouette receives more visual size than dense badge");
        check(open.getPixel(48,48)==Color.TRANSPARENT,"transparent interior stays open");dense.recycle();open.recycle();
        Bitmap whiteDetail=mark(64,64,new Rect(8,8,56,56),Color.RED,false);
        Canvas detailCanvas=new Canvas(whiteDetail);Paint paint=new Paint();paint.setColor(Color.WHITE);detailCanvas.drawRect(24,24,40,40,paint);
        Bitmap preserved=Logos.prepareCrest(whiteDetail,96,Color.WHITE);
        check(preserved.getPixel(48,48)==Color.WHITE,"white detail inside a coloured crest is preserved");preserved.recycle();
        Bitmap blackAndWhite=mark(64,64,new Rect(8,8,56,56),Color.BLACK,false);
        Paint white=new Paint();white.setColor(Color.WHITE);new Canvas(blackAndWhite).drawRect(30,30,34,34,white);
        Bitmap mixed=Logos.prepareCrest(blackAndWhite,96,Color.WHITE);
        check(mixed.getPixel(48,48)==Color.WHITE&&mixed.getPixel(32,32)==Color.BLACK,"small white detail prevents recolouring a black-and-white crest");mixed.recycle();
        int darkInk=0xff182437,lightInk=0xfff2f5fa;
        String mergedUrl="https://oss.5eplay.com/sport/csgo/team/merge_cgchcqhll806h1bg2lt0.png";
        Bitmap lightVariant=Logos.selectCrestVariant(variants(),mergedUrl,darkInk);
        check(lightVariant.getWidth()==64&&lightVariant.getPixel(32,32)==Color.BLACK,"5EPlay merged mark selects only black variant on light surface");lightVariant.recycle();
        Bitmap darkVariant=Logos.selectCrestVariant(variants(),mergedUrl,lightInk);
        check(darkVariant.getWidth()==64&&darkVariant.getPixel(32,32)==Color.WHITE,"5EPlay merged mark selects only white variant on dark surface");darkVariant.recycle();
        Bitmap ordinary=variants(),unchanged=Logos.selectCrestVariant(ordinary,"https://a.espncdn.com/team/merge_sample.png",lightInk);
        check(ordinary==unchanged&&unchanged.getWidth()==128,"ordinary wide team mark is never treated as a merged sprite");unchanged.recycle();
        Bitmap square=mark(64,64,new Rect(8,8,56,56),Color.RED,false),notMerged=Logos.selectCrestVariant(square,mergedUrl,lightInk);
        check(square==notMerged,"merged filename without two equal square variants is not cropped");notMerged.recycle();
        Bitmap blackOnDark=Logos.prepareCrest(mark(64,64,new Rect(8,8,56,56),Color.BLACK,false),96,lightInk);
        check(blackOnDark.getPixel(48,48)==lightInk,"pure black mark is legible on dark surface without a tile");blackOnDark.recycle();
        Bitmap blackOnLight=Logos.prepareCrest(mark(64,64,new Rect(8,8,56,56),Color.BLACK,false),96,darkInk);
        check(blackOnLight.getPixel(48,48)==Color.BLACK,"pure black mark keeps original ink on light surface");blackOnLight.recycle();
        Bitmap whiteOnLight=Logos.prepareCrest(mark(64,64,new Rect(8,8,56,56),Color.WHITE,false),96,darkInk);
        check(whiteOnLight.getPixel(48,48)==darkInk,"pure white mark is legible on light surface without a tile");whiteOnLight.recycle();
        try{Logos.prepareCrest(Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888),96,Color.WHITE);throw new AssertionError("empty crest must use the missing-image fallback");}
        catch(java.io.IOException expected){check(true,"empty crest falls back instead of hiding its placeholder");}
        return checks;
    }
    private static Bitmap mark(int width,int height,Rect bounds,int colour,boolean open){
        Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);Paint paint=new Paint();paint.setColor(colour);
        if(open){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);bounds=new Rect(bounds);bounds.inset(1,1);}
        new Canvas(bitmap).drawRect(bounds,paint);return bitmap;
    }
    private static Bitmap variants(){
        Bitmap bitmap=Bitmap.createBitmap(128,64,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);Paint paint=new Paint();
        paint.setColor(Color.BLACK);canvas.drawRect(8,8,56,56,paint);paint.setColor(Color.WHITE);canvas.drawRect(72,8,120,56,paint);return bitmap;
    }
    private static Rect bounds(Bitmap bitmap){
        Rect result=new Rect(bitmap.getWidth(),bitmap.getHeight(),0,0);
        for(int y=0;y<bitmap.getHeight();y++)for(int x=0;x<bitmap.getWidth();x++)if(Color.alpha(bitmap.getPixel(x,y))>=8){result.left=Math.min(result.left,x);result.top=Math.min(result.top,y);result.right=Math.max(result.right,x+1);result.bottom=Math.max(result.bottom,y+1);}
        return result;
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;android.util.Log.i("MyScoreCrestTests","PASS "+message);}
}
