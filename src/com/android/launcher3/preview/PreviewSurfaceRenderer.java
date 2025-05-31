/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.launcher3.preview;

import static android.content.res.Configuration.UI_MODE_NIGHT_NO;
import static android.content.res.Configuration.UI_MODE_NIGHT_YES;
import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.launcher3.LauncherPrefs.GRID_NAME;
import static com.android.launcher3.LauncherPrefs.NON_FIXED_LANDSCAPE_GRID_NAME;
import static com.android.launcher3.WorkspaceLayoutManager.FIRST_SCREEN_ID;
import static com.android.launcher3.graphics.ThemeManager.PREF_ICON_SHAPE;
import static com.android.launcher3.graphics.ThemeManager.THEMED_ICONS;
import static com.android.launcher3.provider.LauncherDbUtils.selectionForWorkspaceScreen;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;
import static com.android.launcher3.widget.LauncherWidgetHolder.APPWIDGET_HOST_ID;
import static com.android.systemui.shared.Flags.extendibleThemeManager;

import android.app.WallpaperColors;
import android.appwidget.AppWidgetHost;
import android.content.Context;
import android.content.res.Configuration;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.ContextThemeWrapper;
import android.view.Display;
import android.view.SurfaceControlViewHost;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.dagger.LauncherComponentProvider;
import com.android.launcher3.graphics.GridCustomizationsProxy;
import com.android.launcher3.model.BgDataModel;
import com.android.launcher3.model.BgDataModel.Callbacks;
import com.android.launcher3.model.LoaderTask;
import com.android.launcher3.model.UserManagerState;
import com.android.launcher3.preview.PreviewContext.PreviewAppComponent;
import com.android.launcher3.util.RunnableList;
import com.android.launcher3.util.Themes;
import com.android.launcher3.widget.LocalColorExtractor;
import com.android.systemui.shared.Flags;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Render preview using surface view. */
@SuppressWarnings("NewApi")
public class PreviewSurfaceRenderer {

    private static final String TAG = "PreviewSurfaceRenderer";
    public static final int FADE_IN_ANIMATION_DURATION = 200;
    public static final String KEY_HOST_TOKEN = "host_token";
    public static final String KEY_VIEW_WIDTH = "width";
    public static final String KEY_VIEW_HEIGHT = "height";
    public static final String KEY_DISPLAY_ID = "display_id";
    public static final String KEY_COLORS = "wallpaper_colors";
    public static final String KEY_COLOR_RESOURCE_IDS = "color_resource_ids";
    public static final String KEY_COLOR_VALUES = "color_values";
    public static final String KEY_DARK_MODE = "use_dark_mode";
    public static final String KEY_LAYOUT_XML = "layout_xml";
    public static final String KEY_BITMAP_GENERATION_DELAY_MS = "bitmap_delay_ms";
    // Wait for some time before capturing screenshot to allow the surface to be laid out
    public static final long MIN_BITMAP_GENERATION_DELAY_MS = 100L;

    public static final String KEY_WORKSPACE_PAGE_ID = "workspace_page_id";
    public static final String FIXED_LANDSCAPE_GRID = "fixed_landscape_mode";

    private final Context mContext;
    private final String mLayoutXml;
    private final int mWorkspacePageId;

    private SparseIntArray mPreviewColorOverride;
    private String mGridName;
    private String mShapeKey;
    private boolean mIsMonoThemeEnabled;

    @Nullable private Boolean mDarkMode;
    private boolean mDestroyed = false;
    private boolean mHideQsb;
    @Nullable private FrameLayout mViewRoot = null;
    private boolean mDeletingHostOnExit = false;

    private final int mCallingPid;
    private final IBinder mHostToken;
    private final int mWidth;
    private final int mHeight;
    private final boolean mSkipAnimations;
    private final int mDisplayId;
    private final Display mDisplay;
    private final WallpaperColors mWallpaperColors;
    private final RunnableList mLifeCycleTracker;
    private final SurfaceControlViewHost mSurfaceControlViewHost;

    private LauncherPreviewRenderer mCurrentRenderer;

    @Nullable private SurfaceControlViewHost.SurfacePackage mSurfacePackage;

