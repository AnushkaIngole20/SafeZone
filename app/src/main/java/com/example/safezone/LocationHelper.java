package com.example.safezone;

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

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.util.List;
import java.util.Locale;

public class LocationHelper {

    public interface LocationResultCallback {
        void onSuccess(Location location, String addressOrCoordinates);
        void onError(String errorMessage);
    }

    public static boolean hasLocationPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean isLocationEnabled(Context context) {
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return false;
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    public static String getGoogleMapsUrl(double latitude, double longitude) {
        return "https://maps.google.com/?q=" + latitude + "," + longitude;
    }

    public static void getCurrentLocation(Context context, LocationResultCallback callback) {
        if (!hasLocationPermission(context)) {
            callback.onError("Location permission is not granted.");
            return;
        }

        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            callback.onError("Location manager service unavailable.");
            return;
        }

        boolean isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

        if (!isGpsEnabled && !isNetworkEnabled) {
            callback.onError("GPS and location services are disabled. Please turn on GPS.");
            return;
        }

        Location bestLastKnown = null;
        try {
            if (isGpsEnabled) {
                bestLastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
            if (bestLastKnown == null && isNetworkEnabled) {
                bestLastKnown = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
            if (bestLastKnown == null) {
                bestLastKnown = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
            }
        } catch (SecurityException e) {
            callback.onError("Security error accessing location.");
            return;
        }

        // If last known location was updated within the last 45 seconds, use it
        if (bestLastKnown != null && (System.currentTimeMillis() - bestLastKnown.getTime() < 45000)) {
            dispatchResult(context, bestLastKnown, callback);
            return;
        }

        final Location cachedFallback = bestLastKnown;
        final Handler handler = new Handler(Looper.getMainLooper());

        final LocationListener[] listenerHolder = new LocationListener[1];

        final Runnable timeoutRunnable = () -> {
            if (listenerHolder[0] != null) {
                try {
                    locationManager.removeUpdates(listenerHolder[0]);
                } catch (Exception ignored) {}
                listenerHolder[0] = null;
            }

            if (cachedFallback != null) {
                dispatchResult(context, cachedFallback, callback);
            } else {
                callback.onError("Unable to acquire GPS fix within timeout. Please ensure clear sky view or turn on Wi-Fi.");
            }
        };

        listenerHolder[0] = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                handler.removeCallbacks(timeoutRunnable);
                if (listenerHolder[0] != null) {
                    try {
                        locationManager.removeUpdates(listenerHolder[0]);
                    } catch (Exception ignored) {}
                    listenerHolder[0] = null;
                }
                dispatchResult(context, location, callback);
            }

            @Override
            public void onProviderEnabled(@NonNull String provider) {}

            @Override
            public void onProviderDisabled(@NonNull String provider) {}

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {}
        };

        try {
            if (isGpsEnabled) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, listenerHolder[0]);
            }
            if (isNetworkEnabled) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 0, listenerHolder[0]);
            }
            handler.postDelayed(timeoutRunnable, 8000);
        } catch (SecurityException e) {
            callback.onError("Security exception accessing location.");
        } catch (Exception e) {
            if (cachedFallback != null) {
                dispatchResult(context, cachedFallback, callback);
            } else {
                callback.onError("Error requesting location updates: " + e.getLocalizedMessage());
            }
        }
    }

    private static void dispatchResult(Context context, Location location, LocationResultCallback callback) {
        String addressString = formatCoordinates(location.getLatitude(), location.getLongitude());
        try {
            if (Geocoder.isPresent()) {
                Geocoder geocoder = new Geocoder(context, Locale.getDefault());
                List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                if (addresses != null && !addresses.isEmpty()) {
                    Address addr = addresses.get(0);
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i <= addr.getMaxAddressLineIndex(); i++) {
                        if (i > 0) sb.append(", ");
                        sb.append(addr.getAddressLine(i));
                    }
                    if (sb.length() > 0) {
                        addressString = sb.toString();
                    }
                }
            }
        } catch (Exception ignored) {}

        callback.onSuccess(location, addressString);
    }

    public static String formatCoordinates(double lat, double lng) {
        return String.format(Locale.US, "%.5f, %.5f", lat, lng);
    }
}
