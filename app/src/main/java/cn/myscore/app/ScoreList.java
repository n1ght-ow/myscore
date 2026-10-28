package cn.myscore.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.MotionEvent;
import android.widget.AbsListView;
import android.widget.ListView;

/**
 * Platform list with view recycling. The compact header overlay follows pixel deltas without
 * rebuilding the screen, so a ten-second score refresh never replaces the scrolling container.
 */
final class ScoreList extends ListView {
    interface OnScrollPixels { void onScroll(int deltaPixels); }
    private OnScrollPixels listener;
    private final ScrollTracker scrollTracker=new ScrollTracker();
    private boolean fingerDown;
    private boolean layingOut;
    private int scrollState=AbsListView.OnScrollListener.SCROLL_STATE_IDLE;

    ScoreList(Context context) {
        super(context);
        setDivider(null);
        setDividerHeight(0);
        setSelector(new ColorDrawable(Color.TRANSPARENT));
        setVerticalScrollBarEnabled(false);
        setHorizontalScrollBarEnabled(false);
        setClipToPadding(false);
        setCacheColorHint(Color.TRANSPARENT);
        setScrollingCacheEnabled(true);
        setItemsCanFocus(true);
        setSoundEffectsEnabled(false);
        setOnScrollListener(new AbsListView.OnScrollListener(){
            @Override public void onScrollStateChanged(AbsListView view,int state){scrollState=state;}
            @Override public void onScroll(AbsListView view,int first,int visible,int total){
                View child=getChildAt(0);
                if(child==null){scrollTracker.reset();return;}
                int delta=scrollTracker.update(first,child.getTop(),child.getHeight(),getHeight(),
                        !layingOut&&scrollState!=AbsListView.OnScrollListener.SCROLL_STATE_IDLE);
                // Update the sibling header before drawing starts, never from dispatchDraw.
                if(delta!=0&&listener!=null)listener.onScroll(delta);
            }
        });
    }

    boolean isInteracting(){return fingerDown||scrollState!=AbsListView.OnScrollListener.SCROLL_STATE_IDLE;}
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN)fingerDown=true;
        else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)fingerDown=false;
        return super.dispatchTouchEvent(event);
    }
    @Override protected void onDetachedFromWindow(){fingerDown=false;scrollState=AbsListView.OnScrollListener.SCROLL_STATE_IDLE;super.onDetachedFromWindow();}

    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){
        layingOut=true;
        try{super.onLayout(changed,left,top,right,bottom);}finally{layingOut=false;}
    }

    void stopScrollForSelection(){
        scrollState=AbsListView.OnScrollListener.SCROLL_STATE_IDLE;
        fingerDown=false;
        smoothScrollBy(0,0);
        resetScrollTracking();
    }

    void setOnScrollPixels(OnScrollPixels listener){this.listener=listener;}
    /** Called before replacing items so the new layout cannot be mistaken for a user scroll. */
    void resetScrollTracking(){scrollTracker.reset();}

    static final class ScrollTracker {
        private int lastFirst=-1,lastTop,lastHeight;

        void reset(){lastFirst=-1;}

        int update(int first,int top,int height,int viewportHeight,boolean userScrolling) {
            int delta=0;
            if(userScrolling&&lastFirst!=-1) {
                if(first==lastFirst)delta=lastTop-top;
                else if(first==lastFirst+1)delta=lastTop+lastHeight-top;
                else if(first==lastFirst-1)delta=lastTop-top-height;
                // A jump without adjacent rows has no exact pixel distance. Use a viewport
                // in its known direction; scrollbar estimates can reverse on unequal rows.
                else delta=first>lastFirst?viewportHeight:-viewportHeight;
            }
            lastFirst=first;lastTop=top;lastHeight=height;
            return delta;
        }
    }
}