    public PreviewSurfaceRenderer(Context context, RunnableList lifecycleTracker, Bundle bundle,
            int callingPid, boolean skipAnimations) throws Exception {
        mContext = context;
        mLifeCycleTracker = lifecycleTracker;
        mCallingPid = callingPid;
        mGridName = bundle.getString("name");
        bundle.remove("name");
        if (mGridName == null) {
            mGridName = LauncherPrefs.get(context).get(GRID_NAME);
        }
        if (Objects.equals(mGridName, FIXED_LANDSCAPE_GRID)) {
            mGridName = LauncherPrefs.get(context).get(NON_FIXED_LANDSCAPE_GRID_NAME);
        }
        mShapeKey = LauncherPrefs.get(context).get(PREF_ICON_SHAPE);
        mIsMonoThemeEnabled = LauncherPrefs.get(context).get(THEMED_ICONS);
        mWallpaperColors = bundle.getParcelable(KEY_COLORS);
        if (Flags.newCustomizationPickerUi()) {
            updateColorOverrides(bundle);
        }
        mHideQsb = bundle.getBoolean(GridCustomizationsProxy.KEY_HIDE_BOTTOM_ROW);

        mHostToken = bundle.getBinder(KEY_HOST_TOKEN);
        mWidth = bundle.getInt(KEY_VIEW_WIDTH);
        mHeight = bundle.getInt(KEY_VIEW_HEIGHT);
        mSkipAnimations = skipAnimations;
        mDisplayId = bundle.getInt(KEY_DISPLAY_ID);
        mDisplay = context.getSystemService(DisplayManager.class)
                .getDisplay(mDisplayId);
        mLayoutXml = bundle.getString(KEY_LAYOUT_XML);
        mWorkspacePageId = bundle.getInt(KEY_WORKSPACE_PAGE_ID, FIRST_SCREEN_ID);
        if (mDisplay == null) {
            throw new IllegalArgumentException("Display ID does not match any displays.");
        }

        mSurfaceControlViewHost = MAIN_EXECUTOR.submit(() -> new MySurfaceControlViewHost(
                        mContext,
                        context.getSystemService(DisplayManager.class).getDisplay(DEFAULT_DISPLAY),
                        mHostToken,
                        mLifeCycleTracker))
                .get(5, TimeUnit.SECONDS);
        mLifeCycleTracker.add(this::destroy);
    }

    public int getDisplayId() {
        return mDisplayId;
    }

    public IBinder getHostToken() {
        return mHostToken;
    }

    public SurfaceControlViewHost getHost() {
        return mSurfaceControlViewHost;
    }

    private void setCurrentRenderer(LauncherPreviewRenderer renderer) {
        if (mCurrentRenderer != null) {
            mCurrentRenderer.onViewDestroyed();
        }
        mCurrentRenderer = renderer;
    }

    private void destroy() {
        mDestroyed = true;
        if (mSurfacePackage != null) {
            mSurfacePackage.release();
            mSurfacePackage = null;
        }
        mSurfaceControlViewHost.release();
        setCurrentRenderer(null);
    }

    /**
     * Generates the preview in background and returns the generated view
     */
    public CompletableFuture<View> loadAsync() {
        CompletableFuture<View> result = new CompletableFuture<>();
        MODEL_EXECUTOR.execute(() -> loadModelData(result));
        return result;
    }

    /**
     * Update the grid of the launcher preview
     *
     * @param gridNameOrNull Name of the grid (e.g. normal, practical), or null to reset preview to
     *                 current settings
     */
    public void updateGrid(@Nullable String gridNameOrNull) {
        String gridName;
        if (TextUtils.isEmpty(gridNameOrNull)) {
            gridName = LauncherPrefs.get(mContext).get(GRID_NAME);
        } else {
            gridName = gridNameOrNull;
        }
        if (gridName.equals(mGridName) || gridName.equals(FIXED_LANDSCAPE_GRID)) {
            return;
        }
        mGridName = gridName;
        loadAsync();
    }

    /**
     * Update the shapes of the launcher preview
     *
     * @param shapeKey key for the IconShape model
     */
    public void updateShape(String shapeKey) {
        if (shapeKey.equals(mShapeKey)) {
            Log.w(TAG, "Preview shape already set, skipping. shape=" + mShapeKey);
            return;
        }
        mShapeKey = shapeKey;
        loadAsync();
    }

