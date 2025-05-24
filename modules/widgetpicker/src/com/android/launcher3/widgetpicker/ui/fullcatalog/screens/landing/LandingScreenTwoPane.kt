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

package com.android.launcher3.widgetpicker.ui.fullcatalog.screens.landing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Work
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.launcher3.widgetpicker.R
import com.android.launcher3.widgetpicker.shared.model.WidgetAppId
import com.android.launcher3.widgetpicker.shared.model.WidgetUserProfile
import com.android.launcher3.widgetpicker.ui.components.AppHeaderDescriptionStyle
import com.android.launcher3.widgetpicker.ui.components.LeadingIconToolbarTab
import com.android.launcher3.widgetpicker.ui.components.ScrollableFloatingToolbar
import com.android.launcher3.widgetpicker.ui.components.SelectableSuggestionsHeader
import com.android.launcher3.widgetpicker.ui.components.TwoPaneLayout
import com.android.launcher3.widgetpicker.ui.components.WidgetAppHeaderStyle
import com.android.launcher3.widgetpicker.ui.components.WidgetAppsList
import com.android.launcher3.widgetpicker.ui.components.WidgetsGrid
import com.android.launcher3.widgetpicker.ui.fullcatalog.screens.landing.LandingScreenTwoPaneDimens.DEFAULT_SELECTED_TAB
import com.android.launcher3.widgetpicker.ui.fullcatalog.screens.landing.LandingScreenTwoPaneDimens.PERSONAL_TAB_INDEX
import com.android.launcher3.widgetpicker.ui.fullcatalog.screens.landing.LandingScreenTwoPaneDimens.TABS_COUNT_WITHOUT_WORK_PROFILE
import com.android.launcher3.widgetpicker.ui.fullcatalog.screens.landing.LandingScreenTwoPaneDimens.TABS_COUNT_WITH_WORK_PROFILE
import com.android.launcher3.widgetpicker.ui.fullcatalog.screens.landing.LandingScreenTwoPaneDimens.WORK_TAB_INDEX
import com.android.launcher3.widgetpicker.ui.fullcatalog.screens.landing.LandingScreenTwoPaneDimens.contentShape
import com.android.launcher3.widgetpicker.ui.fullcatalog.screens.landing.LandingScreenTwoPaneDimens.leftPaneContentBottomPadding
import com.android.launcher3.widgetpicker.ui.fullcatalog.screens.landing.LandingScreenTwoPaneDimens.pagerItemsSpacing
import kotlinx.coroutines.launch


/**
 * A composable function that provides a two pane layout for landing screen of the full catalog
 * of widgets in the widget picker.
 *
 * Ideal for large screens.
 */
