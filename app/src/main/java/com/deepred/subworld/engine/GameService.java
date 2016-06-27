package com.deepred.subworld.engine;

import android.app.Activity;
import android.app.IntentService;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.os.ResultReceiver;
import android.util.Log;

import com.deepred.subworld.ICommon;
import com.deepred.subworld.InitApplication;
import com.deepred.subworld.model.User;
import com.deepred.subworld.utils.ILoginCallbacks;
import com.deepred.subworld.utils.IUserCallbacks;
import com.deepred.subworld.utils.IViewRangeListener;
import com.deepred.subworld.utils.MyUserManager;
import com.deepred.subworld.views.LoginActivity;
import com.mapbox.mapboxsdk.geometry.LatLng;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

//import com.google.android.gms.maps.model.LatLng;

/**
 * Created by aplicaty on 25/02/16.
 */
public class GameService extends IntentService implements IViewRangeListener {

    private static final int TWO_MINUTES = 1000 * 60 * 2;
    private static String TAG = "GameService";

    // User location
    private Location lastLocation;
    private boolean mapActivityIsResumed;

    private UsersViewRangeManager viewRange;

    // Pending locations, etc.
    private boolean hasMyLocationPending = false;
    private boolean hasLocationsPending = false;
    private Map<String, LatLng> locsPending = new HashMap<String, LatLng>();
    private boolean hasRemovesPending = false;
    private ArrayList<String> removesPending = new ArrayList<String>();
    private boolean hasZoomPending = false;
    private float zoomPending;
    private boolean hasProvPending = false;
    private boolean provPending;

    public GameService() {
        super(TAG);
        lastLocation = null;
        mapActivityIsResumed = false;
        viewRange = UsersViewRangeManager.getInstance();
        viewRange.setServiceContext(this);
    }

    @Override
    protected void onHandleIntent(final Intent workIntent) {
        // Gets data from the incoming Intent
        String dataString = workIntent.getDataString();

        if (dataString.equals(ICommon.NEW_LOCATION_FROM_SRV)) {
            /*
            * A new location is provided by GoogleLocationServiceImpl
             */
            Bundle bundle = workIntent.getExtras();
            if (bundle == null) {
                return;
            }
            Location location = bundle.getParcelable(ICommon.NEW_LOCATION_FROM_SRV);
            if (location == null) {
                return;
            }

            // Filter old, unaccurate locations
            if (isBetterLocation(location)) {
                lastLocation = location;
                // Deal with new location: DDBB query to update the rivals that are within are view range
                viewRange.update(lastLocation);
            }

        } else if (dataString.equals(ICommon.MAP_ACTIVITY_RESUMED)) {
            /*
            * Map activity just resumed
             */
            mapActivityIsResumed = true;

            if (hasMyLocationPending) {
                // Broadcast my location
                broadcastLocation(lastLocation);
                hasMyLocationPending = false;
            }

            if (hasLocationsPending) {
                for (String uid : locsPending.keySet()) {
                    // Broadcast rivals locations
                    broadcastRivalLocation(uid, locsPending.get(uid));
                    locsPending.remove(uid);
                }
                hasLocationsPending = false;
            }

            if (hasRemovesPending) {
                for (String uid : removesPending) {
                    // Remove rivals from map
                    broadcastRemoveRival(uid);
                    removesPending.remove(uid);
                }
                hasRemovesPending = false;
            }

            if (hasZoomPending) {
                // Broadcast zoom change
                broadcastZoom(zoomPending);
                hasZoomPending = false;
            }

            if (hasProvPending) {
                // Broadcast provider change
                broadcastProvider(provPending);
                hasProvPending = false;
            }

        } else if (dataString.equals(ICommon.MAP_ACTIVITY_PAUSED)) {
            /*
            * Map activity just paused
             */
            mapActivityIsResumed = false;

        } else if (dataString.equals(ICommon.SET_BACKGROUND_STATUS)) {
            /*
            *
             */
            Bundle bundle = workIntent.getExtras();
            if (bundle == null) {
                return;
            }
            boolean status = bundle.getBoolean(ICommon.SET_BACKGROUND_STATUS);
            changeBackgroundState(status);
        } else if (dataString.equals(ICommon.LOGIN_REGISTER)) {
            /*
            * Login or register
             */
            String email = workIntent.getStringExtra(ICommon.EMAIL);
            String password = workIntent.getStringExtra(ICommon.PASSWORD);
            final String screen_context = workIntent.getStringExtra(ICommon.SCREEN_CONTEXT);

            // Login with credentials
            DataManager.getInstance().loginOrRegister(email, password, new ILoginCallbacks() {

                @Override
                public void onLoginOk(boolean wait4User) {
                    Log.v(TAG, "Requesting login on firebase");
                    /*LocationService serv = ApplicationHolder.getApp().getLocationService();
                    if(serv != null)
                        serv.onBBDDConnected();
                    else
                        LocationService.setBBDDConnected();*/

                    Intent localIntent = new Intent(ICommon.BBDD_CONNECTED);
                    // Broadcasts the Intent to receivers in this app.
                    LocalBroadcastManager.getInstance(GameService.this).sendBroadcast(localIntent);

                    DataManager.getInstance().getUser();

                    if (screen_context.equals(LoginActivity.class.getName())) {
                        // From LoginActivity: notify the screen to update interface
                        ResultReceiver rec = workIntent.getParcelableExtra(ICommon.RESULT_RECEIVER);
                        //Bundle b= new Bundle();
                        //b.putString("ServiceTag","aziz");
                        rec.send(Activity.RESULT_OK, /*b*/ null);
                    }
                }

                @Override
                public void onLoginError() {

                    if (screen_context.equals(InitApplication.class.getName())) {
                        // From InitActivity navigate to loginActivity
                        Intent outI = new Intent(GameService.this, LoginActivity.class);
                        outI.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(outI);
                    } else {
                        // From LoginActivity: notify the screen to update interface
                        ResultReceiver rec = workIntent.getParcelableExtra(ICommon.RESULT_RECEIVER);
                        //Bundle b= new Bundle();
                        //b.putString("ServiceTag","aziz");
                        rec.send(Activity.RESULT_CANCELED, /*b*/ null);
                    }


                    // Si es desde la pantalla de init
                    Intent outI = new Intent(GameService.this, LoginActivity.class);
                    outI.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(outI);

                    // Si es desde la de login, motrar error
                    /*showProgress(false);
                    mPasswordView.setError(getString(R.string.error_incorrect_password));
                    mPasswordView.requestFocus();*/
                }
            });
        }
    }


