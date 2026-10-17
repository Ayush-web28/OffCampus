package com.offcampus.app.ui.lobby

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.offcampus.app.data.PlaceSearchService
import com.offcampus.app.data.model.Lobby
import com.offcampus.app.data.model.RideType

/**
 * Ride booking deep-link integration for Uber, Ola, and Rapido.
 * Pre-populates the start location (gate/checkpoint) and destination, preventing
 * ride apps from defaulting start location to the phone's current GPS position.
 */
sealed interface RideBookingLink {
    val label: String

    /** Uber's universal link is a real https URL — if the app isn't installed, Android opens it
     * in the browser to Uber's own web fallback, so no extra handling is needed here. */
    data class WebFallback(override val label: String, val uri: Uri) : RideBookingLink

    /** A bare app-scheme link with no web equivalent — needs an explicit Play Store fallback. */
    data class AppOnly(override val label: String, val uri: Uri, val playStorePackage: String) : RideBookingLink
}

/**
 * Resolves a human-readable pickup title from checkpoint and gate.
 */
fun resolvePickupName(checkpoint: String, gate: String): String {
    val cp = checkpoint.trim()
    val gt = gate.trim()
    return when {
        cp.isNotEmpty() && gt.isNotEmpty() && !cp.equals(gt, ignoreCase = true) -> "$cp, $gt"
        cp.isNotEmpty() -> cp
        gt.isNotEmpty() -> gt
        else -> "Main Campus, Vile Parle West"
    }
}

/**
 * Resolves coordinates for a place query from explicit lobby values or curated local presets.
 */
fun resolveCoordinates(
    query: String,
    explicitLat: Double?,
    explicitLng: Double?,
    defaultCampusFallback: Boolean = false
): Pair<Double?, Double?> {
    if (explicitLat != null && explicitLng != null) {
        return Pair(explicitLat, explicitLng)
    }
    val preset = PlaceSearchService.searchLocalPresets(query).firstOrNull()
    if (preset != null) {
        return Pair(preset.lat, preset.lng)
    }
    if (defaultCampusFallback) {
        return Pair(19.1076, 72.8263)
    }
    return Pair(null, null)
}

/**
 * Generates ride booking links from a Lobby model, setting both pickup (start location) and destination.
 */
fun rideBookingLinks(lobby: Lobby): List<RideBookingLink> {
    val pickupName = resolvePickupName(lobby.checkpoint, lobby.gate)
    val (pickupLat, pickupLng) = resolveCoordinates(
        query = pickupName,
        explicitLat = lobby.gateLat,
        explicitLng = lobby.gateLng,
        defaultCampusFallback = true
    )
    val (destLat, destLng) = resolveCoordinates(
        query = lobby.destination,
        explicitLat = lobby.destinationLat,
        explicitLng = lobby.destinationLng,
        defaultCampusFallback = false
    )
    return rideBookingLinks(
        pickup = pickupName,
        destination = lobby.destination,
        pickupLat = pickupLat,
        pickupLng = pickupLng,
        destinationLat = destLat,
        destinationLng = destLng,
        rideType = lobby.rideType
    )
}

/**
 * Full parameterized ride booking link generator for Uber, Ola, and Rapido.
 */
fun rideBookingLinks(
    pickup: String,
    destination: String,
    pickupLat: Double? = 19.1076,
    pickupLng: Double? = 72.8263,
    destinationLat: Double? = null,
    destinationLng: Double? = null,
    rideType: RideType = RideType.AUTO
): List<RideBookingLink> {
    val encodedPickup = Uri.encode(pickup)
    val encodedDestination = Uri.encode(destination)

    // 1. Uber Universal Link:
    // Pass pickup formatted address, nickname, and coordinates without pickup=my_location,
    // so Uber pre-populates the start location to the lobby's gate/pickup point.
    val uberUrl = buildString {
        append("https://m.uber.com/ul/?action=setPickup")
        append("&pickup[formatted_address]=").append(encodedPickup)
        append("&pickup[nickname]=").append(encodedPickup)
        if (pickupLat != null && pickupLng != null) {
            append("&pickup[latitude]=").append(pickupLat)
            append("&pickup[longitude]=").append(pickupLng)
        }
        append("&dropoff[formatted_address]=").append(encodedDestination)
        append("&dropoff[nickname]=").append(encodedDestination)
        if (destinationLat != null && destinationLng != null) {
            append("&dropoff[latitude]=").append(destinationLat)
            append("&dropoff[longitude]=").append(destinationLng)
        }
    }

    // 2. Ola Cabs Deep Link:
    // Documented parameters: lat, lng, address for pickup; drop_lat, drop_lng, drop_address for drop.
    val olaUrl = buildString {
        append("olacabs://app/launch?utm_source=offcampus")
        if (pickupLat != null && pickupLng != null) {
            append("&lat=").append(pickupLat).append("&lng=").append(pickupLng)
        }
        append("&address=").append(encodedPickup)
        if (destinationLat != null && destinationLng != null) {
            append("&drop_lat=").append(destinationLat).append("&drop_lng=").append(destinationLng)
        }
        append("&drop_address=").append(encodedDestination)
        val category = if (rideType == RideType.AUTO) "auto" else "compact"
        append("&category=").append(category)
    }

    // 3. Rapido Deep Link:
    // Pass source and destination parameters.
    val rapidoUrl = buildString {
        append("rapido://ride?")
        append("source=").append(encodedPickup)
        append("&destination=").append(encodedDestination)
        if (pickupLat != null && pickupLng != null) {
            append("&source_lat=").append(pickupLat).append("&source_lng=").append(pickupLng)
        }
        if (destinationLat != null && destinationLng != null) {
            append("&dest_lat=").append(destinationLat).append("&dest_lng=").append(destinationLng)
        }
    }

    return listOf(
        RideBookingLink.WebFallback(
            label = "Uber",
            uri = Uri.parse(uberUrl)
        ),
        RideBookingLink.AppOnly(
            label = "Ola",
            uri = Uri.parse(olaUrl),
            playStorePackage = "com.olacabs.customer"
        ),
        RideBookingLink.AppOnly(
            label = "Rapido",
            uri = Uri.parse(rapidoUrl),
            playStorePackage = "com.rapido.passenger"
        )
    )
}

/**
 * Backward compatibility overload.
 */
fun rideBookingLinks(destination: String): List<RideBookingLink> =
    rideBookingLinks(pickup = "Main Campus, Vile Parle West", destination = destination)

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
