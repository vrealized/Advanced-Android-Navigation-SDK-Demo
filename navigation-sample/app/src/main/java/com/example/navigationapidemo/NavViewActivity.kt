/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.example.navigationapidemo

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.*
import com.google.android.libraries.navigation.*
import com.google.android.libraries.navigation.NavigationApi.NavigatorListener
import com.google.android.libraries.navigation.Navigator.RouteStatus
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode

private const val TAG = "NavViewActivity"

class NavViewActivity : AppCompatActivity() {
  private lateinit var navView: NavigationView
  private var googleMap: GoogleMap? = null

  private var navigator: Navigator? = null
  private var isNavigatorInitialized = false
  private val pendingNavActions = mutableListOf<(Navigator) -> Unit>()

  private var arrivalListener: Navigator.ArrivalListener? = null
  private var routeChangedListener: Navigator.RouteChangedListener? = null
  private var remainingTimeOrDistanceChangedListener: Navigator.RemainingTimeOrDistanceChangedListener? = null

  private lateinit var fusedLocationClient: FusedLocationProviderClient
  private lateinit var locationCallback: LocationCallback
  private var customLocationMarker: Marker? = null
  private var lastKnownLocation: Location? = null
  private var customRoutePolylines = mutableListOf<Polyline>()
  private var currentRouteColor: Int? = null
  private val vehicleIcons = mapOf(
    "默认图标 (SDK)" to 0,
    "黄色轿车" to R.drawable.ic_car_yellow,
    "橙色轿车" to R.drawable.ic_car_orange,
    "绿色轿车" to R.drawable.ic_car_green
  )
  private var currentVehicleIconResId = 0

  private val ORIGIN_REQUEST_CODE = 2
  private val DESTINATION_REQUEST_CODE = 3
  private lateinit var topInputPanel: View
  private lateinit var originTextView: TextView
  private lateinit var destinationTextView: TextView
  private var originWaypoint: Waypoint? = null
  private var destinationWaypoint: Waypoint? = null
  private lateinit var startNavButton: Button
  private lateinit var startSimulationButton: Button
  private lateinit var customControlsView: View

  private var isOriginMyLocation = true
  private var isVoiceMuted = false
  private var isSimulating = false
  private var isRouteOverviewShowing = false
  private var isSpeedLimitEnabled = false
  private var isTripProgressEnabled = false
  private var isTrafficLayerEnabled = true
  private var areTrafficLightsVisible = false
  private var currentNightMode = 0

