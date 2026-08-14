#!/usr/bin/env bash
set -euo pipefail

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
WORK="${TMPDIR:-/tmp}/msa-iot-responsive-harness"
rm -rf "$WORK"
mkdir -p "$WORK/androidx/compose/runtime" "$WORK/androidx/compose/ui/unit" "$WORK/com/msa/iotofflinetoolbox/ui/layout"

cp "$ROOT/composeApp/src/commonMain/kotlin/com/msa/iotofflinetoolbox/ui/layout/ResponsiveLayout.kt" \
  "$WORK/com/msa/iotofflinetoolbox/ui/layout/"

cat > "$WORK/androidx/compose/runtime/RuntimeStubs.kt" <<'KOTLIN'
package androidx.compose.runtime

@Target(AnnotationTarget.FUNCTION)
annotation class Composable

class ProvidableCompositionLocal<T>(private val defaultFactory: () -> T) {
    val current: T get() = defaultFactory()
}

fun <T> staticCompositionLocalOf(defaultFactory: () -> T): ProvidableCompositionLocal<T> =
    ProvidableCompositionLocal(defaultFactory)
KOTLIN

cat > "$WORK/androidx/compose/ui/unit/DpStubs.kt" <<'KOTLIN'
package androidx.compose.ui.unit

data class Dp(val value: Float) : Comparable<Dp> {
    override fun compareTo(other: Dp): Int = value.compareTo(other.value)
}

val Int.dp: Dp get() = Dp(toFloat())
KOTLIN

cat > "$WORK/Main.kt" <<'KOTLIN'
package com.msa.iotofflinetoolbox.ui.layout

import androidx.compose.ui.unit.dp

private fun verify(condition: Boolean, message: String) = check(condition) { message }

fun main() {
    val portrait = ResponsiveLayoutInfo.calculate(390.dp, 844.dp)
    verify(portrait.navigationMode == NavigationMode.DRAWER, "mobile portrait drawer")
    verify(!portrait.isLowHeight, "mobile portrait height")

    val landscape = ResponsiveLayoutInfo.calculate(844.dp, 390.dp)
    verify(landscape.navigationMode == NavigationMode.RAIL, "phone landscape rail")
    verify(landscape.isLowHeight, "phone landscape low-height")

    val drawerBoundary = ResponsiveLayoutInfo.calculate(559.dp, 360.dp)
    val railBoundary = ResponsiveLayoutInfo.calculate(560.dp, 360.dp)
    verify(drawerBoundary.navigationMode == NavigationMode.DRAWER, "drawer boundary")
    verify(railBoundary.navigationMode == NavigationMode.RAIL, "rail boundary")

    val tablet = ResponsiveLayoutInfo.calculate(600.dp, 960.dp)
    verify(tablet.widthClass == WindowWidthClass.MEDIUM, "tablet class")
    verify(tablet.navigationMode == NavigationMode.RAIL, "tablet rail")

    val sidebar = ResponsiveLayoutInfo.calculate(1_100.dp, 620.dp)
    val shortDesktop = ResponsiveLayoutInfo.calculate(1_440.dp, 480.dp)
    verify(sidebar.navigationMode == NavigationMode.SIDEBAR, "sidebar boundary")
    verify(shortDesktop.navigationMode == NavigationMode.RAIL, "short desktop rail")

    val ultraWide = ResponsiveLayoutInfo.calculate(2_560.dp, 1_440.dp)
    verify(ultraWide.maxContentWidth == 1_440.dp, "ultra-wide content bound")

    println("RESPONSIVE_LAYOUT_RUNTIME_HARNESS_PASSED")
}
KOTLIN

mapfile -t SOURCES < <(find "$WORK" -name '*.kt' -print | sort)
kotlinc "${SOURCES[@]}" -include-runtime -d "$WORK/responsive-harness.jar"
java -jar "$WORK/responsive-harness.jar"
