/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.quickstep.util;

import static android.view.View.VISIBLE;

import static com.android.app.animation.Interpolators.ACCELERATE;
import static com.android.app.animation.Interpolators.ACCELERATE_DECELERATE;
import static com.android.app.animation.Interpolators.DECELERATE;
import static com.android.app.animation.Interpolators.DECELERATE_1_7;
import static com.android.app.animation.Interpolators.DECELERATE_3;
import static com.android.app.animation.Interpolators.EMPHASIZED_ACCELERATE;
import static com.android.app.animation.Interpolators.EMPHASIZED_DECELERATE;
import static com.android.app.animation.Interpolators.FAST_OUT_SLOW_IN;
import static com.android.app.animation.Interpolators.FINAL_FRAME;
import static com.android.app.animation.Interpolators.INSTANT;
import static com.android.app.animation.Interpolators.LINEAR;
import static com.android.app.animation.Interpolators.OVERSHOOT_0_75;
import static com.android.app.animation.Interpolators.OVERSHOOT_1_2;
import static com.android.app.animation.Interpolators.clampToProgress;
import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.HINT_STATE;
import static com.android.launcher3.LauncherState.HINT_STATE_TWO_BUTTON;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.LauncherState.OVERVIEW_SPLIT_SELECT;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_ALL_APPS_FADE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_DEPTH;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_ACTIONS_FADE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_FADE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_SCALE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_SPLIT_SELECT_FLOATING_TASK_TRANSLATE_OFFSCREEN;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_SPLIT_SELECT_INSTRUCTIONS_FADE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_TRANSLATE_X;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_TRANSLATE_Y;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_SCRIM_FADE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_WORKSPACE_FADE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_WORKSPACE_SCALE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_WORKSPACE_TRANSLATE;
import static com.android.quickstep.views.RecentsView.ADJACENT_PAGE_HORIZONTAL_OFFSET;
import static com.android.quickstep.views.RecentsView.RECENTS_SCALE_PROPERTY;
import static com.android.quickstep.views.RecentsView.RUNNING_TASK_ATTACH_ALPHA;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Context;

import androidx.dynamicanimation.animation.DynamicAnimation;

import com.android.launcher3.QuickstepTransitionManager;
import com.android.launcher3.anim.SpringAnimationBuilder;
import com.android.launcher3.statemanager.BaseState;
import com.android.launcher3.statemanager.StateManager.AtomicAnimationFactory;
import com.android.launcher3.statemanager.StatefulContainer;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.NavigationMode;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.RecentsViewContainer;

