package cn.myscore.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.VectorDrawable;

/** Render-level checks for the clipped launcher mark and the opaque PNG seam regression. */
final class BrandChecks {
    static int run(Context context) {
        int checks=0;
        Drawable mark=context.getDrawable(R.drawable.brand_s).mutate();
        require(mark instanceof VectorDrawable,"brand mark uses a scalable vector, not the segmented PNG");checks++;
        mark.setTint(Color.WHITE);
        Bitmap full=render(mark,240,224);
        require(Color.alpha(full.getPixel(122,108))==0,"the diagonal S seam remains transparent after tinting");checks++;
        full.recycle();
        for(int size:new int[]{20,24,48}) {
            Bitmap small=render(mark,size,size);
            boolean coverage=false;
            for(int y=0;y<size;y++)for(int x=0;x<size;x++){int alpha=Color.alpha(small.getPixel(x,y));if(alpha>0&&alpha<255)coverage=true;}
            require(coverage,"small brand mark has antialiased edges at "+size+"px");checks++;small.recycle();
        }
        Drawable foreground=context.getDrawable(R.drawable.ic_launcher_foreground).mutate();
        int layer=Math.round(108*context.getResources().getDisplayMetrics().density);
        require(foreground.getIntrinsicWidth()==layer&&foreground.getIntrinsicHeight()==layer,"adaptive foreground has a square 108dp intrinsic canvas");checks++;
        for(int size:new int[]{108,216,432}) {
            Bitmap bitmap=render(foreground,size,size);int left=size,right=-1,top=size,bottom=-1;
            double radius=size*33d/108+.9,furthest=0;
            for(int y=0;y<size;y++)for(int x=0;x<size;x++)if(Color.alpha(bitmap.getPixel(x,y))>128){
                left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);
                furthest=Math.max(furthest,Math.hypot(x+.5-size/2d,y+.5-size/2d));
            }
            require(furthest<=radius,"launcher artwork stays inside the 66dp safe circle at "+size+"px");checks++;
            require(right-left+1>size*.43&&right-left+1<size*.49,"launcher artwork scales with its canvas at "+size+"px: "+left+","+top+" to "+right+","+bottom);checks++;
            require(Math.abs((left+right+1)/2d-size/2d)<=size*.02&&Math.abs((top+bottom+1)/2d-size/2d)<=size*.02,"launcher artwork stays centered at "+size+"px");checks++;
            bitmap.recycle();
        }
        return checks;
    }
    private static Bitmap render(Drawable drawable,int width,int height) {
        Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        drawable.setBounds(0,0,width,height);drawable.draw(new Canvas(bitmap));return bitmap;
    }
    private static void require(boolean okay,String message){if(!okay)throw new AssertionError(message);}
}
