package com.deepred.subworld;

import com.deepred.subworld.model.User;

import java.util.ArrayList;

/**
 * Created by aplicaty on 25/02/16.
 */
public class MyUserManager {
    private static Object lock = new Object();
    private static volatile MyUserManager instance = null;
    private ArrayList<IUserCallbacks> activities;
    private User user;

    private MyUserManager() {
        user = null;
        activities = new ArrayList<IUserCallbacks>();
    }

    public static MyUserManager getInstance() {
        MyUserManager localInstance = instance;
        if(localInstance == null) {
            synchronized (lock) {
                localInstance = instance;
                if(localInstance == null) {
                    instance = localInstance = new MyUserManager();
                }
            }
        }
        return localInstance;
    }

    public void register4UserNotifications(IUserCallbacks act) {
        activities.add(act);
    }

    public void unregister4UserNotifications(IUserCallbacks act) {
        if(activities.contains(act))
            activities.remove(act);
    }

    public User getUser() {
        return user!=null?user : new User();
    }

    public User getUser(String uid) {
        return user!=null?user : new User(uid);
    }

    public void updateUser(User u) {
        user = u;

        for(IUserCallbacks act:activities) {
            act.onUserChange(user);
        }
    }
}
