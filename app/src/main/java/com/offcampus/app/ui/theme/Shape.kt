package com.offcampus.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Lloyds Bank & iOS Inset Grouped card architecture:
// Balanced, symmetrical 14dp corner radiuses paired with hairline dividers,
// replacing asymmetrical ticket stubs for a calm, institutional mobile banking aesthetic.
val TicketCardShape = RoundedCornerShape(14.dp)

val OffCampusShapes = Shapes(
    small = RoundedCornerShape(8.dp),    // chips, badges, pill tags
    medium = RoundedCornerShape(14.dp),  // inset grouped cards (lobby cards, detail tiles)
    large = RoundedCornerShape(20.dp)    // bottom sheets, dialogs
)
