package com.msa.iotofflinetoolbox.ui.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class WindowWidthClass {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

enum class NavigationMode {
    DRAWER,
    RAIL,
    SIDEBAR,
}

data class ResponsiveLayoutInfo(
    val width: Dp,
    val height: Dp,
    val widthClass: WindowWidthClass,
    val navigationMode: NavigationMode,
    val isLandscape: Boolean,
    val isLowHeight: Boolean,
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val pageSpacing: Dp,
    val cardPadding: Dp,
    val cardSpacing: Dp,
    val cardCornerRadius: Dp,
    val maxContentWidth: Dp,
    val minimumFieldWidth: Dp,
) {
    val isCompactWidth: Boolean get() = widthClass == WindowWidthClass.COMPACT

    companion object {
        fun calculate(width: Dp, height: Dp): ResponsiveLayoutInfo {
            val widthClass = when {
                width < 600.dp -> WindowWidthClass.COMPACT
                width < 1_000.dp -> WindowWidthClass.MEDIUM
                else -> WindowWidthClass.EXPANDED
            }
            val isLandscape = width > height
            val isLowHeight = height < 520.dp
            val navigationMode = when {
                width >= 1_100.dp && height >= 620.dp -> NavigationMode.SIDEBAR
                width >= 600.dp -> NavigationMode.RAIL
                width >= 560.dp && isLandscape -> NavigationMode.RAIL
                else -> NavigationMode.DRAWER
            }
            val horizontalPadding = when {
                isLowHeight && widthClass == WindowWidthClass.COMPACT -> 12.dp
                widthClass == WindowWidthClass.COMPACT -> 16.dp
                widthClass == WindowWidthClass.MEDIUM -> 20.dp
                else -> 28.dp
            }
            val verticalPadding = when {
                isLowHeight -> 10.dp
                widthClass == WindowWidthClass.COMPACT -> 16.dp
                widthClass == WindowWidthClass.MEDIUM -> 20.dp
                else -> 24.dp
            }
            val pageSpacing = when {
                isLowHeight -> 12.dp
                widthClass == WindowWidthClass.COMPACT -> 14.dp
                else -> 18.dp
            }
            val cardPadding = when {
                isLowHeight -> 14.dp
                widthClass == WindowWidthClass.COMPACT -> 16.dp
                widthClass == WindowWidthClass.MEDIUM -> 18.dp
                else -> 20.dp
            }
            val cardSpacing = when {
                isLowHeight -> 10.dp
                widthClass == WindowWidthClass.COMPACT -> 12.dp
                else -> 14.dp
            }
            val cardCornerRadius = when {
                isLowHeight || widthClass == WindowWidthClass.COMPACT -> 16.dp
                widthClass == WindowWidthClass.MEDIUM -> 20.dp
                else -> 22.dp
            }
            val maxContentWidth = when (widthClass) {
                WindowWidthClass.COMPACT -> width
                WindowWidthClass.MEDIUM -> 1_000.dp
                WindowWidthClass.EXPANDED -> 1_440.dp
            }
            val minimumFieldWidth = when {
                isLowHeight -> 200.dp
                widthClass == WindowWidthClass.COMPACT -> 220.dp
                else -> 240.dp
            }
            return ResponsiveLayoutInfo(
                width = width,
                height = height,
                widthClass = widthClass,
                navigationMode = navigationMode,
                isLandscape = isLandscape,
                isLowHeight = isLowHeight,
                horizontalPadding = horizontalPadding,
                verticalPadding = verticalPadding,
                pageSpacing = pageSpacing,
                cardPadding = cardPadding,
                cardSpacing = cardSpacing,
                cardCornerRadius = cardCornerRadius,
                maxContentWidth = maxContentWidth,
                minimumFieldWidth = minimumFieldWidth,
            )
        }
    }
}

val LocalResponsiveLayout = staticCompositionLocalOf {
    ResponsiveLayoutInfo.calculate(width = 360.dp, height = 800.dp)
}

@Composable
fun responsiveLayout(): ResponsiveLayoutInfo = LocalResponsiveLayout.current
