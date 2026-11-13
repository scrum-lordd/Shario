package com.localshare;
import android.app.Application;
import com.localshare.persistence.DbHelper;

public class LocalShareApp extends Application {
    @Override public void onCreate(){
        super.onCreate();
        // apply theme
        com.localshare.util.AppPreferences.applyTheme(com.localshare.util.AppPreferences.getTheme(this));
        // init db
        new DbHelper(this).getWritableDatabase().close();
    }
}

