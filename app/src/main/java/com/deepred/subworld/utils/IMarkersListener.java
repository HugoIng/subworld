package com.deepred.subworld.utils;

import android.location.Location;

import com.google.android.gms.maps.model.LatLng;

/**
 *
 */
public interface IMarkersListener {
    public void updateMarker(String uid, LatLng latLng);
    public void updateMyMarker(Location loc);
    public void removeMarker(String uid);
    public void providerChanged(boolean isGps);
    public void setZoom(float zoom);
}