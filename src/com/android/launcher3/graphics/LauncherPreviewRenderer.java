/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.launcher3.graphics;

import static android.app.WallpaperManager.FLAG_SYSTEM;
import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.View.MeasureSpec.makeMeasureSpec;
import static android.view.View.VISIBLE;

import static com.android.launcher3.Flags.extendibleThemeManager;
import static com.android.launcher3.Hotseat.ALPHA_CHANNEL_PREVIEW_RENDERER;
import static com.android.launcher3.LauncherPrefs.FIXED_LANDSCAPE_MODE;
import static com.android.launcher3.LauncherPrefs.GRID_NAME;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION;
import static com.android.launcher3.graphics.ThemeManager.PREF_ICON_SHAPE;
import static com.android.launcher3.graphics.ThemeManager.THEMED_ICONS;
import static com.android.launcher3.model.ModelUtils.currentScreenContentFilter;
import static com.android.launcher3.widget.LauncherWidgetHolder.APPWIDGET_HOST_ID;

import static java.util.Comparator.comparingDouble;

import android.app.Fragment;
import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Size;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.ContextThemeWrapper;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.android.launcher3.BuildConfig;
import com.android.launcher3.CellLayout;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Hotseat;
import com.android.launcher3.InsettableFrameLayout;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.ProxyPrefs;
import com.android.launcher3.R;
import com.android.launcher3.WorkspaceLayoutManager;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.celllayout.CellPosMapper;
import com.android.launcher3.concurrent.ExecutorsModule;
import com.android.launcher3.dagger.ApiWrapperModule;
import com.android.launcher3.dagger.AppModule;
import com.android.launcher3.dagger.LauncherAppComponent;
import com.android.launcher3.dagger.LauncherAppSingleton;
import com.android.launcher3.dagger.LauncherComponentProvider;
import com.android.launcher3.dagger.LauncherConcurrencyModule;
import com.android.launcher3.dagger.PerDisplayModule;
import com.android.launcher3.dagger.PluginManagerWrapperModule;
import com.android.launcher3.dagger.StaticObjectModule;
import com.android.launcher3.dagger.WindowManagerProxyModule;
import com.android.launcher3.model.BaseLauncherBinder.BaseLauncherBinderFactory;
import com.android.launcher3.model.BgDataModel;
import com.android.launcher3.model.LayoutParserFactory;
import com.android.launcher3.model.LayoutParserFactory.XmlLayoutParserFactory;
import com.android.launcher3.model.LoaderTask.LoaderTaskFactory;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.util.BaseContext;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.ItemInflater;
import com.android.launcher3.util.SandboxContext;
import com.android.launcher3.util.Themes;
import com.android.launcher3.util.dagger.LauncherExecutorsModule;
import com.android.launcher3.util.window.WindowManagerProxy;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.BaseDragLayer;
import com.android.launcher3.widget.LauncherWidgetHolder;
import com.android.launcher3.widget.LauncherWidgetHolder.WidgetHolderFactory;
import com.android.launcher3.widget.LocalColorExtractor;
import com.android.systemui.shared.Flags;

import dagger.BindsInstance;
import dagger.Component;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Utility class for generating the preview of Launcher for a given InvariantDeviceProfile.
 * Steps:
 *   1) Create a dummy icon info with just white icon
 *   2) Inflate a strip down layout definition for Launcher
 *   3) Place appropriate elements like icons and first-page qsb
 *   4) Measure and draw the view on a canvas
 */