  @SuppressLint("MissingPermission")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_nav_view)

    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    initializePlacesSdk()

    navView = findViewById(R.id.navigation_view)
    topInputPanel = findViewById(R.id.origin_destination_card)
    originTextView = findViewById(R.id.origin_text_view)
    destinationTextView = findViewById(R.id.destination_text_view)

    customControlsView = LayoutInflater.from(this).inflate(R.layout.custom_footer_controls, navView, false)
    startNavButton = customControlsView.findViewById(R.id.button_start_navigation)
    startSimulationButton = customControlsView.findViewById(R.id.button_start_simulation)

    fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

    setupTopInputPanelListeners()
    setupBottomControlsListeners(customControlsView)

    navView.onCreate(savedInstanceState)
    initializeNavigationApi()
    createLocationCallback()
  }

  private fun initializePlacesSdk() {
    if (!Places.isInitialized()) {
      try {
        val apiKey = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA).metaData.getString("com.google.android.geo.API_KEY")
        if (apiKey != null) { Places.initialize(applicationContext, apiKey) }
      } catch (e: Exception) { Log.e(TAG, "Error initializing Places SDK", e) }
    }
  }

  private fun withNavigatorAsync(action: (Navigator) -> Unit) {
    if (isNavigatorInitialized) { navigator?.let(action) } else { pendingNavActions.add(action) }
  }

  private fun setupTopInputPanelListeners() {
    originTextView.setOnClickListener { launchAutocomplete(ORIGIN_REQUEST_CODE) }
    destinationTextView.setOnClickListener { launchAutocomplete(DESTINATION_REQUEST_CODE) }
    findViewById<View>(R.id.button_origin_my_location).setOnClickListener { setOriginToMyLocation() }
    findViewById<View>(R.id.button_swap_origin_destination).setOnClickListener { swapOriginAndDestination() }
  }

  private fun setupBottomControlsListeners(view: View) {
    startNavButton.setOnClickListener { withNavigatorAsync { n -> if (n.isGuidanceRunning) resetToIdleState() else startNavigation() } }

    startSimulationButton.isEnabled = false
    startSimulationButton.setOnClickListener { button ->
      withNavigatorAsync { navigator ->
        if (isSimulating) {
          navigator.simulator.unsetUserLocation()
          (button as Button).text = "模拟导航"
        } else {
          if (navigator.isGuidanceRunning) {
            navigator.simulator.simulateLocationsAlongExistingRoute(SimulationOptions().speedMultiplier(5.0f))
            (button as Button).text = "停止模拟"
          } else { showToast("请先开始导航") }
        }
        isSimulating = !isSimulating
      }
    }

    view.findViewById<Button>(R.id.button_toggle_speed_limit).setOnClickListener { btn -> isSpeedLimitEnabled = !isSpeedLimitEnabled; navView.setSpeedometerEnabled(isSpeedLimitEnabled); (btn as Button).text = if (isSpeedLimitEnabled) "隐藏限速" else "显示限速" }
    view.findViewById<Button>(R.id.button_toggle_progress_bar).setOnClickListener { btn -> isTripProgressEnabled = !isTripProgressEnabled; navView.setTripProgressBarEnabled(isTripProgressEnabled); (btn as Button).text = if (isTripProgressEnabled) "隐藏进度条" else "显示进度条" }
    view.findViewById<Button>(R.id.button_toggle_voice).setOnClickListener { btn -> withNavigatorAsync { n -> isVoiceMuted = !isVoiceMuted; n.setAudioGuidance(if (isVoiceMuted) Navigator.AudioGuidance.SILENT else Navigator.AudioGuidance.VOICE_ALERTS_AND_GUIDANCE); (btn as Button).text = if (isVoiceMuted) "开启语音" else "关闭语音" } }
    view.findViewById<Button>(R.id.button_toggle_camera_view).setOnClickListener { btn -> isRouteOverviewShowing = !isRouteOverviewShowing; if (isRouteOverviewShowing) { navView.showRouteOverview(); (btn as Button).text = "返回跟随" } else { navView.getMapAsync { it.followMyLocation(1) }; (btn as Button).text = "调整视角" } }
    view.findViewById<Button>(R.id.button_toggle_traffic_layer).setOnClickListener { btn -> isTrafficLayerEnabled = !isTrafficLayerEnabled; googleMap?.isTrafficEnabled = isTrafficLayerEnabled; (btn as Button).text = if (isTrafficLayerEnabled) "隐藏路况" else "显示路况" }
    view.findViewById<Button>(R.id.button_toggle_traffic_lights).setOnClickListener { btn -> areTrafficLightsVisible = !areTrafficLightsVisible; (btn as Button).text = if (areTrafficLightsVisible) "隐藏红绿灯" else "显示红绿灯"; calculateAndShowRoutePreview() }
    view.findViewById<Button>(R.id.button_style_route).setOnClickListener { showRouteColorPicker() }
    view.findViewById<Button>(R.id.button_change_vehicle_icon).setOnClickListener { showVehicleIconPicker() }
    view.findViewById<Button>(R.id.button_toggle_night_mode).setOnClickListener { showNightModePicker() }
  }

  private fun initializeNavigationApi() {
    navView.getMapAsync { map ->
      this.googleMap = map
      map.isTrafficEnabled = isTrafficLayerEnabled
      updateVehicleIconVisibility()
    }

    NavigationApi.getNavigator(this, object : NavigatorListener {
      override fun onNavigatorReady(navigator: Navigator) {
        this@NavViewActivity.navigator = navigator
        isNavigatorInitialized = true
        registerNavigationListeners()
        navView.setCustomControl(customControlsView, CustomControlPosition.FOOTER)
        pendingNavActions.forEach { it(navigator) }
        pendingNavActions.clear()
        navigator.setTaskRemovedBehavior(Navigator.TaskRemovedBehavior.QUIT_SERVICE)
      }
      override fun onError(@NavigationApi.ErrorCode errorCode: Int) { showToast("Error loading Nav API: $errorCode") }
    })
  }

  private fun registerNavigationListeners() {
    withNavigatorAsync { navigator ->
      arrivalListener = Navigator.ArrivalListener { showToast("已到达目的地！"); resetToIdleState() }
      navigator.addArrivalListener(arrivalListener)

      routeChangedListener = Navigator.RouteChangedListener { showToast("路线已更新"); redrawCustomRoute() }
      navigator.addRouteChangedListener(routeChangedListener)

      remainingTimeOrDistanceChangedListener = object : Navigator.RemainingTimeOrDistanceChangedListener {
        override fun onRemainingTimeOrDistanceChanged() {
          runOnUiThread {
            if (currentVehicleIconResId == 0) return@runOnUiThread
            if (isSimulating) {
              val routeSegments = navigator.getRouteSegments()
              if (routeSegments.isNullOrEmpty() || routeSegments[0].latLngs.isNullOrEmpty()) return@runOnUiThread
              updateCustomMarker(routeSegments[0].latLngs[0], null)
            } else {
              lastKnownLocation?.let { updateCustomMarker(LatLng(it.latitude, it.longitude), it.bearing) }
            }
          }
        }
      }
      navigator.addRemainingTimeOrDistanceChangedListener(1, 1, remainingTimeOrDistanceChangedListener!!)
    }
  }

  @SuppressLint("MissingPermission")
  private fun calculateAndShowRoutePreview() {
    if (destinationWaypoint == null) return
    withNavigatorAsync { navigator ->
      val waypoints = mutableListOf<Waypoint>()
      if (!isOriginMyLocation) {
        originWaypoint?.let { waypoints.add(it) } ?: run { showToast("请选择一个起点"); return@withNavigatorAsync }
      }
      destinationWaypoint?.let { waypoints.add(it) }

      val displayOptions = DisplayOptions().showTrafficLights(areTrafficLightsVisible).showStopSigns(areTrafficLightsVisible)
      navigator.setDestinations(waypoints, RoutingOptions(), displayOptions)?.setOnResultListener { code ->
        if (code == RouteStatus.OK) {
          showToast("路线已规划"); redrawCustomRoute(); startNavButton.isEnabled = true
        } else { showToast("错误：无法规划路线，代码 $code"); startNavButton.isEnabled = false }
      }
    }
  }

  private fun startNavigation() {
    withNavigatorAsync { n ->
      if (destinationWaypoint != null) {
        stopLocationUpdates()
        topInputPanel.visibility = View.GONE
        n.startGuidance()
        startNavButton.text = "停止导航"
        startSimulationButton.isEnabled = true
      } else {
        showToast("请先设置目的地")
      }
    }
  }

  private fun resetToIdleState() {
    withNavigatorAsync { n -> n.stopGuidance(); n.clearDestinations(); if (isSimulating) { n.simulator.unsetUserLocation(); isSimulating = false } }
    topInputPanel.visibility = View.VISIBLE
    startNavButton.text = "开始导航"; startNavButton.isEnabled = false
    startSimulationButton.text = "模拟导航"; startSimulationButton.isEnabled = false
    isRouteOverviewShowing = false
    findViewById<Button>(R.id.button_toggle_camera_view).text = "调整视角"
    findViewById<Button>(R.id.button_start_simulation).text = "模拟导航"

    originTextView.text = "您的位置"; destinationTextView.text = ""; destinationTextView.hint = "输入目的地"
    originWaypoint = null; destinationWaypoint = null; isOriginMyLocation = true

    currentRouteColor = null
    redrawCustomRoute()
    currentVehicleIconResId = 0
    updateVehicleIconVisibility()
    startLocationUpdates()
  }

  private fun setOriginToMyLocation() {
    isOriginMyLocation = true
    originWaypoint = null
    originTextView.text = "您的位置"
    showToast("起点已设置为您的当前位置")
    if (destinationWaypoint != null) {
      calculateAndShowRoutePreview()
    }
  }

  private fun swapOriginAndDestination() {
    // Add a guard clause to prevent swapping when origin is "My Location"
    if (isOriginMyLocation) {
      showToast("无法交换，请先为起点选择一个具体地点")
      return
    }

    val tempWaypoint = originWaypoint
    originWaypoint = destinationWaypoint
    destinationWaypoint = tempWaypoint

    val tempText = originTextView.text
    originTextView.text = destinationTextView.text
    destinationTextView.text = tempText

    isOriginMyLocation = (originWaypoint == null)

    if (destinationWaypoint != null) {
      calculateAndShowRoutePreview()
    }
  }

  private fun showNightModePicker() {
    val modes = mapOf("自动模式" to 0, "白天模式" to 1, "夜间模式" to 2)
    val modeNames = modes.keys.toTypedArray()

    AlertDialog.Builder(this)
      .setTitle("选择显示模式")
      .setItems(modeNames) { _, which ->
        val selectedMode = modes[modeNames[which]] ?: 0
        currentNightMode = selectedMode
        navView.setForceNightMode(currentNightMode)
        showToast("${modeNames[which]}已应用")
      }
      .show()
  }

  private fun showRouteColorPicker() {
    val colors = mapOf("默认 (SDK 蓝色)" to null, "红色" to Color.RED, "绿色" to Color.GREEN, "黄色" to Color.YELLOW)
    val colorNames = colors.keys.toTypedArray()
    AlertDialog.Builder(this).setTitle("选择路线颜色").setItems(colorNames) { _, w ->
      currentRouteColor = colors[colorNames[w]]
      redrawCustomRoute()
    }.show()
  }

  private fun redrawCustomRoute() {
    withNavigatorAsync { n ->
      customRoutePolylines.forEach { it.remove() }
      customRoutePolylines.clear()

      val color = currentRouteColor ?: return@withNavigatorAsync
      val segments = n.routeSegments ?: return@withNavigatorAsync
      if (segments.isEmpty()) return@withNavigatorAsync

      val opts = PolylineOptions().color(color).width(25f).zIndex(1f)
      segments.forEach { s -> s.latLngs.forEach { opts.add(it) } }
      googleMap?.let { customRoutePolylines.add(it.addPolyline(opts)) }
    }
  }

  private fun showVehicleIconPicker() {
    val iconNames = vehicleIcons.keys.toTypedArray()
    AlertDialog.Builder(this).setTitle("选择车辆图标").setItems(iconNames) { _, which ->
      currentVehicleIconResId = vehicleIcons[iconNames[which]] ?: 0
      updateVehicleIconVisibility()
    }.show()
  }

  @SuppressLint("MissingPermission")
  private fun updateVehicleIconVisibility() {
    runOnUiThread {
      customLocationMarker?.remove()
      customLocationMarker = null

      if (currentVehicleIconResId != 0) {
        googleMap?.isMyLocationEnabled = false
      } else {
        googleMap?.isMyLocationEnabled = true
      }
    }
  }

  private fun getBitmapDescriptorFromVector(context: Context, resId: Int): BitmapDescriptor {
    val vectorDrawable = ContextCompat.getDrawable(context, resId)!!
    vectorDrawable.setBounds(0, 0, vectorDrawable.intrinsicWidth, vectorDrawable.intrinsicHeight)
    val bitmap = Bitmap.createBitmap(vectorDrawable.intrinsicWidth, vectorDrawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    vectorDrawable.draw(canvas)
    return BitmapDescriptorFactory.fromBitmap(bitmap)
  }

  private fun createCustomLocationMarker(position: LatLng) {
    if (currentVehicleIconResId == 0 || googleMap == null) return

    customLocationMarker?.remove()
    val icon = getBitmapDescriptorFromVector(this, currentVehicleIconResId)
    customLocationMarker = googleMap!!.addMarker(MarkerOptions().position(position).icon(icon).anchor(0.5f, 0.5f).flat(true).zIndex(2f))
  }

  private fun updateCustomMarker(position: LatLng, bearing: Float?) {
    if (googleMap == null) return

    runOnUiThread {
      if (customLocationMarker == null && currentVehicleIconResId != 0) {
        createCustomLocationMarker(position)
      } else if (customLocationMarker != null) {
        customLocationMarker?.position = position
        bearing?.let { customLocationMarker?.rotation = it }
      }
    }
  }

  private fun createLocationCallback() {
    locationCallback = object : LocationCallback() {
      override fun onLocationResult(locationResult: LocationResult) {
        val location = locationResult.lastLocation ?: return
        lastKnownLocation = location

        if (navigator?.isGuidanceRunning == false && currentVehicleIconResId != 0) {
          updateCustomMarker(LatLng(location.latitude, location.longitude), location.bearing)
        }
      }
    }
  }

  @SuppressLint("MissingPermission")
  private fun startLocationUpdates() {
    val locationRequest = com.google.android.gms.location.LocationRequest.create().apply {
      interval = 1000
      fastestInterval = 1000
      priority = com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY
    }
    fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
  }

  private fun stopLocationUpdates() {
    fusedLocationClient.removeLocationUpdates(locationCallback)
  }

  private fun launchAutocomplete(requestCode: Int) {
    val fields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG)
    val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields).build(this)
    startActivityForResult(intent, requestCode)
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    if (resultCode == RESULT_OK && data != null) {
      try {
        val place = Autocomplete.getPlaceFromIntent(data)
        val waypoint = place.id?.let { Waypoint.builder().setPlaceIdString(it).build() } ?: run { showToast("所选地点没有Place ID"); return }
        when (requestCode) {
          //  When an origin is selected, update the flag.
          ORIGIN_REQUEST_CODE -> {
            originWaypoint = waypoint
            originTextView.text = place.name
            isOriginMyLocation = false
          }
          DESTINATION_REQUEST_CODE -> {
            destinationWaypoint = waypoint
            destinationTextView.text = place.name
          }
        }
        calculateAndShowRoutePreview()
      } catch (e: Exception) { Log.e(TAG, "Error getting place from Autocomplete intent.", e) }
    }
  }

  override fun onResume() { super.onResume(); navView.onResume(); startLocationUpdates() }
  override fun onPause() { stopLocationUpdates(); navView.onPause(); super.onPause() }
  override fun onSaveInstanceState(savedInstanceState: Bundle) { super.onSaveInstanceState(savedInstanceState); navView.onSaveInstanceState(savedInstanceState) }
  override fun onStart() { super.onStart(); navView.onStart() }
  override fun onStop() { super.onStop(); navView.onStop() }
  override fun onConfigurationChanged(configuration: Configuration) { super.onConfigurationChanged(configuration); navView.onConfigurationChanged(configuration) }
  override fun onDestroy() {
    withNavigatorAsync { n ->
      arrivalListener?.let { n.removeArrivalListener(it) }
      routeChangedListener?.let { n.removeRouteChangedListener(it) }
      remainingTimeOrDistanceChangedListener?.let { n.removeRemainingTimeOrDistanceChangedListener(it) }
      if (isSimulating) { n.simulator.unsetUserLocation() }
      n.cleanup()
    }
    super.onDestroy()
    navView.onDestroy()
  }

  private fun showToast(message: String) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
}