    @Override
    public void updateMyLocation() {
        if (mapActivityIsResumed) {
            // Broadcast my location
            broadcastLocation(lastLocation);
        } else {
            hasMyLocationPending = true;
        }
    }

    @Override
    public void updateRivalLocation(String uid, LatLng latLng) {
        if (mapActivityIsResumed) {
            // Broadcast rival
            broadcastRivalLocation(uid, latLng);
        } else {
            locsPending.put(uid, latLng);
            hasLocationsPending = true;
        }
    }

    @Override
    public void removeRivalLocation(String uid) {
        if (mapActivityIsResumed) {
            // Broadcast marker id to be erased from map
            broadcastRemoveRival(uid);
        } else {
            removesPending.add(uid);
            hasRemovesPending = true;
        }
    }

    @Override
    public void setZoom(float zoom) {
        if (mapActivityIsResumed) {
            // Broadcast zoom change
            broadcastZoom(zoom);
        } else {
            zoomPending = zoom;
            hasZoomPending = true;
        }
    }

    /*

     */
    private void updateProvider(boolean status) {
        if (mapActivityIsResumed) {
            // Broadcast provider status
            broadcastProvider(status);
        } else {
            provPending = status;
            hasProvPending = true;
        }
    }

    /*
    * Helper methods to send the location to the screen
     */
    private void broadcastLocation(Location loc) {
        Intent localIntent =
                new Intent(ICommon.MY_LOCATION)
                        // Puts the status into the Intent
                        .putExtra(ICommon.MY_LOCATION, loc);
        // Broadcasts the Intent to receivers in this app.
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
    }

    private void broadcastRivalLocation(String uid, LatLng latLng) {
        Intent localIntent =
                new Intent(ICommon.RIVAL_LOCATION)
                        // Puts the status into the Intent
                        .putExtra(ICommon.RIVAL_LOCATION, latLng)
                        .putExtra(ICommon.UID, uid);
        // Broadcasts the Intent to receivers in this app.
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
    }

    private void broadcastRemoveRival(String uid) {
        Intent localIntent =
                new Intent(ICommon.REMOVE_RIVAL_LOCATION)
                        // Puts the status into the Intent
                        .putExtra(ICommon.UID, uid);
        // Broadcasts the Intent to receivers in this app.
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
    }

