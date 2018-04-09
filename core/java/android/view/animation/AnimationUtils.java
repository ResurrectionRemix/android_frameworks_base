/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.view.animation;

import android.annotation.AnimRes;
import android.annotation.InterpolatorRes;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.content.res.Resources.Theme;
import android.content.res.XmlResourceParser;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Xml;
import android.util.PathParser;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/**
 * Defines common utilities for working with animations.
 *
 */
public class AnimationUtils {

    /**
     * These flags are used when parsing AnimatorSet objects
     */
    private static final int TOGETHER = 0;
    private static final int SEQUENTIALLY = 1;

    private static class AnimationState {
        boolean animationClockLocked;
        long currentVsyncTimeMillis;
        long lastReportedTimeMillis;
    };

    private static ThreadLocal<AnimationState> sAnimationState
            = new ThreadLocal<AnimationState>() {
        @Override
        protected AnimationState initialValue() {
            return new AnimationState();
        }
    };

    /** @hide */
    public static void lockAnimationClock(long vsyncMillis) {
        AnimationState state = sAnimationState.get();
        state.animationClockLocked = true;
        state.currentVsyncTimeMillis = vsyncMillis;
    }

    /** @hide */
    public static void unlockAnimationClock() {
        sAnimationState.get().animationClockLocked = false;
    }

    /**
     * Returns the current animation time in milliseconds. This time should be used when invoking
     * {@link Animation#setStartTime(long)}. Refer to {@link android.os.SystemClock} for more
     * information about the different available clocks. The clock used by this method is
     * <em>not</em> the "wall" clock (it is not {@link System#currentTimeMillis}).
     *
     * @return the current animation time in milliseconds
     *
     * @see android.os.SystemClock
     */
    public static long currentAnimationTimeMillis() {
        AnimationState state = sAnimationState.get();
        if (state.animationClockLocked) {
            // It's important that time never rewinds
            return Math.max(state.currentVsyncTimeMillis,
                    state.lastReportedTimeMillis);
        }
        state.lastReportedTimeMillis = SystemClock.uptimeMillis();
        return state.lastReportedTimeMillis;
    }

    /**
     * Loads an {@link Animation} object from a resource
     *
     * @param context Application context used to access resources
     * @param id The resource id of the animation to load
     * @return The animation object reference by the specified id
     * @throws NotFoundException when the animation cannot be loaded
     */
    public static Animation loadAnimation(Context context, @AnimRes int id)
            throws NotFoundException {

              String name = context.getResources().getResourceEntryName(id);
              switch(name) {
                case "activity_open_enter" : return getActivityOpenEnterAnim();
                case "activity_open_exit" : return getActivityOpenExitAnim();
                case "activity_close_enter" : return getActivityCloseEnterAnim();
                case "activity_close_exit" : return getActivityCloseExitAnim();
                case "task_open_enter" : return getTaskOpenEnterAnim();
                case "task_open_exit" : return getTaskOpenExitAnim();
                case "task_close_enter" : return getTaskCloseEnterAnim();
                case "task_close_exit" : return getTaskCloseExitAnim();
                default: return loadAnimationFromXml(context,id);
              }
    }

    private static Animation getActivityOpenEnterAnim(){
      AnimationSet animationSet = new AnimationSet(false);
      animationSet.setZAdjustment(Animation.ZORDER_TOP);
      TranslateAnimation translateAnimation = new TranslateAnimation(Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.04100001f, Animation.RELATIVE_TO_SELF, 0.0f);
      translateAnimation.setInterpolator(fastOutSlowIn());
      translateAnimation.setDuration(425L);
      animationSet.addAnimation(translateAnimation);
      ClipRectAnimation clipRectAnimation = new ClipRectAnimation(0.0f, 0.959f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f);
      clipRectAnimation.setDuration(425L);
      clipRectAnimation.setInterpolator(fastOutExtraSlowIn());
      animationSet.addAnimation(clipRectAnimation);
      return animationSet;
    }

    private static Animation getActivityOpenExitAnim(){
      AnimationSet animationSet = new AnimationSet(false);
      TranslateAnimation translateAnimation = new TranslateAnimation(Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, -0.019999981f);
      translateAnimation.setDuration(425L);
      translateAnimation.setInterpolator(fastOutSlowIn());
      animationSet.addAnimation(translateAnimation);
      AlphaAnimation alphaAnimation = new AlphaAnimation(1.0f,0.9f);
      alphaAnimation.setDuration(117L);
      alphaAnimation.setInterpolator(new LinearInterpolator());
      animationSet.addAnimation(alphaAnimation);
      return animationSet;
    }

