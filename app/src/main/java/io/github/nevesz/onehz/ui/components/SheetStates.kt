@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.nevesz.onehz.ui.components

import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable

/** Full-height modal sheet: no partially expanded anchor. */
@Composable
fun rememberFullHeightSheetState(): SheetState = rememberBottomSheetState(
    initialValue = SheetValue.Hidden,
    enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
)

/** Modal sheet that can rest at the partially expanded anchor, matching the previous default. */
@Composable
fun rememberPartialSheetState(): SheetState = rememberBottomSheetState(
    initialValue = SheetValue.Hidden,
    enabledValues = setOf(SheetValue.Hidden, SheetValue.PartiallyExpanded, SheetValue.Expanded),
)
