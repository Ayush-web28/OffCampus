package com.offcampus.app.ui.lobby

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * This app has no geocoding and no live GPS (both explicitly out of scope), so pickup is left to
 * each platform's own current-location detection once it opens, and drop-off is passed as a text
 * hint rather than a map pin — that's what "prefilled where the platform's URL scheme supports
 * it" means in practice: we send what we honestly have (a destination name), not fabricated coordinates.
 */
sealed interface RideBookingLink {
    val label: String

    /** Uber's universal link is a real https URL — if the app isn't installed, Android opens it
     * in the browser to Uber's own web fallback, so no extra handling is needed here. */
    data class WebFallback(override val label: String, val uri: Uri) : RideBookingLink

    /** A bare app-scheme link with no web equivalent — needs an explicit Play Store fallback. */
    data class AppOnly(override val label: String, val uri: Uri, val playStorePackage: String) : RideBookingLink
}

fun rideBookingLinks(destination: String): List<RideBookingLink> {
    val encodedDestination = Uri.encode(destination)
    return listOf(
        // Documented at developer.uber.com/docs/riders/ride-requests/tutorials/deep-links —
        // pickup=my_location is the supported way to say "use wherever the phone is right now";
        // dropoff[formatted_address] is a best-effort text hint (no lat/lng, since we don't geocode).
        RideBookingLink.WebFallback(
            label = "Uber",
            uri = Uri.parse(
                "https://m.uber.com/ul/?action=setPickup&pickup=my_location" +
                    "&dropoff[formatted_address]=$encodedDestination&dropoff[nickname]=$encodedDestination"
            )
        ),
        // Ola and Rapido don't publish a stable, documented deep-link schema for prefilling a
        // trip the way Uber does, so these just open the app itself rather than guessing at
        // undocumented query parameters that could easily be wrong.
        RideBookingLink.AppOnly(
            label = "Ola",
            uri = Uri.parse("olacabs://app/launch"),
            playStorePackage = "com.olacabs.customer"
        ),
        RideBookingLink.AppOnly(
            label = "Rapido",
            uri = Uri.parse("rapido://"),
            playStorePackage = "com.rapido.passenger"
        )
    )
}

fun openRideBookingLink(context: Context, link: RideBookingLink) {
    when (link) {
        is RideBookingLink.WebFallback -> context.startActivity(Intent(Intent.ACTION_VIEW, link.uri))
        is RideBookingLink.AppOnly -> {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, link.uri))
            } catch (e: ActivityNotFoundException) {
                openPlayStoreListing(context, link.playStorePackage)
            }
        }
    }
}

private fun openPlayStoreListing(context: Context, packageName: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
    } catch (e: ActivityNotFoundException) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
        )
    }
}
