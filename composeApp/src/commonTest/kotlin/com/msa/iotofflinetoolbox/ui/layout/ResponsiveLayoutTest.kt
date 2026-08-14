package com.msa.iotofflinetoolbox.ui.layout

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResponsiveLayoutTest {
    @Test
    fun compactPortraitUsesDrawer() {
        val layout = ResponsiveLayoutInfo.calculate(width = 390.dp, height = 844.dp)

        assertEquals(WindowWidthClass.COMPACT, layout.widthClass)
        assertEquals(NavigationMode.DRAWER, layout.navigationMode)
        assertFalse(layout.isLandscape)
        assertFalse(layout.isLowHeight)
    }

    @Test
    fun phoneLandscapeUsesCompactRailAndLowHeightDensity() {
        val layout = ResponsiveLayoutInfo.calculate(width = 844.dp, height = 390.dp)

        assertEquals(WindowWidthClass.MEDIUM, layout.widthClass)
        assertEquals(NavigationMode.RAIL, layout.navigationMode)
        assertTrue(layout.isLandscape)
        assertTrue(layout.isLowHeight)
        assertEquals(10.dp, layout.verticalPadding)
    }

    @Test
    fun tabletPortraitUsesNavigationRail() {
        val layout = ResponsiveLayoutInfo.calculate(width = 600.dp, height = 960.dp)

        assertEquals(WindowWidthClass.MEDIUM, layout.widthClass)
        assertEquals(NavigationMode.RAIL, layout.navigationMode)
        assertFalse(layout.isLandscape)
    }

    @Test
    fun largeWindowUsesSidebarOnlyWithEnoughHeight() {
        val normal = ResponsiveLayoutInfo.calculate(width = 1_440.dp, height = 900.dp)
        val short = ResponsiveLayoutInfo.calculate(width = 1_440.dp, height = 480.dp)

        assertEquals(NavigationMode.SIDEBAR, normal.navigationMode)
        assertEquals(NavigationMode.RAIL, short.navigationMode)
        assertTrue(short.isLowHeight)
    }


    @Test
    fun splitScreenAndTabletLandscapeUseRailWithoutAssumingDeviceType() {
        val splitScreen = ResponsiveLayoutInfo.calculate(width = 600.dp, height = 400.dp)
        val tabletLandscape = ResponsiveLayoutInfo.calculate(width = 1_024.dp, height = 768.dp)

        assertEquals(NavigationMode.RAIL, splitScreen.navigationMode)
        assertTrue(splitScreen.isLowHeight)
        assertEquals(WindowWidthClass.EXPANDED, tabletLandscape.widthClass)
        assertEquals(NavigationMode.RAIL, tabletLandscape.navigationMode)
    }

    @Test
    fun sidebarBoundaryRequiresBothWidthAndHeight() {
        val exact = ResponsiveLayoutInfo.calculate(width = 1_100.dp, height = 620.dp)
        val narrow = ResponsiveLayoutInfo.calculate(width = 1_099.dp, height = 900.dp)
        val short = ResponsiveLayoutInfo.calculate(width = 1_440.dp, height = 619.dp)

        assertEquals(NavigationMode.SIDEBAR, exact.navigationMode)
        assertEquals(NavigationMode.RAIL, narrow.navigationMode)
        assertEquals(NavigationMode.RAIL, short.navigationMode)
    }

    @Test
    fun compactLandscapeRailStartsAtExplicitMinimumWidth() {
        val below = ResponsiveLayoutInfo.calculate(width = 559.dp, height = 360.dp)
        val exact = ResponsiveLayoutInfo.calculate(width = 560.dp, height = 360.dp)

        assertEquals(NavigationMode.DRAWER, below.navigationMode)
        assertEquals(NavigationMode.RAIL, exact.navigationMode)
    }

    @Test
    fun contentWidthIsBoundedOnLargeDisplays() {
        val layout = ResponsiveLayoutInfo.calculate(width = 2_560.dp, height = 1_440.dp)

        assertEquals(1_440.dp, layout.maxContentWidth)
        assertEquals(WindowWidthClass.EXPANDED, layout.widthClass)
    }
}
