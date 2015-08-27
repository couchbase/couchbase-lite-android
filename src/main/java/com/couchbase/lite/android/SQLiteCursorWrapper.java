package com.couchbase.lite.android;

import com.couchbase.lite.storage.Cursor;

/**
 * Created by pasin on 8/30/15.
 */
class SQLiteCursorWrapper implements Cursor {
    private android.database.Cursor delegate;

    public SQLiteCursorWrapper(android.database.Cursor delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean moveToNext() {
        return delegate.moveToNext();
    }

    @Override
    public boolean isAfterLast() {
        return delegate.isAfterLast();
    }

    @Override
    public String getString(int columnIndex) {
        return delegate.getString(columnIndex);
    }

    @Override
    public int getInt(int columnIndex) {
        return delegate.getInt(columnIndex);
    }

    @Override
    public long getLong(int columnIndex) {
        return delegate.getLong(columnIndex);
    }

    @Override
    public byte[] getBlob(int columnIndex) {
        return delegate.getBlob(columnIndex);
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public boolean isNull(int columnIndex) {
        return delegate.isNull(columnIndex);
    }
}
