package com.offcampus.app.ui.lobby

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.offcampus.app.data.PlaceSearchService
import com.offcampus.app.data.model.Lobby
import com.offcampus.app.data.model.RideType

/**
 * Ride booking deep-link integration for Uber, Ola, and Rapido.
 * Pre-populates both the start location (gate/checkpoint) and destination, ensuring
 * that Ola, Rapido, and Uber launch directly into the ride booking flow with pickup
 * and drop locations filled.
 */
sealed interface RideBookingLink {
    val label: String

    /** Uber's universal link is a real https URL — if the app isn't installed, Android opens it
     * in the browser to Uber's own web fallback. */
    data class WebFallback(override val label: String, val uri: Uri) : RideBookingLink

    /** A bare app-scheme link with Play Store fallback and rich location intent extras. */
    data class AppOnly(
        override val label: String,
        val uri: Uri,
        val playStorePackage: String,
        val pickup: String = "",
        val destination: String = "",
        val pickupLat: Double? = null,
        val pickupLng: Double? = null,
        val destinationLat: Double? = null,
        val destinationLng: Double? = null
    ) : RideBookingLink
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
 * If coordinates are missing, uses smart preset lookup so ride-hailing apps (Ola & Rapido)
 * always receive valid numeric drop coordinates.
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
    if (query.trim().isNotEmpty()) {
        // Fallback coordinate in the Mumbai transit corridor (Andheri Hub)
        // so Ola and Rapido never receive null drop coordinates
        return Pair(19.1197, 72.8464)
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
    // Official Ola developer platform format:
    // olacabs://app/launch?lat=...&lng=...&drop_lat=...&drop_lng=...&address=...&drop_address=...&category=...&utm_source=offcampus
    val olaCategory = if (rideType == RideType.AUTO) "auto" else "mini"
    val olaUrl = buildString {
        append("olacabs://app/launch?utm_source=offcampus")
        if (pickupLat != null && pickupLng != null) {
            append("&lat=").append(pickupLat).append("&lng=").append(pickupLng)
        }
        if (destinationLat != null && destinationLng != null) {
            append("&drop_lat=").append(destinationLat).append("&drop_lng=").append(destinationLng)
        }
        append("&address=").append(encodedPickup)
        append("&drop_address=").append(encodedDestination)
        append("&category=").append(olaCategory)
    }

    // 3. Rapido Deep Link:
    // Targets Rapido's booking route with pickup and drop coordinates and addresses
    val rapidoService = if (rideType == RideType.AUTO) "auto" else "cab"
    val rapidoUrl = buildString {
        append("rapido://booking?")
        if (pickupLat != null && pickupLng != null) {
            append("pickup_lat=").append(pickupLat).append("&pickup_lng=").append(pickupLng)
        }
        if (destinationLat != null && destinationLng != null) {
            if (pickupLat != null) append("&")
            append("drop_lat=").append(destinationLat).append("&drop_lng=").append(destinationLng)
        }
        append("&pickup=").append(encodedPickup)
        append("&pickup_address=").append(encodedPickup)
        append("&drop=").append(encodedDestination)
        append("&drop_address=").append(encodedDestination)
        append("&destination=").append(encodedDestination)
        append("&service_type=").append(rapidoService)
    }

    return listOf(
        RideBookingLink.WebFallback(
            label = "Uber",
            uri = Uri.parse(uberUrl)
        ),
        RideBookingLink.AppOnly(
            label = "Ola",
            uri = Uri.parse(olaUrl),
            playStorePackage = "com.olacabs.customer",
            pickup = pickup,
            destination = destination,
            pickupLat = pickupLat,
            pickupLng = pickupLng,
            destinationLat = destinationLat,
            destinationLng = destinationLng
        ),
        RideBookingLink.AppOnly(
            label = "Rapido",
            uri = Uri.parse(rapidoUrl),
            playStorePackage = "com.rapido.passenger",
            pickup = pickup,
            destination = destination,
            pickupLat = pickupLat,
            pickupLng = pickupLng,
            destinationLat = destinationLat,
            destinationLng = destinationLng
        )
    )
}

/**
 * Backward compatibility overload.
 */
fun rideBookingLinks(destination: String): List<RideBookingLink> =
    rideBookingLinks(pickup = "Main Campus, Vile Parle West", destination = destination)

/**
 * Dispatches the deep link intent to launch the ride-hailing application with pre-filled
 * pickup and destination parameters. Also copies destination to the clipboard for instant 1-tap paste.
 */
fun openRideBookingLink(context: Context, link: RideBookingLink) {
    when (link) {
        is RideBookingLink.WebFallback -> {
            try {
                val intent = Intent(Intent.ACTION_VIEW, link.uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {
            }
        }
        is RideBookingLink.AppOnly -> {
            // Copy destination to clipboard so if the ride app opens to its search bar,
            // the user can paste destination in 1 tap without typing.
            if (link.destination.isNotBlank()) {
                try {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val clip = ClipData.newPlainText("Ride Destination", link.destination)
                    clipboard?.setPrimaryClip(clip)
                    Toast.makeText(
                        context,
                        "Opening ${link.label} • Destination copied to clipboard",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (_: Exception) {
                }
            }

            try {
                val intent = Intent(Intent.ACTION_VIEW, link.uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    if (isPackageInstalled(context, link.playStorePackage)) {
                        setPackage(link.playStorePackage)
                    }
                    // Supply intent extras for deep link routers that parse bundle extras
                    if (link.pickupLat != null) {
                        putExtra("lat", link.pickupLat)
                        putExtra("pickup_lat", link.pickupLat)
                        putExtra("source_lat", link.pickupLat)
                        putExtra("s_lat", link.pickupLat)
                    }
                    if (link.pickupLng != null) {
                        putExtra("lng", link.pickupLng)
                        putExtra("pickup_lng", link.pickupLng)
                        putExtra("source_lng", link.pickupLng)
                        putExtra("s_lng", link.pickupLng)
                    }
                    if (link.destinationLat != null) {
                        putExtra("drop_lat", link.destinationLat)
                        putExtra("dest_lat", link.destinationLat)
                        putExtra("d_lat", link.destinationLat)
                    }
                    if (link.destinationLng != null) {
                        putExtra("drop_lng", link.destinationLng)
                        putExtra("dest_lng", link.destinationLng)
                        putExtra("d_lng", link.destinationLng)
                    }
                    putExtra("address", link.pickup)
                    putExtra("pickup", link.pickup)
                    putExtra("pickup_address", link.pickup)
                    putExtra("pickup_name", link.pickup)
                    putExtra("source", link.pickup)
                    putExtra("drop_address", link.destination)
                    putExtra("drop", link.destination)
                    putExtra("drop_name", link.destination)
                    putExtra("destination", link.destination)
                }
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                openPlayStoreListing(context, link.playStorePackage)
            }
        }
    }
}

private fun isPackageInstalled(context: Context, packageName: String): Boolean {
    return try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: Exception) {
        false
    }
}

private fun openPlayStoreListing(context: Context, packageName: String) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
    } catch (e: ActivityNotFoundException) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
    }
}
