package com.verksoft.noturne.ui;

import android.content.Context;
import android.widget.ScrollView;
import android.view.MotionEvent;
import android.util.AttributeSet;
/**
 * Created by esdras on 29/06/17.
 */

public class LockableScrollView extends ScrollView{
    private boolean scrollable = true;
    public LockableScrollView(Context context) {
        super(context);
    }

    public LockableScrollView(Context context, AttributeSet attr){
        super(context, attr);
    }

    public void setScrollingEnabled(boolean enabled){
        scrollable = enabled;
    }

    public boolean isScrollable(){
        return scrollable;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev){
        return scrollable ?
                super.onInterceptTouchEvent(ev) : false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev){
        return ev.getAction() == MotionEvent.ACTION_DOWN && !scrollable ? false :
                super.onTouchEvent(ev);
    }
}