public class LauncherPreviewRenderer extends BaseContext
        implements ActivityContext, WorkspaceLayoutManager, LayoutInflater.Factory2 {

    /**
     * Context used just for preview. It also provides a few objects (e.g. UserCache) just for
     * preview purposes.
     */
    public static class PreviewContext extends SandboxContext {

        private final String mPrefName;

        private final File mDbDir;

        public PreviewContext(Context base, String gridName, String shapeKey,
                boolean isMonoThemeEnabled) {
            this(base, gridName, shapeKey, APPWIDGET_HOST_ID, null, isMonoThemeEnabled);
        }

        public PreviewContext(Context base, String gridName, String shapeKey,
                int widgetHostId, @Nullable String layoutXml, boolean isMonoThemeEnabled) {
            super(base);
            String randomUid = UUID.randomUUID().toString();
            mPrefName = "preview-" + randomUid;
            LauncherPrefs prefs =
                    new ProxyPrefs(this, getSharedPreferences(mPrefName, MODE_PRIVATE));
            prefs.put(GRID_NAME, gridName);
            prefs.put(PREF_ICON_SHAPE, shapeKey);
            prefs.put(FIXED_LANDSCAPE_MODE, false);
            prefs.put(THEMED_ICONS, isMonoThemeEnabled);

            PreviewAppComponent.Builder builder =
                    DaggerLauncherPreviewRenderer_PreviewAppComponent.builder().bindPrefs(prefs);
            if (TextUtils.isEmpty(layoutXml) || !extendibleThemeManager()) {
                mDbDir = null;
                builder.bindParserFactory(new LayoutParserFactory(this))
                        .bindWidgetsFactory(
                                LauncherComponentProvider.get(base).getWidgetHolderFactory());
            } else {
                mDbDir = new File(base.getFilesDir(), randomUid);
                emptyDbDir();
                mDbDir.mkdirs();
                builder.bindParserFactory(new XmlLayoutParserFactory(this, layoutXml))
                        .bindWidgetsFactory(c -> {
                            LauncherWidgetHolder holder = new LauncherWidgetHolder(c, widgetHostId);
                            holder.startListening();
                            return holder;
                        });
            }
            initDaggerComponent(builder);

            if (!TextUtils.isEmpty(layoutXml)) {
                // Use null the DB file so that we use a new in-memory DB
                InvariantDeviceProfile.INSTANCE.get(this).dbFile = null;
            }
        }

        private void emptyDbDir() {
            if (mDbDir != null && mDbDir.exists()) {
                Arrays.stream(mDbDir.listFiles()).forEach(File::delete);
            }
        }

        @Override
        protected void cleanUpObjects() {
            super.cleanUpObjects();
            deleteSharedPreferences(mPrefName);
            if (mDbDir != null) {
                emptyDbDir();
                mDbDir.delete();
            }
        }

        @Override
        public File getDatabasePath(String name) {
            return mDbDir != null ? new File(mDbDir, name) :  super.getDatabasePath(name);
        }
    }

    private final Handler mUiHandler;
    private final InvariantDeviceProfile mIdp;
    private final DeviceProfile mDp;
    private final DeviceProfile mDpOrig;
    private final Rect mInsets;
    private final LayoutInflater mHomeElementInflater;
    private final InsettableFrameLayout mRootView;
    private final Hotseat mHotseat;
    private final Map<Integer, CellLayout> mWorkspaceScreens = new HashMap<>();
    private final ItemInflater<LauncherPreviewRenderer> mItemInflater;
    private final SparseArray<Size> mLauncherWidgetSpanInfo;

    public LauncherPreviewRenderer(Context context,
            InvariantDeviceProfile idp,
            WallpaperColors wallpaperColorsOverride,
            int workspaceScreenId,
            @Nullable final SparseArray<Size> launcherWidgetSpanInfo) {
        this(context, idp, null, wallpaperColorsOverride, workspaceScreenId,
                launcherWidgetSpanInfo);
    }

    public LauncherPreviewRenderer(Context context,
            InvariantDeviceProfile idp,
            SparseIntArray previewColorOverride,
            WallpaperColors wallpaperColorsOverride,
            int workspaceScreenId,
            @Nullable final SparseArray<Size> launcherWidgetSpanInfo) {

        super(context, Themes.getActivityThemeRes(context));
        mUiHandler = new Handler(Looper.getMainLooper());
        mIdp = idp;
        mDp = getDeviceProfileForPreview(context).toBuilder(context).build();
        if (context instanceof PreviewContext) {
            Context tempContext = ((PreviewContext) context).getBaseContext();
            mDpOrig = InvariantDeviceProfile.INSTANCE.get(tempContext)
                    .getDeviceProfile(tempContext)
                    .copy(tempContext);
        } else {
            mDpOrig = mDp;
        }
        mInsets = getInsets(context);
        mDp.updateInsets(mInsets);

        mHomeElementInflater = LayoutInflater.from(
                new ContextThemeWrapper(this, R.style.HomeScreenElementTheme));
        mHomeElementInflater.setFactory2(this);

        int layoutRes = mDp.isTwoPanels ? R.layout.launcher_preview_two_panel_layout
                : R.layout.launcher_preview_layout;
        mRootView = (InsettableFrameLayout) mHomeElementInflater.inflate(
                layoutRes, null, false);
        mRootView.setInsets(mInsets);
        measureView(mRootView, mDp.widthPx, mDp.heightPx);

        mHotseat = mRootView.findViewById(R.id.hotseat);
        mHotseat.resetLayout(false);

        mLauncherWidgetSpanInfo = launcherWidgetSpanInfo == null ? new SparseArray<>() :
                launcherWidgetSpanInfo;

        CellLayout firstScreen = mRootView.findViewById(R.id.workspace);
        firstScreen.setPadding(
                mDp.workspacePadding.left + mDp.cellLayoutPaddingPx.left,
                mDp.workspacePadding.top + mDp.cellLayoutPaddingPx.top,
                mDp.isTwoPanels ? (mDp.cellLayoutBorderSpacePx.x / 2)
                        : (mDp.workspacePadding.right + mDp.cellLayoutPaddingPx.right),
                mDp.workspacePadding.bottom + mDp.cellLayoutPaddingPx.bottom
        );

        if (mDp.isTwoPanels) {
            CellLayout rightPanel = mRootView.findViewById(R.id.workspace_right);
            rightPanel.setPadding(
                    mDp.cellLayoutBorderSpacePx.x / 2,
                    mDp.workspacePadding.top + mDp.cellLayoutPaddingPx.top,
                    mDp.workspacePadding.right + mDp.cellLayoutPaddingPx.right,
                    mDp.workspacePadding.bottom + mDp.cellLayoutPaddingPx.bottom
            );

            int closestEvenPageId = workspaceScreenId - (workspaceScreenId % 2);
            mWorkspaceScreens.put(closestEvenPageId, firstScreen);
            mWorkspaceScreens.put(closestEvenPageId + 1, rightPanel);
        } else {
            mWorkspaceScreens.put(workspaceScreenId, firstScreen);
        }

        SparseIntArray wallpaperColorResources;
        if (Flags.newCustomizationPickerUi()) {
            if (previewColorOverride != null) {
                wallpaperColorResources = previewColorOverride;
            } else if (wallpaperColorsOverride != null) {
                wallpaperColorResources = LocalColorExtractor.newInstance(
                        context).generateColorsOverride(wallpaperColorsOverride);
            } else {
                WallpaperColors wallpaperColors = WallpaperManager.getInstance(
                        context).getWallpaperColors(FLAG_SYSTEM);
                wallpaperColorResources = wallpaperColors != null
                        ? LocalColorExtractor.newInstance(context).generateColorsOverride(
                        wallpaperColors)
                        : null;
            }
        } else {
            WallpaperColors wallpaperColors = wallpaperColorsOverride != null
                    ? wallpaperColorsOverride
                    : WallpaperManager.getInstance(context).getWallpaperColors(FLAG_SYSTEM);
            wallpaperColorResources = wallpaperColors != null
                    ? LocalColorExtractor.newInstance(context).generateColorsOverride(
                    wallpaperColors)
                    : null;
        }

        LauncherWidgetHolder widgetHolder = LauncherComponentProvider.get(this)
                .getWidgetHolderFactory().newInstance(this);
        if (wallpaperColorResources != null) {
            widgetHolder.setOnViewCreationCallback(
                    v -> v.setColorResources(wallpaperColorResources));
        }

        mItemInflater = new ItemInflater<>(
                this,
                widgetHolder,
                view -> { },
                (view, b) -> { },
                mHotseat
        );
        onViewCreated();
        getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onDestroy(@NonNull LifecycleOwner owner) {
                widgetHolder.destroy();
            }
        });
    }

    @Override
    public InsettableFrameLayout getRootView() {
        return mRootView;
    }

    /**
     * Returns the device profile based on resource configuration for previewing various display
     * sizes
     */
    private DeviceProfile getDeviceProfileForPreview(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        Configuration config = context.getResources().getConfiguration();

        return mIdp.getBestMatch(
                config.screenWidthDp * density,
                config.screenHeightDp * density,
                WindowManagerProxy.INSTANCE.get(context).getRotation(context)
        );
    }

    /**
     * Returns the insets of the screen closest to the display given by the context
     */
    private Rect getInsets(Context context) {
        Display display = context.getDisplay();
        return DisplayController.INSTANCE.get(context).getInfo().supportedBounds.stream()
                .filter(w -> w.rotationHint == display.getRotation())
                .min(comparingDouble(w ->
                        Math.pow(display.getWidth() - w.availableSize.x, 2)
                        + Math.pow(display.getHeight() - w.availableSize.y, 2)))
                .map(w -> new Rect(w.insets))
                .orElse(new Rect());
    }

    @Override
    public View onCreateView(View parent, String name, Context context, AttributeSet attrs) {
        if ("TextClock".equals(name)) {
            // Workaround for TextClock accessing handler for unregistering ticker.
            return new TextClock(context, attrs) {

                @Override
                public Handler getHandler() {
                    return mUiHandler;
                }
            };
        } else if (!"fragment".equals(name)) {
            return null;
        }

        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.PreviewFragment);
        FragmentWithPreview f = (FragmentWithPreview) Fragment.instantiate(
                context, ta.getString(R.styleable.PreviewFragment_android_name));
        f.enterPreviewMode(context);
        f.onInit(null);

        View view = f.onCreateView(LayoutInflater.from(context), (ViewGroup) parent, null);
        view.setId(ta.getInt(R.styleable.PreviewFragment_android_id, View.NO_ID));
        ta.recycle();
        return view;
    }

    @Override
    public View onCreateView(String name, Context context, AttributeSet attrs) {
        return onCreateView(null, name, context, attrs);
    }

    @Override
    public BaseDragLayer getDragLayer() {
        throw new UnsupportedOperationException();
    }

    @Override
    public DeviceProfile getDeviceProfile() {
        return mDp;
    }

    @Override
    public Hotseat getHotseat() {
        return mHotseat;
    }

    /**
     * Hides the components in the bottom row.
     *
     * @param hide True to hide and false to show.
     */
    public void hideBottomRow(boolean hide) {
        mUiHandler.post(() -> {
            if (mDp.isTaskbarPresent) {
                // hotseat icons on bottom
                mHotseat.setIconsAlpha(hide ? 0 : 1, ALPHA_CHANNEL_PREVIEW_RENDERER);
                if (mDp.isQsbInline) {
                    mHotseat.setQsbAlpha(hide ? 0 : 1, ALPHA_CHANNEL_PREVIEW_RENDERER);
                }
            } else {
                mHotseat.setQsbAlpha(hide ? 0 : 1, ALPHA_CHANNEL_PREVIEW_RENDERER);
            }
        });
    }

    @Override
    public CellLayout getScreenWithId(int screenId) {
        return mWorkspaceScreens.get(screenId);
    }

    @Override
    public CellPosMapper getCellPosMapper() {
        return CellPosMapper.DEFAULT;
    }

    private void dispatchVisibilityAggregated(View view, boolean isVisible) {
        // Similar to View.dispatchVisibilityAggregated implementation.
        final boolean thisVisible = view.getVisibility() == VISIBLE;
        if (thisVisible || !isVisible) {
            view.onVisibilityAggregated(isVisible);
        }

        if (view instanceof ViewGroup vg) {
            isVisible = thisVisible && isVisible;
            int count = vg.getChildCount();

            for (int i = 0; i < count; i++) {
                dispatchVisibilityAggregated(vg.getChildAt(i), isVisible);
            }
        }
    }

    /** Populate preview and render it. */
    public void populate(BgDataModel dataModel) {
        // Separate the items that are on the current screen, and the other remaining items.
        dataModel.itemsIdMap.stream()
                .filter(currentScreenContentFilter(IntSet.wrap(mWorkspaceScreens.keySet())))
                .forEach(this::inflateAndAdd);
        populateHotseatPredictions(dataModel);


        // Add first page QSB
        if (BuildConfig.QSB_ON_FIRST_SCREEN) {
            CellLayout firstScreen = mWorkspaceScreens.get(FIRST_SCREEN_ID);
            if (firstScreen != null) {
                View qsb = mHomeElementInflater.inflate(R.layout.qsb_preview, firstScreen, false);
                // TODO: set bgHandler on qsb when it is BaseTemplateCard, which requires API
                //  changes.
                CellLayoutLayoutParams lp = new CellLayoutLayoutParams(
                        0, 0, firstScreen.getCountX(), 1);
                lp.canReorder = false;
                firstScreen.addViewToCellLayout(qsb, 0, R.id.search_container_workspace, lp, true);
            }
        }

        measureView(mRootView, mDp.widthPx, mDp.heightPx);
        dispatchVisibilityAggregated(mRootView, true);
        measureView(mRootView, mDp.widthPx, mDp.heightPx);
        // Additional measure for views which use auto text size API
        measureView(mRootView, mDp.widthPx, mDp.heightPx);
    }

    private void populateHotseatPredictions(BgDataModel dataModel) {
        List<ItemInfo> predictions = dataModel.itemsIdMap
                .getPredictedContents(CONTAINER_HOTSEAT_PREDICTION);
        int predictionIndex = 0;
        for (int rank = 0; rank < mDp.numShownHotseatIcons; rank++) {
            if (predictions.size() <= predictionIndex) continue;

            int cellX = mHotseat.getCellXFromOrder(rank);
            int cellY = mHotseat.getCellYFromOrder(rank);
            if (mHotseat.isOccupied(cellX, cellY)) continue;

            WorkspaceItemInfo itemInfo =
                    new WorkspaceItemInfo((WorkspaceItemInfo) predictions.get(predictionIndex));
            predictionIndex++;
            itemInfo.rank = rank;
            itemInfo.cellX = cellX;
            itemInfo.cellY = cellY;
            itemInfo.screenId = rank;
            inflateAndAdd(itemInfo);
        }
    }

    private void inflateAndAdd(ItemInfo itemInfo) {
        View view = mItemInflater.inflateItem(itemInfo);
        if (view != null) {
            addInScreenFromBind(view, itemInfo);
        }
    }

    private static void measureView(View view, int width, int height) {
        view.measure(makeMeasureSpec(width, EXACTLY), makeMeasureSpec(height, EXACTLY));
        view.layout(0, 0, width, height);
    }

    /** Root layout for launcher preview that intercepts all touch events. */
    public static class LauncherPreviewLayout extends InsettableFrameLayout {
        public LauncherPreviewLayout(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            return true;
        }
    }

    @LauncherAppSingleton
    // Exclude widget module since we bind widget holder separately
    @Component(modules = {WindowManagerProxyModule.class,
            ApiWrapperModule.class,
            PluginManagerWrapperModule.class,
            StaticObjectModule.class,
            AppModule.class,
            PerDisplayModule.class,
            LauncherConcurrencyModule.class,
            ExecutorsModule.class,
            LauncherExecutorsModule.class,
    })
    public interface PreviewAppComponent extends LauncherAppComponent {

        LoaderTaskFactory getLoaderTaskFactory();
        BaseLauncherBinderFactory getBaseLauncherBinderFactory();
        BgDataModel getDataModel();

        /** Builder for NexusLauncherAppComponent. */
        @Component.Builder
        interface Builder extends LauncherAppComponent.Builder {
            @BindsInstance Builder bindPrefs(LauncherPrefs prefs);
            @BindsInstance Builder bindParserFactory(LayoutParserFactory parserFactory);
            @BindsInstance Builder bindWidgetsFactory(WidgetHolderFactory holderFactory);
            PreviewAppComponent build();
        }
    }
}
