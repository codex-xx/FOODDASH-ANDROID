package com.example.fooddash;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.core.content.ContextCompat;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LocationHelper {

    public interface AddressCallback {
        void onAddressReady(String address);
    }

    public interface LocationCallback {
        void onLocationReady(LocationData locationData);
        void onError(String message);
    }

    public static final class LocationData {
        public final double latitude;
        public final double longitude;
        public final String address;

        public LocationData(double latitude, double longitude, String address) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.address = address == null ? "" : address;
        }
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private LocationHelper() {
    }

    public static boolean hasLocationPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public static void resolveCurrentLocation(Context context, LocationCallback callback) {
        if (context == null || callback == null) {
            return;
        }

        if (!hasLocationPermission(context)) {
            callback.onError("Location permission is required to detect the current location.");
            return;
        }

        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            callback.onError("Location services are unavailable on this device.");
            return;
        }

        Location bestLastKnown = getBestLastKnownLocation(locationManager);
        if (bestLastKnown != null) {
            reverseGeocodeAndReturn(context, bestLastKnown.getLatitude(), bestLastKnown.getLongitude(), callback);
            return;
        }

        String provider = getBestProvider(locationManager);
        if (TextUtils.isEmpty(provider)) {
            callback.onError("Could not determine the device location.");
            return;
        }

        LocationListener listener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                locationManager.removeUpdates(this);
                if (location == null) {
                    callback.onError("Unable to read the current location.");
                    return;
                }
                reverseGeocodeAndReturn(context, location.getLatitude(), location.getLongitude(), callback);
            }

            @Override public void onProviderEnabled(String provider) { }
            @Override public void onProviderDisabled(String provider) { }
            @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
        };

        try {
            locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper());
        } catch (SecurityException securityException) {
            callback.onError("Location permission is required to detect the current location.");
        } catch (IllegalArgumentException illegalArgumentException) {
            callback.onError("Unable to access location provider.");
        }
    }

    public static void resolveAddress(Context context, double latitude, double longitude, AddressCallback callback) {
        if (context == null || callback == null) {
            return;
        }

        EXECUTOR.execute(() -> {
            String address = buildAddress(context, latitude, longitude);
            new Handler(Looper.getMainLooper()).post(() -> callback.onAddressReady(address));
        });
    }

    private static Location getBestLastKnownLocation(LocationManager locationManager) {
        Location gps = null;
        Location network = null;
        Location passive = null;

        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                gps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
        } catch (SecurityException ignored) {
        }

        try {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                network = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
        } catch (SecurityException ignored) {
        }

        try {
            passive = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
        } catch (SecurityException ignored) {
        }

        Location best = gps;
        if (isBetterLocation(network, best)) {
            best = network;
        }
        if (isBetterLocation(passive, best)) {
            best = passive;
        }
        return best;
    }

    private static boolean isBetterLocation(Location candidate, Location currentBest) {
        if (candidate == null) {
            return false;
        }
        if (currentBest == null) {
            return true;
        }
        return candidate.getTime() > currentBest.getTime();
    }

    private static String getBestProvider(LocationManager locationManager) {
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                return LocationManager.GPS_PROVIDER;
            }
        } catch (SecurityException ignored) {
        }

        try {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                return LocationManager.NETWORK_PROVIDER;
            }
        } catch (SecurityException ignored) {
        }

        return LocationManager.PASSIVE_PROVIDER;
    }

    private static void reverseGeocodeAndReturn(Context context, double latitude, double longitude, LocationCallback callback) {
        EXECUTOR.execute(() -> {
            String address = buildAddress(context, latitude, longitude);
            new Handler(Looper.getMainLooper()).post(() -> callback.onLocationReady(new LocationData(latitude, longitude, address)));
        });
    }

    private static String buildAddress(Context context, double latitude, double longitude) {
        try {
            Geocoder geocoder = new Geocoder(context, Locale.getDefault());
            List<Address> results = geocoder.getFromLocation(latitude, longitude, 1);
            if (results != null && !results.isEmpty()) {
                Address address = results.get(0);
                String line = address.getAddressLine(0);
                if (!TextUtils.isEmpty(line)) {
                    return line;
                }
                StringBuilder builder = new StringBuilder();
                for (int i = 0; i <= address.getMaxAddressLineIndex(); i++) {
                    String addressLine = address.getAddressLine(i);
                    if (!TextUtils.isEmpty(addressLine)) {
                        if (builder.length() > 0) {
                            builder.append(", ");
                        }
                        builder.append(addressLine);
                    }
                }
                if (builder.length() > 0) {
                    return builder.toString();
                }
            }
        } catch (Exception ignored) {
        }

        return String.format(Locale.getDefault(), "Lat: %.6f, Lng: %.6f", latitude, longitude);
    }
}