    /**
     * Update whether to enable monochrome themed icon
     *
     * @param isMonoThemeEnabled True if enabling mono themed icons
     */
    public void updateTheme(boolean isMonoThemeEnabled) {
        if (mIsMonoThemeEnabled == isMonoThemeEnabled) {
            return;
        }
        mIsMonoThemeEnabled = isMonoThemeEnabled;
        loadAsync();
    }

    /**
     * Hides the components in the bottom row.
     *
     * @param hide True to hide and false to show.
     */
    public void hideBottomRow(boolean hide) {
        mHideQsb = hide;
        loadAsync();
    }

    /**
     * Updates the colors of the preview.
     *
     * @param bundle Bundle with an int array of color ids and an int array of overriding colors.
     */
    public void previewColor(Bundle bundle) {
        updateColorOverrides(bundle);
        loadAsync();
    }

    private void updateColorOverrides(Bundle bundle) {
        mDarkMode =
                bundle.containsKey(KEY_DARK_MODE) ? bundle.getBoolean(KEY_DARK_MODE) : null;
        int[] ids = bundle.getIntArray(KEY_COLOR_RESOURCE_IDS);
        int[] colors = bundle.getIntArray(KEY_COLOR_VALUES);
        if (ids != null && colors != null) {
            mPreviewColorOverride = new SparseIntArray();
            for (int i = 0; i < ids.length; i++) {
                mPreviewColorOverride.put(ids[i], colors[i]);
            }
        } else {
            mPreviewColorOverride = null;
        }
    }

    /***
     * Generates a new context overriding the theme color and the display size without affecting the
     * main application context
     */
    private Context getPreviewContext() {
        Context context = mContext.createDisplayContext(mDisplay);
        if (mDarkMode != null) {
            Configuration configuration = new Configuration(
                    context.getResources().getConfiguration());
            if (mDarkMode) {
                configuration.uiMode &= ~UI_MODE_NIGHT_NO;
                configuration.uiMode |= UI_MODE_NIGHT_YES;
            } else {
                configuration.uiMode &= ~UI_MODE_NIGHT_YES;
                configuration.uiMode |= UI_MODE_NIGHT_NO;
            }
            context = context.createConfigurationContext(configuration);
        }

        if (Flags.newCustomizationPickerUi()) {
            if (mPreviewColorOverride != null) {
                LocalColorExtractor.newInstance(context)
                        .applyColorsOverride(context, mPreviewColorOverride);
            } else if (mWallpaperColors != null) {
                LocalColorExtractor.newInstance(context)
                        .applyColorsOverride(context, mWallpaperColors);
            }
            if (mWallpaperColors != null) {
                return new ContextThemeWrapper(context,
                        Themes.getActivityThemeRes(context, mWallpaperColors.getColorHints()));
            } else {
                return new ContextThemeWrapper(context,
                        Themes.getActivityThemeRes(context));
            }
        } else {
            if (mWallpaperColors == null) {
                return new ContextThemeWrapper(context,
                        Themes.getActivityThemeRes(context));
            }
            LocalColorExtractor.newInstance(context)
                    .applyColorsOverride(context, mWallpaperColors);
            return new ContextThemeWrapper(context,
                    Themes.getActivityThemeRes(context, mWallpaperColors.getColorHints()));
        }
    }

