package com.deepred.subworld.engine;

import android.content.Context;
import android.location.Location;
import android.util.Log;

import com.deepred.subworld.ICommon;
import com.deepred.subworld.model.Treasure;
import com.deepred.subworld.model.User;
import com.deepred.subworld.utils.ICallbacks;
import com.deepred.subworld.utils.MyUserManager;
import com.firebase.client.AuthData;
import com.firebase.client.DataSnapshot;
import com.firebase.client.Firebase;
import com.firebase.client.FirebaseError;
import com.firebase.client.ValueEventListener;
import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.mapbox.mapboxsdk.geometry.LatLng;

import java.util.Map;

/**
 * Created by aplicaty on 25/02/16.
 */
class DataManager implements GeoQueryEventListener {
    private final static String TAG = "SW ENGINE DataManager  ";
    private static volatile DataManager INSTANCE;
    private static Object obj = new Object();
    private Firebase dbRef = null;
    private GeoFire dbGeoRef = null;
    private GeoQuery geoQuery;
    private String uid = null;
    private User user = null;
    private Context ctx;
    private ViewRangeManager viewRange;

    private DataManager(){
        //Firebase.setAndroidContext(ApplicationHolder.getApp().getApplicationContext());
        /*Firebase.setAndroidContext(gm);
        dbRef = new Firebase(ICommon.FIREBASE_REF);
        dbGeoRef = new GeoFire(new Firebase(ICommon.GEO_FIRE_REF));
        geoQuery = null;*/
    }

    static DataManager getInstance(){
        if(INSTANCE == null){
            synchronized(obj){
                if(INSTANCE == null){
                    INSTANCE = new DataManager();
                }
            }
        }
        return INSTANCE;
    }

    public void setContext(Context _ctx, ViewRangeManager _viewRange) {
        ctx = _ctx;
        viewRange = _viewRange;
        Firebase.setAndroidContext(ctx);
        dbRef = new Firebase(ICommon.FIREBASE_REF);
        dbGeoRef = new GeoFire(new Firebase(ICommon.GEO_FIRE_REF));
        geoQuery = null;
    }

    Firebase getDbRef() {
        return dbRef;
    }

    void loginOrRegister(final String eMail, final String password, final ICallbacks.ILoginCallbacks cb) {
        dbRef.authWithPassword(eMail, password, new Firebase.AuthResultHandler() {
            @Override
            public void onAuthenticated(AuthData authData) {
                Log.d(TAG, "User ID: " + authData.getUid() + ", Provider: " + authData.getProvider());
                uid = authData.getUid();
                cb.onLoginOk(true);
            }

            @Override
            public void onAuthenticationError(FirebaseError firebaseError) {
                // there was an error
                if (firebaseError.getCode() == FirebaseError.INVALID_EMAIL || firebaseError.getCode() == FirebaseError.USER_DOES_NOT_EXIST) {
                    dbRef.createUser(eMail, password, new Firebase.ValueResultHandler<Map<String, Object>>() {
                        @Override
                        public void onSuccess(Map<String, Object> result) {
                            Log.d(TAG, "Successfully created user account with uid: " + result.get("uid"));
                            uid = (String) result.get("uid");
                            cb.onLoginOk(false);
                        }

                        @Override
                        public void onError(FirebaseError firebaseError) {
                            // there was an error
                            Log.d(TAG, "Error al intentar el login en Firebase.");
                            cb.onLoginError();
                        }
                    });
                } else if (firebaseError.getCode() == FirebaseError.INVALID_PASSWORD) {
                    cb.onLoginError();
                }
            }
        });
    }

