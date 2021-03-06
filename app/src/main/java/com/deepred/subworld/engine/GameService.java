package com.deepred.subworld.engine;

import android.app.Activity;
import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.deepred.subworld.ICommon;
import com.deepred.subworld.SubworldApplication;
import com.deepred.subworld.model.MapElement;
import com.deepred.subworld.model.MapRival;
import com.deepred.subworld.model.MapTreasure;
import com.deepred.subworld.model.Treasure;
import com.deepred.subworld.model.User;
import com.deepred.subworld.utils.ICallbacks;
import com.deepred.subworld.utils.IViewRangeListener;
import com.deepred.subworld.utils.MyUserManager;
import com.firebase.geofire.GeoLocation;
import com.mapbox.mapboxsdk.geometry.LatLng;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


/**
 * Created by aplicaty on 25/02/16.
 */
public class GameService extends IntentService implements IViewRangeListener {
    private final static String TAG = "SW ENGINE GameService  ";

    // User location
    private Location lastLocation;
    private boolean mapActivityIsInBackground;

    private ViewRangeManager viewRange;
    private DataManager dataManager;

    // Pending locations, etc.
    private boolean hasMyLocationPending = false;
    private boolean hasLocationsPending = false;
    private Map<String, LatLng> locsPendingRivals = new HashMap<>();
    private Map<String, LatLng> locsPendingTreasures = new HashMap<>();
    private boolean hasRemovesPending = false;
    private ArrayList<String> removesPendingRivals = new ArrayList<>();
    private ArrayList<String> removesPendingTreasures = new ArrayList<>();
    private boolean hasZoomPending = false;
    private float zoomPending;
    //private boolean hasProvPending = false;
    //private boolean provPending;

    public GameService() {
        super(TAG);
        mapActivityIsInBackground = false;
        viewRange = ViewRangeManager.getInstance();
        dataManager = DataManager.getInstance();
        viewRange.setContext(this, dataManager);
        dataManager.setContext(getBaseContext(), viewRange);

        // Get initial location from localStorage or default
        //lastLocation = ApplicationHolder.getApp().getLastKnownLocation();
        lastLocation = ((SubworldApplication)getApplication()).getLastKnownLocation();
    }

