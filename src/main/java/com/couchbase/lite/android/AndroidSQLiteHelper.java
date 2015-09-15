package com.couchbase.lite.android;

import com.couchbase.lite.storage.ContentValues;

import java.util.Map;

/**
 * Created by pasin on 8/30/15.
 */
class AndroidSQLiteHelper {
    public static android.content.ContentValues toAndroidContentValues(ContentValues values) {
        android.content.ContentValues contentValues = new android.content.ContentValues(values.size());
        for (Map.Entry<String, Object> value : values.valueSet()) {
            if (value.getValue() == null) {
                contentValues.put(value.getKey(), (String) null);
            } else if (value.getValue() instanceof String) {
                contentValues.put(value.getKey(), (String) value.getValue());
            } else if (value.getValue() instanceof Integer) {
                contentValues.put(value.getKey(), (Integer) value.getValue());
            } else if (value.getValue() instanceof Long) {
                contentValues.put(value.getKey(), (Long) value.getValue());
            } else if (value.getValue() instanceof Boolean) {
                contentValues.put(value.getKey(), (Boolean) value.getValue());
            } else if (value.getValue() instanceof byte[]) {
                contentValues.put(value.getKey(), (byte[]) value.getValue());
            }
        }
        return contentValues;
    }
}
