package com.couchbase.lite;

import com.couchbase.lite.internal.bridge.LiteCoreBridge;
import com.couchbase.litecore.C4Document;
import com.couchbase.litecore.LiteCoreException;
import com.couchbase.litecore.fleece.FLDict;

import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Readonly version of the Document.
 */
public class ReadOnlyDocument implements ReadOnlyDictionaryInterface, Iterable<String> {
    private static final String TAG = Log.DATABASE;

    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private Database database;
    private String id;
    private CBLC4Doc c4doc;
    protected ReadOnlyDictionary dict; // access from Document

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    ReadOnlyDocument(Database database,
                     String id,
                     CBLC4Doc c4doc,
                     CBLFLDict data) {
        setDictionaryFromData(data);
        this.database = database;
        this.id = id;
        //setC4Doc(c4doc);
        this.c4doc = c4doc;
    }

    ReadOnlyDocument(Database database,
                     String id,
                     boolean mustExist) {
        this(database, id, null, null);

        C4Document rawDoc;
        try {
            rawDoc = readC4Doc(mustExist);
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertRuntimeException(e);
        }

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
        return c4doc != null && c4doc.getRawDoc() != null ? c4doc.getRawDoc().deleted() : false;
    }

    @Override
    public String toString() {
        return String.format(Locale.ENGLISH, "ReadOnlyDocument[%s]", id);
    }

    //---------------------------------------------
    // API - Implemenents ReadOnlyDictionaryInterface
    //---------------------------------------------

    /**
     * Gets a number of the entries in the dictionary.
     *
     * @return
     */
    @Override
    public int count() {
        return dict.count();
    }

    @Override
    public List<String> getKeys() {
        return dict.getKeys();
    }


    /**
     * Gets a property's value as an object. The object types are Blob, Array,
     * Dictionary, Number, or String based on the underlying data type; or nil if the
     * property value is null or the property doesn't exist.
     *
     * @param key the key.
     * @return the object value or nil.
     */
    @Override
    public Object getObject(String key) {
        return dict.getObject(key);
    }

    /**
     * Gets a property's value as a String.
     * Returns null if the value doesn't exist, or its value is not a String.
     *
     * @param key the key
     * @return the String or null.
     */
    @Override
    public String getString(String key) {
        return dict.getString(key);
    }

    /**
     * Gets a property's value as a Number.
     * Returns null if the value doesn't exist, or its value is not a Number.
     *
     * @param key the key
     * @return the Number or nil.
     */
    @Override
    public Number getNumber(String key) {
        return dict.getNumber(key);
    }

    /**
     * Gets a property's value as an int.
     * Floating point values will be rounded. The value `true` is returned as 1, `false` as 0.
     * Returns 0 if the value doesn't exist or does not have a numeric value.
     *
     * @param key the key
     * @return the int value.
     */
    @Override
    public int getInt(String key) {
        return dict.getInt(key);
    }

    /**
     * Gets a property's value as an long.
     * Floating point values will be rounded. The value `true` is returned as 1, `false` as 0.
     * Returns 0 if the value doesn't exist or does not have a numeric value.
     *
     * @param key the key
     * @return the long value.
     */
    @Override
    public long getLong(String key) {
        return dict.getLong(key);
    }

    /**
     * Gets a property's value as an float.
     * Integers will be converted to float. The value `true` is returned as 1.0, `false` as 0.0.
     * Returns 0.0 if the value doesn't exist or does not have a numeric value.
     *
     * @param key the key
     * @return the float value.
     */
    @Override
    public float getFloat(String key) {
        return dict.getFloat(key);
    }

    /**
     * Gets a property's value as an double.
     * Integers will be converted to double. The value `true` is returned as 1.0, `false` as 0.0.
     * Returns 0.0 if the property doesn't exist or does not have a numeric value.
     *
     * @param key the key
     * @return the double value.
     */
    @Override
    public double getDouble(String key) {
        return dict.getDouble(key);
    }

    /**
     * Gets a property's value as a boolean. Returns true if the value exists, and is either `true`
     * or a nonzero number.
     *
     * @param key the key
     * @return the boolean value.
     */
    @Override
    public boolean getBoolean(String key) {
        return dict.getBoolean(key);
    }

    /**
     * Gets a property's value as a Blob.
     * Returns null if the value doesn't exist, or its value is not a Blob.
     *
     * @param key the key
     * @return the Blob value or null.
     */
    @Override
    public Blob getBlob(String key) {
        return dict.getBlob(key);
    }

