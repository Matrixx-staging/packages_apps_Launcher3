/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.graphics

import android.content.Context
import android.content.res.Resources
import com.android.launcher3.EncryptionType
import com.android.launcher3.Item
import com.android.launcher3.LauncherPrefChangeListener
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherPrefs.Companion.backedUpItem
import com.android.launcher3.concurrent.annotations.Ui
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.graphics.ShapeDelegate.Companion.DEFAULT_PATH_SIZE_INT
import com.android.launcher3.graphics.ShapeDelegate.Companion.pickBestShape
import com.android.launcher3.icons.DotRenderer.IconShapeInfo
import com.android.launcher3.icons.GraphicsUtils.generateIconShape
import com.android.launcher3.icons.IconShape
import com.android.launcher3.icons.IconThemeController
import com.android.launcher3.icons.mono.MonoIconThemeController
import com.android.launcher3.shapes.IconShapeModel.Companion.DEFAULT_ICON_RADIUS
import com.android.launcher3.shapes.ShapesProvider
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.ListenableRef
import com.android.launcher3.util.LooperExecutor
import com.android.launcher3.util.MutableListenableRef
import com.android.launcher3.util.SimpleBroadcastReceiver
import com.android.launcher3.util.SimpleBroadcastReceiver.Companion.packageFilter
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject

