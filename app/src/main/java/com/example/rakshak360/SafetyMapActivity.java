package com.example.rakshak360;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class SafetyMapActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private Location myCurrentLocation;

    private LinearLayout topDangerPrompt;
    private TextView tvRouteTimeDist, tvRouteType;
    private BottomSheetBehavior<View> bottomSheetBehavior;
    private AutoCompleteTextView etSearchDestination;

    private List<Polyline> activePolylines = new ArrayList<>();
    private List<Marker> activeRouteLabels = new ArrayList<>();
    private OkHttpClient httpClient = new OkHttpClient();

    private List<LatLng> rawRedZones = new ArrayList<>();
    private List<LatLng> rawYellowZones = new ArrayList<>();
    private HashMap<String, LatLng> searchPlacesMap = new HashMap<>();

    // UI Navigation State Elements
    private Button btnStartNav, btnExitNav;
    private boolean isNavigating = false;
    private boolean isPreviewingRoute = false;

    private boolean showRed = true;
    private boolean showYellow = true;

    private LinearLayout policeStationCard;
    private TextView tvPoliceName, tvPoliceDist;
    private List<PoliceStation> policeStationsList = new ArrayList<>();
    private LatLng nearestPoliceLocation = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_safety_map);

        topDangerPrompt = findViewById(R.id.topDangerPrompt);
        tvRouteTimeDist = findViewById(R.id.tvRouteTimeDist);
        tvRouteType = findViewById(R.id.tvRouteType);
        etSearchDestination = findViewById(R.id.etSearchDestination);

        View bottomSheet = findViewById(R.id.bottomSheet);
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnReportArea).setOnClickListener(v -> showGoogleStyleReportDialog());

        findViewById(R.id.btnMyLocation).setOnClickListener(v -> {
            if (myCurrentLocation != null && mMap != null) {
                if (isNavigating) {
                    CameraPosition pos = new CameraPosition.Builder().target(new LatLng(myCurrentLocation.getLatitude(), myCurrentLocation.getLongitude())).zoom(18.5f).tilt(65).bearing(myCurrentLocation.getBearing()).build();
                    mMap.animateCamera(CameraUpdateFactory.newCameraPosition(pos));
                } else {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(myCurrentLocation.getLatitude(), myCurrentLocation.getLongitude()), 15f));
                }
            }
        });

        findViewById(R.id.btnFilterRed).setOnClickListener(v -> { showRed = true; showYellow = false; drawCrimeZones(); });
        findViewById(R.id.btnFilterYellow).setOnClickListener(v -> { showRed = false; showYellow = true; drawCrimeZones(); });
        findViewById(R.id.btnFilterGreen).setOnClickListener(v -> { showRed = false; showYellow = false; drawCrimeZones(); });

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        setupUberStyleSearch();
        setupExtensivePoliceStations();
        createDynamicNavigationUI();
    }

    // ====================================================================
    // 🚓 1. EXTENSIVE POLICE STATION DATA & UI
    // ====================================================================
    private void setupExtensivePoliceStations() {
        // Police Stations & Chowkis mixed for max coverage
        policeStationsList.add(new PoliceStation("Vidhyadhar Nagar Thana", new LatLng(26.9635, 75.7725)));
        policeStationsList.add(new PoliceStation("Malviya Nagar Thana", new LatLng(26.8549, 75.8243)));
        policeStationsList.add(new PoliceStation("Mansarovar Thana", new LatLng(26.8544, 75.7629)));
        policeStationsList.add(new PoliceStation("Sindhi Camp Thana", new LatLng(26.9231, 75.7981)));
        policeStationsList.add(new PoliceStation("Ashok Nagar Thana", new LatLng(26.9080, 75.7950)));
        policeStationsList.add(new PoliceStation("Jagatpura Police Chowki", new LatLng(26.8260, 75.8630)));
        policeStationsList.add(new PoliceStation("Bajaj Nagar Thana", new LatLng(26.8732, 75.8033)));
        policeStationsList.add(new PoliceStation("Pratap Nagar Thana", new LatLng(26.8049, 75.8200)));
        policeStationsList.add(new PoliceStation("Sanganer Thana", new LatLng(26.8185, 75.7834)));
        policeStationsList.add(new PoliceStation("Mahesh Nagar Thana", new LatLng(26.8821, 75.7770)));
        policeStationsList.add(new PoliceStation("Jotwara Thana", new LatLng(26.9388, 75.7486)));
        policeStationsList.add(new PoliceStation("Murlipura Police Chowki", new LatLng(26.9667, 75.7656)));
        policeStationsList.add(new PoliceStation("Brahmpuri Thana", new LatLng(26.9361, 75.8288)));
        policeStationsList.add(new PoliceStation("Adarsh Nagar Thana", new LatLng(26.9023, 75.8340)));
        policeStationsList.add(new PoliceStation("Shyam Nagar Thana", new LatLng(26.8920, 75.7616)));
        policeStationsList.add(new PoliceStation("Gandhi Nagar Thana", new LatLng(26.8871, 75.8058)));
    }

    private void createDynamicNavigationUI() {
        // Top Police Tracker Card
        policeStationCard = new LinearLayout(this);
        policeStationCard.setOrientation(LinearLayout.VERTICAL);
        policeStationCard.setBackgroundColor(Color.parseColor("#E8F0FE"));
        policeStationCard.setPadding(40, 20, 40, 20);
        policeStationCard.setElevation(8f);

        tvPoliceName = new TextView(this); tvPoliceName.setTextColor(Color.parseColor("#174EA6")); tvPoliceName.setTextSize(15f); tvPoliceName.setTypeface(null, android.graphics.Typeface.BOLD);
        tvPoliceDist = new TextView(this); tvPoliceDist.setTextColor(Color.parseColor("#1967D2")); tvPoliceDist.setTextSize(13f);
        policeStationCard.addView(tvPoliceName); policeStationCard.addView(tvPoliceDist);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(40, 240, 40, 0);
        addContentView(policeStationCard, params);

        policeStationCard.setOnClickListener(v -> {
            if (nearestPoliceLocation != null) initiateRouteCalculation(nearestPoliceLocation);
        });

        // Start Navigation Button (Green)
        btnStartNav = new Button(this);
        btnStartNav.setText("▶ Start Navigation");
        btnStartNav.setBackgroundColor(Color.parseColor("#188038")); // Google Green
        btnStartNav.setTextColor(Color.WHITE);
        btnStartNav.setVisibility(View.GONE);
        FrameLayout.LayoutParams startParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 150);
        startParams.setMargins(60, 0, 60, 300); // Above bottom sheet
        startParams.gravity = Gravity.BOTTOM;
        addContentView(btnStartNav, startParams);

        // Exit Navigation Button (Red)
        btnExitNav = new Button(this);
        btnExitNav.setText("❌ Exit");
        btnExitNav.setBackgroundColor(Color.parseColor("#D93025")); // Google Red
        btnExitNav.setTextColor(Color.WHITE);
        btnExitNav.setVisibility(View.GONE);
        FrameLayout.LayoutParams exitParams = new FrameLayout.LayoutParams(300, 150);
        exitParams.setMargins(40, 0, 0, 80);
        exitParams.gravity = Gravity.BOTTOM | Gravity.START;
        addContentView(btnExitNav, exitParams);

        // UI Interactions
        btnStartNav.setOnClickListener(v -> {
            isPreviewingRoute = false;
            isNavigating = true;
            btnStartNav.setVisibility(View.GONE);
            btnExitNav.setVisibility(View.VISIBLE);
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
            etSearchDestination.setVisibility(View.GONE); // Hide search while driving
            Toast.makeText(this, "Head towards the route", Toast.LENGTH_SHORT).show();
        });

        btnExitNav.setOnClickListener(v -> {
            isNavigating = false;
            isPreviewingRoute = false;
            btnExitNav.setVisibility(View.GONE);
            etSearchDestination.setVisibility(View.VISIBLE);
            clearRoutes();
            mMap.animateCamera(CameraUpdateFactory.newCameraPosition(new CameraPosition.Builder().target(new LatLng(myCurrentLocation.getLatitude(), myCurrentLocation.getLongitude())).zoom(15f).tilt(0).build()));
        });
    }

    // ====================================================================
    // 🗺️ 2. MAP & SMART CLUSTERING
    // ====================================================================
    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) mMap.setMyLocationEnabled(true);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(26.8850, 75.8000), 12f));
        mMap.setOnMapLongClickListener(this::initiateRouteCalculation);
        startLocationTracking();
        loadCrimesFromDatabase();
    }

    private void loadCrimesFromDatabase() {
        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(getApplicationContext());
            List<CrimeEntity> crimeList = db.crimeDao().getAllCrimes();
            runOnUiThread(() -> {
                rawRedZones.clear(); rawYellowZones.clear();
                for (CrimeEntity crime : crimeList) {
                    if (crime.Crime_Type != null && (crime.Crime_Type.toLowerCase().contains("cyber") || crime.Crime_Type.toLowerCase().contains("fraud"))) continue;
                    LatLng loc = new LatLng(crime.Latitude, crime.Longitude);
                    if (crime.Severity == 5) rawRedZones.add(loc); else if (crime.Severity >= 3) rawYellowZones.add(loc);
                }
                drawCrimeZones();
            });
        });
    }

    // 🔥 SMART CLUSTERING ALGORITHM 🔥
    // This prevents circles from colliding and making a visual mess
    private List<LatLng> getClusteredZones(List<LatLng> rawZones, float minMergeDistance) {
        List<LatLng> clustered = new ArrayList<>();
        for (LatLng current : rawZones) {
            boolean isMerged = false;
            for (LatLng existing : clustered) {
                float[] dist = new float[1];
                Location.distanceBetween(current.latitude, current.longitude, existing.latitude, existing.longitude, dist);
                if (dist[0] < minMergeDistance) { isMerged = true; break; } // It's too close, skip drawing a new one
            }
            if (!isMerged) clustered.add(current);
        }
        return clustered;
    }

    private void drawCrimeZones() {
        if (mMap == null) return;
        mMap.clear();
        drawGreenJaipurBase();

        // Merge circles that are within 400 meters of each other into 1 single marker/circle
        List<LatLng> cleanRedZones = getClusteredZones(rawRedZones, 400f);
        List<LatLng> cleanYellowZones = getClusteredZones(rawYellowZones, 400f);

        if (showRed) {
            for (LatLng red : cleanRedZones) mMap.addCircle(new CircleOptions().center(red).radius(450).fillColor(Color.parseColor("#30D32F2F")).strokeWidth(0));
        }
        if (showYellow) {
            for (LatLng yellow : cleanYellowZones) mMap.addCircle(new CircleOptions().center(yellow).radius(450).fillColor(Color.parseColor("#30FBC02D")).strokeWidth(0));
        }

        // Draw Police Stations as Safe Green Dots
        for (PoliceStation ps : policeStationsList) {
            mMap.addCircle(new CircleOptions().center(ps.latLng).radius(150).fillColor(Color.parseColor("#60188038")).strokeColor(Color.parseColor("#188038")).strokeWidth(2));
        }
    }

    private void drawGreenJaipurBase() {
        if (mMap == null) return;
        mMap.addPolygon(new PolygonOptions().add(new LatLng(27.1, 75.6), new LatLng(27.1, 76.0), new LatLng(26.7, 76.0), new LatLng(26.7, 75.6)).fillColor(Color.parseColor("#0800E676")).strokeWidth(0));
    }

    // ====================================================================
    // 📍 3. LIVE TRACKING
    // ====================================================================
    private void startLocationTracking() {
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000).build();
        locationCallback = new LocationCallback() {
            @Override public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    myCurrentLocation = location;

                    if (isNavigating && activePolylines.size() > 0) {
                        CameraPosition currentNavPos = new CameraPosition.Builder().target(new LatLng(location.getLatitude(), location.getLongitude())).zoom(18.5f).tilt(65).bearing(location.getBearing()).build();
                        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(currentNavPos));
                    }
                    findNearestPoliceStation();
                }
            }
        };
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        }
    }

    private void findNearestPoliceStation() {
        if (myCurrentLocation == null) return;
        PoliceStation nearest = null; float minDistance = Float.MAX_VALUE;
        for (PoliceStation ps : policeStationsList) {
            float[] results = new float[1]; Location.distanceBetween(myCurrentLocation.getLatitude(), myCurrentLocation.getLongitude(), ps.latLng.latitude, ps.latLng.longitude, results);
            if (results[0] < minDistance) { minDistance = results[0]; nearest = ps; }
        }
        if (nearest != null) {
            nearestPoliceLocation = nearest.latLng; float distKm = minDistance / 1000.0f;
            tvPoliceName.setText("🛡️ Safe Zone: " + nearest.name);
            tvPoliceDist.setText(String.format(Locale.US, "%.1f km away • Tap in emergency", distKm));
        }
    }

    // ====================================================================
    // 🗺️ 4. OSRM MULTI-ROUTING & POLICE SAFE ZONES
    // ====================================================================
    private void initiateRouteCalculation(LatLng destination) {
        clearRoutes();
        isPreviewingRoute = true;
        mMap.addMarker(new MarkerOptions().position(destination).title("Destination").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
        LatLng startPoint = (myCurrentLocation != null) ? new LatLng(myCurrentLocation.getLatitude(), myCurrentLocation.getLongitude()) : new LatLng(26.9124, 75.8173);
        Toast.makeText(this, "AI finding best paths...", Toast.LENGTH_SHORT).show();

        // Force alternative=3 to try to get maximum routes
        String url = "https://router.project-osrm.org/route/v1/driving/" + startPoint.longitude + "," + startPoint.latitude + ";" + destination.longitude + "," + destination.latitude + "?overview=full&geometries=polyline&alternatives=3";

        Request request = new Request.Builder().url(url).build();
        httpClient.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {}
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String jsonString = response.body().string(); JSONObject jsonObject = new JSONObject(jsonString); JSONArray routesArray = jsonObject.getJSONArray("routes");
                        List<RouteInfo> evaluatedRoutes = new ArrayList<>();

                        for (int i = 0; i < routesArray.length(); i++) {
                            JSONObject routeObj = routesArray.getJSONObject(i);
                            List<LatLng> path = decodePoly(routeObj.getString("geometry"));
                            int durationMins = (int) (routeObj.getDouble("duration") / 60);
                            double distKm = routeObj.getDouble("distance") / 1000.0;
                            int dangerScore = calculateDangerScore(path);
                            evaluatedRoutes.add(new RouteInfo(path, durationMins, distKm, dangerScore));
                        }
                        categorizeRoutes(evaluatedRoutes);
                        runOnUiThread(() -> drawRankedRoutes(evaluatedRoutes));
                    } catch (Exception e) {}
                }
            }
        });
    }

    private void clearRoutes() {
        for (Polyline line : activePolylines) line.remove(); for (Marker marker : activeRouteLabels) marker.remove();
        activePolylines.clear(); activeRouteLabels.clear();
        btnStartNav.setVisibility(View.GONE); drawCrimeZones();
    }

    private int calculateDangerScore(List<LatLng> path) {
        int score = 0;
        for (LatLng point : path) {
            boolean nearPolice = false;
            // CHECK 1: Is this point near a Police Station? (SAFE ZONE OVERRIDE)
            for (PoliceStation ps : policeStationsList) {
                float[] pDist = new float[1]; Location.distanceBetween(point.latitude, point.longitude, ps.latLng.latitude, ps.latLng.longitude, pDist);
                if (pDist[0] < 1500) { nearPolice = true; break; } // 1.5 KM radius of police station is pure safe
            }
            if (nearPolice) continue; // Skip danger points if under police coverage

            // CHECK 2: Danger zones
            for (LatLng red : rawRedZones) { float[] dist = new float[1]; Location.distanceBetween(point.latitude, point.longitude, red.latitude, red.longitude, dist); if (dist[0] < 400) score += 10; }
            for (LatLng yellow : rawYellowZones) { float[] dist = new float[1]; Location.distanceBetween(point.latitude, point.longitude, yellow.latitude, yellow.longitude, dist); if (dist[0] < 400) score += 2; }
        }
        return score;
    }

    private void categorizeRoutes(List<RouteInfo> routes) {
        if (routes.isEmpty()) return;
        if (routes.size() == 1) { routes.get(0).category = "🌟 Best Route"; return; } // Handled fallback gracefully

        RouteInfo fastest = Collections.min(routes, Comparator.comparingInt(r -> r.durationMins)); fastest.category = "🚀 Fastest";
        RouteInfo safest = Collections.min(routes, Comparator.comparingInt(r -> r.dangerScore)); safest.category = "🛡️ Safest";
        for (RouteInfo route : routes) { if (route != fastest && route != safest) route.category = "⚖️ Balanced"; }
        Collections.sort(routes, (r1, r2) -> { if (r1.category.contains("Safest") || r1.category.contains("Best")) return -1; return 1; });
    }

    private void drawRankedRoutes(List<RouteInfo> routes) {
        if (routes.isEmpty()) return;
        LatLngBounds.Builder builder = new LatLngBounds.Builder();

        for (int i = routes.size() - 1; i >= 0; i--) {
            RouteInfo route = routes.get(i);
            int routeColor = (route.category.contains("Safest") || route.category.contains("Best")) ? Color.parseColor("#188038") : (route.category.contains("Fastest") ? Color.parseColor("#1A73E8") : Color.parseColor("#F29900"));

            Polyline polyline = mMap.addPolyline(new PolylineOptions().addAll(route.path).width(16).color(routeColor).zIndex(100 - i).geodesic(true));
            activePolylines.add(polyline);
            for (LatLng point : route.path) builder.include(point);

            if (route.path.size() > 0) {
                Marker marker = mMap.addMarker(new MarkerOptions().position(route.path.get(route.path.size() / 2)).icon(BitmapDescriptorFactory.fromBitmap(createRouteTextBitmap(route.category + " (" + route.durationMins + "m)", routeColor))).zIndex(150 - i));
                activeRouteLabels.add(marker);
            }
        }

        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 150));
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        btnStartNav.setVisibility(View.VISIBLE); // Show Start Button

        RouteInfo bestRoute = routes.get(0);
        tvRouteTimeDist.setText(bestRoute.category + ": " + bestRoute.durationMins + " min (" + String.format(Locale.US, "%.1f", bestRoute.distKm) + " km)");
        tvRouteType.setText(bestRoute.dangerScore == 0 ? "🛡️ Completely covered by Safe Zones / Police" : "⚠️ Minor unlit areas detected ahead");
    }

    // Helpers
    private Bitmap createRouteTextBitmap(String text, int bgColor) { Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG); paint.setTextSize(36f); paint.setColor(Color.WHITE); paint.setFakeBoldText(true); Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG); bgPaint.setColor(bgColor); int width = (int) (paint.measureText(text) + 40); Bitmap bitmap = Bitmap.createBitmap(width, 60, Bitmap.Config.ARGB_8888); Canvas canvas = new Canvas(bitmap); canvas.drawRoundRect(0, 0, width, 60, 30, 30, bgPaint); canvas.drawText(text, 20, 42, paint); return bitmap; }
    private static class RouteInfo { List<LatLng> path; int durationMins; double distKm; int dangerScore; String category = ""; RouteInfo(List<LatLng> path, int durationMins, double distKm, int dangerScore) { this.path = path; this.durationMins = durationMins; this.distKm = distKm; this.dangerScore = dangerScore; } }
    private static class PoliceStation { String name; LatLng latLng; PoliceStation(String name, LatLng latLng) { this.name = name; this.latLng = latLng; } }
    private List<LatLng> decodePoly(String encoded) { List<LatLng> poly = new ArrayList<>(); int index = 0, len = encoded.length(), lat = 0, lng = 0; while (index < len) { int b, shift = 0, result = 0; do { b = encoded.charAt(index++) - 63; result |= (b & 0x1f) << shift; shift += 5; } while (b >= 0x20); int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1)); lat += dlat; shift = 0; result = 0; do { b = encoded.charAt(index++) - 63; result |= (b & 0x1f) << shift; shift += 5; } while (b >= 0x20); int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1)); lng += dlng; poly.add(new LatLng((((double) lat / 1E5)), (((double) lng / 1E5)))); } return poly; }
    // 🟢 1. REPLACE YOUR OLD setupUberStyleSearch() WITH THIS:
    private void setupUberStyleSearch() {
        // मैंने डेमो के लिए SKIT भी डाल दिया है!
        searchPlacesMap.put("SKIT Jaipur", new com.google.android.gms.maps.model.LatLng(26.8220, 75.8648));
        searchPlacesMap.put("SMS Hospital", new com.google.android.gms.maps.model.LatLng(26.8932, 75.8155));
        searchPlacesMap.put("WTP", new com.google.android.gms.maps.model.LatLng(26.8660, 75.8188));

        etSearchDestination.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, new java.util.ArrayList<>(searchPlacesMap.keySet())));

        // Dropdown Click (Demo Places)
        etSearchDestination.setOnItemClickListener((parent, view, position, id) -> {
            String selectedPlace = (String) parent.getItemAtPosition(position);
            com.google.android.gms.maps.model.LatLng dest = searchPlacesMap.get(selectedPlace);
            if (dest != null) {
                hideKeyboard();
                mMap.animateCamera(com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(dest, 16f));
            }
        });

        // 🟢 REAL DYNAMIC SEARCH: जब कीबोर्ड पर 'Search' या 'Enter' दबाएंगे
        etSearchDestination.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH ||
                    actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                    (event != null && event.getAction() == android.view.KeyEvent.ACTION_DOWN && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER)) {

                String query = etSearchDestination.getText().toString();
                if(!query.isEmpty()) {
                    performRealSearch(query);
                }
                return true;
            }
            return false;
        });
    }

    // 🟢 2. ADD THIS NEW FUNCTION BELOW IT (For Real World Search)
    private void performRealSearch(String locationName) {
        hideKeyboard();
        android.location.Geocoder geocoder = new android.location.Geocoder(this, java.util.Locale.getDefault());
        try {
            // "India" ऐड कर दिया ताकि रिज़ल्ट ज़्यादा एक्यूरेट आए
            java.util.List<android.location.Address> addresses = geocoder.getFromLocationName(locationName + ", India", 1);

            if (addresses != null && !addresses.isEmpty()) {
                android.location.Address address = addresses.get(0);
                com.google.android.gms.maps.model.LatLng latLng = new com.google.android.gms.maps.model.LatLng(address.getLatitude(), address.getLongitude());

                // Camera Move & Zoom
                mMap.animateCamera(com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(latLng, 16f));

                // Add Hacker Target Marker
                mMap.addMarker(new com.google.android.gms.maps.model.MarkerOptions()
                        .position(latLng)
                        .title(address.getAddressLine(0)));

                Toast.makeText(this, "Target Located! 🎯", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Location not found! 🚫", Toast.LENGTH_SHORT).show();
            }
        } catch (java.io.IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Search failed! Check Internet.", Toast.LENGTH_SHORT).show();
        }
    }

    // 🟢 3. ADD THIS TO HIDE KEYBOARD AFTER SEARCH
    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
    private void showGoogleStyleReportDialog() { Toast.makeText(this, "Report Menu Opened", Toast.LENGTH_SHORT).show(); }
    @Override protected void onDestroy() { super.onDestroy(); if (fusedLocationClient != null && locationCallback != null) fusedLocationClient.removeLocationUpdates(locationCallback); }
}