@Composable
fun LandingScreenTwoPane(
    searchBar: @Composable () -> Unit,
    featuredWidgets: @Composable () -> Unit,
    featuredWidgetsCount: Int,
    widgetAppIconsState: AppIconsState,
    browseWidgetsState: BrowseWidgetsState.Data,
    personalWidgetPreviewsState: PreviewsState,
    workWidgetPreviewsState: PreviewsState,
    selectedPersonalWidgetAppId: WidgetAppId?,
    onPersonalWidgetAppToggle: (WidgetAppId?) -> Unit,
    selectedWorkWidgetAppId: WidgetAppId?,
    onWorkWidgetAppToggle: (WidgetAppId?) -> Unit,
) {
    val hasWorkProfile = remember(browseWidgetsState) { browseWidgetsState.workProfile != null }
    var isFeaturedSectionShowing by rememberSaveable { mutableStateOf(true) }
    val pageCount = remember {
        if (hasWorkProfile) {
            TABS_COUNT_WITH_WORK_PROFILE
        } else {
            TABS_COUNT_WITHOUT_WORK_PROFILE
        }
    }

    val pagerState =
        rememberPagerState(
            initialPage = DEFAULT_SELECTED_TAB,
            pageCount = { pageCount }
        )

    Box(modifier = Modifier.fillMaxSize()) {
        TwoPaneLayout(
            searchBar = searchBar,
            leftContent = {
                LeftPaneContent(
                    isFeaturedSectionSelected = isFeaturedSectionShowing,
                    featuredWidgetsCount = featuredWidgetsCount,
                    pagerState = pagerState,
                    hasWorkProfile = hasWorkProfile,
                    browseWidgetsState = browseWidgetsState,
                    selectedPersonalWidgetAppId = selectedPersonalWidgetAppId,
                    widgetAppIconsState = widgetAppIconsState,
                    personalWidgetPreviewsState = personalWidgetPreviewsState,
                    selectedWorkWidgetAppId = selectedWorkWidgetAppId,
                    workWidgetPreviewsState = workWidgetPreviewsState,
                    onFeaturedHeaderClick = {
                        isFeaturedSectionShowing = true
                        onPersonalWidgetAppToggle(null)
                        onWorkWidgetAppToggle(null)
                    },
                    onPersonalWidgetAppToggle = { id ->
                        isFeaturedSectionShowing = false
                        onPersonalWidgetAppToggle(id)
                    },
                    onWorkWidgetAppToggle = { id ->
                        isFeaturedSectionShowing = false
                        onWorkWidgetAppToggle(id)
                    },
                )
            },
            rightPaneTitle = rightPaneTitle(
                showingFeaturedTab = isFeaturedSectionShowing,
                currentPageIndex = pagerState.currentPage,
                browseWidgetsState = browseWidgetsState,
                selectedPersonalWidgetAppId = selectedPersonalWidgetAppId,
                selectedWorkWidgetAppId = selectedWorkWidgetAppId
            ),
            rightContent = {
                RightPaneContent(
                    pagerState = pagerState,
                    isFeaturedSectionSelected = isFeaturedSectionShowing,
                    featuredWidgets = featuredWidgets,
                    browseWidgetsState = browseWidgetsState,
                    selectedPersonalWidgetAppId = selectedPersonalWidgetAppId,
                    personalWidgetPreviewsState = personalWidgetPreviewsState,
                    widgetAppIconsState = widgetAppIconsState,
                    selectedWorkWidgetAppId = selectedWorkWidgetAppId,
                    workWidgetPreviewsState = workWidgetPreviewsState,
                )
            }
        )
    }
}

/**
 * Title for the right pane that is updated when selected tab on left changes. When set, a talkback
 * user can use four finger swipe down to switch to right pane.
 */
@Composable
private fun rightPaneTitle(
    showingFeaturedTab: Boolean,
    currentPageIndex: Int,
    browseWidgetsState: BrowseWidgetsState.Data,
    selectedPersonalWidgetAppId: WidgetAppId?,
    selectedWorkWidgetAppId: WidgetAppId?,
): String? {
    val selectedAppName: CharSequence? =
        if (currentPageIndex == 0) {
            selectedPersonalWidgetAppId?.let { selectedId ->
                browseWidgetsState.personalWidgetApps.find { it.id == selectedId }?.title
            }
        } else {
            selectedWorkWidgetAppId?.let { selectedId ->
                browseWidgetsState.workWidgetApps.find { it.id == selectedId }?.title
            }
        }


    return if (showingFeaturedTab) {
        stringResource(
            R.string.widget_picker_right_pane_accessibility_label,
            stringResource(R.string.featured_widgets_tab_label)
        )
    } else if (selectedAppName != null) {
        stringResource(
            R.string.widget_picker_right_pane_accessibility_label,
            selectedAppName
        )
    } else {
        null
    }
}

