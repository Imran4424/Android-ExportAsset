package com.luminous.exportasset

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/** Stroke stored in normalized coordinates (0..1) so it exports crisply at any size. */
data class StrokeData(
    val points: SnapshotStateList<Offset> = mutableStateListOf(),
    val widthFraction: Float,
    val color: Color
)