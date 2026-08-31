package com.offcampus.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// The Phase 0 "ticket stub" motif: three normal corners, one corner given a much smaller
// radius so a card reads as a torn/clipped stub instead of a generic rounded rectangle.
// RoundedCornerShape already takes a separate radius per corner, so no custom Shape is needed.
val TicketCardShape = RoundedCornerShape(
    topStart = 20.dp,
    topEnd = 20.dp,
    bottomEnd = 20.dp,
    bottomStart = 4.dp
)

val OffCampusShapes = Shapes(
    small = RoundedCornerShape(10.dp),   // chips, badges
    medium = TicketCardShape,            // cards (lobby cards, the concept card, etc.)
    large = RoundedCornerShape(24.dp)    // bottom sheets, dialogs
)
