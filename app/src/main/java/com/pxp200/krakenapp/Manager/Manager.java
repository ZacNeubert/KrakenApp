package com.pxp200.krakenapp.Manager;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;

import com.pxp200.krakenapp.KrakenApplication;
import com.pxp200.krakenapp.Storage.LastOpenedPreference;
import com.pxp200.krakenapp.model.BuildingInfo;
import com.pxp200.krakenapp.model.Resource;
import com.pxp200.krakenapp.model.UserResponse;

import java.util.ArrayList;
import java.util.Date;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import static android.icu.lang.UCharacter.GraphemeClusterBreak.T;

/**
 * Created by HayzBomb on 11/17/2017.
 */

public class Manager extends Service {

    Thread thread;

    long lastServerUpdate;
    long lastUpdate;

    public ArrayList<Resource> staticResources;
    public ArrayList<BuildingInfo> staticBuildings;

    public UserResponse user;
    public boolean userSet = false;

    public ArrayList<UserUpdateListener> listeners;

    public interface UserUpdateListener {
        void updated(Manager manager);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        KrakenApplication.setManager(this.getBaseContext(), this);
        lastServerUpdate = Integer.MAX_VALUE;
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                new Timer().scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {
                        if(!userSet) {
                            return;
                        }

                        long now = System.currentTimeMillis();
                        long delta = now - lastUpdate;
                        lastUpdate = now;

                        LastOpenedPreference.setLastOpenDate(Manager.this.getBaseContext(), new Date(lastUpdate));
                        if(now + 15000 < lastServerUpdate) {
                            lastServerUpdate = now;
                            syncWithServer();
                        }
                        update(delta);
                        if(listeners != null) {
                            for (UserUpdateListener l : listeners) {
                                l.updated(Manager.this);
                            }
                        }
                    }
                }, 0, 1000); //update resources
            }
        });
        thread.start();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {

        return null;
    }

    public void addListener(UserUpdateListener listener) {
        if(listeners == null) {
            listeners = new ArrayList<UserUpdateListener>();
        }
        listeners.add(listener);
    }

    public void removeListener(UserUpdateListener listener) {
        if(listeners != null) {
            listeners.remove(listener);
        }
    }

    public void setInitialUser(UserResponse userRes) {
        user = userRes;
        if(user.getResources() == null || user.getResources().size() == 0) {
            HashMap<String, Double> res = new HashMap<>();
            for(Resource r : staticResources) {
                res.put(r.getId(), 0d);
            }
            user.setResources(res);
        }

        if(user.getBuildings() == null) {
            user.setBuildings(new ArrayList<String>());
        }

        resume();
        userSet = true;
    }
    //updates periodically to apply new changes to the server
    public void syncWithServer() {
        KrakenApplication.getKrakenApi(this.getBaseContext());
    }

    //when you close the app and reopen and accrue resources
    public void resume() {
        long prev = LastOpenedPreference.getLastOpenDate(this.getBaseContext()).getTime(); //pulls from when app closes returns a date
        long now = System.currentTimeMillis();
        long closedTime = now - prev;

        update(closedTime);
    }


    public void update(long delta) {
        ArrayList<String> userBuildings = user.getBuildings();
        for (int i = 0; i < userBuildings.size(); i++){
            for(int y = 0; y < this.staticBuildings.size(); y++) {
                if (this.staticBuildings.get(y).getName() == userBuildings.get(i)) {
                    boolean execute = true;
                    for(int z = 0; z < this.staticBuildings.get(y).getConsumes().size(); z++) {
                        if (this.user.getResources().get(this.staticBuildings.get(y).getName()) < this.staticBuildings.get(y).getConsumes().get(z).getAmount()) {
                            execute = false;
                        }
                    }
                    if(execute){
                        for(int z = 0; z < this.staticBuildings.get(y).getConsumes().size(); z++) {
                            this.user.incrementResource(this.staticBuildings.get(y).getConsumes().get(z).getName(),
                                    this.staticBuildings.get(y).getConsumes().get(z).getAmount() * delta/1000.0*-1);
                            //lower consumed resources
                        }
                        for(int z = 0; z < this.staticBuildings.get(y).getProduces().size(); z++) {
                            this.user.incrementResource(this.staticBuildings.get(y).getProduces().get(z).getName(),
                                    this.staticBuildings.get(y).getProduces().get(z).getAmount() * delta/1000.0);
                        }
                    }

                }
            }
        }
    }
}