    private static Animation getActivityCloseEnterAnim(){
      AnimationSet animationSet = new AnimationSet(false);
      TranslateAnimation translateAnimation = new TranslateAnimation(Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, -0.019999981f, Animation.RELATIVE_TO_SELF, 0.0f);
      translateAnimation.setDuration(425L);
      translateAnimation.setInterpolator(fastOutSlowIn());
      animationSet.addAnimation(translateAnimation);
      AlphaAnimation alphaAnimation = new AlphaAnimation(0.9f,1.0f);
      alphaAnimation.setDuration(425L);
      alphaAnimation.setStartOffset(0);
      alphaAnimation.setInterpolator(activityCloseDim());
      animationSet.addAnimation(alphaAnimation);
      return animationSet;
    }

    private static Animation getActivityCloseExitAnim(){
      AnimationSet animationSet = new AnimationSet(false);
      TranslateAnimation translateAnimation = new TranslateAnimation(Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.04100001f);
      translateAnimation.setDuration(425L);
      translateAnimation.setInterpolator(fastOutSlowIn());
      animationSet.addAnimation(translateAnimation);
      ClipRectAnimation clipRectAnimation = new ClipRectAnimation(0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.959f, 1.0f, 1.0f);
      clipRectAnimation.setDuration(425L);
      clipRectAnimation.setInterpolator(fastOutExtraSlowIn());
      animationSet.addAnimation(clipRectAnimation);
      return animationSet;
    }