    void getUser() {
        Firebase userRef = dbRef.child("users").child(uid);

        userRef.addValueEventListener(new ValueEventListener() {

            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.hasChildren()) {
                    user = snapshot.getValue(User.class);
                    user.setUid(uid);
                    MyUserManager.getInstance().updateUser(user);
                } else {
                    Log.d(TAG, "Tenemos credenciales en el dispositivo, pero no hay registro de usuario en BBDD");
                    MyUserManager.getInstance().updateUser(null);
                }
            }

            @Override
            public void onCancelled(FirebaseError databaseError) {
                Log.d(TAG, "The read failed: " + databaseError.getMessage());
            }
        });

    }

    void getUser(final String _uid, final ICallbacks.IChangeCallbacks<User> cb) {
        Firebase userRef = dbRef.child("users").child(_uid);

        // Attach an listener to read the data at our posts reference
        userRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {

                if (snapshot.hasChildren()) {
                    user = snapshot.getValue(User.class);
                    user.setUid(_uid);
                    //cb.onUserChange(user);
                    cb.onChange(user);
                } else {
                    Log.d(TAG, "Tenemos credenciales en el dispositivo, pero no hay registro de usuario en BBDD");
                    //cb.onUserChange(null);
                    cb.onChange(user);
                }
            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {
                Log.d(TAG, "The read failed: " + firebaseError.getMessage());
            }
        });
    }


    void checkName(String name, final ICallbacks.INameCheckCallbacks cb) {
        Firebase ref = dbRef.child("usernames").child(name.toLowerCase());
        // Hacer la consulta ignorecase

        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (0 < snapshot.getChildrenCount()) {
                    // Ya existe el nombre
                    cb.onNameAlreadyExists();
                } else {
                    cb.onValidUsername();
                }
            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {
            }
        });
    }

    void storeUsername(String name, final ICallbacks.IStoreCallbacks cb) {
        Firebase ref = dbRef.child("usernames");
        ref.push().setValue(name, new Firebase.CompletionListener() {
            @Override
            public void onComplete(FirebaseError firebaseError, Firebase firebase) {
                if (firebaseError != null) {
                    Log.d(TAG, "Data could not be saved. " + firebaseError.getMessage());
                    cb.onError();
                } else {
                    Log.d(TAG, "Data saved successfully.");
                    cb.onSuccess();
                }
            }
        });
    }

    void saveUser(User user, final ICallbacks.IStoreCallbacks cb) {
        Firebase ref = dbRef.child("users").child(user.getUid());
        ref.setValue(user, new Firebase.CompletionListener() {
            @Override
            public void onComplete(FirebaseError firebaseError, Firebase firebase) {
                if (firebaseError != null) {
                    Log.d(TAG, "Data could not be saved. " + firebaseError.getMessage());
                    cb.onError();
                } else {
                    Log.d(TAG, "Data saved successfully.");
                    cb.onSuccess();
                }
            }
        });
    }


    void saveTreasure(final Treasure treasure, final ICallbacks.IStoreCallbacks cb) {
        final Firebase ref = dbRef.child("treasures").push();
        ref.setValue(treasure, new Firebase.CompletionListener() {
            @Override
            public void onComplete(FirebaseError firebaseError, Firebase firebase) {
                if (firebaseError != null) {
                    Log.d(TAG, "Data could not be saved. " + firebaseError.getMessage());
                    cb.onError();
                } else {
                    Log.d(TAG, "Data saved successfully.");
                    treasure.setUid(ref.getKey());
                    cb.onSuccess();
                }
            }
        });
    }


    /*
        rad is actual range radius in kilometers
     */
    void queryLocations(final Location l, final double rad) {
        Log.d(TAG, "QueryLocations for location:" + l.getLatitude() + "," + l.getLongitude() + " (" + l.getProvider() +  ")");
        if(uid == null)
            return;

        dbGeoRef.setLocation(ICommon.GEO_USR_PREFIX + uid, new GeoLocation(l.getLatitude(), l.getLongitude()), new GeoFire.CompletionListener() {
            @Override
            public void onComplete(String key, FirebaseError error) {
                if (error != null) {
                    Log.e(TAG, "There was an error saving the location to GeoFire: " + error);
                } else {
                    Log.d(TAG, "Location saved on server successfully!");

                    if (geoQuery == null) {
                        geoQuery = dbGeoRef.queryAtLocation(new GeoLocation(l.getLatitude(), l.getLongitude()), rad);
                        geoQuery.addGeoQueryEventListener(INSTANCE);
                    } else {
                        geoQuery.setCenter(new GeoLocation(l.getLatitude(), l.getLongitude()));
                        geoQuery.setRadius(rad);
                    }
                }
            }
        });
    }

    public void insertTreasureLocation(String id, final LatLng l) {
        dbGeoRef.setLocation(ICommon.GEO_TREASURE_PREFIX + id, new GeoLocation(l.getLatitude(), l.getLongitude()), new GeoFire.CompletionListener() {
            @Override
            public void onComplete(String key, FirebaseError error) {
                if (error != null) {
                    Log.e(TAG, "There was an error saving the location to GeoFire: " + error);
                } else {
                    Log.d(TAG, "Location saved on server successfully!");
                }
            }
        });
    }

    void removeTreasureLocation(String id) {
        dbGeoRef.removeLocation(ICommon.GEO_TREASURE_PREFIX + id, new GeoFire.CompletionListener() {
                    @Override
                    public void onComplete(String key, FirebaseError error) {
                        if (error != null) {
                            Log.e(TAG, "There was an error deleting the location to GeoFire: " + error);
                        } else {
                            Log.d(TAG, "Location deleted successfully!");
                        }
                    }
                }
        );
    }

    public void stopQueryingLocations() {
        geoQuery.removeAllListeners();
    }

    String getUid() {
        return uid;
    }


    @Override
    public void onKeyEntered(String id, GeoLocation geoLocation) {
        Log.d(TAG, "onKeyEntered: " + id);

        // Check if location is a rival or a treasure
        int type;
        if (id.startsWith(ICommon.GEO_TREASURE_PREFIX)) {
            type = ICommon.LOCATION_TYPE_TREASURE;
        } else {
            type = ICommon.LOCATION_TYPE_RIVAL;
        }
        String _uid = stripPrefix(id);
        viewRange.add(_uid, type, geoLocation, _uid.equals(uid));
    }

    @Override
    public void onKeyExited(String id) {
        Log.d(TAG, "onKeyExited: " + id);
        viewRange.remove(stripPrefix(id));
    }

    @Override
    public void onKeyMoved(String id, GeoLocation geoLocation) {
        Log.d(TAG, "onKeyMoved: " + id);
        String _uid = stripPrefix(id);
        viewRange.add(_uid, ICommon.LOCATION_TYPE_RIVAL, geoLocation, _uid.equals(uid));
    }

    private String stripPrefix(String id) {
        String prefix;
        if (id.startsWith(ICommon.GEO_TREASURE_PREFIX)) {
            prefix = ICommon.GEO_TREASURE_PREFIX;
        } else {
            prefix = ICommon.GEO_USR_PREFIX;
        }
        return id.substring(prefix.length());
    }

    @Override
    public void onGeoQueryReady() {
        Log.d(TAG, "onGeoQueryReady");
        viewRange.queryCompleted();
    }

    @Override
    public void onGeoQueryError(FirebaseError firebaseError) {
        Log.d(TAG, "onGeoQueryError");
    }
}
