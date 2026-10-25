package cn.myscore.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    // Creating a Typeface for every TextView is measurable on long lists; these are process-wide.
    private static final Typeface REGULAR=Typeface.create("sans-serif",Typeface.NORMAL);
    private static final Typeface MEDIUM=Typeface.create("sans-serif-medium",Typeface.NORMAL);
    /** A window hint only: Android still owns power, thermal and user refresh policy. */
    static void highRefresh(android.view.Window window) {
        if(window==null)return;
        android.view.Display display=window.getDecorView().getDisplay();
        if(display==null)return;
        android.view.Display.Mode current=display.getMode();float best=current.getRefreshRate();
        for(android.view.Display.Mode mode:display.getSupportedModes())
            if(mode.getPhysicalWidth()==current.getPhysicalWidth()&&mode.getPhysicalHeight()==current.getPhysicalHeight())best=Math.max(best,mode.getRefreshRate());
        android.view.WindowManager.LayoutParams params=window.getAttributes();
        if(params.preferredDisplayModeId==0&&Math.abs(params.preferredRefreshRate-best)<0.1f)return;
        params.preferredDisplayModeId=0;params.preferredRefreshRate=best;window.setAttributes(params);
    }
    final Context c;
    static final int CARD_RADIUS=16,CONTROL_RADIUS=12,SHEET_RADIUS=28;
    final int bg,surface,text,muted,primary,onPrimary,tonal,line,error,secondary;
    static boolean isDark(Context context) {
        String theme=context.getSharedPreferences("settings",0).getString("theme","dark");
        return "dark".equals(theme)||("system".equals(theme)&&(context.getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES);
    }
    Ui(Context c,boolean dark) {
        this.c=c;
        bg=Color.parseColor(dark?"#0C1017":"#F3F6FB");
        surface=Color.parseColor(dark?"#151C27":"#FFFFFF");
        text=Color.parseColor(dark?"#F2F5FA":"#182437");
        muted=Color.parseColor(dark?"#9BAAC0":"#52627A");
        primary=Color.parseColor(dark?"#8BC8FF":"#235E99");
        onPrimary=Color.parseColor(dark?"#10243A":"#FFFFFF");
        tonal=Color.parseColor(dark?"#202E40":"#DFEAF7");
        line=Color.parseColor(dark?"#2A3547":"#D6DFEB");
        error=Color.parseColor(dark?"#FFB4A9":"#A6342C");
        secondary=Color.parseColor(dark?"#B5ACEF":"#65548E");
    }
    int dp(float n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    LinearLayout column(){LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.VERTICAL);return v;}
    LinearLayout row(){LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    TextView text(String s,int sp,int color,boolean bold) {
        TextView v=new TextView(c);v.setText(s);v.setTextSize(sp);v.setTextColor(color);
        v.setFontFeatureSettings("tnum");v.setIncludeFontPadding(false);
        v.setTypeface(bold?MEDIUM:REGULAR);
        return v;
    }
    ScoreText score(String home,String away,boolean available,int maxSp){return new ScoreText(home,away,available,maxSp);}
    /** Preserve a readable minimum: narrow scoreboards stack the two scores instead of clipping. */
    final class ScoreText extends TextView {
        private final Paint measure=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int maxSp;
        private String home="",away="",single="VS";
        private boolean available;
        private int ceiling;
        ScoreText(String home,String away,boolean available,int maxSp){
            super(Ui.this.c);this.maxSp=maxSp;setTypeface(MEDIUM);setFontFeatureSettings("tnum");setIncludeFontPadding(false);
            setTextColor(text);setGravity(Gravity.CENTER);setScore(home,away,available);
        }
        void setScore(String home,String away,boolean available){
            if(ceiling!=0&&this.available==available&&this.home.equals(home)&&this.away.equals(away))return;
            this.home=home;this.away=away;this.available=available;single=available?home+"–"+away:"VS";
            int next=available?maxSp:Math.min(24,maxSp);
            if(next!=ceiling){ceiling=next;setAutoSizeTextTypeUniformWithConfiguration(22,ceiling,1,android.util.TypedValue.COMPLEX_UNIT_SP);}
            setContentDescription(available?"比分 "+home+" 比 "+away:"来源尚未提供比分");
            if(!single.contentEquals(getText()))setText(single);
        }
        @Override protected void onMeasure(int widthSpec,int heightSpec){
            int width=MeasureSpec.getSize(widthSpec)-getPaddingLeft()-getPaddingRight();
            measure.set(getPaint());measure.setTextSize(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,22,c.getResources().getDisplayMetrics()));
            boolean stack=available&&MeasureSpec.getMode(widthSpec)!=MeasureSpec.UNSPECIFIED&&width>0&&measure.measureText(single)>width;
            String display=stack?home+"\n"+away:single;
            int lines=stack?2:1;if(getMaxLines()!=lines)setMaxLines(lines);if(!display.contentEquals(getText()))setText(display);
            super.onMeasure(widthSpec,heightSpec);
        }
    }
    GradientDrawable shape(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    GradientDrawable panel(){GradientDrawable g=shape(surface,CARD_RADIUS);g.setStroke(dp(1),line);return g;}
    void clickable(View v,int fill,int radius){
        GradientDrawable background=shape(fill,radius);
        if(fill==surface&&radius>=CARD_RADIUS)background.setStroke(dp(1),line);
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(primary&0x00ffffff|0x25000000),background,shape(Color.WHITE,radius)));
    }
    TextView chip(String label,boolean selected,Runnable action) {
        TextView chip=text(label,13,selected?onPrimary:muted,selected);chip.setGravity(Gravity.CENTER);
        chip.setMinHeight(dp(48));chip.setPadding(dp(16),dp(10),dp(16),dp(10));
        clickable(chip,selected?primary:surface,CONTROL_RADIUS);chip.setSelected(selected);chip.setFocusable(true);
        chip.setOnClickListener(v->action.run());return chip;
    }
    TextView button(String label,boolean filled,Runnable action) {
        TextView b=text(label,14,filled?onPrimary:primary,true);b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(48));b.setPadding(dp(16),dp(14),dp(16),dp(14));clickable(b,filled?primary:tonal,CONTROL_RADIUS);
        b.setFocusable(true);b.setOnClickListener(v->action.run());return b;
    }
    View divider(){View d=new View(c);d.setBackgroundColor(line);d.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(1)));return d;}
    void gap(LinearLayout into,int h){View space=new View(c);into.addView(space,new LinearLayout.LayoutParams(1,dp(h)));}
    static final class Icon extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private String kind;private int color;private final Path refreshArrow=new Path();
        Icon(Context c,String kind,int color){super(c);this.kind=kind;this.color=color;refreshArrow.moveTo(19,3);refreshArrow.lineTo(19,9);refreshArrow.lineTo(13,9);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        void set(String kind,int color){if(kind.equals(this.kind)&&color==this.color)return;this.kind=kind;this.color=color;invalidate();}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);canvas.save();float scale=Math.min(getWidth(),getHeight())/24f;
            canvas.translate((getWidth()-24*scale)/2,(getHeight()-24*scale)/2);canvas.scale(scale,scale);
            p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.8f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
            if(kind.equals("back")){canvas.drawLine(20,12,4,12,p);canvas.drawLine(4,12,11,5,p);canvas.drawLine(4,12,11,19,p);}
            else if(kind.equals("check")){canvas.drawLine(5,12,10,17,p);canvas.drawLine(10,17,19,7,p);}
            else if(kind.equals("chevron")){canvas.drawLine(9,6,15,12,p);canvas.drawLine(15,12,9,18,p);}
            else if(kind.equals("refresh")) {canvas.drawArc(4,4,20,20,35,285,false,p);canvas.drawPath(refreshArrow,p);}
            else if(kind.equals("calendar")) {canvas.drawRoundRect(4,5,20,21,2,2,p);canvas.drawLine(8,3,8,7,p);canvas.drawLine(16,3,16,7,p);canvas.drawLine(4,10,20,10,p);canvas.drawLine(8,14,10,14,p);canvas.drawLine(14,14,16,14,p);canvas.drawLine(8,17,10,17,p);}
            else if(kind.equals("calendar-add")||kind.equals("calendar-check")) {
                canvas.drawRoundRect(4.5f,6,19.5f,20.5f,2,2,p);canvas.drawLine(8.5f,3.5f,8.5f,8,p);canvas.drawLine(15.5f,3.5f,15.5f,8,p);canvas.drawLine(4.5f,10.5f,19.5f,10.5f,p);
                if(kind.equals("calendar-add")){canvas.drawLine(9,15.5f,15,15.5f,p);canvas.drawLine(12,12.5f,12,18.5f,p);}
                else{canvas.drawLine(8.5f,15,11,17.5f,p);canvas.drawLine(11,17.5f,15.5f,13,p);}
            }
            else if(kind.equals("settings")) {for(int i=0;i<3;i++){int y=6+i*6;canvas.drawLine(4,y,20,y,p);p.setStyle(Paint.Style.FILL);canvas.drawCircle(i==1?9:15,y,2.8f,p);p.setStyle(Paint.Style.STROKE);}}
            else if(kind.equals("clock")){canvas.drawCircle(12,12,8.5f,p);canvas.drawLine(12,7,12,12,p);canvas.drawLine(12,12,16,14,p);}
            else if(kind.equals("theme")){canvas.drawCircle(12,12,8,p);p.setStyle(Paint.Style.FILL);canvas.drawArc(4,4,20,20,90,180,true,p);}
            else if(kind.equals("info")){canvas.drawCircle(12,12,8.5f,p);canvas.drawLine(12,11,12,16,p);p.setStyle(Paint.Style.FILL);canvas.drawCircle(12,7.5f,1,p);}
            else if(kind.equals("download")){canvas.drawLine(12,3,12,15,p);canvas.drawLine(7,10,12,15,p);canvas.drawLine(12,15,17,10,p);canvas.drawLine(4,16,4,21,p);canvas.drawLine(4,21,20,21,p);canvas.drawLine(20,21,20,16,p);}
            else if(kind.equals("scores")){canvas.drawRoundRect(3,4,21,20,3,3,p);canvas.drawLine(12,4,12,20,p);canvas.drawLine(6.5f,9,8.5f,9,p);canvas.drawLine(6.5f,15,8.5f,15,p);canvas.drawLine(15.5f,9,17.5f,9,p);canvas.drawLine(15.5f,15,17.5f,15,p);}
            else {canvas.drawRoundRect(3,5,21,20,2,2,p);canvas.drawLine(6,10,12,10,p);canvas.drawLine(6,15,12,15,p);canvas.drawLine(17,9,17,11,p);canvas.drawLine(17,14,17,16,p);}
            canvas.restore();
        }
    }
}