@Composable
private fun RightPaneContent(
    pagerState: PagerState,
    isFeaturedSectionSelected: Boolean,
    featuredWidgets: @Composable () -> Unit,
    browseWidgetsState: BrowseWidgetsState.Data,
    selectedPersonalWidgetAppId: WidgetAppId?,
    personalWidgetPreviewsState: PreviewsState,
    widgetAppIconsState: AppIconsState,
    selectedWorkWidgetAppId: WidgetAppId?,
    workWidgetPreviewsState: PreviewsState
) {
    when {
        isFeaturedSectionSelected -> featuredWidgets()

        pagerState.currentPage == PERSONAL_TAB_INDEX -> {
            selectedPersonalWidgetAppId?.let {
                val selectedPersonalWidgets =
                    remember(selectedPersonalWidgetAppId, browseWidgetsState.personalWidgetApps) {
                        selectedPersonalWidgetAppId.let { selectedId ->
                            browseWidgetsState.personalWidgetApps.find {
                                it.id == selectedId
                            }?.widgetSizeGroups
                        } ?: listOf()
                    }

                WidgetsGrid(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentSize()
                        .verticalScroll(rememberScrollState()),
                    showAllWidgetDetails = true,
                    widgetSizeGroups = selectedPersonalWidgets,
                    previews = personalWidgetPreviewsState.previews,
                    appIcons = widgetAppIconsState.icons
                )
            }
        }

        pagerState.currentPage == WORK_TAB_INDEX -> {
            selectedWorkWidgetAppId?.let {
                val selectedWorkWidgets =
                    remember(selectedWorkWidgetAppId, browseWidgetsState.workWidgetApps) {
                        selectedWorkWidgetAppId.let { selectedId ->
                            browseWidgetsState.workWidgetApps.find {
                                it.id == selectedId
                            }?.widgetSizeGroups
                        } ?: listOf()
                    }

                WidgetsGrid(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentSize()
                        .verticalScroll(rememberScrollState()),
                    showAllWidgetDetails = true,
                    widgetSizeGroups = selectedWorkWidgets,
                    previews = workWidgetPreviewsState.previews,
                    appIcons = widgetAppIconsState.icons
                )
            }
        }
    }
}

@Composable
private fun LeftPaneContent(
    isFeaturedSectionSelected: Boolean,
    onFeaturedHeaderClick: () -> Unit,
    featuredWidgetsCount: Int,
    pagerState: PagerState,
    hasWorkProfile: Boolean,
    browseWidgetsState: BrowseWidgetsState.Data,
    selectedPersonalWidgetAppId: WidgetAppId?,
    widgetAppIconsState: AppIconsState,
    personalWidgetPreviewsState: PreviewsState,
    onPersonalWidgetAppToggle: (WidgetAppId) -> Unit,
    selectedWorkWidgetAppId: WidgetAppId?,
    workWidgetPreviewsState: PreviewsState,
    onWorkWidgetAppToggle: (WidgetAppId) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            SelectableSuggestionsHeader(
                selected = isFeaturedSectionSelected,
                onSelect = onFeaturedHeaderClick,
                count = featuredWidgetsCount,
                shape = contentShape,
                modifier = Modifier.fillMaxWidth()
            )
            HorizontalPager(
                state = pagerState,
                verticalAlignment = Alignment.Top,
                pageSpacing = pagerItemsSpacing,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        bottom = leftPaneContentBottomPadding(
                            hasFloatingToolbar = hasWorkProfile
                        )
                    )
                    .weight(1f),
            ) { index ->
                when (index) {
                    PERSONAL_TAB_INDEX -> {
                        PersonalSection(
                            browseWidgetsState = browseWidgetsState,
                            selectedPersonalWidgetAppId = selectedPersonalWidgetAppId,
                            widgetAppIconsState = widgetAppIconsState,
                            personalWidgetPreviewsState = personalWidgetPreviewsState,
                            onPersonalWidgetAppToggle = onPersonalWidgetAppToggle
                        )
                    }

                    WORK_TAB_INDEX -> if (hasWorkProfile) {
                        WorkSection(
                            browseWidgetsState = browseWidgetsState,
                            selectedWorkWidgetAppId = selectedWorkWidgetAppId,
                            widgetAppIconsState = widgetAppIconsState,
                            workWidgetPreviewsState = workWidgetPreviewsState,
                            onWorkWidgetAppToggle = onWorkWidgetAppToggle
                        )
                    }
                }
            }
        }
        browseWidgetsState.workProfile?.let { workProfile ->
            PersonalWorkToolbar(
                pagerState = pagerState,
                personalUserProfile = browseWidgetsState.personalProfile,
                workUserProfile = workProfile,
                modifier = Modifier
                    .align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun PersonalSection(
    browseWidgetsState: BrowseWidgetsState.Data,
    selectedPersonalWidgetAppId: WidgetAppId?,
    widgetAppIconsState: AppIconsState,
    personalWidgetPreviewsState: PreviewsState,
    onPersonalWidgetAppToggle: (WidgetAppId) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        WidgetAppsList(
            modifier = Modifier.fillMaxSize(),
            widgetApps = browseWidgetsState.personalWidgetApps,
            selectedWidgetAppId = selectedPersonalWidgetAppId,
            widgetAppHeaderStyle = WidgetAppHeaderStyle.CLICKABLE,
            headerDescriptionStyle = AppHeaderDescriptionStyle.WIDGETS_COUNT,
            appIcons = widgetAppIconsState.icons,
            widgetPreviews = personalWidgetPreviewsState.previews,
            onWidgetAppClick = { widgetApp ->
                onPersonalWidgetAppToggle(widgetApp.id)
            },
        )
    }
}

@Composable
private fun WorkSection(
    browseWidgetsState: BrowseWidgetsState.Data,
    selectedWorkWidgetAppId: WidgetAppId?,
    widgetAppIconsState: AppIconsState,
    workWidgetPreviewsState: PreviewsState,
    onWorkWidgetAppToggle: (WidgetAppId) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        WidgetAppsList(
            modifier = Modifier.fillMaxSize(),
            widgetApps = browseWidgetsState.workWidgetApps,
            selectedWidgetAppId = selectedWorkWidgetAppId,
            widgetAppHeaderStyle = WidgetAppHeaderStyle.CLICKABLE,
            headerDescriptionStyle = AppHeaderDescriptionStyle.WIDGETS_COUNT,
            appIcons = widgetAppIconsState.icons,
            widgetPreviews = workWidgetPreviewsState.previews,
            onWidgetAppClick = { widgetApp ->
                onWorkWidgetAppToggle(widgetApp.id)
            },
        )
    }
}

