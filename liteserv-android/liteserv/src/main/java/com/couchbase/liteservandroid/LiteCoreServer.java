package com.couchbase.liteservandroid;

import android.content.Context;
import android.util.Log;

import com.couchbase.lite.internal.C4Listener;
import com.couchbase.lite.internal.C4ListenerAPIs;
import com.couchbase.lite.internal.C4ListenerConfig;
import com.couchbase.lite.internal.Database;
import com.couchbase.lite.internal.LiteCoreException;

import java.io.File;
import java.io.FileFilter;
import java.util.Locale;


public class LiteCoreServer implements Constants {

    interface StatusCallback{
        void update(String message);
    }

    private int listeningPort;

    private File directory;
    private C4Listener listener = null;
    private int flags;
    private StatusCallback callback;

    public LiteCoreServer(Context context, int listeningPort, StatusCallback callback) {
        if (context == null) throw new IllegalArgumentException();

        this.listeningPort = listeningPort;

        this.directory = context.getFilesDir();
        this.listener = null;
        this.flags = Database.Create | Database.Bundle | Database.SharedKeys;

        this.callback = callback;
    }

    public void start() throws LiteCoreException {
        if (listener != null)
            return;

        C4ListenerConfig config = new C4ListenerConfig();
        config.setPort(this.listeningPort);
        config.setApis(C4ListenerAPIs.kC4RESTAPI);
        config.setAllowCreateDBs(true);
        config.setAllowDeleteDBs(true);
        config.setAllowPull(true);
        config.setAllowPush(true);
        config.setDirectory(directory.getAbsolutePath());
        listener = new C4Listener(config);
        Log("LiteCoreServ is now listening at http://localhost:%d/", listeningPort);

        Log("Sharing all databases in %s: ", directory.getAbsolutePath());
        File[] files = directory.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.getName().endsWith(".cblite2");
            }
        });
        for (File path : files) {
            String name = path.getName().substring(0, path.getName().indexOf(".cblite2"));
            Log("\t- %s", name);
            shareDatabase(path, name);
        }
    }

    public void stop() {
        if (listener != null) {
            listener.free();
            listener = null;
        }
    }

    private void shareDatabase(File path, String name) {
        try {
            Database db = new Database(path.getAbsolutePath(), flags, 0, null);
            try {
                listener.shareDB(name, db);
            } finally {
                db.free();
            }
        } catch (LiteCoreException ex) {
            Log("Error with open database: %s", name);
            ex.printStackTrace();
        }
    }

    private void Log(String formatString, Object... args) {
        String message = String.format(Locale.ENGLISH, formatString, args);
        Log.i(TAG, message);
        if (callback != null)
            callback.update(message);
    }
}