    @WorkerThread
    private void loadModelData(CompletableFuture<View> onCompleteCallback) {
        final Context inflationContext = getPreviewContext();
        boolean isCustomLayout = extendibleThemeManager() && !TextUtils.isEmpty(mLayoutXml);
        int widgetHostId = isCustomLayout ? APPWIDGET_HOST_ID + mCallingPid : APPWIDGET_HOST_ID;

        // Start the migration
        PreviewContext previewContext = new PreviewContext(
                inflationContext, mGridName, mShapeKey, mIsMonoThemeEnabled,
                widgetHostId, mLayoutXml);
        PreviewAppComponent appComponent =
                (PreviewAppComponent) LauncherComponentProvider.get(previewContext);

        if (extendibleThemeManager() && isCustomLayout && !mDeletingHostOnExit) {
            mDeletingHostOnExit = true;
            mLifeCycleTracker.add(() -> {
                AppWidgetHost host = new AppWidgetHost(mContext, widgetHostId);
                // Start listening here, so that any previous active host is disabled
                host.startListening();
                host.stopListening();
                host.deleteHost();
            });
        }

        LoaderTask task = appComponent.getLoaderTaskFactory().newLoaderTask(
                appComponent.getBaseLauncherBinderFactory().createBinder(new Callbacks[0]),
                new UserManagerState());

        InvariantDeviceProfile idp = appComponent.getIDP();
        DeviceProfile deviceProfile = idp.getDeviceProfile(previewContext);

        int closestEvenPageId = mWorkspacePageId - (mWorkspacePageId % 2);
        String query = deviceProfile.getDeviceProperties().isTwoPanels()
                ? selectionForWorkspaceScreen(closestEvenPageId, closestEvenPageId + 1)
                : selectionForWorkspaceScreen(mWorkspacePageId);

        task.loadWorkspaceForPreview(query);

        MAIN_EXECUTOR.execute(() -> {
            renderView(previewContext, appComponent.getDataModel(), idp, onCompleteCallback);
            mLifeCycleTracker.add(previewContext::onDestroy);
        });
    }

    @UiThread
    private void renderView(Context inflationContext, BgDataModel dataModel,
            InvariantDeviceProfile idp, CompletableFuture<View> onCompleteCallback) {
        if (mDestroyed) {
            onCompleteCallback.completeExceptionally(new RuntimeException("Renderer destroyed"));
            return;
        }
        LauncherPreviewRenderer renderer;
        if (Flags.newCustomizationPickerUi()) {
            renderer = new LauncherPreviewRenderer(inflationContext, idp, mPreviewColorOverride,
                    mWallpaperColors, mWorkspacePageId);
        } else {
            renderer = new LauncherPreviewRenderer(inflationContext, idp,
                    mWallpaperColors, mWorkspacePageId);
        }
        renderer.hideBottomRow(mHideQsb);
        renderer.populate(dataModel);

        View view = renderer.getRootView();
        setCurrentRenderer(renderer);

        view.setPivotX(0);
        view.setPivotY(0);
        // This aspect scales the view to fit in the surface and centers it
        final float scale = Math.min(mWidth / (float) view.getMeasuredWidth(),
                mHeight / (float) view.getMeasuredHeight());
        view.setScaleX(scale);
        view.setScaleY(scale);
        view.setTranslationX((mWidth - scale * view.getWidth()) / 2);
        view.setTranslationY((mHeight - scale * view.getHeight()) / 2);

        if (!Flags.newCustomizationPickerUi()) {
            view.setAlpha(mSkipAnimations ? 1 : 0);
            view.animate().alpha(1)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .setDuration(FADE_IN_ANIMATION_DURATION)
                    .start();
            mSurfaceControlViewHost.setView(
                    view,
                    view.getMeasuredWidth(),
                    view.getMeasuredHeight()
            );
            onCompleteCallback.complete(view);
            return;
        }

        if (mViewRoot == null) {
            mViewRoot = new FrameLayout(inflationContext);
            FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, // Width
                    FrameLayout.LayoutParams.WRAP_CONTENT  // Height
            );
            mViewRoot.setLayoutParams(layoutParams);
            mViewRoot.addView(view);
            mViewRoot.setAlpha(mSkipAnimations ? 1 : 0);
            mViewRoot.animate().alpha(1)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .setDuration(FADE_IN_ANIMATION_DURATION)
                    .start();
            mSurfaceControlViewHost.setView(
                    mViewRoot,
                    view.getMeasuredWidth(),
                    view.getMeasuredHeight()
            );
        } else  {
            mViewRoot.removeAllViews();
            mViewRoot.addView(view);
        }
        onCompleteCallback.complete(view);
    }

    private static class MySurfaceControlViewHost extends SurfaceControlViewHost {

        private final RunnableList mLifecycleTracker;

        MySurfaceControlViewHost(Context context, Display display, IBinder hostToken,
                RunnableList lifeCycleTracker) {
            super(context, display, hostToken);
            mLifecycleTracker = lifeCycleTracker;
            mLifecycleTracker.add(this::release);
        }

        @Override
        public void release() {
            super.release();
            // RunnableList ensures that the callback is only called once
            MAIN_EXECUTOR.execute(mLifecycleTracker::executeAllAndDestroy);
        }
    }
}
