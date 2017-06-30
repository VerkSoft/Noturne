package com.verksoft.noturne.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Handler;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.method.ArrowKeyMovementMethod;
import android.text.method.MovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Scroller;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

public class SyntaxHightlightEditor extends EditText implements EditTextInterface, View.OnKeyListener, GestureDetector.OnGestureListener {

    public boolean SHOW_LINE_NUMBERS = true;
    public boolean SYNTAX_HIGHLIGHTING = true;
    public float TEXT_SIZE = 14;
    public boolean WORDWRAP = false;
    public boolean FLING_TO_SCROLL = true;

    private Context mContext;
    protected Paint mPaintNumbers;
    protected Paint mPaintHighlight;
    protected int mPaddingDP = 6;
    protected int mPadding, mLinePadding;
    protected float mScale;

    protected Scroller mScroller;
    protected GestureDetector mGestureDetector;
    protected Point mMaxSize;

    protected int mHighlightedLine;
    protected int mHighlightStart;

    protected Rect mDrawingRect, mLineBounds;

    public interface OnTextChangedListener {
        public void onTextChanged(String text);
    }

    public OnTextChangedListener onTextChangedListener = null;
    public int updateDelay = 100;
    public int errorLine = 0;
    public boolean dirty = false;
    private CRC32 mCRC32;

    //Colors
    private static final int COLOR_ERROR = 0x80ff0000;
    private static final int COLOR_NUMBER = 0xffBC0000;
    private static final int COLOR_KEYWORD = 0xff0096FF;
    private static final int COLOR_BLTN = 0xff0046A5;
    private static final int COLOR_COMMENT = 0xff009B00;
    private static final int COLOR_BOOLEANS = 0xffBC0000;
    private static final int COLOR_STRINGS = 0xffBC0000;

    //Words
    private static final Pattern line = Pattern.compile(".*\\n");
    private static final Pattern booleans = Pattern.compile(
            "\\b(true|false|null|undefined|boolean)\\b");
    private static final Pattern numbers = Pattern.compile(
            "\\b(\\d*[.]?\\d+)\\b");
    private static final Pattern keywords = Pattern.compile(
            "\\b(const|break|continue|Date|"+
                    "do|for|while|if|else|in|out|this|"+
                    "return|function|var|Math|Object|default|case|Array)\\b");
    private static final Pattern bltns = Pattern.compile(
            "\\b(catch|try|sin|cos|log|sqrt|abs|floor|ceil|PI|length|equal|exec|find|next|" +
                    "ModPE|Block|Entity|Item|Player|Server|Level|new|useItem|newLevel|match|" +
                    "procCmd|chatHook|serverMessageReceiveHook|entityAddedHook|entityRemovedHook|" +
                    "destroyBlock|attackHook|selectLevelHook|leaveGame|modTick|deathHook|startDestroyBlock|" +
                    "blockEventHook|levelEventHook|chatReceiveHook|parseInt|run|ItemCathegory|ChatColor|ParticleType|" +
                    "ArmorType|switch|pop|push|shift|sort|unshift|reverse|splice|concat|indexOf|join|lastIndexOf|slice|" +
                    "toSource|toString|getText|valueOf|filter|every|map|some|forEach|acos|asin|atan|atan2|" +
                    "max|min|random|round|exp|pow|tan|charAt|charCodeAt|replace|search|split|toLocaleTimeString|" +
                    "toLowerCase|toUpperCase|eval|parseFloat|append|toArray|replaceAll|toPrecision|toUTCString|" +
                    "toLocaleString|toExponential|toFixed|substring|substr|SQRT2|LN2|SQRT1_2|LOG10E|" +
                    "LN10|LOG2E|LOG10E|caller|apply|constructor|arity|call|arguments|toLocaleDateString)\\b");
    private static final Pattern comments = Pattern.compile(
            "/\\*(?:.|[\\n\\r])*?\\*/|//.*" );
    private static final Pattern symbols = Pattern.compile(
            "[\\+\\-\\*\\^\\&\\?\\!\\=\\|\\<\\>\\:\\/]");
    private static final Pattern trailingWhiteSpace = Pattern.compile(
            "[\\t ]+$", Pattern.MULTILINE);
    public static final Pattern general_strings = Pattern.compile("\"(.*?)\"|'(.*?)'");