    @Override
    protected void onHandleIntent(final Intent workIntent) {
        // Gets data from the incoming Intent
        String dataString = workIntent.getDataString();

        switch (dataString) {
            case ICommon.NEW_LOCATION_FROM_SRV: {
                /*
                * A new location is provided by GoogleLocationServiceImpl for my user
                 */
                Log.v(TAG, "Msg received: NEW_LOCATION_FROM_SRV");
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
                    // Store location
                    //ApplicationHolder.getApp().setLastKnownLocation(location);
                    ((SubworldApplication)getApplication()).setLastKnownLocation(location);
                    // Deal with new location: DDBB query to update the rivals that are within are view range
                    viewRange.update(lastLocation);
                }

                break;
            }
            case ICommon.SET_BACKGROUND_STATUS: {
                /*
                *
                 */
                Log.v(TAG, "Msg received: SET_BACKGROUND_STATUS");
                Bundle bundle = workIntent.getExtras();
                if (bundle == null) {
                    return;
                }
                mapActivityIsInBackground = bundle.getBoolean(ICommon.SET_BACKGROUND_STATUS);

                if (!mapActivityIsInBackground) {
                    if (hasMyLocationPending) {
                        // Broadcast my location
                        broadcastMyLocation(lastLocation);
                        hasMyLocationPending = false;
                    }

                    if (hasLocationsPending) {
                        for (String uid : locsPendingRivals.keySet()) {
                            // Broadcast rivals locations
                            broadcastMapElementlLocation(uid, ICommon.LOCATION_TYPE_RIVAL, locsPendingRivals.get(uid));
                            locsPendingRivals.remove(uid);
                        }
                        for (String uid : locsPendingTreasures.keySet()) {
                            // Broadcast rivals locations
                            broadcastMapElementlLocation(uid, ICommon.LOCATION_TYPE_TREASURE, locsPendingTreasures.get(uid));
                            locsPendingTreasures.remove(uid);
                        }
                        hasLocationsPending = false;
                    }

                    if (hasRemovesPending) {
                        for (String uid : removesPendingRivals) {
                            // Remove rivals from map
                            broadcastRemoveMapElement(uid);
                            removesPendingRivals.remove(uid);
                        }
                        for (String uid : removesPendingTreasures) {
                            // Remove rivals from map
                            broadcastRemoveMapElement(uid);
                            removesPendingTreasures.remove(uid);
                        }
                        hasRemovesPending = false;
                    }

                    if (hasZoomPending) {
                        // Broadcast zoom change
                        broadcastZoom(zoomPending);
                        hasZoomPending = false;
                    }
                }

                break;
            }
            case ICommon.LOGIN_REGISTER:
                /*
                * Login or register
                 */
                Log.v(TAG, "Msg received: LOGIN_REGISTER");
                String email = workIntent.getStringExtra(ICommon.EMAIL);
                String password = workIntent.getStringExtra(ICommon.PASSWORD);
                final String screen_context = workIntent.getStringExtra(ICommon.SCREEN_CONTEXT);

                // Login with credentials
                dataManager.loginOrRegister(email, password, new ICallbacks.ILoginCallbacks() {

                    @Override
                    public void onLoginOk(boolean wait4User) {
                        Log.v(TAG, "Requesting login on firebase");

                        dataManager.getUser();

                        // Notify the screen to update interface
                        ResultReceiver rec = workIntent.getParcelableExtra(ICommon.RESULT_RECEIVER);
                        //Bundle b= new Bundle();
                        //b.putString("ServiceTag","aziz");
                        rec.send(Activity.RESULT_OK, /*b*/ null);
                    }

                    @Override
                    public void onLoginError() {
                        // From LoginActivity: notify the screen to update interface
                        ResultReceiver rec = workIntent.getParcelableExtra(ICommon.RESULT_RECEIVER);
                        //Bundle b= new Bundle();
                        //b.putString("ServiceTag","aziz");
                        rec.send(Activity.RESULT_CANCELED, /*b*/ null);
                    }
                });
                break;
            case ICommon.CHECK_NAME:
                /*

                 */
                Log.v(TAG, "Msg received: CHECK_NAME");
                final String name = workIntent.getStringExtra(ICommon.NAME);
                final int chr_selected = workIntent.getIntExtra(ICommon.CHR_TYPE, ICommon.CHRS_NOT_SET);

                dataManager.checkName(name, new ICallbacks.INameCheckCallbacks() {
                    @Override
                    public void onValidUsername() {
                        dataManager.storeUsername(name, new ICallbacks.IStoreCallbacks() {
                            @Override
                            public void onSuccess() {
                                final User u = MyUserManager.getInstance().getUser();
                                String uid = dataManager.getUid();
                                u.setUid(uid);
                                u.setName(name);
                                u.setChrType(chr_selected);
                                //SharedPreferences prefs = ApplicationHolder.getApp().getPreferences();
                                SharedPreferences prefs = ((SubworldApplication)getApplication()).getPreferences();
                                u.setEmail(prefs.getString(ICommon.EMAIL, null));

                                // Generate and save 2 treasures randomly
                                addDefaultTreasures(u, new ICallbacks.IStoreCallbacks() {
                                    @Override
                                    public void onError() {
                                        ResultReceiver rec = workIntent.getParcelableExtra(ICommon.RESULT_RECEIVER);
                                        Bundle b = new Bundle();
                                        b.putString(ICommon.MOTIVE, "Error storing treasures. Try again later.");
                                        rec.send(Activity.RESULT_CANCELED, b);
                                    }

                                    @Override
                                    public void onSuccess() {
                                        // Save user changes
                                        dataManager.saveUser(u, new ICallbacks.IStoreCallbacks() {
                                            @Override
                                            public void onError() {
                                                ResultReceiver rec = workIntent.getParcelableExtra(ICommon.RESULT_RECEIVER);
                                                Bundle b = new Bundle();
                                                b.putString(ICommon.MOTIVE, "Error storing user. Try again later.");
                                                rec.send(Activity.RESULT_CANCELED, b);
                                            }

                                            @Override
                                            public void onSuccess() {
                                                ResultReceiver rec = workIntent.getParcelableExtra(ICommon.RESULT_RECEIVER);
                                                rec.send(Activity.RESULT_OK, null);
                                            }
                                        });
                                    }
                                });

                            }

                            @Override
                            public void onError() {
                                ResultReceiver rec = workIntent.getParcelableExtra(ICommon.RESULT_RECEIVER);
                                Bundle b = new Bundle();
                                b.putString(ICommon.MOTIVE, "Error storing user name. Try again later.");
                                rec.send(Activity.RESULT_CANCELED, b);
                            }
                        });
                    }

                    @Override
                    public void onNameAlreadyExists() {
                        ResultReceiver rec = workIntent.getParcelableExtra(ICommon.RESULT_RECEIVER);
                        Bundle b = new Bundle();
                        b.putString(ICommon.MOTIVE, "Name already exists");
                        rec.send(Activity.RESULT_CANCELED, b);
                    }
                });

                break;

            case ICommon.MAPELEMENT_SELECTED:
                /*

                 */
                Log.v(TAG, "Msg received: MAPELEMENT_SELECTED");
                final String uid = workIntent.getStringExtra(ICommon.UID);
                if (uid != null && !uid.isEmpty()) {
                    MapElement r = viewRange.getMapElement(uid);

                    // Acciones en funcion de las habilidades y la distancia

                    // Info a mostrar del usuario

                    /*Intent outI = new Intent(this, UserActionActivity.class);
                    Location loc = r.getLocation();
                    // distancia entre mi usuario y el rival
                    float distance = loc.distanceTo(lastLocation);
                    outI.putExtra(ICommon.DISTANCE, Double.toString(distance));
                    startActivity(outI);*/

                    Log.v(TAG, "sending SHOW_ACTION_SCREEN");
                    Intent localIntent =
                            new Intent(ICommon.SHOW_ACTION_SCREEN)
                                    // Puts the status into the Intent
                                    .putExtra(ICommon.UID, uid);
                    // Broadcasts the Intent to receivers in this app.
                    LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);

                }
                break;
        }
    }

    private void addDefaultTreasures(final User user, final ICallbacks.IStoreCallbacks cb) {
        final Treasure t = new Treasure(user.getUid());
        dataManager.saveTreasure(t, new ICallbacks.IStoreCallbacks() {
            @Override
            public void onError() {
                cb.onError();
            }

            @Override
            public void onSuccess() {
                final Treasure t2 = new Treasure(user.getUid());
                dataManager.saveTreasure(t2, new ICallbacks.IStoreCallbacks() {
                    @Override
                    public void onError() {
                        cb.onError();
                    }

                    @Override
                    public void onSuccess() {
                        user.getBackpack().put(t.getUid(), t);
                        user.getBackpack().put(t2.getUid(), t2);
                        cb.onSuccess();
                    }
                });
            }
        });
    }


    @Override
    public void updateMyLocation() {
        if (!mapActivityIsInBackground) {
            // Broadcast my location
            broadcastMyLocation(lastLocation);
        } else {
            Log.v(TAG, "updateMyLocation: Storing pending MyLocation");
            hasMyLocationPending = true;
        }
    }

    @Override
    public void updateMapElementLocation(String uid, int type, LatLng latLng) {
        if (!mapActivityIsInBackground) {
            // Broadcast rival
            broadcastMapElementlLocation(uid, type, latLng);
        } else {
            Log.v(TAG, "updateMapElementLocation: Storing pending MapElement");
            if (type == ICommon.LOCATION_TYPE_RIVAL) {
                locsPendingRivals.put(uid, latLng);
                if (removesPendingRivals.contains(uid)) {
                    removesPendingRivals.remove(uid);
                }
            } else {
                locsPendingTreasures.put(uid, latLng);
                if (removesPendingTreasures.contains(uid)) {
                    removesPendingTreasures.remove(uid);
                }
            }
            hasLocationsPending = true;
        }
    }

    @Override
    public void removeMapElementLocation(String uid, int type) {
        if (!mapActivityIsInBackground) {
            // Broadcast marker id to be erased from map
            broadcastRemoveMapElement(uid);
        } else {
            Log.v(TAG, "removeMapElementLocation: removing pending MapElement");
            if (type == ICommon.LOCATION_TYPE_RIVAL) {
                removesPendingRivals.add(uid);
                if (locsPendingRivals.containsKey(uid)) {
                    locsPendingRivals.remove(uid);
                }
            } else {
                removesPendingTreasures.add(uid);
                if (locsPendingTreasures.containsKey(uid)) {
                    locsPendingTreasures.remove(uid);
                }
            }
            hasRemovesPending = true;
        }
    }

    @Override
    public void setZoom(float zoom) {
        if (!mapActivityIsInBackground) {
            // Broadcast zoom change
            broadcastZoom(zoom);
        } else {
            Log.v(TAG, "setZoom: Store pending zoom");
            zoomPending = zoom;
            hasZoomPending = true;
        }
    }

    /*
    * Helper methods to send the location to the screen
     */
    private void broadcastMyLocation(Location loc) {
        if (loc == null) {
            Log.e(TAG, "broadcastMyLocation: Location is null");
            return;
        }
        Log.v(TAG, "broadcastMyLocation: sending MyLocation");
        Intent localIntent =
                new Intent(ICommon.MY_LOCATION)
                        // Puts the status into the Intent
                        .putExtra(ICommon.MY_LOCATION, loc);
        // Broadcasts the Intent to receivers in this app.
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
    }

    private void broadcastMapElementlLocation(String uid, int tipo, LatLng latLng) {
        if (latLng == null) {
            Log.e(TAG, "broadcastMapElementlLocation: MapElement is null");
            return;
        }
        Log.v(TAG, "broadcastMapElementlLocation: sending MapElement");
        Intent localIntent =
                new Intent(ICommon.MAPELEMENT_LOCATION)
                        // Puts the status into the Intent
                        .putExtra(ICommon.MAPELEMENT_LOCATION, latLng)
                        .putExtra(ICommon.MAPELEMENT_TYPE, (tipo == ICommon.LOCATION_TYPE_RIVAL) ? ICommon.MARKER_RIVAL : ICommon.MARKER_TREASURE)
                        .putExtra(ICommon.UID, uid);
        // Broadcasts the Intent to receivers in this app.
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
    }

    private void broadcastRemoveMapElement(String uid) {
        Log.v(TAG, "broadcastRemoveMapElement: request MapElement removal");
        Intent localIntent =
                new Intent(ICommon.REMOVE_MAPELEMENT_LOCATION)
                        // Puts the status into the Intent
                        .putExtra(ICommon.UID, uid);
        // Broadcasts the Intent to receivers in this app.
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
    }

    private void broadcastZoom(float zoom) {
        Log.v(TAG, "broadcastZoom: sending zoom");
        Intent localIntent =
                new Intent(ICommon.SET_ZOOM)
                        // Puts the status into the Intent
                        .putExtra(ICommon.SET_ZOOM, zoom);
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
        boolean isSignificantlyNewer = timeDelta > ICommon.TWO_MINUTES;
        boolean isSignificantlyOlder = timeDelta < -ICommon.TWO_MINUTES;
        boolean isNewer = timeDelta > 0;

        float distance = location.distanceTo(lastLocation);
        if (distance < ICommon.LOCATION_MIN_DISTANCE_CHANGE_FOR_UPDATES) {
            return false;
        }

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
        Intent localIntent = new Intent(ICommon.SET_GPS_STATUS)
                // Puts the status into the Intent
                .putExtra(ICommon.SET_GPS_STATUS, !backgroudStatus);
        sendBroadcast(localIntent);
    }

    public void checkVisibility(final String uid) {
        // Aplicar las reglas de visibilidad entre mi usuario y este
        Log.d(TAG, "checkVisibility: " + uid);

        final User myUser = MyUserManager.getInstance().getUser();

        // Obtain MapRival
        final MapElement elem = viewRange.getMapElement(uid);

        // My own user is not inserted in the viewRange list, so do nothing here if elem is not found
        if (elem == null) {
            Log.w(TAG, "checkVisibility: element is null.");
        } else {
            GeoLocation gloc = elem.getGeolocation();
            Location otherUserLocation = new Location("?");
            otherUserLocation.setLatitude(gloc.latitude);
            otherUserLocation.setLongitude(gloc.longitude);
            final float distance = lastLocation.distanceTo(otherUserLocation);

            if (elem instanceof MapRival) {
                // Obtener el usuario de la BBDD
                //DataManager.getInstance().getUser(uid, new ICallbacks.IUserCallbacks() {
                dataManager.getUser(uid, new ICallbacks.IChangeCallbacks<User>() {
                    @Override
                    //public void onUserChange(User user) {
                    public void onChange(User user) {
                        ((MapRival) elem).setUser(user);
                        boolean isVisible = applyVisibilityRules(myUser, user, distance);
                        Log.d(TAG, "checkVisibility on user: " + user.getUid() + " at distance: " + distance + " returns:" + isVisible);
                        onVisibilityResult(uid, elem, isVisible);
                    }
                });
            } else if (elem instanceof MapTreasure) {
                Log.d(TAG, "checkVisibility: todo maptreasure");
                //TODO
                //onVisibilityResult(null, true);
            }
        }
    }

    private void onVisibilityResult(String uid, MapElement elem, boolean isVisible) {

        if (isVisible != elem.isVisible()) {
            Log.d(TAG, "checkElementVisibility: Applying visibility change!!!");
            elem.setVisible(isVisible);
            int tipo = (elem instanceof MapRival) ? ICommon.LOCATION_TYPE_RIVAL : ICommon.LOCATION_TYPE_TREASURE;
            if (isVisible) {
                // Lo pinto
                GeoLocation g = elem.getGeolocation();
                LatLng loc = new LatLng(g.latitude, g.longitude);
                updateMapElementLocation(uid, tipo, loc);
            } else {
                // Ya no se ve, lo borro.
                removeMapElementLocation(uid, tipo);
            }
        }
    }

    private boolean applyVisibilityRules(User myUser, User otherUser, float distance) {
        Log.d(TAG, "applyVisibilityRules");
        boolean ret = false;
        int myWatchingSkill = myUser.getSkills().getWatching().getValue();
        int otherHidingSkill = otherUser.getSkills().getHiding().getValue();
        int tot = myWatchingSkill - otherHidingSkill;
        if (tot < 0)
            tot = 0;
        int range = calculateDistanceRange(distance);
        return range > -1 && ICommon.distanceTable[tot][range];
    }

   /* private boolean applyVisibilityRules(User myUser, Treasure treasure, float distance) {
        boolean ret = false;
        int myWatchingSkill = myUser.getSkills().getWatching().getValue();
        int otherHidingSkill = treasure.getSkills().getHiding().getValue();
        int tot = myWatchingSkill - otherHidingSkill;
        if (tot < 0)
            tot = 0;
        int range = calculateDistanceRange(distance);
        return range > -1 && ICommon.distanceTable[tot][range];*
    }*/

    private int calculateDistanceRange(float distance) {
        Float d = distance;
        int dist = d.intValue();
        for (int i = 0; i < ICommon.distanceRanges.length; i++) {
            if(dist <= ICommon.distanceRanges[i]) {
                return i;
            }
        }
        return -1;
    }
}
