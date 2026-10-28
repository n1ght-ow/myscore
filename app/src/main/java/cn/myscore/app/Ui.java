package cn.myscore.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
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
    static final int CARD_RADIUS=20,CONTROL_RADIUS=24,SHEET_RADIUS=28;
    static final float ACTION_ICON_ALPHA=.75f;
    final int bg,surface,text,muted,primary,onPrimary,tonal,line,error,secondary,action,onAction;
    private static Typeface interRegular,interMedium;
    private final Typeface regular,medium;
    static boolean isDark(Context context) {
        String theme=context.getSharedPreferences("settings",0).getString("theme","dark");
        return "dark".equals(theme)||("system".equals(theme)&&(context.getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES);
    }
    Ui(Context c,boolean dark) {
        this.c=c;
        if(interRegular==null){
            interRegular=new Typeface.Builder(c.getAssets(),"fonts/Inter.ttf").setWeight(400).build();
            interMedium=new Typeface.Builder(c.getAssets(),"fonts/Inter.ttf").setWeight(600).build();
        }
        regular=interRegular==null?REGULAR:interRegular;medium=interMedium==null?MEDIUM:interMedium;
        bg=Color.parseColor(dark?"#000000":"#F5F5F3");
        surface=Color.parseColor(dark?"#141414":"#FFFFFF");
        text=Color.parseColor(dark?"#F5F5F3":"#191B1A");
        muted=Color.parseColor(dark?"#A3A5A3":"#616660");
        primary=Color.parseColor(dark?"#76DDB0":"#176B49");
        onPrimary=Color.parseColor(dark?"#092C1D":"#FFFFFF");
        tonal=Color.parseColor(dark?"#252525":"#E9EBE7");
        line=Color.parseColor(dark?"#2A2A2A":"#E4E6E1");
        error=Color.parseColor(dark?"#FFAAA5":"#AD322B");
        secondary=Color.parseColor(dark?"#B5B8B5":"#5E655E");
        action=text;onAction=bg;
    }
    int dp(float n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    /** A 12.4" tablet reports a smallest width far above any phone; this is the layout split. */
    static boolean isPad(Context context){
        android.content.res.Configuration cfg=context.getResources().getConfiguration();
        // smallestScreenWidthDp is device-wide; screenWidthDp follows the current window, so a
        // tablet in split-screen/freeform falls back to the phone layout instead of squeezing
        // the 128dp rail + 360dp list into a 400dp window.
        return cfg.smallestScreenWidthDp>=720&&cfg.screenWidthDp>=720;
    }
    int contentWidthDp(){
        int width=c.getResources().getConfiguration().screenWidthDp;
        return isPad(c)?Math.min(1120,width):Math.min(640,width);
    }
    int pageInset(){return isPad(c)?0:dp(Math.max(0,(c.getResources().getConfiguration().screenWidthDp-640)/2f));}
    LinearLayout column(){LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.VERTICAL);return v;}
    LinearLayout row(){LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    /** Keep asynchronous reading-pane replacements out of a touch or an unfinished fling. */
    static final class ReadingScroll extends android.widget.ScrollView {
        private boolean fingerDown;
        private long lastScroll;
        ReadingScroll(Context context){super(context);}
        boolean isInteracting(){return fingerDown||android.os.SystemClock.uptimeMillis()-lastScroll<250;}
        @Override public boolean dispatchTouchEvent(android.view.MotionEvent event){
            int action=event.getActionMasked();
            if(action==android.view.MotionEvent.ACTION_DOWN)fingerDown=true;
            else if(action==android.view.MotionEvent.ACTION_UP||action==android.view.MotionEvent.ACTION_CANCEL)fingerDown=false;
            return super.dispatchTouchEvent(event);
        }
        @Override protected void onScrollChanged(int x,int y,int oldX,int oldY){super.onScrollChanged(x,y,oldX,oldY);lastScroll=android.os.SystemClock.uptimeMillis();}
        @Override protected void onDetachedFromWindow(){fingerDown=false;super.onDetachedFromWindow();}
    }
    TextView text(String s,int sp,int color,boolean bold) {
        // AOSP JSONObject.optString turns explicit JSON null into "null"; keep that sentinel
        // out of the rendered UI even if a parser missed it.
        if ("null".equals(s)) s = "";
        TextView v=new TextView(c);v.setText(s);v.setTextSize(sp);v.setTextColor(color);
        v.setFontFeatureSettings("tnum");v.setIncludeFontPadding(true);v.setFallbackLineSpacing(true);
        v.setTypeface(bold?medium:regular);
        v.setLineSpacing(dp(2),1f);
        return v;
    }
    ScoreText score(String home,String away,boolean available,int maxSp){return new ScoreText(home,away,available,maxSp);}
    int scoreColor(Match match,boolean home){return scoreColor(home?match.homeScore:match.awayScore,home?match.awayScore:match.homeScore,match.finalised());}
    int scoreColor(String own,String other,boolean finished){int result=DetailFormat.outcome(own,other,finished);return result>0?primary:result<0?error:text;}
    /** Preserve a readable minimum: narrow scoreboards stack the two scores instead of clipping. */
    final class ScoreText extends TextView {
        private final Paint measure=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int maxSp;
        private String home="",away="",single="VS";
        private boolean available;
        private int homeColor=text,awayColor=text;
        private int ceiling;
        ScoreText(String home,String away,boolean available,int maxSp){
            super(Ui.this.c);this.maxSp=maxSp;setTypeface(medium);setFontFeatureSettings("tnum");setIncludeFontPadding(true);setFallbackLineSpacing(true);
            setTextColor(text);setGravity(Gravity.CENTER);setScore(home,away,available);
        }
        void setScore(String home,String away,boolean available){
            if(ceiling!=0&&this.available==available&&this.home.equals(home)&&this.away.equals(away))return;
            this.home=home;this.away=away;this.available=available;single=available?home+"–"+away:"VS";
            int next=available?maxSp:Math.min(24,maxSp);
            ceiling=next;
            setContentDescription(available?"比分 "+home+" 比 "+away:"来源尚未提供比分");
            if(!single.contentEquals(getText()))setText(single);
        }
        void setResultColors(int home,int away){if(homeColor==home&&awayColor==away&&getText() instanceof android.text.Spanned)return;homeColor=home;awayColor=away;paintScore(getText().toString());}
        private void paintScore(String display){
            android.text.SpannableString colored=new android.text.SpannableString(display);
            if(available){colored.setSpan(new android.text.style.ForegroundColorSpan(homeColor),0,home.length(),0);colored.setSpan(new android.text.style.ForegroundColorSpan(awayColor),display.length()-away.length(),display.length(),0);}
            setText(colored);
        }
        @Override protected void onMeasure(int widthSpec,int heightSpec){
            int width=MeasureSpec.getSize(widthSpec)-getPaddingLeft()-getPaddingRight();
            measure.set(getPaint());measure.setTextSize(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,22,c.getResources().getDisplayMetrics()));
            boolean stack=available&&MeasureSpec.getMode(widthSpec)!=MeasureSpec.UNSPECIFIED&&width>0&&measure.measureText(single)>width;
            String display=stack?home+"\n"+away:single;
            int lines=stack?2:1;if(getMaxLines()!=lines)setMaxLines(lines);if(!display.contentEquals(getText()))paintScore(display);
            // Choose the font before measuring height. Framework auto-size can increase
            // it during layout after a wrap-content height was measured at the default size.
            int size=ceiling;
            if(MeasureSpec.getMode(widthSpec)!=MeasureSpec.UNSPECIFIED&&width>0)for(;size>22;size--){
                measure.setTextSize(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,size,c.getResources().getDisplayMetrics()));
                if((stack?Math.max(measure.measureText(home),measure.measureText(away)):measure.measureText(single))<=width)break;
            }
            float pixels=android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,size,c.getResources().getDisplayMetrics());
            if(Math.abs(getTextSize()-pixels)>.1f)setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP,size);
            super.onMeasure(widthSpec,heightSpec);
        }
    }
    GradientDrawable shape(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    GradientDrawable panel(){return shape(surface,CARD_RADIUS);}
    void clickable(View v,int fill,int radius){
        GradientDrawable background=shape(fill,radius);
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(text&0x00ffffff|0x18000000),background,shape(Color.WHITE,radius)));
    }
    TextView chip(String label,boolean selected,Runnable action) {
        TextView chip=text(label,13,selected?onAction:muted,selected);chip.setGravity(Gravity.CENTER);
        chip.setMinHeight(dp(48));chip.setPadding(dp(16),dp(10),dp(16),dp(10));
        clickable(chip,selected?this.action:tonal,CONTROL_RADIUS);chip.setSelected(selected);chip.setFocusable(true);
        chip.setOnClickListener(v->action.run());return chip;
    }
    /** Text tabs share one underline and grow vertically with system font size. */
    TextView tab(String label,boolean selected,Runnable action) {
        TextView tab=new TextView(c){
            final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas canvas){
                super.onDraw(canvas);
                if(isSelected()){ink.setColor(text);float inset=dp(14);canvas.drawRoundRect(inset,getHeight()-dp(2),getWidth()-inset,getHeight(),dp(1),dp(1),ink);}
            }
        };
        tab.setText(label);tab.setTextSize(14);tab.setTextColor(selected?text:muted);tab.setTypeface(selected?medium:regular);
        tab.setIncludeFontPadding(true);tab.setFallbackLineSpacing(true);tab.setGravity(Gravity.CENTER);
        tab.setMinHeight(dp(48));tab.setPadding(dp(14),dp(10),dp(14),dp(12));
        // Reserve the wider weight so changing the selection cannot shift nearby tabs.
        Paint measure=new Paint(tab.getPaint());
        measure.setTypeface(regular);float regularWidth=measure.measureText(label);
        measure.setTypeface(medium);
        tab.setMinWidth((int)Math.ceil(Math.max(regularWidth,measure.measureText(label)))+dp(28));
        clickable(tab,Color.TRANSPARENT,0);tab.setSelected(selected);tab.setFocusable(true);tab.setOnClickListener(v->action.run());return tab;
    }
    void selectTab(TextView tab,boolean selected) {
        tab.setSelected(selected);tab.setTextColor(selected?text:muted);tab.setTypeface(selected?medium:regular);
    }
    TextView button(String label,boolean filled,Runnable action) {
        TextView b=text(label,14,filled?onAction:text,true);b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(48));b.setPadding(dp(20),dp(12),dp(20),dp(12));clickable(b,filled?this.action:tonal,CONTROL_RADIUS);
        b.setFocusable(true);b.setOnClickListener(v->action.run());return b;
    }
    /**
     * A media shortcut for dense score cards. Keep the full destination in its accessible name
     * and long-press tooltip; a bare icon sits in a transparent 48dp touch target.
     */
    final class WatchPill extends FrameLayout {
        private final Icon play;
        private String shown="";
        WatchPill(int targetHeightDp) {
            super(c);
            setMinimumWidth(dp(48));setMinimumHeight(dp(Math.max(48,targetHeightDp)));
            play=new Icon(c,"play",muted);play.setAlpha(ACTION_ICON_ALPHA);
            addView(play,new FrameLayout.LayoutParams(dp(24),dp(24),Gravity.CENTER));
            clickable(this,Color.TRANSPARENT,CONTROL_RADIUS);setFocusable(true);
            setTag("watch-shortcut");
        }
        void bind(WatchLinks.Link link,boolean live,Runnable action) {
            if(link==null){setVisibility(GONE);return;}
            String next=link.kind.icon;
            if(!next.equals(shown)){
                shown=next;
                play.set(link.kind.icon,muted);
            }
            setContentDescription(link.provider+"，"+link.title+"，"+link.kind.label+"，在外部应用打开");
            setTooltipText(link.shortProvider+" · "+link.kind.label);
            setOnClickListener(v->action.run());setVisibility(VISIBLE);
        }
    }
    WatchPill watchPill(int targetHeightDp) {return new WatchPill(targetHeightDp);}
    /**
     * One compact media control: provider, destination state and external-link mark. Match context
     * already lives in the scoreboard. Rebinding preserves the row when a verified URL arrives.
     */
    final class WatchRow extends LinearLayout {
        private final TextView provider;
        private final TextView state;
        private final Icon play;
        WatchRow() {
            super(c);
            setOrientation(LinearLayout.HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setMinimumHeight(dp(56));setPadding(dp(10),dp(10),dp(14),dp(10));
            FrameLayout badge=new FrameLayout(c);
            play=new Icon(c,"play",muted);play.setAlpha(ACTION_ICON_ALPHA);
            badge.addView(play,new FrameLayout.LayoutParams(dp(24),dp(24),Gravity.CENTER));
            addView(badge,new LinearLayout.LayoutParams(dp(36),dp(36)));
            provider=text("",14,text,true);
            provider.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(0,-2,1f);pp.setMarginStart(dp(12));
            addView(provider,pp);
            state=text("",12,muted,false);state.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-2,-2);sp.setMarginStart(dp(12));
            addView(state,sp);
            LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(dp(16),dp(16));ep.setMarginStart(dp(12));
            addView(new Icon(c,"external",muted),ep);
            clickable(this,tonal,16);setFocusable(true);setTag("watch-control");
        }
        void bind(WatchLinks.Link link,boolean live,Runnable action) {
            if(link==null){setVisibility(GONE);return;}
            provider.setText(link.provider);
            state.setText(link.kind.label);
            state.setTextColor(live&&link.kind!=WatchLinks.Kind.PENDING&&link.kind!=WatchLinks.Kind.REPLAY?primary:muted);
            play.set(link.kind.icon,muted);
            setContentDescription(link.title+"。"+link.note+"。通过"+link.provider+"在外部应用打开。");
            setTooltipText(link.title);
            setOnClickListener(v->action.run());
            setVisibility(VISIBLE);
        }
    }
    WatchRow watchRow() {return new WatchRow();}
    View divider(){View d=new View(c);d.setBackgroundColor(line);d.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(1)));return d;}
    void gap(LinearLayout into,int h){View space=new View(c);into.addView(space,new LinearLayout.LayoutParams(1,dp(h)));}
    /** Match time leads; source freshness stays readable without competing with it. */
    final class MatchTime extends LinearLayout {
        private final TextView time,caption;
        MatchTime(){
            super(c);setOrientation(VERTICAL);setGravity(Gravity.CENTER_VERTICAL);
            setMinimumHeight(dp(48));setPadding(0,dp(4),0,dp(4));setScreenReaderFocusable(true);
            time=text("",16,Ui.this.text,true);caption=text("",11,muted,false);
            addView(time,new LinearLayout.LayoutParams(-1,-2));
            addView(caption,new LinearLayout.LayoutParams(-1,-2));
        }
        void bind(String start,String provenance){
            if(!start.contentEquals(time.getText()))time.setText(start);
            if(!provenance.contentEquals(caption.getText()))caption.setText(provenance);
            caption.setVisibility(provenance.isBlank()?GONE:VISIBLE);
        }
    }
    static final class Icon extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private String kind;private int color;private final Path refreshArrow=new Path();
        private Drawable glyph;private Path appPath;
        Icon(Context c,String kind,int color){super(c);this.kind=kind;this.color=color;loadGlyph();refreshArrow.moveTo(19,3);refreshArrow.lineTo(19,9);refreshArrow.lineTo(13,9);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        private void loadGlyph(){
            appPath=AppIcons.path(kind);
            int resource=kind.equals("mark")?R.drawable.brand_s:0;
            glyph=resource==0?null:getContext().getDrawable(resource).mutate();
            if(glyph!=null)glyph.setTint(color);
        }
        void set(String kind,int color){if(kind.equals(this.kind)&&color==this.color)return;boolean changed=!kind.equals(this.kind);this.kind=kind;this.color=color;if(changed)loadGlyph();else if(glyph!=null)glyph.setTint(color);invalidate();}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            if(glyph!=null){
                float fit=Math.min(getWidth()/(float)glyph.getIntrinsicWidth(),getHeight()/(float)glyph.getIntrinsicHeight());
                int width=Math.round(glyph.getIntrinsicWidth()*fit),height=Math.round(glyph.getIntrinsicHeight()*fit);
                int left=(getWidth()-width)/2,top=(getHeight()-height)/2;
                glyph.setBounds(left,top,left+width,top+height);glyph.draw(canvas);return;
            }
            canvas.save();float scale=Math.min(getWidth(),getHeight())/24f;
            canvas.translate((getWidth()-24*scale)/2,(getHeight()-24*scale)/2);canvas.scale(scale,scale);
            p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.8f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
            if(appPath!=null){p.setStrokeWidth(1.7f);canvas.drawPath(appPath,p);}
            else if(kind.equals("back")){canvas.drawLine(20,12,4,12,p);canvas.drawLine(4,12,11,5,p);canvas.drawLine(4,12,11,19,p);}
            else if(kind.equals("check")){canvas.drawLine(5,12,10,17,p);canvas.drawLine(10,17,19,7,p);}
            else if(kind.equals("play")){canvas.drawLine(8,5,8,19,p);canvas.drawLine(8,5,19,12,p);canvas.drawLine(19,12,8,19,p);}
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
            else {canvas.drawRoundRect(3,5,21,20,2,2,p);canvas.drawLine(6,10,12,10,p);canvas.drawLine(6,15,12,15,p);canvas.drawLine(17,9,17,11,p);canvas.drawLine(17,14,17,16,p);}
            canvas.restore();
        }
    }
}
