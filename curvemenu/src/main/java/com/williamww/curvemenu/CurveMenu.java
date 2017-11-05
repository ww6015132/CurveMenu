package com.williamww.curvemenu;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.TimeInterpolator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnticipateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Component for a FloatingActionMenu.
 * <p>
 * Created by WW on 2017/10/11.
 */
public class CurveMenu extends ViewGroup
{

    //-- Properties --//

    /**
     * Defines the rate of change for the open animation.
     */
    static final TimeInterpolator DEFAULT_OPEN_INTERPOLATOR = new OvershootInterpolator();

    /**
     * Defines the rate of change for the close animation.
     */
    static final TimeInterpolator DEFAULT_CLOSE_INTERPOLATOR = new AnticipateInterpolator();

    /**
     * The main menu button that is always visible. Clicking this will open/close the menu.
     */
    private ImageView mMenuButton;

    /**
     * The list of menu items to appear when the menu opens.
     */
    private ArrayList<ImageView> mMenuItems;

    /**
     * The list of labels to appear next to the menu items.
     */
    private ArrayList<TextView> mMenuItemLabels;

    /**
     * Animators to animate the appearance/disappearance of the menu items.
     */
    private ArrayList<ItemAnimator> mMenuItemAnimators;

    /**
     * The set of animations to occur when the menu opens.
     */
    private AnimatorSet mOpenAnimatorSet = new AnimatorSet();

    /**
     * The set of animations to occur when the menu closes.
     */
    private AnimatorSet mCloseAnimatorSet = new AnimatorSet();

    /**
     * A flag representing the open state of the menu.
     */
    private boolean mOpen;

    /**
     * A flag representing the current animation stte of the menu.
     */
    private boolean animating;

    /**
     * A flag representing whether or not the menu should close if the user touches outside of the menu items.
     */
    private boolean mIsSetClosedOnTouchOutside = true;

    /**
     * The duration of the open/close animations.
     */
    private long duration = 500;

    public static final int SHAPE_CIRCLE = 1;

    public static final int SHAPE_CURVE = 0;

    public static final int SHAPE_LINE = 2;

    /**
     * A flag representing whether or not the menu appearance is a circle (true) or linear menu (false).
     */
    private int shape = SHAPE_CURVE;

    /**
     * The radius of the circular menu (if application).
     */
    private int mRadius = 256;

    /**
     * If the radius of the circle is a multiple of the FB width, this is what that ratio is.
     */
    private float multipleOfFB = 0;

    /**
     * The gap between menu items.
     */
    private int mItemGap = 0;

    private static boolean mIsButtonRotate = true;

    /**
     * A click listener for the main menu item.
     */
    private OnMenuItemClickListener onMenuItemClickListener;

    /**
     * A listener for when the menu toggles between open and closed.
     */
    private OnMenuToggleListener onMenuToggleListener;

    /**
     * A GestureDetector that looks for gestures outside the FAM and closes it if necessary.
     */
    GestureDetector mGestureDetector = new GestureDetector(getContext(),
            new GestureDetector.SimpleOnGestureListener()
            {

                @Override
                public boolean onDown(MotionEvent e)
                {
                    return mIsSetClosedOnTouchOutside && isOpened();
                }

                @Override
                public boolean onSingleTapUp(MotionEvent e)
                {
                    close();
                    return true;
                }
            }
    );

    /**
     * An OnItemClickListener that handles clicks on a menu item or one of its labels.
     */
    private OnClickListener mOnItemClickListener = new OnClickListener()
    {
        @Override
        public void onClick(View v)
        {
            // If we click a menu item, call our MenuItemClickListener.
            // Else - we are clicking a label, call our MenuItemClickListener.
            // This is split into an if/else since we access different arrays for each.
            if (v instanceof ImageView)
            {
                int i = mMenuItems.indexOf(v);
                if (onMenuItemClickListener != null)
                {
                    onMenuItemClickListener
                            .onMenuItemClick(CurveMenu.this, i, (ImageView) v);
                }
            }
            else if (v instanceof TextView)
            {
                int i = mMenuItemLabels.indexOf(v);
                if (onMenuItemClickListener != null)
                {
                    onMenuItemClickListener.onMenuItemClick(CurveMenu.this, i, mMenuItems.get(i));
                }
            }
            close();
        }
    };

    //-- Constructors --//

    public CurveMenu(Context context)
    {
        this(context, null, 0);
    }

