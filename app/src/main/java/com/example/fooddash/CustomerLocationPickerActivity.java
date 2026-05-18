package com.example.fooddash;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

public class CustomerLocationPickerActivity extends AppCompatActivity {

    public static final String EXTRA_LATITUDE = "extra_latitude";
    public static final String EXTRA_LONGITUDE = "extra_longitude";
    public static final String EXTRA_ADDRESS = "extra_address";

    private static final int REQUEST_LOCATION_PERMISSION = 501;
    private static final GeoPoint DEFAULT_CENTER = new GeoPoint(14.5995, 120.9842);

    private MapView mapView;
    private TextView locationSummaryText;
    private Button btnCurrentLocation;
    private Button btnConfirmLocation;
    private Button btnCancel;

    private Marker locationMarker;
    private GeoPoint selectedPoint = DEFAULT_CENTER;
    private String selectedAddress = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_customer_location_picker);

        mapView = findViewById(R.id.locationMapView);
        locationSummaryText = findViewById(R.id.locationSummaryText);
        btnCurrentLocation = findViewById(R.id.btnCurrentLocation);
        btnConfirmLocation = findViewById(R.id.btnConfirmLocation);
        btnCancel = findViewById(R.id.btnCancel);

        setupMap();
        restoreInitialPoint();

        btnCurrentLocation.setOnClickListener(v -> requestCurrentLocation());
        btnConfirmLocation.setOnClickListener(v -> returnSelectedLocation());
        btnCancel.setOnClickListener(v -> finish());

        if (LocationHelper.hasLocationPermission(this)) {
            requestCurrentLocation();
        } else {
            updateLocationSummary();
        }
    }

    private void setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        IMapController controller = mapView.getController();
        controller.setZoom(17.0);
        controller.setCenter(selectedPoint);

        locationMarker = new Marker(mapView);
        locationMarker.setPosition(selectedPoint);
        locationMarker.setDraggable(true);
        locationMarker.setOnMarkerDragListener(new Marker.OnMarkerDragListener() {
            @Override
            public void onMarkerDrag(Marker marker) {
                updateSelectedPoint(marker.getPosition());
            }

            @Override
            public void onMarkerDragEnd(Marker marker) {
                updateSelectedPoint(marker.getPosition());
            }

            @Override
            public void onMarkerDragStart(Marker marker) {
            }
        });
        mapView.getOverlays().add(locationMarker);

        mapView.setOnTouchListener((View view, MotionEvent event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                GeoPoint tappedPoint = (GeoPoint) mapView.getProjection().fromPixels((int) event.getX(), (int) event.getY());
                if (tappedPoint != null) {
                    updateSelectedPoint(tappedPoint);
                    locationSummaryText.setText(R.string.location_loading);
                    LocationHelper.resolveAddress(CustomerLocationPickerActivity.this,
                            tappedPoint.getLatitude(),
                            tappedPoint.getLongitude(),
                            address -> {
                                selectedAddress = address == null ? "" : address;
                                updateLocationSummary();
                            });
                }
            }
            return false;
        });
    }

    private void restoreInitialPoint() {
        Intent intent = getIntent();
        double latitude = intent.getDoubleExtra(EXTRA_LATITUDE, DEFAULT_CENTER.getLatitude());
        double longitude = intent.getDoubleExtra(EXTRA_LONGITUDE, DEFAULT_CENTER.getLongitude());
        selectedAddress = intent.getStringExtra(EXTRA_ADDRESS);
        if (selectedAddress == null) {
            selectedAddress = "";
        }
        selectedPoint = new GeoPoint(latitude, longitude);
        updateSelectedPoint(selectedPoint);
    }

    private void requestCurrentLocation() {
        if (!LocationHelper.hasLocationPermission(this)) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQUEST_LOCATION_PERMISSION);
            return;
        }

        btnCurrentLocation.setEnabled(false);
        locationSummaryText.setText(R.string.location_loading);
        LocationHelper.resolveCurrentLocation(this, new LocationHelper.LocationCallback() {
            @Override
            public void onLocationReady(LocationHelper.LocationData locationData) {
                btnCurrentLocation.setEnabled(true);
                if (locationData == null) {
                    Toast.makeText(CustomerLocationPickerActivity.this, "Unable to read current location", Toast.LENGTH_SHORT).show();
                    return;
                }
                selectedAddress = locationData.address;
                updateSelectedPoint(new GeoPoint(locationData.latitude, locationData.longitude));
            }

            @Override
            public void onError(String message) {
                btnCurrentLocation.setEnabled(true);
                Toast.makeText(CustomerLocationPickerActivity.this, message, Toast.LENGTH_LONG).show();
                updateLocationSummary();
            }
        });
    }

    private void updateSelectedPoint(GeoPoint point) {
        if (point == null) {
            return;
        }
        selectedPoint = point;
        locationMarker.setPosition(point);
        mapView.getController().setCenter(point);
        mapView.invalidate();
    }

    private void updateLocationSummary() {
        String summary;
        if (selectedAddress == null || selectedAddress.trim().isEmpty()) {
            summary = String.format("Lat: %.6f, Lng: %.6f", selectedPoint.getLatitude(), selectedPoint.getLongitude());
        } else {
            summary = selectedAddress + "\n" + String.format("Lat: %.6f, Lng: %.6f", selectedPoint.getLatitude(), selectedPoint.getLongitude());
        }
        locationSummaryText.setText(summary);
    }

    private void returnSelectedLocation() {
        Intent data = new Intent();
        data.putExtra(EXTRA_LATITUDE, selectedPoint.getLatitude());
        data.putExtra(EXTRA_LONGITUDE, selectedPoint.getLongitude());
        data.putExtra(EXTRA_ADDRESS, selectedAddress == null || selectedAddress.trim().isEmpty()
                ? String.format("Lat: %.6f, Lng: %.6f", selectedPoint.getLatitude(), selectedPoint.getLongitude())
                : selectedAddress);
        setResult(RESULT_OK, data);
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_LOCATION_PERMISSION) {
            return;
        }

        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            requestCurrentLocation();
        } else {
            Toast.makeText(this, getString(R.string.location_permission_required), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }
}