/** Centralized class for managing Launcher icon theming */
@LauncherAppSingleton
class ThemeManager
@Inject
constructor(
    @ApplicationContext private val context: Context,
    @Ui private val uiExecutor: LooperExecutor,
    private val prefs: LauncherPrefs,
    private val iconControllerFactory: IconControllerFactory,
    lifecycle: DaggerSingletonTracker,
) {

    private val _iconShapeData = MutableListenableRef(IconShape.EMPTY)

    /** listenable value holder for current IconShape */
    val iconShapeData: ListenableRef<IconShape> = _iconShapeData.asListenable()
    /** Representation of the current icon state */
    var iconState = parseIconState(null)
        private set

    var isMonoThemeEnabled
        set(value) = prefs.put(THEMED_ICONS, value)
        get() = prefs.get(THEMED_ICONS)

    val themeController
        get() = iconState.themeController

    val isIconThemeEnabled
        get() = themeController != null

    val iconShape
        get() = iconState.iconShape

    val folderShape
        get() = iconState.folderShape

    private val listeners = CopyOnWriteArrayList<ThemeChangeListener>()

    init {
        val receiver = SimpleBroadcastReceiver(context, uiExecutor) { verifyIconState() }
        receiver.register(packageFilter("android", ACTION_OVERLAY_CHANGED))
        lifecycle.addCloseable(receiver)

        val keys = (iconControllerFactory.prefKeys + PREF_ICON_SHAPE)

        val keysArray = keys.toTypedArray()
        val prefKeySet = keys.map { it.sharedPrefKey }
        val prefListener = LauncherPrefChangeListener { key ->
            if (prefKeySet.contains(key)) verifyIconState()
        }
        prefs.addListener(prefListener, *keysArray)
        lifecycle.addCloseable { prefs.removeListener(prefListener, *keysArray) }
    }

    private fun verifyIconState() {
        val newState = parseIconState(iconState)
        if (newState == iconState) return
        val hasThemedChanged = newState.themeCode != iconState.themeCode
        iconState = newState
        if (hasThemedChanged) {
            // trigger listeners only for theme change, not shape change
            listeners.forEach { it.onThemeChanged() }
        }
    }

    fun addChangeListener(listener: ThemeChangeListener) = listeners.add(listener)

    fun removeChangeListener(listener: ThemeChangeListener) = listeners.remove(listener)

    /**
     * Generates new IconShape based given [iconSize] and current [iconShape] Allocates new Bitmap
     * via [createIconShape]
     */
    fun generateIconShape(iconSize: Int) {
        if (iconShapeData.value.pathSize == iconSize) return
        _iconShapeData.dispatchValue(iconShape.createIconShape(iconSize))
    }

    private fun parseIconState(oldState: IconState?): IconState {
        val shapeModel =
            prefs.get(PREF_ICON_SHAPE).let { shapeOverride ->
                ShapesProvider.iconShapes.firstOrNull { it.key == shapeOverride }
            }
        val iconMask =
            when {
                shapeModel != null -> shapeModel.pathString
                CONFIG_ICON_MASK_RES_ID == Resources.ID_NULL -> ""
                else -> context.resources.getString(CONFIG_ICON_MASK_RES_ID)
            }

        val iconShape =
            if (oldState != null && oldState.iconMask == iconMask) {
                oldState.iconShape
            } else {
                pickBestShape(iconMask)
            }

        if (oldState?.iconMask != iconMask) {
            // Create only if shape changed.
            _iconShapeData.dispatchValue(iconShape.createIconShape(iconShapeData.value.pathSize))
        }

        val folderRadius = shapeModel?.folderRadiusRatio ?: 1f
        val folderShape =
            if (oldState != null && oldState.folderRadius == folderRadius) {
                oldState.folderShape
            } else if (folderRadius == 1f) {
                ShapeDelegate.Circle()
            } else {
                ShapeDelegate.RoundedSquare(folderRadius)
            }

        return IconState(
            iconMask = iconMask,
            folderRadius = folderRadius,
            themeController = iconControllerFactory.createThemeController(),
            iconShape = iconShape,
            folderShape = folderShape,
            shapeRadius = shapeModel?.shapeRadius ?: DEFAULT_ICON_RADIUS,
        )
    }

    data class IconState(
        val iconMask: String,
        val folderRadius: Float,
        val themeController: IconThemeController?,
        val themeCode: String = themeController?.themeID ?: "no-theme",
        val iconShape: ShapeDelegate,
        val folderShape: ShapeDelegate,
        val shapeRadius: Float,
    ) {
        fun toUniqueId() = "${iconMask.hashCode()},$themeCode"

        val iconShapeInfo = IconShapeInfo.fromPath(iconShape.getPath(), DEFAULT_PATH_SIZE_INT)
        val folderShapeInfo = IconShapeInfo.fromPath(folderShape.getPath(), DEFAULT_PATH_SIZE_INT)
    }

    /** Interface for receiving theme change events */
    fun interface ThemeChangeListener {
        fun onThemeChanged()
    }

    open class IconControllerFactory @Inject constructor(protected val prefs: LauncherPrefs) {

        open val prefKeys: List<Item> = listOf(THEMED_ICONS)

        open fun createThemeController(): IconThemeController? {
            return if (prefs.get(THEMED_ICONS)) MONO_THEME_CONTROLLER else null
        }
    }

    companion object {

        @JvmField val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getThemeManager)
        const val KEY_ICON_SHAPE = "icon_shape_model"

        const val KEY_THEMED_ICONS = "themed_icons"
        @JvmField val THEMED_ICONS = backedUpItem(KEY_THEMED_ICONS, false, EncryptionType.ENCRYPTED)
        @JvmField val PREF_ICON_SHAPE = backedUpItem(KEY_ICON_SHAPE, "", EncryptionType.ENCRYPTED)

        @JvmField val DEFAULT_SHAPE_DELEGATE = pickBestShape(shapeStr = "")

        private const val ACTION_OVERLAY_CHANGED = "android.intent.action.OVERLAY_CHANGED"
        private val CONFIG_ICON_MASK_RES_ID: Int =
            Resources.getSystem().getIdentifier("config_icon_mask", "string", "android")

        // Use a constant to allow equality check in verifyIconState
        private val MONO_THEME_CONTROLLER = MonoIconThemeController(shouldForceThemeIcon = true)

        private fun ShapeDelegate.createIconShape(size: Int) =
            generateIconShape(size, getPath(size.toFloat()))
    }
}