    public CurveMenu(Context context, AttributeSet attrs)
    {
        this(context, attrs, 0);
    }

    public CurveMenu(Context context, AttributeSet attrs, int defStyleAttr)
    {
        super(context, attrs, defStyleAttr);

        // Default all lists to 5 items.
        mMenuItems = new ArrayList<>(5);
        mMenuItemAnimators = new ArrayList<>(5);
        mMenuItemLabels = new ArrayList<>(5);
    }

    //-- Overriden methods --//

    @Override
    protected void onFinishInflate()
    {
        bringChildToFront(mMenuButton);
        super.onFinishInflate();
    }

    @Override
    public void addView(@NonNull View child, int index, LayoutParams params)
    {
        super.addView(child, index, params);

        // If the child count is greater than one, we are adding menu items.
        // If the child count is not greater than one, we are adding the initial menu item.
        if (getChildCount() > 1)
        {
            if (child instanceof ImageView)
            {
                addMenuItem((ImageView) child);
            }
        }
        else
        {
            mMenuButton = (ImageView) child;
            mMenuButton.setImageDrawable(mMenuButton.getDrawable());
            mMenuButton.setOnClickListener(new OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    toggle();
                }
            });
        }
    }

    /**
     * Handles the measuring of the FAM, and sets the size according to the number of children.
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
    {
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int width;
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int height;
        final int count = getChildCount();
        int maxChildWidth = 0;
        for (int i = 0; i < count; i++)
        {
            View child = getChildAt(i);
            measureChild(child, widthMeasureSpec, heightMeasureSpec);
        }
        for (int i = 0; i < mMenuItems.size(); i++)
        {
            ImageView fab = mMenuItems.get(i);
            TextView label = mMenuItemLabels.get(i);
            maxChildWidth = Math.max(maxChildWidth,
                    label.getMeasuredWidth() + fab.getMeasuredWidth());

        }

        maxChildWidth = Math.max(mMenuButton.getMeasuredWidth(), maxChildWidth);

        if (widthMode == MeasureSpec.EXACTLY)
        {
            width = widthSize;
        }
        else
        {
            width = maxChildWidth + 30;
        }
        if (heightMode == MeasureSpec.EXACTLY)
        {
            height = heightSize;
        }
        else
        {
            int heightSum = 0;
            for (int i = 0; i < count; i++)
            {
                View child = getChildAt(i);
                heightSum += child.getMeasuredHeight();
            }
            height = heightSum + 20;
        }

        setMeasuredDimension(resolveSize(width, widthMeasureSpec),
                resolveSize(height, heightMeasureSpec));
    }

    /**
     * Handles a touch event in the ViewGroup and closes the FAM if necessary.
     */
    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event)
    {
        if (mIsSetClosedOnTouchOutside)
        {
            return mGestureDetector.onTouchEvent(event);
        }
        else
        {
            return super.onTouchEvent(event);
        }
    }

    /**
     * Sets the layout of the ViewGroup dependent on the number of menu items as well as menu direction.
     */
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b)
    {
        System.out.println("onLayout:" + changed);
        if (changed)
        {

            if (shape == SHAPE_CURVE)
            {
                mOpen = true;
                int left = l + getPaddingLeft();
                int top = t + getPaddingTop();
                int right = r - getPaddingRight();
                int bottom = b - getPaddingBottom();

                int layoutWidth = right - left;
                int layoutHeight = bottom - top;

                mMenuButton.layout(0, layoutHeight - mMenuButton.getMeasuredHeight(), mMenuButton.getMeasuredWidth(), layoutHeight);

                if (mMenuItems.size() < 2)
                {
                    Log.e("onLayout", "Floating Action Buttons must more then one!");
                    return;
                }

                int gap = layoutWidth / (mMenuItems.size());
                int a = layoutWidth;

                for (int i = 0; i < mMenuItems.size(); i++)
                {
                    int c = gap * (i + 1);
                    int d = (int) (((-3.4641) * a + Math.sqrt(12 * a * a - 4 * (c * c - 2 * a * c))) / 2);

                    int pointL = mMenuButton.getMeasuredWidth() + mMenuItems.get(i).getMeasuredWidth() / 2 + gap * i;
                    int pointT = layoutHeight - mMenuButton.getMeasuredHeight() - d;
                    int pointR = pointL + mMenuItems.get(i).getMeasuredWidth();
                    int pointB = pointT + mMenuItems.get(i).getMeasuredHeight();

                    mMenuItems.get(i).layout(pointL, pointT, pointR, pointB);

                }
            }
            else if (shape == SHAPE_CIRCLE)
            {

                int right = r - getPaddingRight();
                int bottom = b - getPaddingBottom();
                int top = bottom - mMenuButton.getMeasuredHeight();
                mMenuButton.layout(right - mMenuButton.getMeasuredWidth(), top, right, bottom);

                if (mMenuItems.size() < 2)
                {
                    Log.e("onLayout", "Floating Action Buttons must more then one!");
                    return;
                }
                double angle = Math.PI / 2d / (mMenuItems.size() - 1);
                for (int i = 0; i < mMenuItems.size(); i++)
                {
                    ImageView itemFB = mMenuItems.get(i);
                    int fbWidth = itemFB.getMeasuredWidth();
                    int fbHeight = itemFB.getMeasuredHeight();
                    if (0 != multipleOfFB)
                    {
                        mRadius = (int) (fbWidth * multipleOfFB);
                    }
                    int itemDw = (mMenuButton.getMeasuredWidth() - fbWidth) / 2;
                    int itemDh = (mMenuButton.getMeasuredHeight() - fbHeight) / 2;
                    int itemX = (int) (mRadius * Math.cos(i * angle));
                    int itemY = (int) (mRadius * Math.sin(i * angle));
                    itemFB.layout(right - itemX - fbWidth - itemDw, bottom - itemY - fbHeight - itemDh,
                            right - itemX - itemDw, bottom - itemY - itemDh);

                    if (!animating)
                    {
                        if (!mOpen)
                        {
                            itemFB.setTranslationY(mMenuButton.getTop() - itemFB.getTop());
                            itemFB.setTranslationX(mMenuButton.getLeft() - itemFB.getLeft());
                            itemFB.setVisibility(GONE);
                        }
                        else
                        {
                            itemFB.setTranslationY(0);
                            itemFB.setTranslationX(0);
                            itemFB.setVisibility(VISIBLE);
                        }
                    }
                }
            }
            else if (shape == SHAPE_LINE)
            {

                int right = r - getPaddingRight();
                int bottom = b - getPaddingBottom();
                int top = bottom - mMenuButton.getMeasuredHeight();
                mMenuButton.layout(right - mMenuButton.getMeasuredWidth(), top, right, bottom);

                for (int i = 0; i < mMenuItems.size(); i++)
                {
                    ImageView item = mMenuItems.get(i);
                    TextView label = mMenuItemLabels.get(i);

                    label.setBackgroundResource(R.drawable.rounded_corners);
                    bottom = top -= mItemGap;

                    top -= item.getMeasuredHeight();
                    int width = item.getMeasuredWidth();
                    int d = (mMenuButton.getMeasuredWidth() - width) / 2;
                    item.layout(right - width - d, top, right - d, bottom);
                    d = (item.getMeasuredHeight() - label.getMeasuredHeight()) / 2;

                    label.layout(item.getLeft() - label.getMeasuredWidth() - 50,
                            item.getTop() + d, item.getLeft(),
                            item.getTop() + d + label.getMeasuredHeight());
                    if (!animating)
                    {
                        if (!mOpen)
                        {
                            item.setTranslationY(mMenuButton.getTop() - item.getTop());
                            item.setVisibility(GONE);
                            label.setVisibility(GONE);
                        }
                        else
                        {
                            item.setTranslationY(0);
                            item.setVisibility(VISIBLE);
                            label.setVisibility(VISIBLE);
                        }
                    }
                }
            }
            if (!animating && getBackground() != null)
            {
                if (!mOpen)
                {
                    getBackground().setAlpha(0);
                }
                else
                {
                    getBackground().setAlpha(0xff);
                }
            }
        }
    }

    /**
     * Saves the state of the menu item as open or close to be able to handle device rotations.
     */
    @Override
    public Parcelable onSaveInstanceState()
    {
        d("onSaveInstanceState");
        Bundle bundle = new Bundle();
        bundle.putParcelable("instanceState", super.onSaveInstanceState());
        bundle.putBoolean("mOpen", mOpen);
        // ... save everything
        return bundle;
    }

    /**
     * Restores the state of the FAM after a rotation.
     */
    @Override
    public void onRestoreInstanceState(Parcelable state)
    {
        d("onRestoreInstanceState");
        if (state instanceof Bundle)
        {
            Bundle bundle = (Bundle) state;
            mOpen = bundle.getBoolean("mOpen");
            // ... load everything
            state = bundle.getParcelable("instanceState");
        }
        super.onRestoreInstanceState(state);
    }

    @Override
    protected void onDetachedFromWindow()
    {
        d("onDetachedFromWindow");
        //getBackground().setAlpha(bgAlpha);//reset default alpha
        super.onDetachedFromWindow();
    }

    @Override
    public void setBackground(Drawable background)
    {
        if (background instanceof ColorDrawable)
        {
            // after activity finish and relaucher , background drawable state still remain?
            int bgAlpha = Color.alpha(((ColorDrawable) background).getColor());
            d("bg:" + Integer.toHexString(bgAlpha));
            super.setBackground(background);
        }
        else
        {
            throw new IllegalArgumentException("floating only support color background");
        }
    }

    //-- Open and close methods methods --//

    /**
     * Toggles the menu between open and closed, depending on its current state.
     */
    public void toggle()
    {
        if (!mOpen)
        {
            open();
        }
        else
        {
            close();
        }
    }

    /**
     * Opens the FloatingActionMenu.
     */
    public void open()
    {
        d("open");
        startOpenAnimator();
        mOpen = true;
        if (onMenuToggleListener != null)
        {
            onMenuToggleListener.onMenuToggle(true);
        }
    }

    /**
     * Closes the FloatingActionMenu.
     */
    public void close()
    {
        startCloseAnimator();
        mOpen = false;
        if (onMenuToggleListener != null)
        {
            onMenuToggleListener.onMenuToggle(true);
        }
    }

    //-- Animation methods. --//

    /**
     * Initiates all of the closing animations.
     */
    protected void startCloseAnimator()
    {
        mCloseAnimatorSet.start();
        for (ItemAnimator anim : mMenuItemAnimators)
        {
            anim.startCloseAnimator();
        }
    }

    /**
     * Initiating all of the opening animations.
     */
    protected void startOpenAnimator()
    {
        mOpenAnimatorSet.start();
        for (ItemAnimator anim : mMenuItemAnimators)
        {
            anim.startOpenAnimator();
        }
    }

    /**
     * Adds a new menu item to the FloatingActionMenu.
     *
     * @param item The ImageView to add to the menu.
     */
    public void addMenuItem(ImageView item)
    {
        mMenuItems.add(item);
        mMenuItemAnimators.add(new ItemAnimator(item));

        TextView button = new TextView(getContext());

        LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        button.setLayoutParams(params);

        button.setBackgroundResource(R.drawable.rounded_corners);

        button.setTextColor(Color.WHITE);
        button.setText(item.getContentDescription());

        Integer paddingSize = (int) button.getTextSize() / 3;

        button.setPadding(paddingSize, paddingSize, paddingSize, paddingSize);

        addView(button);
        mMenuItemLabels.add(button);
        item.setTag(button);
        item.setOnClickListener(mOnItemClickListener);
        button.setOnClickListener(mOnItemClickListener);
    }

    //-- Accessors --//

    /**
     * Determines whether or not the menu is open.
     *
     * @return True if the menu is open, false otherwise.
     */
    public boolean isOpened()
    {
        return mOpen;
    }

    /**
     * Retrieves the OnMenuToggleListener that is applied to the FloatingActionMenu.
     */
    public OnMenuToggleListener getOnMenuToggleListener()
    {
        return onMenuToggleListener;
    }

    /**
     * Retrieves the OnMenuItemClickListener that is applied to the FloatingActionMenu.
     */
    public OnMenuItemClickListener getOnMenuItemClickListener()
    {
        return onMenuItemClickListener;
    }

    //-- Mutators --//

    /**
     * Assigns an OnMenuToggleListener to the FloatingActionMenu.
     */
    public void setOnMenuToggleListener(OnMenuToggleListener onMenuToggleListener)
    {
        this.onMenuToggleListener = onMenuToggleListener;
    }

    /**
     * Assigns an OnMenuItemClickListener to the FloatingActionMenu.
     */
    public void setOnMenuItemClickListener(OnMenuItemClickListener onMenuItemClickListener)
    {
        this.onMenuItemClickListener = onMenuItemClickListener;
    }

    /**
     * Set as circle(default) or line pattern
     */
    public void setLayoutMode(int shape)
    {
        this.shape = shape;
    }

    /**
     * Set the radius of menu, default 256
     */
    public void setmRadius(int mRadius)
    {
        this.mRadius = mRadius;
    }

    /**
     * Set radius as multiple of width of floating action button
     */
    public void setMultipleOfFB(float multipleOfFB)
    {
        this.multipleOfFB = multipleOfFB;
    }

    /**
     * Duration of anim, default 300
     */
    public void setDuration(long duration)
    {
        this.duration = duration;
    }

    /**
     * Only usefully in Line pattern - sets the gap between menu items.
     */
    public void setmItemGap(int mItemGap)
    {
        this.mItemGap = mItemGap;
    }

    //-- Misc/Helper methods --//

    protected void d(String msg)
    {
        Log.d("FAM", msg == null ? null : msg);
    }

    //-- Interfaces --//

    /**
     * Interface that handles a change in the open/close state of the FloatingActionMenu.
     */
    public interface OnMenuToggleListener
    {
        void onMenuToggle(boolean opened);
    }

    /**
     * Interface that handles the click action of a MenuItem.
     */
    public interface OnMenuItemClickListener
    {
        void onMenuItemClick(CurveMenu fam, int index, ImageView item);
    }

    /**
     * Animator that controls the open/close animation of menu items.
     */
    private class ItemAnimator implements Animator.AnimatorListener
    {
        private View mView;
        private boolean playingOpenAnimator;

        public ItemAnimator(View v)
        {
            v.animate().setListener(this);
            mView = v;
        }

        public void startOpenAnimator()
        {
            mView.animate().cancel();
            playingOpenAnimator = true;
            mView.animate()
                    .translationY(0)
                    .translationX(0)
                    .setDuration(duration)
                    .setInterpolator(DEFAULT_OPEN_INTERPOLATOR)
                    .start();
            if (mIsButtonRotate)
            {
                mMenuButton.animate()
                        .rotation(135f)
                        .setDuration(duration)
                        .setInterpolator(DEFAULT_OPEN_INTERPOLATOR)
                        .start();
            }
        }

        public void startCloseAnimator()
        {
            if (shape == SHAPE_CURVE)
            {
                mView.animate().cancel();
                playingOpenAnimator = false;
                mView.animate()
                        .translationX(mMenuButton.getLeft() + mMenuButton.getMeasuredWidth() / 2 - mView.getMeasuredWidth() / 2 - mView.getLeft())
                        .translationY(mMenuButton.getTop() + mMenuButton.getMeasuredHeight() / 2 - mView.getMeasuredHeight() / 2 - mView.getTop())
                        .setInterpolator(DEFAULT_CLOSE_INTERPOLATOR)
                        .setDuration(duration)
                        .start();
                if (mIsButtonRotate)
                {
                    mMenuButton.animate()
                            .rotation(0f)
                            .setDuration(duration)
                            .setInterpolator(DEFAULT_CLOSE_INTERPOLATOR)
                            .start();
                }
                return;
            }
            mView.animate().cancel();
            playingOpenAnimator = false;
            mView.animate()
                    .translationX(mMenuButton.getLeft() - mView.getLeft())
                    .translationY((mMenuButton.getTop() - mView.getTop()))
                    .setDuration(duration)
                    .setInterpolator(DEFAULT_CLOSE_INTERPOLATOR)
                    .start();
            if (mIsButtonRotate)
            {
                mMenuButton.animate()
                        .rotation(0f)
                        .setDuration(duration)
                        .setInterpolator(DEFAULT_CLOSE_INTERPOLATOR)
                        .start();
            }
        }

        @Override
        public void onAnimationStart(Animator animation)
        {
            if (playingOpenAnimator)
            {
                mView.setVisibility(VISIBLE);
            }
            else
            {
                ((TextView) mView.getTag()).setVisibility(GONE);
            }
        }

        @Override
        public void onAnimationEnd(Animator animation)
        {
            if (!playingOpenAnimator)
            {
                mView.setVisibility(GONE);
            }
            else
            {
                ((TextView) mView.getTag()).setVisibility(VISIBLE);
            }
        }

        @Override
        public void onAnimationCancel(Animator animation)
        {

        }

        @Override
        public void onAnimationRepeat(Animator animation)
        {
        }
    }

    public static void setmIsButtonRotate(boolean mIsButtonRotate)
    {
        CurveMenu.mIsButtonRotate = mIsButtonRotate;
    }
}