    private static Animation getTaskOpenEnterAnim(){
      AnimationSet animationSet = new AnimationSet(false);
      TranslateAnimation translateAnimation = new TranslateAnimation(Animation.RELATIVE_TO_SELF, -1.0499878f, Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f);
      translateAnimation.setDuration(383L);
      translateAnimation.setStartOffset(50);
      translateAnimation.setInterpolator(aggressiveEase());
      translateAnimation.setFillEnabled(true);
      translateAnimation.setFillBefore(true);
      translateAnimation.setFillAfter(true);
      animationSet.addAnimation(translateAnimation);
      ScaleAnimation scaleAnimation = new ScaleAnimation(1.0526f, 1.0f, 1.0526f, 1.0f,Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
      scaleAnimation.setDuration(283L);
      scaleAnimation.setInterpolator(fastOutSlowIn());
      scaleAnimation.setFillEnabled(true);
      scaleAnimation.setFillBefore(true);
      scaleAnimation.setFillAfter(true);
      animationSet.addAnimation(scaleAnimation);
      ScaleAnimation scaleAnimation2 = new ScaleAnimation(0.95f, 1.0f, 0.95f, 1.0f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
      scaleAnimation2.setDuration(317L);
      scaleAnimation2.setStartOffset(283);
      scaleAnimation2.setInterpolator(fastOutSlowIn());
      scaleAnimation2.setFillEnabled(true);
      scaleAnimation2.setFillBefore(true);
      scaleAnimation2.setFillAfter(true);
      animationSet.addAnimation(scaleAnimation2);
      return animationSet;
    }

    private static Animation getTaskOpenExitAnim(){
      AnimationSet animationSet = new AnimationSet(false);
      TranslateAnimation translateAnimation = new TranslateAnimation(Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 1.0499878f, Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f);
      translateAnimation.setDuration(383L);
      translateAnimation.setStartOffset(50);
      translateAnimation.setInterpolator(aggressiveEase());
      translateAnimation.setFillEnabled(true);
      translateAnimation.setFillBefore(true);
      translateAnimation.setFillAfter(true);
      animationSet.addAnimation(translateAnimation);
      ScaleAnimation scaleAnimation = new ScaleAnimation(1.0f, 0.95f, 1.0f, 0.95f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
      scaleAnimation.setDuration(283L);
      scaleAnimation.setInterpolator(fastOutSlowIn());
      scaleAnimation.setFillEnabled(true);
      scaleAnimation.setFillBefore(true);
      scaleAnimation.setFillAfter(true);
      animationSet.addAnimation(scaleAnimation);
      return animationSet;
    }

    private static Animation getTaskCloseEnterAnim(){
      AnimationSet animationSet = new AnimationSet(false);
      TranslateAnimation translateAnimation = new TranslateAnimation(Animation.RELATIVE_TO_SELF, 1.0499878f, Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f);
      translateAnimation.setDuration(383L);
      translateAnimation.setStartOffset(50);
      translateAnimation.setInterpolator(aggressiveEase());
      translateAnimation.setFillEnabled(true);
      translateAnimation.setFillBefore(true);
      translateAnimation.setFillAfter(true);
      animationSet.addAnimation(translateAnimation);
      ScaleAnimation scaleAnimation = new ScaleAnimation(1.0526f, 1.0f, 1.0526f, 1.0f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
      scaleAnimation.setDuration(283);
      scaleAnimation.setInterpolator(fastOutSlowIn());
      scaleAnimation.setFillEnabled(true);
      scaleAnimation.setFillBefore(true);
      scaleAnimation.setFillAfter(true);
      animationSet.addAnimation(scaleAnimation);
      ScaleAnimation scaleAnimation2 = new ScaleAnimation(0.95f, 1.0f, 0.95f, 1.0f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
      scaleAnimation2.setDuration(317L);
      scaleAnimation2.setStartOffset(283);
      scaleAnimation2.setInterpolator(fastOutSlowIn());
      scaleAnimation2.setFillEnabled(true);
      scaleAnimation2.setFillBefore(true);
      scaleAnimation2.setFillAfter(true);
      animationSet.addAnimation(scaleAnimation2);
      return animationSet;
    }

    private static Animation getTaskCloseExitAnim(){
      AnimationSet animationSet = new AnimationSet(false);
      TranslateAnimation translateAnimation = new TranslateAnimation(Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, -1.0499878f, Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f);
      translateAnimation.setDuration(383L);
      translateAnimation.setStartOffset(50);
      translateAnimation.setInterpolator(aggressiveEase());
      translateAnimation.setFillEnabled(true);
      translateAnimation.setFillBefore(true);
      translateAnimation.setFillAfter(true);
      animationSet.addAnimation(translateAnimation);
      ScaleAnimation scaleAnimation = new ScaleAnimation(1.0f, 0.95f, 1.0f, 0.95f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
      scaleAnimation.setDuration(283);
      scaleAnimation.setInterpolator(fastOutSlowIn());
      scaleAnimation.setFillEnabled(true);
      scaleAnimation.setFillBefore(true);
      scaleAnimation.setFillAfter(true);
      animationSet.addAnimation(scaleAnimation);
      return animationSet;
    }

    private static Interpolator fastOutSlowIn() {
        return new PathInterpolator(0.4F, 0.0F, 0.2F, 1.0F);
    }

    private static Interpolator activityCloseDim() {
        return new PathInterpolator(0.33f, 0.0f, 1.0f, 1.0f);
    }

    private static Interpolator aggressiveEase() {
        return new PathInterpolator(0.2f, 0.0f, 0.0f, 1.0f);
    }

    private static Interpolator fastOutExtraSlowIn() {
        return new PathInterpolator(PathParser.createPathFromPathData("M 0,0 C 0.05, 0, 0.133333, 0.06, 0.166666, 0.4 C 0.208333, 0.82, 0.25, 1, 1, 1"));
    }

    private static Animation loadAnimationFromXml(Context context,int id) {
        XmlResourceParser parser = null;
        try {
            parser = context.getResources().getAnimation(id);
            return createAnimationFromXml(context, parser);
        } catch (XmlPullParserException ex) {
            NotFoundException rnf = new NotFoundException("Can't load animation resource ID #0x" +
                    Integer.toHexString(id));
            rnf.initCause(ex);
            throw rnf;
        } catch (IOException ex) {
            NotFoundException rnf = new NotFoundException("Can't load animation resource ID #0x" +
                    Integer.toHexString(id));
            rnf.initCause(ex);
            throw rnf;
        } finally {
            if (parser != null) parser.close();
        }
    }

    private static Animation createAnimationFromXml(Context c, XmlPullParser parser)
            throws XmlPullParserException, IOException {

        return createAnimationFromXml(c, parser, null, Xml.asAttributeSet(parser));
    }

    private static Animation createAnimationFromXml(Context c, XmlPullParser parser,
            AnimationSet parent, AttributeSet attrs) throws XmlPullParserException, IOException {

        Animation anim = null;

        // Make sure we are on a start tag.
        int type;
        int depth = parser.getDepth();

        while (((type=parser.next()) != XmlPullParser.END_TAG || parser.getDepth() > depth)
               && type != XmlPullParser.END_DOCUMENT) {

            if (type != XmlPullParser.START_TAG) {
                continue;
            }

            String  name = parser.getName();

            if (name.equals("set")) {
                anim = new AnimationSet(c, attrs);
                createAnimationFromXml(c, parser, (AnimationSet)anim, attrs);
            } else if (name.equals("alpha")) {
                anim = new AlphaAnimation(c, attrs);
            } else if (name.equals("scale")) {
                anim = new ScaleAnimation(c, attrs);
            }  else if (name.equals("rotate")) {
                anim = new RotateAnimation(c, attrs);
            }  else if (name.equals("translate")) {
                anim = new TranslateAnimation(c, attrs);
            } else {
                throw new RuntimeException("Unknown animation name: " + parser.getName());
            }

            if (parent != null) {
                parent.addAnimation(anim);
            }
        }

        return anim;

    }

    /**
     * Loads a {@link LayoutAnimationController} object from a resource
     *
     * @param context Application context used to access resources
     * @param id The resource id of the animation to load
     * @return The animation object reference by the specified id
     * @throws NotFoundException when the layout animation controller cannot be loaded
     */
    public static LayoutAnimationController loadLayoutAnimation(Context context, @AnimRes int id)
            throws NotFoundException {

        XmlResourceParser parser = null;
        try {
            parser = context.getResources().getAnimation(id);
            return createLayoutAnimationFromXml(context, parser);
        } catch (XmlPullParserException ex) {
            NotFoundException rnf = new NotFoundException("Can't load animation resource ID #0x" +
                    Integer.toHexString(id));
            rnf.initCause(ex);
            throw rnf;
        } catch (IOException ex) {
            NotFoundException rnf = new NotFoundException("Can't load animation resource ID #0x" +
                    Integer.toHexString(id));
            rnf.initCause(ex);
            throw rnf;
        } finally {
            if (parser != null) parser.close();
        }
    }

    private static LayoutAnimationController createLayoutAnimationFromXml(Context c,
            XmlPullParser parser) throws XmlPullParserException, IOException {

        return createLayoutAnimationFromXml(c, parser, Xml.asAttributeSet(parser));
    }

    private static LayoutAnimationController createLayoutAnimationFromXml(Context c,
            XmlPullParser parser, AttributeSet attrs) throws XmlPullParserException, IOException {

        LayoutAnimationController controller = null;

        int type;
        int depth = parser.getDepth();

        while (((type = parser.next()) != XmlPullParser.END_TAG || parser.getDepth() > depth)
                && type != XmlPullParser.END_DOCUMENT) {

            if (type != XmlPullParser.START_TAG) {
                continue;
            }

            String name = parser.getName();

            if ("layoutAnimation".equals(name)) {
                controller = new LayoutAnimationController(c, attrs);
            } else if ("gridLayoutAnimation".equals(name)) {
                controller = new GridLayoutAnimationController(c, attrs);
            } else {
                throw new RuntimeException("Unknown layout animation name: " + name);
            }
        }

        return controller;
    }

    /**
     * Make an animation for objects becoming visible. Uses a slide and fade
     * effect.
     *
     * @param c Context for loading resources
     * @param fromLeft is the object to be animated coming from the left
     * @return The new animation
     */
    public static Animation makeInAnimation(Context c, boolean fromLeft) {
        Animation a;
        if (fromLeft) {
            a = AnimationUtils.loadAnimation(c, com.android.internal.R.anim.slide_in_left);
        } else {
            a = AnimationUtils.loadAnimation(c, com.android.internal.R.anim.slide_in_right);
        }

        a.setInterpolator(new DecelerateInterpolator());
        a.setStartTime(currentAnimationTimeMillis());
        return a;
    }

    /**
     * Make an animation for objects becoming invisible. Uses a slide and fade
     * effect.
     *
     * @param c Context for loading resources
     * @param toRight is the object to be animated exiting to the right
     * @return The new animation
     */
    public static Animation makeOutAnimation(Context c, boolean toRight) {
        Animation a;
        if (toRight) {
            a = AnimationUtils.loadAnimation(c, com.android.internal.R.anim.slide_out_right);
        } else {
            a = AnimationUtils.loadAnimation(c, com.android.internal.R.anim.slide_out_left);
        }

        a.setInterpolator(new AccelerateInterpolator());
        a.setStartTime(currentAnimationTimeMillis());
        return a;
    }


    /**
     * Make an animation for objects becoming visible. Uses a slide up and fade
     * effect.
     *
     * @param c Context for loading resources
     * @return The new animation
     */
    public static Animation makeInChildBottomAnimation(Context c) {
        Animation a;
        a = AnimationUtils.loadAnimation(c, com.android.internal.R.anim.slide_in_child_bottom);
        a.setInterpolator(new AccelerateInterpolator());
        a.setStartTime(currentAnimationTimeMillis());
        return a;
    }

    /**
     * Loads an {@link Interpolator} object from a resource
     *
     * @param context Application context used to access resources
     * @param id The resource id of the animation to load
     * @return The animation object reference by the specified id
     * @throws NotFoundException
     */
    public static Interpolator loadInterpolator(Context context, @AnimRes @InterpolatorRes int id)
            throws NotFoundException {
        XmlResourceParser parser = null;
        try {
            parser = context.getResources().getAnimation(id);
            return createInterpolatorFromXml(context.getResources(), context.getTheme(), parser);
        } catch (XmlPullParserException ex) {
            NotFoundException rnf = new NotFoundException("Can't load animation resource ID #0x" +
                    Integer.toHexString(id));
            rnf.initCause(ex);
            throw rnf;
        } catch (IOException ex) {
            NotFoundException rnf = new NotFoundException("Can't load animation resource ID #0x" +
                    Integer.toHexString(id));
            rnf.initCause(ex);
            throw rnf;
        } finally {
            if (parser != null) parser.close();
        }

    }

    /**
     * Loads an {@link Interpolator} object from a resource
     *
     * @param res The resources
     * @param id The resource id of the animation to load
     * @return The interpolator object reference by the specified id
     * @throws NotFoundException
     * @hide
     */
    public static Interpolator loadInterpolator(Resources res, Theme theme, int id) throws NotFoundException {
        XmlResourceParser parser = null;
        try {
            parser = res.getAnimation(id);
            return createInterpolatorFromXml(res, theme, parser);
        } catch (XmlPullParserException ex) {
            NotFoundException rnf = new NotFoundException("Can't load animation resource ID #0x" +
                    Integer.toHexString(id));
            rnf.initCause(ex);
            throw rnf;
        } catch (IOException ex) {
            NotFoundException rnf = new NotFoundException("Can't load animation resource ID #0x" +
                    Integer.toHexString(id));
            rnf.initCause(ex);
            throw rnf;
        } finally {
            if (parser != null)
                parser.close();
        }

    }

    private static Interpolator createInterpolatorFromXml(Resources res, Theme theme, XmlPullParser parser)
            throws XmlPullParserException, IOException {

        BaseInterpolator interpolator = null;

        // Make sure we are on a start tag.
        int type;
        int depth = parser.getDepth();

        while (((type = parser.next()) != XmlPullParser.END_TAG || parser.getDepth() > depth)
                && type != XmlPullParser.END_DOCUMENT) {

            if (type != XmlPullParser.START_TAG) {
                continue;
            }

            AttributeSet attrs = Xml.asAttributeSet(parser);

            String name = parser.getName();

            if (name.equals("linearInterpolator")) {
                interpolator = new LinearInterpolator();
            } else if (name.equals("accelerateInterpolator")) {
                interpolator = new AccelerateInterpolator(res, theme, attrs);
            } else if (name.equals("decelerateInterpolator")) {
                interpolator = new DecelerateInterpolator(res, theme, attrs);
            } else if (name.equals("accelerateDecelerateInterpolator")) {
                interpolator = new AccelerateDecelerateInterpolator();
            } else if (name.equals("cycleInterpolator")) {
                interpolator = new CycleInterpolator(res, theme, attrs);
            } else if (name.equals("anticipateInterpolator")) {
                interpolator = new AnticipateInterpolator(res, theme, attrs);
            } else if (name.equals("overshootInterpolator")) {
                interpolator = new OvershootInterpolator(res, theme, attrs);
            } else if (name.equals("anticipateOvershootInterpolator")) {
                interpolator = new AnticipateOvershootInterpolator(res, theme, attrs);
            } else if (name.equals("bounceInterpolator")) {
                interpolator = new BounceInterpolator();
            } else if (name.equals("pathInterpolator")) {
                interpolator = new PathInterpolator(res, theme, attrs);
            } else {
                throw new RuntimeException("Unknown interpolator name: " + parser.getName());
            }
        }
        return interpolator;
    }
}