@Composable
private fun PersonalWorkToolbar(
    pagerState: PagerState,
    personalUserProfile: WidgetUserProfile,
    workUserProfile: WidgetUserProfile,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    val currentPage = pagerState.currentPage

    val tabs: List<@Composable () -> Unit> =
        remember(currentPage, personalUserProfile, workUserProfile) {
            buildList {
                add {
                    LeadingIconToolbarTab(
                        label = personalUserProfile.label,
                        leadingIcon = Icons.Filled.Person,
                        selected = currentPage == PERSONAL_TAB_INDEX,
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(PERSONAL_TAB_INDEX)
                            }
                        }
                    )
                }
                add {
                    LeadingIconToolbarTab(
                        label = workUserProfile.label,
                        leadingIcon = Icons.Outlined.Work,
                        selected = currentPage == WORK_TAB_INDEX,
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(WORK_TAB_INDEX)
                            }
                        },
                    )
                }
            }
        }

    ScrollableFloatingToolbar(
        modifier = modifier,
        selectedTabIndex = currentPage,
        tabs = tabs
    )
}

private object LandingScreenTwoPaneDimens {
    val contentShape = RoundedCornerShape(24.dp)
    val pagerItemsSpacing = 8.dp

    fun leftPaneContentBottomPadding(hasFloatingToolbar: Boolean) = if (hasFloatingToolbar) {
        56.dp
    } else 0.dp

    const val TABS_COUNT_WITH_WORK_PROFILE = 2
    const val TABS_COUNT_WITHOUT_WORK_PROFILE = 1

    const val PERSONAL_TAB_INDEX = 0
    const val WORK_TAB_INDEX = 1
    const val DEFAULT_SELECTED_TAB = PERSONAL_TAB_INDEX
}
