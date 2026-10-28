package com.personaldevelopment.commuterreminder.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.personaldevelopment.commuterreminder.domain.model.Station
import com.personaldevelopment.commuterreminder.tracking.AppLocation
import com.personaldevelopment.commuterreminder.tracking.LocationClient
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@Composable
fun StationMap(
    currentLocation: AppLocation?,
    destination: Station?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val mapView = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(12.0)
            controller.setCenter(GeoPoint(-6.2088, 106.8456))
        }
    }

    val myLocationOverlay = remember {
        MyLocationNewOverlay(GpsMyLocationProvider(context), mapView).apply {
            setDrawAccuracyEnabled(true)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            myLocationOverlay.disableMyLocation()
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = {
            updateMap(mapView, currentLocation, destination, myLocationOverlay, context)
            mapView
        },
        update = { view ->
            updateMap(view, currentLocation, destination, myLocationOverlay, context)
        },
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(20.dp)
            )
    )

    var hasCentered by remember { mutableStateOf(false) }
    LaunchedEffect(currentLocation, destination) {
        val dest = destination
        val loc = currentLocation
        when {
            dest != null && loc != null -> {
                // kamera memuat posisi user + tujuan
                val points = listOf(
                    GeoPoint(loc.latitude, loc.longitude),
                    GeoPoint(dest.latitude, dest.longitude)
                )
                mapView.zoomToBoundingBox(
                    org.osmdroid.util.BoundingBox.fromGeoPoints(points).increaseByScale(1.4f),
                    false
                )
                hasCentered = true
            }
            loc != null && !hasCentered -> {
                // belum pilih tujuan: fokus ke posisi user
                mapView.controller.animateTo(GeoPoint(loc.latitude, loc.longitude))
                mapView.controller.setZoom(14.0)
                hasCentered = true
            }
        }
    }
}

private fun buildCirclePoints(
    centerLat: Double,
    centerLon: Double,
    radiusKm: Double
): List<GeoPoint> {
    val points = ArrayList<GeoPoint>(73)
    val latOffset = radiusKm / 111.1
    val lonOffset = radiusKm / (111.1 * kotlin.math.cos(Math.toRadians(centerLat)))
    for (i in 0..71) {
        val angle = Math.toRadians(i * 5.0)
        points.add(
            GeoPoint(
                centerLat + latOffset * kotlin.math.sin(angle),
                centerLon + lonOffset * kotlin.math.cos(angle)
            )
        )
    }
    return points
}

private fun updateMap(
    mapView: MapView,
    currentLocation: AppLocation?,
    destination: Station?,
    myLocationOverlay: MyLocationNewOverlay,
    context: android.content.Context
) {
    val overlays = mapView.overlays
    overlays.clear()

    if (destination != null) {
        // garis posisi -> tujuan
        currentLocation?.let { loc ->
            val line = Polyline(mapView).apply {
                setPoints(
                    listOf(
                        GeoPoint(loc.latitude, loc.longitude),
                        GeoPoint(destination.latitude, destination.longitude)
                    )
                )
                outlinePaint.color = Color(0xFF1565C0).toArgb()
                outlinePaint.strokeWidth = 6f
            }
            overlays.add(line)
        }

        // lingkaran radius reminder (dibuat dari titik-titik poligon)
        val circlePoints = buildCirclePoints(
            destination.latitude, destination.longitude,
            com.personaldevelopment.commuterreminder.domain.ReminderSettings.ACTIVATION_RADIUS_KM
        )
        val circle = Polyline(mapView).apply {
            setPoints(circlePoints + circlePoints.first())
            outlinePaint.color = Color(0xFFC62828).toArgb()
            outlinePaint.strokeWidth = 3f
        }
        overlays.add(circle)

        // pin tujuan
        val destMarker = Marker(mapView).apply {
            position = GeoPoint(destination.latitude, destination.longitude)
            title = destination.name
            setIconColored(mapView, Color(0xFFC62828))
        }
        overlays.add(destMarker)
    }

    // pin posisi saat ini
    currentLocation?.let { loc ->
        val meMarker = Marker(mapView).apply {
            position = GeoPoint(loc.latitude, loc.longitude)
            title = "Posisi Kamu"
            setIconColored(mapView, Color(0xFFF9A825), isUser = true)
        }
        overlays.add(meMarker)
    }

    // titik lokasi realtime bawaan osmdroid (update otomatis mengikuti GPS)
    if (LocationClient.hasPermission(context)) {
        if (!myLocationOverlay.isMyLocationEnabled) {
            myLocationOverlay.enableMyLocation()
        }
        overlays.add(myLocationOverlay)
    } else {
        myLocationOverlay.disableMyLocation()
    }

    mapView.invalidate()
}

private fun Marker.setIconColored(mapView: MapView, color: Color, isUser: Boolean = false) {
    val sizePx = if (isUser) 44 else 56
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        style = Paint.Style.FILL
    }
    if (isUser) {
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2.6f, paint)
        paint.color = Color.White.copy(alpha = 0.4f).toArgb()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 6f
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2.6f + 6f, paint)
    } else {
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2.6f, paint)
        paint.color = Color.White.toArgb()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2.6f, paint)
    }
    icon = android.graphics.drawable.BitmapDrawable(mapView.resources, bitmap)
    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
}
