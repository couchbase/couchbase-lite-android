package com.couchbase.upgradetest;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import com.couchbase.lite.Database;
import com.couchbase.lite.DatabaseOptions;
import com.couchbase.lite.Document;
import com.couchbase.lite.Manager;
import com.couchbase.lite.android.AndroidContext;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private final static String TAG = "UpgradeTest";
    private final static String DB_NAME = "upgrade";
    private final static String DOC_ID = "doc_upgrade";

    private static final int REQUEST_WRITE_STORAGE = 112;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        boolean hasPermission = (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
        if (!hasPermission) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_WRITE_STORAGE);
        } else {
            // databaseOperation();
            databaseOperationSDCard();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_WRITE_STORAGE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //reload my activity with permission granted or use the features what required the permission

                    // databaseOperation();
                    databaseOperationSDCard();
                } else {
                    Toast.makeText(this, "The app was not allowed to write to your storage. Hence, it cannot function properly. Please consider granting it this permission", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    // TODO - comment out CBL 1.4.1, activate 2.0.0

    // -------------------------------------------
    // 1.4.1
    // -------------------------------------------
    void databaseOperation() {
        try {
            Manager mgr = new Manager(new AndroidContext(getBaseContext()), null);
            DatabaseOptions opt = new DatabaseOptions();
            opt.setCreate(true);
            Database db = mgr.openDatabase(DB_NAME, opt);

            Map<String, Object> props;
            Document doc = db.getExistingDocument(DOC_ID);
            if (doc == null) {
                // new doc
                doc = db.getDocument(DOC_ID);
                props = new HashMap<>();
                props.put("Database Version", "1.4.1");
                props.put("update", 1);
                doc.putProperties(props);
            } else {
                // update
                props = new HashMap<>(doc.getProperties());
                props.put("update", (Integer) props.get("update") + 1);
                doc.putProperties(props);
            }

            Log.i(TAG, "Num of docs: " + db.getDocumentCount());
            Log.i(TAG, "Doc content: " + db.getDocument(DOC_ID).getProperties());

            db.close();
        } catch (Exception e) {
            Log.e(TAG, "" + e.getMessage());
            e.printStackTrace();
        }
    }

    // 1.4.1  SDCARD
    static class SDCardContext extends AndroidContext {

        public SDCardContext(Context wrappedContext) {
            super(wrappedContext);
        }

        @Override
        public File getFilesDir() {
            //return new File("/sdcard");
            if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                //handle case of no SDCARD present
                throw new RuntimeException("no SDCARD present");
            } else {
                String dir = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "test" + File.separator;
                File folder = new File(dir); //folder name
                return folder;
            }
        }
    }

    void databaseOperationSDCard() {
        try {
            Manager mgr = new Manager(new SDCardContext(getBaseContext()), null);
            DatabaseOptions opt = new DatabaseOptions();
            opt.setCreate(true);
            Database db = mgr.openDatabase(DB_NAME, opt);

            Map<String, Object> props;
            Document doc = db.getExistingDocument(DOC_ID);
            if (doc == null) {
                // new doc
                doc = db.getDocument(DOC_ID);
                props = new HashMap<>();
                props.put("Database Version", "1.4.1");
                props.put("update", 1);
                doc.putProperties(props);
            } else {
                // update
                props = new HashMap<>(doc.getProperties());
                props.put("update", (Integer) props.get("update") + 1);
                doc.putProperties(props);
            }

            Log.i(TAG, "Num of docs: " + db.getDocumentCount());
            Log.i(TAG, "Doc content: " + db.getDocument(DOC_ID).getProperties());

            db.close();
        } catch (Exception e) {
            Log.e(TAG, "" + e.getMessage());
            e.printStackTrace();
        }
    }

    /*
    // -------------------------------------------
    // 2.0.0
    // -------------------------------------------
    void databaseOperation() {
        try {
            DatabaseConfiguration config = new DatabaseConfiguration(getBaseContext());
            Database db = new Database(DB_NAME, config);

            Document doc = db.getDocument(DOC_ID);
            if (doc != null) {
                Log.i(TAG, "Doc content: " + db.getDocument(DOC_ID).toMap());
                MutableDocument mDoc = doc.toMutable();
                mDoc.setString("Database Version", "2.0.0");
                mDoc.setInt("update", mDoc.getInt("update") + 1);
                db.save(mDoc);
            }

            Log.i(TAG, "Num of docs: " + db.getCount());
            Log.i(TAG, "Doc content: " + db.getDocument(DOC_ID).toMap());

            db.close();
        } catch (Exception e) {
            Log.e(TAG, "" + e.getMessage());
            e.printStackTrace();
        }
    }

    // 2.0.0 - SDCARD
    void databaseOperationSDCard() {
        try {
            DatabaseConfiguration config = new DatabaseConfiguration(getBaseContext());
            String dir = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "test" + File.separator;
            config.setDirectory(dir);
            Database db = new Database(DB_NAME, config);

            Document doc = db.getDocument(DOC_ID);
            if (doc != null) {
                Log.i(TAG, "Doc content: " + db.getDocument(DOC_ID).toMap());
                MutableDocument mDoc = doc.toMutable();
                mDoc.setString("Database Version", "2.0.0");
                mDoc.setInt("update", mDoc.getInt("update") + 1);
                db.save(mDoc);
            }

            Log.i(TAG, "Num of docs: " + db.getCount());
            Log.i(TAG, "Doc content: " + db.getDocument(DOC_ID).toMap());

            db.close();
        } catch (Exception e) {
            Log.e(TAG, "" + e.getMessage());
            e.printStackTrace();
        }
    }
    */
}
