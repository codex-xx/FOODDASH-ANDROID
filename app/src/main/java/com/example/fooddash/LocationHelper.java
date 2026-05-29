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
import java.util.concurrent.atomic.AtomicBoolean;

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
    private static final long LOCATION_UPDATE_TIMEOUT_MS = 12000;
    private static final long LAST_KNOWN_MAX_AGE_MS = 5 * 60 * 1000;
    private static final float ACCEPTABLE_ACCURACY_METERS = 50f;

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
        if (bestLastKnown != null
                && bestLastKnown.hasAccuracy()
                && bestLastKnown.getAccuracy() <= ACCEPTABLE_ACCURACY_METERS
                && System.currentTimeMillis() - bestLastKnown.getTime() <= LAST_KNOWN_MAX_AGE_MS) {
            reverseGeocodeAndReturn(context, bestLastKnown.getLatitude(), bestLastKnown.getLongitude(), callback);
            return;
        }

        requestCurrentLocation(context, locationManager, callback);
    }

    private static void requestCurrentLocation(Context context, LocationManager locationManager, LocationCallback callback) {
        final AtomicBoolean callbackCalled = new AtomicBoolean(false);
        final Location[] bestLocation = new Location[1];
        final Handler handler = new Handler(Looper.getMainLooper());
        final Runnable[] timeoutTaskHolder = new Runnable[1];

        final LocationListener listener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                if (location == null) {
                    return;
                }
                if (isBetterLocation(location, bestLocation[0])) {
                    bestLocation[0] = location;
                }
                if (location.hasAccuracy() && location.getAccuracy() <= ACCEPTABLE_ACCURACY_METERS) {
                    if (callbackCalled.compareAndSet(false, true)) {
                        removeUpdates(locationManager, this);
                        handler.removeCallbacks(timeoutTaskHolder[0]);
                        reverseGeocodeAndReturn(context, location.getLatitude(), location.getLongitude(), callback);
                    }
                }
            }

            @Override public void onProviderEnabled(String provider) { }
            @Override public void onProviderDisabled(String provider) { }
            @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
        };

        final Runnable timeoutTask = () -> {
            if (callbackCalled.compareAndSet(false, true)) {
                removeUpdates(locationManager, listener);
                if (bestLocation[0] != null) {
                    reverseGeocodeAndReturn(context, bestLocation[0].getLatitude(), bestLocation[0].getLongitude(), callback);
                } else {
                    callback.onError("Unable to read the current location.");
                }
            }
        };
        timeoutTaskHolder[0] = timeoutTask;

        try {
            boolean requested = false;
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0f, listener, Looper.getMainLooper());
                requested = true;
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 0f, listener, Looper.getMainLooper());
                requested = true;
            }
            if (!requested) {
                callback.onError("Location providers are disabled.");
                return;
            }
            handler.postDelayed(timeoutTask, LOCATION_UPDATE_TIMEOUT_MS);
        } catch (SecurityException securityException) {
            if (callbackCalled.compareAndSet(false, true)) {
                callback.onError("Location permission is required to detect the current location.");
            }
        } catch (IllegalArgumentException illegalArgumentException) {
            if (callbackCalled.compareAndSet(false, true)) {
                callback.onError("Unable to access location provider.");
            }
        }
    }

    private static void removeUpdates(LocationManager locationManager, LocationListener listener) {
        try {
            locationManager.removeUpdates(listener);
        } catch (SecurityException | IllegalArgumentException ignored) {
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

        return null;
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