public class RecentsAtomicAnimationFactory<
        CONTAINER extends Context & RecentsViewContainer & StatefulContainer<STATE_TYPE>,
        STATE_TYPE extends BaseState<STATE_TYPE>> extends AtomicAnimationFactory<STATE_TYPE> {

    public static final int INDEX_RECENTS_FADE_ANIM = AtomicAnimationFactory.NEXT_INDEX + 0;
    public static final int INDEX_RECENTS_TRANSLATE_X_ANIM = AtomicAnimationFactory.NEXT_INDEX + 1;
    public static final int INDEX_RECENTS_ATTACHED_ALPHA_ANIM =
            AtomicAnimationFactory.NEXT_INDEX + 2;

    private static final int MY_ANIM_COUNT = 3;

    // Scale recents takes before animating in
    private static final float RECENTS_PREPARE_SCALE = 1.33f;

    // Constants to specify how to scroll RecentsView to the default page if it's not already there.
    private static final int DEFAULT_PAGE = 0;
    private static final int PER_PAGE_SCROLL_DURATION = 150;
    private static final int MAX_PAGE_SCROLL_DURATION = 750;

    protected final CONTAINER mContainer;

    public RecentsAtomicAnimationFactory(CONTAINER container) {
        super(MY_ANIM_COUNT);
        mContainer = container;
    }

    @Override
    public Animator createStateElementAnimation(int index, float... values) {
        switch (index) {
            case INDEX_RECENTS_FADE_ANIM:
                ObjectAnimator alpha = ObjectAnimator.ofFloat(mContainer.getOverviewPanel(),
                        RecentsView.CONTENT_ALPHA, values);
                return alpha;
            case INDEX_RECENTS_ATTACHED_ALPHA_ANIM:
            case INDEX_RECENTS_TRANSLATE_X_ANIM: {
                RecentsView rv = mContainer.getOverviewPanel();
                return new SpringAnimationBuilder(mContainer)
                        .setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE)
                        .setDampingRatio(0.8f)
                        .setStiffness(250)
                        .setValues(values)
                        .build(rv, index == INDEX_RECENTS_ATTACHED_ALPHA_ANIM
                                ? RUNNING_TASK_ATTACH_ALPHA : ADJACENT_PAGE_HORIZONTAL_OFFSET);
            }
            default:
                return super.createStateElementAnimation(index, values);
        }
    }

    protected void applyOverviewToHomeAnimConfig(
            STATE_TYPE fromState,
            StateAnimationConfig config,
            RecentsView<CONTAINER, STATE_TYPE> overview,
            boolean isPinnedTaskbar,
            boolean isThreeButton) {
        overview.switchToScreenshot(() ->
                overview.finishRecentsAnimation(true /* toRecents */, null));

        if (fromState.equals(OVERVIEW_SPLIT_SELECT)) {
            config.setInterpolator(ANIM_OVERVIEW_SPLIT_SELECT_FLOATING_TASK_TRANSLATE_OFFSCREEN,
                    clampToProgress(EMPHASIZED_ACCELERATE, 0, 0.4f));
            config.setInterpolator(ANIM_OVERVIEW_SPLIT_SELECT_INSTRUCTIONS_FADE,
                    clampToProgress(LINEAR, 0, 0.33f));
        }

        // We sync the scrim fade with the taskbar animation duration to avoid any flickers for
        // taskbar icons disappearing before hotseat icons show up.
        boolean isPersistentTaskbarAndNotInDesktopMode =
                (isThreeButton || isPinnedTaskbar)
                        && !DisplayController.isInDesktopMode(mContainer);
        float scrimUpperBoundFromSplit =
                QuickstepTransitionManager.getTaskbarToHomeDuration(
                        isPersistentTaskbarAndNotInDesktopMode)
                        / (float) config.duration;
        scrimUpperBoundFromSplit = Math.min(scrimUpperBoundFromSplit, 1f);
        config.setInterpolator(ANIM_OVERVIEW_ACTIONS_FADE, clampToProgress(LINEAR, 0, 0.25f));
        config.setInterpolator(ANIM_SCRIM_FADE,
                fromState.equals(OVERVIEW_SPLIT_SELECT)
                        ? clampToProgress(LINEAR, 0.33f, scrimUpperBoundFromSplit)
                        : LINEAR);
        config.setInterpolator(ANIM_WORKSPACE_SCALE, DECELERATE);
        config.setInterpolator(ANIM_WORKSPACE_FADE, ACCELERATE);

        if (DisplayController.getNavigationMode(mContainer).hasGestures
                && overview.hasTaskViews()) {
            // Overview is going offscreen, so keep it at its current scale and opacity.
            config.setInterpolator(ANIM_OVERVIEW_SCALE, FINAL_FRAME);
            config.setInterpolator(ANIM_OVERVIEW_FADE, FINAL_FRAME);
            config.setInterpolator(ANIM_OVERVIEW_TRANSLATE_X,
                    fromState.equals(OVERVIEW_SPLIT_SELECT)
                            ? EMPHASIZED_DECELERATE
                            : clampToProgress(FAST_OUT_SLOW_IN, 0, 0.75f));
            config.setInterpolator(ANIM_OVERVIEW_TRANSLATE_Y, FINAL_FRAME);

            // Scroll RecentsView to page 0 as it goes offscreen, if necessary.
            int numPagesToScroll = overview.getNextPage() - DEFAULT_PAGE;
            long scrollDuration = Math.min(MAX_PAGE_SCROLL_DURATION,
                    numPagesToScroll * PER_PAGE_SCROLL_DURATION);
            config.duration = Math.max(config.duration, scrollDuration);

            // Sync scroll so that it ends before or at the same time as the taskbar animation.
            if (mContainer.getDeviceProfile().isTaskbarPresent) {
                config.duration = Math.min(
                        config.duration,
                        QuickstepTransitionManager.getTaskbarToHomeDuration(
                                isPersistentTaskbarAndNotInDesktopMode));
            }
            overview.snapToPage(DEFAULT_PAGE, Math.toIntExact(config.duration));
        } else {
            config.setInterpolator(ANIM_OVERVIEW_TRANSLATE_X, ACCELERATE_DECELERATE);
            config.setInterpolator(ANIM_OVERVIEW_SCALE, clampToProgress(ACCELERATE, 0, 0.9f));
            config.setInterpolator(ANIM_OVERVIEW_FADE, DECELERATE_1_7);
        }
    }

    protected int getHintToNormalAnimationDuration(STATE_TYPE toState) {
        return -1;
    }

    protected void applyAllAppsToNormalConfig(StateAnimationConfig config) { }

    protected void applyNormalToAllAppsAnimConfig(StateAnimationConfig config) { }

    @Override
    public void prepareForAtomicAnimation(
            STATE_TYPE fromState, STATE_TYPE toState, StateAnimationConfig config) {
        RecentsView<CONTAINER, STATE_TYPE> overview = mContainer.getOverviewPanel();
        boolean isPinnedTaskbar = DisplayController.isPinnedTaskbar(mContainer);
        boolean isThreeButton = DisplayController.getNavigationMode(mContainer)
                == NavigationMode.THREE_BUTTONS;
        if ((fromState.equals(OVERVIEW)
                || fromState.equals(OVERVIEW_SPLIT_SELECT))
                && toState.equals(NORMAL)) {
            applyOverviewToHomeAnimConfig(
                    fromState, config, overview, isPinnedTaskbar, isThreeButton);
        } else if ((fromState.equals(NORMAL) || fromState.equals(HINT_STATE)
                || fromState.equals(HINT_STATE_TWO_BUTTON)) && toState.equals(OVERVIEW)) {
            if (DisplayController.getNavigationMode(mContainer).hasGestures) {
                config.setInterpolator(ANIM_WORKSPACE_SCALE,
                        fromState.equals(NORMAL) ? ACCELERATE : OVERSHOOT_1_2);
                config.setInterpolator(ANIM_WORKSPACE_TRANSLATE, ACCELERATE);

                // Scrolling in tasks, so show straight away
                if (overview.hasTaskViews()) {
                    config.setInterpolator(ANIM_OVERVIEW_FADE, INSTANT);
                } else {
                    config.setInterpolator(ANIM_OVERVIEW_FADE, OVERSHOOT_1_2);
                }
            } else {
                config.setInterpolator(ANIM_WORKSPACE_SCALE, OVERSHOOT_1_2);
                config.setInterpolator(ANIM_OVERVIEW_FADE, OVERSHOOT_1_2);

                // Scale up the recents, if it is not coming from the side
                if (overview.getVisibility() != VISIBLE || overview.getContentAlpha() == 0) {
                    RECENTS_SCALE_PROPERTY.set(overview, RECENTS_PREPARE_SCALE);
                }
            }
            config.setInterpolator(ANIM_WORKSPACE_FADE, OVERSHOOT_1_2);
            config.setInterpolator(ANIM_ALL_APPS_FADE, OVERSHOOT_1_2);
            config.setInterpolator(ANIM_OVERVIEW_SCALE, OVERSHOOT_1_2);
            config.setInterpolator(ANIM_DEPTH, OVERSHOOT_1_2);
            config.setInterpolator(ANIM_SCRIM_FADE, t -> {
                // Animate at the same rate until reaching progress 1, and skip the overshoot.
                return Math.min(1, OVERSHOOT_1_2.getInterpolation(t));
            });
            config.setInterpolator(ANIM_OVERVIEW_TRANSLATE_X, OVERSHOOT_1_2);
            config.setInterpolator(ANIM_OVERVIEW_TRANSLATE_Y, OVERSHOOT_1_2);
        } else if (fromState.equals(HINT_STATE) && toState.equals(NORMAL)) {
            config.setInterpolator(ANIM_DEPTH, DECELERATE_3);
            config.duration = Math.max(config.duration, getHintToNormalAnimationDuration(toState));
        } else if (fromState.equals(ALL_APPS) && toState.equals(NORMAL)) {
            applyAllAppsToNormalConfig(config);
        } else if (fromState.equals(NORMAL) && toState.equals(ALL_APPS)) {
            applyNormalToAllAppsAnimConfig(config);
        } else if (fromState.equals(OVERVIEW) && toState.equals(OVERVIEW_SPLIT_SELECT)) {
            SplitAnimationTimings timings =
                    mContainer.getDeviceProfile().getDeviceProperties().isTablet()
                            ? SplitAnimationTimings.TABLET_OVERVIEW_TO_SPLIT
                            : SplitAnimationTimings.PHONE_OVERVIEW_TO_SPLIT;
            config.setInterpolator(ANIM_OVERVIEW_ACTIONS_FADE, clampToProgress(LINEAR,
                    timings.getActionsFadeStartOffset(),
                    timings.getActionsFadeEndOffset()));
        } else if ((fromState.equals(NORMAL) || fromState.equals(ALL_APPS))
                && toState.equals(OVERVIEW_SPLIT_SELECT)) {
            // Splitting from Home is currently only available on tablets
            SplitAnimationTimings timings = SplitAnimationTimings.TABLET_HOME_TO_SPLIT;
            config.setInterpolator(ANIM_SCRIM_FADE, clampToProgress(LINEAR,
                    timings.getScrimFadeInStartOffset(),
                    timings.getScrimFadeInEndOffset()));
            config.setInterpolator(ANIM_OVERVIEW_TRANSLATE_X, OVERSHOOT_0_75);
            config.setInterpolator(ANIM_OVERVIEW_TRANSLATE_Y, OVERSHOOT_0_75);
        }
    }
}