    private final Handler updateHandler = new Handler();
    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            Editable e = getText();
            if(onTextChangedListener != null )
                onTextChangedListener.onTextChanged(e.toString());
            if(SYNTAX_HIGHLIGHTING)
                highlightWithoutChange(e);
        }
    };

    private boolean modified = true;

    public SyntaxHightlightEditor(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mContext = context;
        mPaintNumbers = new Paint();
        mPaintNumbers.setAntiAlias(true);
        mPaintHighlight = new Paint();
        mScale = context.getResources().getDisplayMetrics().density;
        mPadding = (int) (mPaddingDP * mScale);
        mHighlightedLine = mHighlightStart = -1;
        mDrawingRect = new Rect();
        mLineBounds = new Rect();
        mGestureDetector = new GestureDetector(getContext(), this);
        updateFromSettings();
        init();
    }

    public void computeScroll() {
        if (mScroller != null) {
            if (mScroller.computeScrollOffset()) {
                scrollTo(mScroller.getCurrX(), mScroller.getCurrY());
            }
        } else {
            super.computeScroll();
        }
    }

    public void onDraw(Canvas canvas) {
        int count, lineX, baseline;
        count = getLineCount();
        if (SHOW_LINE_NUMBERS) {
            int padding = (int) (Math.floor(Math.log10(count)) + 1);
            padding = (int) ((padding * mPaintNumbers.getTextSize()) + mPadding + (TEXT_SIZE * mScale * 0.5));
            if (mLinePadding != padding) {
                mLinePadding = padding;
                setPadding(mLinePadding, mPadding, mPadding, mPadding);
            }
        }

        getDrawingRect(mDrawingRect);
        lineX = (int) (mDrawingRect.left + mLinePadding - (TEXT_SIZE * mScale * 0.5));
        int min = 0;
        int max = count;
        getLineBounds(0, mLineBounds);
        int startBottom = mLineBounds.bottom;
        int startTop = mLineBounds.top;
        getLineBounds(count - 1, mLineBounds);
        int endBottom = mLineBounds.bottom;
        int endTop = mLineBounds.top;
        if (count > 1 && endBottom > startBottom && endTop > startTop) {
            min = Math.max(min, ((mDrawingRect.top - startBottom) * (count - 1)) / (endBottom - startBottom));
            max = Math.min(max, ((mDrawingRect.bottom - startTop) * (count - 1)) / (endTop - startTop) + 1);
        }
        for (int i = min; i < max; i++) {
            baseline = getLineBounds(i, mLineBounds);
            if ((mMaxSize != null) && (mMaxSize.x < mLineBounds.right)) {
                mMaxSize.x = mLineBounds.right;
            }
            if ((i == mHighlightedLine) && (!WORDWRAP)) {
                canvas.drawRect(mLineBounds, mPaintHighlight);
            }
            if (SHOW_LINE_NUMBERS) {
                canvas.drawText("" + (i + 1), mDrawingRect.left + mPadding, baseline, mPaintNumbers);
            }
            if (SHOW_LINE_NUMBERS) {
                canvas.drawLine(lineX, mDrawingRect.top, lineX, mDrawingRect.bottom, mPaintNumbers);
            }
        }
        getLineBounds(count - 1, mLineBounds);
        if (mMaxSize != null) {
            mMaxSize.y = mLineBounds.bottom;
            mMaxSize.x = Math.max(mMaxSize.x + mPadding - mDrawingRect.width(), 0);
            mMaxSize.y = Math.max(mMaxSize.y + mPadding - mDrawingRect.height(), 0);
        }
        super.onDraw(canvas);
    }

    public boolean onKey(View v, int keyCode, KeyEvent event) {
        return false;
    }

    public boolean onTouchEvent(MotionEvent event) {
        super.onTouchEvent(event);
        if (mGestureDetector != null) {
            return mGestureDetector.onTouchEvent(event);
        }
        return true;
    }

    public boolean onDown(MotionEvent e) {
        return true;
    }

    public boolean onSingleTapUp(MotionEvent e) {
        if (isEnabled()) {
            ((InputMethodManager) getContext().getSystemService(
                    Context.INPUT_METHOD_SERVICE)).showSoftInput(this, InputMethodManager.SHOW_IMPLICIT);
        }
        return true;
    }

    public void onShowPress(MotionEvent e) {
    }

    public void onLongPress(MotionEvent e) {
    }

    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        //mScroller.setFriction(0);
        return true;
    }

    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        if (!FLING_TO_SCROLL) {
            return true;
        }
        if (mScroller != null) {
            mScroller.fling(getScrollX(), getScrollY(), -(int) velocityX,
                    -(int) velocityY, 0, mMaxSize.x, 0, mMaxSize.y);
        }
        return true;
    }

    public void updateFromSettings() {
        if (isInEditMode()) {
            return;
        }

        setHorizontallyScrolling(WORDWRAP);
        mPaintHighlight.setAlpha(48);
        setTextSize(TEXT_SIZE);
        mPaintNumbers.setTextSize(TEXT_SIZE * mScale * 0.85f);
        postInvalidate();
        refreshDrawableState();

        if (FLING_TO_SCROLL) {
            mScroller = new Scroller(getContext());
            mMaxSize = new Point();
        } else {
            mScroller = null;
            mMaxSize = null;
        }

        mLinePadding = mPadding;
        int count = getLineCount();
        if (SHOW_LINE_NUMBERS) {
            mLinePadding = (int) (Math.floor(Math.log10(count)) + 1);
            mLinePadding = (int) ((mLinePadding * mPaintNumbers.getTextSize())
                    + mPadding + (TEXT_SIZE * mScale * 0.5));
            setPadding(mLinePadding, mPadding, mPadding, mPadding);
        } else {
            setPadding(mPadding, mPadding, mPadding, mPadding);
        }
    }

    @Override
    protected boolean getDefaultEditable() {
        return true;
    }

    @Override
    protected MovementMethod getDefaultMovementMethod() {
        return ArrowKeyMovementMethod.getInstance();
    }

    @Override
    public Editable getText() {
        return (Editable) super.getText();
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        super.setText(text, BufferType.EDITABLE);
    }

    public void setSelection(int start, int stop) {
        Selection.setSelection(getText(), start, stop);
    }

    public void setSelection(int index) {
        Selection.setSelection(getText(), index);
    }

    public void selectAll() {
        Selection.selectAll(getText());
    }

    public void extendSelection(int index) {
        Selection.extendSelection(getText(), index);
    }

    public void setTextHighlighted(CharSequence text) {
        cancelUpdate();
        errorLine = 0;
        dirty = false;
        modified = false;
        setText(highlight(new SpannableStringBuilder(text)));
        modified = true;
        if(onTextChangedListener != null )
            onTextChangedListener.onTextChanged(text.toString());
    }

    public String getCleanText() {
        return trailingWhiteSpace.matcher(getText()).replaceAll("");
    }

    public void refresh() {
        highlightWithoutChange(getText());
    }

    @Override
    public void init() {
        mCRC32 = new CRC32();
        setFilters(new InputFilter[]{
                new InputFilter() {
                    @Override
                    public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                        if(modified &&
                                end-start == 1 &&
                                start < source.length() &&
                                dstart < dest.length()) {
                            char c = source.charAt(start);
                            if(c == '\n')
                                return autoIndent(source, start, end, dest, dstart, dend);
                        }
                        return source;
                    }
                }
        });

        addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable e) {
                cancelUpdate();
                if(!modified)
                    return;
                dirty = true;
                updateHandler.postDelayed(updateRunnable, updateDelay );
            }
        });
    }

    @Override
    public void show() {
        setVisibility(View.VISIBLE);
    }

    @Override
    public void hide() {
        setVisibility(View.GONE);
    }

    @Override
    public String getString() {
        String text = "";
        try {
            text = getText().toString();
        }catch (OutOfMemoryError e) {
        }
        return text;
    }

    private int mOldTextlength = 0;
    private long mOldTextCrc32 = 0;

    @Override
    public void updateTextFinger() {
        mOldTextlength = getText().length();
        byte bytes[] = getString().getBytes();
        mCRC32.reset();
        mCRC32.update(bytes, 0, bytes.length);
        mOldTextCrc32 = mCRC32.getValue();
    }

    @Override
    public boolean isTextChanged() {
        CharSequence text = getText();
        int hash = text.length();
        if(mOldTextlength != hash){
            return true;
        }
        mCRC32.reset();
        byte bytes[] = getString().getBytes();
        mCRC32.update(bytes, 0, bytes.length);
        return mOldTextCrc32 != mCRC32.getValue();
    }

    private void cancelUpdate() {
        updateHandler.removeCallbacks(updateRunnable);
    }

    private void highlightWithoutChange(Editable e) {
        modified = false;
        highlight(e);
        modified = true;
    }

    private Editable highlight(Editable e) {
        try {
            clearSpans(e);
            if(e.length() == 0)
                return e;
            if(errorLine > 0) {
                Matcher m = line.matcher(e);

                for(int n = errorLine; n-- > 0 && m.find(););
                e.setSpan(
                        new BackgroundColorSpan(COLOR_ERROR),
                        m.start(),
                        m.end(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            for(Matcher m = numbers.matcher(e); m.find();)
                e.setSpan(
                        new ForegroundColorSpan(COLOR_NUMBER),
                        m.start(),
                        m.end(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            for(Matcher m = keywords.matcher(e); m.find();)
                e.setSpan(
                        new ForegroundColorSpan(COLOR_KEYWORD),
                        m.start(),
                        m.end(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            for(Matcher m = bltns.matcher(e); m.find();)
                e.setSpan(
                        new ForegroundColorSpan(COLOR_BLTN),
                        m.start(),
                        m.end(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            for(Matcher m = booleans.matcher(e); m.find();)
                e.setSpan(
                        new ForegroundColorSpan(COLOR_BOOLEANS),
                        m.start(),
                        m.end(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            for(Matcher m = symbols.matcher(e); m.find();)
                e.setSpan(
                        new ForegroundColorSpan(COLOR_COMMENT),
                        m.start(),
                        m.end(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            for(Matcher m = comments.matcher(e); m.find();)
                e.setSpan(
                        new ForegroundColorSpan(COLOR_COMMENT),
                        m.start(),
                        m.end(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            for(Matcher m = general_strings.matcher(e); m.find();)
                e.setSpan(
                        new ForegroundColorSpan(COLOR_STRINGS),
                        m.start(),
                        m.end(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } catch(Exception ex) {
        }
        return e;
    }

    private void clearSpans(Editable e){
        {
            ForegroundColorSpan spans[] = e.getSpans(
                    0, e.length(), ForegroundColorSpan.class);
            for(int n = spans.length; n-- > 0;)
                e.removeSpan(spans[n]);
        }

        {
            BackgroundColorSpan spans[] = e.getSpans(0, e.length(), BackgroundColorSpan.class);
            for(int n = spans.length; n-- > 0;)
                e.removeSpan(spans[n]);
        }
    }

    private CharSequence autoIndent(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
        String indent = "";
        int istart = dstart-1;
        int iend = -1;
        boolean dataBefore = false;
        int pt = 0;

        for(; istart > -1; --istart) {
            char c = dest.charAt(istart);
            if(c == '\n')
                break;
            if(c != ' ' && c != '\t') {
                if(!dataBefore) {
                    if(c == '{' ||
                            c == '+' ||
                            c == '-' ||
                            c == '*' ||
                            c == '/' ||
                            c == '%' ||
                            c == '^' ||
                            c == '=')
                        --pt;
                    dataBefore = true;
                }
                if(c == '(')
                    --pt;
                else if(c == ')')
                    ++pt;
            }
        }
        if(istart > -1) {
            char charAtCursor = dest.charAt(dstart);
            for(iend = ++istart; iend < dend; ++iend) {
                char c = dest.charAt(iend);
                if(charAtCursor != '\n' && c == '/' && iend+1 < dend && dest.charAt(iend) == c) {
                    iend += 2;
                    break;
                }
                if(c != ' ' && c != '\t')
                    break;
            }
            indent += dest.subSequence(istart, iend);
        }
        if(pt < 0)
            indent += "\t";
        return source+indent;
    }
}