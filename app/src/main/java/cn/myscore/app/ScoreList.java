package cn.myscore.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.widget.ListView;

/**
 * Platform list with view recycling. The compact header overlay follows pixel deltas without
 * rebuilding the screen, so a ten-second score refresh never replaces the scrolling container.
 */
final class ScoreList extends ListView {
    interface OnScrollPixels { void onScroll(int deltaPixels); }
    private OnScrollPixels listener;
    private int lastOffset=-1,lastFirst=-1,lastTop,lastHeight;

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
    }

    void setOnScrollPixels(OnScrollPixels listener){this.listener=listener;}
    /** Called before replacing items so the new layout cannot be mistaken for a user scroll. */
    void resetScrollTracking(){lastOffset=-1;lastFirst=-1;lastHeight=0;}

    @Override protected void dispatchDraw(Canvas canvas) {
        View child=getChildAt(0);
        if(child!=null) {
            int first=getFirstVisiblePosition(),top=child.getTop(),height=child.getHeight();
            int offset=computeVerticalScrollOffset();
            if(lastOffset!=-1&&listener!=null) {
                int delta;
                if(first==lastFirst)delta=lastTop-top;
                else if(first==lastFirst+1)delta=lastTop+lastHeight-top;
                else delta=offset-lastOffset;
                if(delta!=0)listener.onScroll(delta);
            }
            lastFirst=first;lastTop=top;lastHeight=height;lastOffset=offset;
        }
        super.dispatchDraw(canvas);
    }
}