    private void broadcastZoom(float zoom) {
        Intent localIntent =
                new Intent(ICommon.SET_ZOOM)
                        // Puts the status into the Intent
                        .putExtra(ICommon.SET_ZOOM, zoom);
        // Broadcasts the Intent to receivers in this app.
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
    }

    private void broadcastProvider(boolean prov) {
        Intent localIntent =
                new Intent(ICommon.SET_PROVIDER_INFO)
                        // Puts the status into the Intent
                        .putExtra(ICommon.SET_PROVIDER_INFO, prov);
        // Broadcasts the Intent to receivers in this app.
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
    }


    /**
     * Determines whether one Location reading is better than the current Location fix
     * @param location  The new Location that you want to evaluate
     */
    protected boolean isBetterLocation(Location location) {
        if (lastLocation == null) {
            // A new location is always better than no location
            return true;
        }

        // Check whether the new location fix is newer or older
        long timeDelta = location.getTime() - lastLocation.getTime();
        boolean isSignificantlyNewer = timeDelta > TWO_MINUTES;
        boolean isSignificantlyOlder = timeDelta < -TWO_MINUTES;
        boolean isNewer = timeDelta > 0;

        // If it's been more than two minutes since the current location, use the new location
        // because the user has likely moved
        if (isSignificantlyNewer) {
            return true;
            // If the new location is more than two minutes older, it must be worse
        } else if (isSignificantlyOlder) {
            return false;
        }

        // Check whether the new location fix is more or less accurate
        int accuracyDelta = (int) (location.getAccuracy() - lastLocation.getAccuracy());
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 200;

        // Check if the old and new location are from the same provider
        boolean isFromSameProvider = isSameProvider(location.getProvider(),
                lastLocation.getProvider());

        // Determine location quality using a combination of timeliness and accuracy
        if (isMoreAccurate) {
            return true;
        } else if (isNewer && !isLessAccurate) {
            return true;
        } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
            return true;
        }
        return false;
    }

    /** Checks whether two providers are the same */
    private boolean isSameProvider(String provider1, String provider2) {
        if (provider1 == null) {
            return provider2 == null;
        }
        return provider1.equals(provider2);
    }



    public void changeBackgroundState(boolean backgroudStatus) {
        Log.d(TAG, "changeBackgroundState:" + backgroudStatus);
        // App in fore/background status
        // Si pasamos a background bajamos la resolucion del GPS
        // Si pasamos a foreground subimos la precision del GPS
        updateProvider(!backgroudStatus);
    }

    /*private boolean checkBackgroundStatus() {
        Log.d(TAG, "checkBackgroundStatus");
        boolean backgroudStatus = ApplicationLifecycleHandler.getInstance().isAppInBackground();
        boolean useGps = !backgroudStatus;
        updateProvider(useGps);
        return useGps;
    }*/


    public void checkVisibility(String uid, LatLng loc, final UsersViewRangeManager.IVisibilityCompletionListener cb) {
        // Aplicar las reglas de visibilidad entre mi usuario y este
        Log.d(TAG, "checkVisibility: " + uid);

        final User myUser = MyUserManager.getInstance().getUser();
        Location otherUserLocation = new Location("?");
        otherUserLocation.setLatitude(loc.getLatitude());
        otherUserLocation.setLongitude(loc.getLongitude());
        final float distance = lastLocation.distanceTo(otherUserLocation);

        // Obtener el usuario de la BBDD
        DataManager.getInstance().getUser(uid, new IUserCallbacks() {
            @Override
            public void onUserChange(User user) {
                boolean isVisible = applyVisibilityRules(myUser, user, distance);
                Log.d(TAG, "checkVisibility returns:" + isVisible);
                cb.onCompleted(isVisible);
            }
        });
    }

    private boolean applyVisibilityRules(User myUser, User otherUser, float distance) {
        boolean ret = false;
        int myWatchingSkill = myUser.getSkills().getWatching().getValue();
        int otherHidingSkill = otherUser.getSkills().getHiding().getValue();
        int tot = myWatchingSkill - otherHidingSkill;
        if(tot < 0)
            tot = 0;
        int range = calculateDistanceRange(distance);
        if (range > -1)
            return ICommon.distanceTable[tot][range];
        else
            return false;
    }

    private int calculateDistanceRange(float distance) {
        Float d = new Float(distance);
        int dist = d.intValue();
        for (int i = 0; i < ICommon.distanceRanges.length; i++) {
            if(dist <= ICommon.distanceRanges[i]) {
                return i;
            }
        }
        return -1;
    }
}