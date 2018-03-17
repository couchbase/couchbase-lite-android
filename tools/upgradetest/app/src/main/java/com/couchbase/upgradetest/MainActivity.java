package com.couchbase.upgradetest;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.couchbase.lite.Database;
import com.couchbase.lite.DatabaseOptions;
import com.couchbase.lite.Document;
import com.couchbase.lite.Manager;
import com.couchbase.lite.android.AndroidContext;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private final static String TAG = "UpgradeTest";
    private final static String DB_NAME = "ecouchpojox1";
    private final static String DOC_ID = "doc_upgrade";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        databaseOperation();
    }

    // TODO - comment out CBL 1.4.1, activate 2.0.0

    // 1.4.1
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

/*
    // 2.0.0
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
*/
}
