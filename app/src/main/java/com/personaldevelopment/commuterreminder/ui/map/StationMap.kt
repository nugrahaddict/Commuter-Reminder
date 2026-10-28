package com.personaldevelopment.commuterreminder.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.personaldevelopment.commuterreminder.R
import com.personaldevelopment.commuterreminder.domain.model.Station
import com.personaldevelopment.commuterreminder.tracking.AppLocation
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@Composable
fun StationMap(
    currentLocation: AppLocation?,
    routeStations: List<Station>,
    origin: Station?,
    destination: Station?,
    modifier: Modifier = Modifier,
    recenterTrigger: Int = 0,
    focusBounds: List<Station> = emptyList()
) {
    val context = LocalContext.current

    val mapView = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(14.0)
            controller.setCenter(GeoPoint(-6.2088, 106.8456))
        }
    }

    DisposableEffect(Unit) {
        onDispose { mapView.onDetach() }
    }

    AndroidView(
        factory = { view ->
            updateMap(mapView, currentLocation, routeStations, origin, destination)
            if (focusBounds.size >= 2) {
                fitToStations(mapView, focusBounds)
            }
            mapView
        },
        update = { view ->
            updateMap(view, currentLocation, routeStations, origin, destination)
            if (focusBounds.size >= 2) {
                fitToStations(view, focusBounds)
            }
        },
        modifier = modifier
    )

    LaunchedEffect(recenterTrigger) {
        if (recenterTrigger > 0) {
            currentLocation?.let { loc ->
                mapView.controller.animateTo(GeoPoint(loc.latitude, loc.longitude))
                mapView.controller.setZoom(15.5)
            }
        }
    }
}

private fun fitToStations(mapView: MapView, stations: List<Station>) {
    val points = stations.map { GeoPoint(it.latitude, it.longitude) }
    if (points.size < 2) return
    val boundingBox = org.osmdroid.util.BoundingBox.fromGeoPoints(points)
    mapView.zoomToBoundingBox(boundingBox.increaseByScale(1.35f), false)
}

private fun updateMap(
    mapView: MapView,
    currentLocation: AppLocation?,
    routeStations: List<Station>,
    origin: Station?,
    destination: Station?
) {
    android.util.Log.d("StationMap", "updateMap: stations=${routeStations.size}, origin=${origin?.name}, dest=${destination?.name}")
    val overlays = mapView.overlays
    overlays.clear()

    routeStations.forEach { station ->
        val marker = Marker(mapView).apply {
            position = GeoPoint(station.latitude, station.longitude)
            title = station.name
            when (station.id) {
                origin?.id -> setIconColored(mapView, Color(0xFF2E7D32))
                destination?.id -> setIconColored(mapView, Color(0xFFC62828))
                else -> setIconColored(mapView, Color(0xFF1565C0))
            }
        }
        overlays.add(marker)
    }

    if (routeStations.size >= 2) {
        val line = Polyline(mapView).apply {
            setPoints(
                routeStations.sortedBy { it.orderIndex }
                    .map { GeoPoint(it.latitude, it.longitude) }
            )
            outlinePaint.color = Color(0xFF1565C0).toArgb()
            outlinePaint.strokeWidth = 8f
        }
        overlays.add(line)
    }

    currentLocation?.let { loc ->
        val meMarker = Marker(mapView).apply {
            position = GeoPoint(loc.latitude, loc.longitude)
            title = "Posisi Kamu"
            setIconColored(mapView, Color(0xFFF9A825), isUser = true)
        }
        overlays.add(meMarker)
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
