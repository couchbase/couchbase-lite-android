package com.couchbase.lite;

import com.couchbase.lite.internal.bridge.LiteCoreBridge;
import com.couchbase.litecore.Constants;
import com.couchbase.litecore.LiteCoreException;
import com.couchbase.litecore.fleece.FLDict;
import com.couchbase.litecore.fleece.FLValue;

import java.util.Arrays;
import java.util.Locale;

/**
 * Readonly version of the Document.
 */
public class ReadOnlyDocument extends ReadOnlyDictionary {
    private static final String TAG = Log.DATABASE;

    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private Database database;
    private String id;
    private CBLC4Doc c4doc;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    /* package */ ReadOnlyDocument(Database database,
                                   String id,
                                   CBLC4Doc c4doc,
                                   CBLFLDict data) {
        super(data);
        this.database = database;
        this.id = id;
        setC4Doc(c4doc);
    }

    /* package */ ReadOnlyDocument(Database database,
                                   String id,
                                   boolean mustExist)
            throws CouchbaseLiteException {
        this(database, id, null, null);

        // NOTE: readC4Doc should not return null instead of throwing CouchbaseLiteException.
        com.couchbase.litecore.Document rawDoc = readC4Doc(mustExist);

        // NOTE: c4doc should not be null.
        setC4Doc(new CBLC4Doc(rawDoc));
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * return the document's ID.
     *
     * @return the document's ID
     */
    public String getId() {
        return id;
    }

    /**
     * Return the sequence number of the document in the database.
     * This indicates how recently the document has been changed: every time any document is updated,
     * the database assigns it the next sequential sequence number. Thus, if a document's `sequence`
     * property changes that means it's been changed (on-disk); and if one document's `sequence`
     * is greater than another's, that means it was changed more recently.
     *
     * @return the sequence number of the document in the database.
     */
    public long getSequence() {
        return c4doc != null ? c4doc.getSelectedSequence() : 0;
    }

    /**
     * Return whether the document is deleted
     *
     * @return true if deleted, false otherwise
     */
    public boolean isDeleted() {
        return c4doc != null ? c4doc.getRawDoc().deleted() : false;
    }

    @Override
    public String toString() {
        return String.format(Locale.ENGLISH, "ReadOnlyDocument[%s]", id);
    }

    //---------------------------------------------
    // protected level access
    //---------------------------------------------

    // Sets c4doc and updates my root dictionary
    protected void setC4Doc(CBLC4Doc c4doc) throws CouchbaseLiteException {
        this.c4doc = c4doc;

        if (c4doc != null) {
            FLDict root = null;
            byte[] body = null;
            try {
                if (!c4doc.getRawDoc().deleted())
                    body = c4doc.getRawDoc().getSelectedBody();
            } catch (LiteCoreException e) {
                // in case body is empty, deleted is thrown.
                if (e.code != Constants.LiteCoreError.kC4ErrorDeleted)
                    throw LiteCoreBridge.convertException(e);
            }
            if (body != null && body.length > 0)
                root = FLValue.fromData(body).asFLDict();
            setData(new CBLFLDict(root, c4doc, database));
        } else {
            setData(null);
        }
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    /*package*/  Database getDatabase() {
        return database;
    }

    /*package*/ void setDatabase(Database database) {
        this.database = database;
    }

    /**
     * Return whether the document exists in the database.
     *
     * @return true if exists, false otherwise.
     */
    /* package */ boolean exists() {
        return c4doc != null ? c4doc.getRawDoc().exists() : false;
    }

    // Document overrides this
    /* package */ long generation() {
        // TODO: c4rev_getGeneration
        return generationFromRevID(getRevID());
    }

    /**
     * TODO: This code is from v1.x. Better to replace with c4rev_getGeneration().
     */
     /* package */ long generationFromRevID(String revID) {
        long generation = 0;
        long length = Math.min(revID == null ? 0 : revID.length(), 9);
        for (int i = 0; i < length; ++i) {
            char c = revID.charAt(i);
            if (Character.isDigit(c))
                generation = 10 * generation + Character.getNumericValue(c);
            else if (c == '-')
                return generation;
            else
                break;
        }
        return 0;
    }

    /*package*/ CBLC4Doc getC4doc() {
        return c4doc;
    }

    // Reads the document from the db into a new C4Document and returns it, w/o affecting my state.
    /*package*/ com.couchbase.litecore.Document readC4Doc(boolean mustExist) throws CouchbaseLiteException, IllegalStateException {
        try {
            return database.getC4Database().getDocument(getId(), mustExist);
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertException(e);
        }
    }

    /*package*/ ConflictResolver effectiveConflictResolver() {
        return getDatabase().getConflictResolver() != null ?
                getDatabase().getConflictResolver() :
                new DefaultConflictResolver();
    }

    /* package */ boolean selectConflictingRevision() {
        try {
            if (!c4doc.getRawDoc().selectNextLeaf(false, true))
                return false;
            setC4Doc(c4doc); // self.c4Doc = _c4Doc; // This will update to the selected revision
        } catch (LiteCoreException e) {
            Log.e(TAG, "Failed to selectNextLeaf: doc -> " + c4doc, e);
            return false;
        }
        return true;
    }

    /* package */ boolean selectCommonAncestor(ReadOnlyDocument doc1, ReadOnlyDocument doc2) {
        if (!c4doc.getRawDoc().selectCommonAncestorRevision(doc1.getRevID(), doc2.getRevID()))
            return false;
        setC4Doc(c4doc); // self.c4Doc = _c4Doc; // This will update to the selected revision
        return true;
    }

    /* package */ String getRevID() {
        return c4doc != null ? c4doc.getSelectedRevID() : null;
    }

    // Document overrides this
    /*package*/ byte[] encode() {
        byte[] body = new byte[0];
        try {
            body = c4doc.getRawDoc().getSelectedBody();
        } catch (LiteCoreException e) {
            Log.e(TAG, "Failed to get getSelectedBody()", e);
        }
        return body != null ? Arrays.copyOf(body, body.length) : new byte[0];
    }
}