    /**
     * Gets a property's value as a Date.
     * JSON does not directly support dates, so the actual property value must be a string, which is
     * then parsed according to the ISO-8601 date format (the default used in JSON.)
     * Returns null if the value doesn't exist, is not a string, or is not parseable as a date.
     * NOTE: This is not a generic date parser! It only recognizes the ISO-8601 format, with or
     * without milliseconds.
     *
     * @param key the key
     * @return the Date value or null.
     */
    @Override
    public Date getDate(String key) {
        return dict.getDate(key);
    }

    @Override
    public ReadOnlyArrayInterface getArray(String key) {
        return dict.getArray(key);
    }

    @Override
    public ReadOnlyDictionaryInterface getDictionary(String key) {
        return dict.getDictionary(key);
    }

    /**
     * Gets content of the current object as an Map. The values contained in the returned
     * Map object are all JSON based values.
     *
     * @return the Map object representing the content of the current object in the JSON format.
     */
    @Override
    public Map<String, Object> toMap() {
        return dict.toMap();
    }

    /**
     * Tests whether a property exists or not.
     * This can be less expensive than getObject(String),
     * because it does not have to allocate an Object for the property value.
     *
     * @param key the key
     * @return the boolean value representing whether a property exists or not.
     */
    @Override
    public boolean contains(String key) {
        return dict.contains(key);
    }

    //---------------------------------------------
    // Iterable implementation
    //---------------------------------------------
    @Override
    public Iterator<String> iterator() {
        return getKeys().iterator();
    }

    //---------------------------------------------
    // protected level access
    //---------------------------------------------

    @Override
    protected void finalize() throws Throwable {
        free();
        super.finalize();
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    void free() {
        if (this.c4doc != null) {
            this.c4doc.free();
            this.c4doc = null;
        }
    }

    // Sets c4doc and updates my root dictionary
    synchronized void setC4Doc(CBLC4Doc c4doc) {
        // NOTE: don't call free(), both c4doc and this.c4doc point to same C4Document.

        this.c4doc = c4doc;
        if (c4doc != null) {
            FLDict root = null;
            if (c4doc.getRawDoc() != null && !c4doc.getRawDoc().deleted())
                root = c4doc.getRawDoc().getSelectedBody2();
            setDictionaryFromData(new CBLFLDict(root, c4doc, database));
        } else
            this.dict = null;
    }

    void setDictionaryFromData(CBLFLDict data) {
        // Document overrides this
        this.dict = new ReadOnlyDictionary(data);
    }

    Database getDatabase() {
        return database;
    }

    void setDatabase(Database database) {
        this.database = database;
    }

    /**
     * Return whether the document exists in the database.
     *
     * @return true if exists, false otherwise.
     */
    boolean exists() {
        return c4doc != null ? c4doc.getRawDoc().exists() : false;
    }

    // Document overrides this
    long generation() {
        // TODO: c4rev_getGeneration
        return generationFromRevID(getSelectedRevID());
    }

    CBLFLDict getData() {
        return dict != null ? dict.getData() : null;
    }

    /**
     * TODO: This code is from v1.x. Better to replace with c4rev_getGeneration().
     */
    long generationFromRevID(String revID) {
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

    CBLC4Doc getC4doc() {
        return c4doc;
    }

    // Reads the document from the db into a new C4Document and returns it, w/o affecting my state.
    C4Document readC4Doc(boolean mustExist) throws LiteCoreException {
        if (database == null || database.getC4Database() == null) throw new IllegalStateException();
        return database.getC4Database().get(getId(), mustExist);
    }

    ConflictResolver effectiveConflictResolver() {
        return getDatabase().getConflictResolver() != null ?
                getDatabase().getConflictResolver() :
                new DefaultConflictResolver();
    }

    boolean selectConflictingRevision() {
        try {
            c4doc.getRawDoc().selectNextLeafRevision(false, true);
            setC4Doc(c4doc); // self.c4Doc = _c4Doc; // This will update to the selected revision
        } catch (LiteCoreException e) {
            Log.e(TAG, "Failed to selectNextLeaf: doc -> %s", e, c4doc);
            return false;
        }
        return true;
    }

    boolean selectCommonAncestor(ReadOnlyDocument doc1, ReadOnlyDocument doc2) {
        if (!c4doc.getRawDoc().selectCommonAncestorRevision(doc1.getSelectedRevID(), doc2.getSelectedRevID()))
            return false;
        setC4Doc(c4doc); // self.c4Doc = _c4Doc; // This will update to the selected revision
        return true;
    }

    String getSelectedRevID() {
        return c4doc != null ? c4doc.getSelectedRevID() : null;
    }

    // Document overrides this
    byte[] encode() {
        return c4doc != null ? c4doc.getRawDoc().getSelectedBody() : null;
    }
}
