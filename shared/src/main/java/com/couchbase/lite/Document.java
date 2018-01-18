package com.couchbase.lite;

import com.couchbase.lite.internal.support.Log;
import com.couchbase.litecore.C4Document;
import com.couchbase.litecore.LiteCoreException;
import com.couchbase.litecore.fleece.FLDict;
import com.couchbase.litecore.fleece.FLEncoder;
import com.couchbase.litecore.fleece.FLSliceResult;
import com.couchbase.litecore.fleece.MRoot;

import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.couchbase.litecore.C4Constants.C4DocumentFlags.kDocDeleted;
import static com.couchbase.litecore.C4Constants.C4ErrorDomain.LiteCoreDomain;
import static com.couchbase.litecore.C4Constants.LiteCoreError.kC4ErrorNotFound;

/**
 * Readonly version of the Document.
 */
public class Document implements DictionaryInterface, Iterable<String> {
    private static final String TAG = Log.DATABASE;

    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private Database _database;
    private String _id;
    private C4Document _c4doc;
    private MRoot _root;
    private LiteCoreException encodingError;

    // access from MutableDocument
    FLDict _data;
    Dictionary _dict;
    boolean invalidated = false;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    Document(Database database,
             String id,
             C4Document c4doc) {
        this._database = database;
        this._id = id;
        this.invalidated = false;
        setC4Document(c4doc);
    }

    Document(Database database,
             String id,
             boolean includeDeleted) throws CouchbaseLiteException {
        this(database, id, null);
        C4Document doc;
        try {
            if (_database == null || _database.getC4Database() == null)
                throw new IllegalStateException();
            doc = _database.getC4Database().get(getId(), true);
        } catch (LiteCoreException e) {
            if (e.getDomain() == LiteCoreDomain && e.getCode() == kC4ErrorNotFound)
                throw new CouchbaseLiteException(Status.CBLErrorDomain, Status.NotFound);
            throw LiteCoreBridge.convertRuntimeException(e);
        }

        if (!includeDeleted && (doc.getFlags() & kDocDeleted) != 0) {
            doc.free();
            throw new CouchbaseLiteException(Status.CBLErrorDomain, Status.NotFound);
        }

        // NOTE: _c4doc should not be null.
        setC4Document(doc);
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
        return _id;
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
        return _c4doc != null ? _c4doc.getSelectedSequence() : 0;
    }

    /**
     * Return whether the document is deleted
     *
     * @return true if deleted, false otherwise
     */
    public boolean isDeleted() {
        return _c4doc != null && _c4doc != null ? _c4doc.deleted() : false;
    }

    /**
     * Return a mutable copy of the document
     *
     * @return the MutableDocument instance
     */
    public MutableDocument toMutable() {
        return new MutableDocument(this, null);
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
        return _dict.count();
    }

    /**
     * Get an List containing all keys, or an empty List if the document has no properties.
     *
     * @return all keys
     */
    @Override
    public List<String> getKeys() {
        return _dict.getKeys();
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
    public Object getValue(String key) {
        return _dict.getValue(key);
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
        return _dict.getString(key);
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
        return _dict.getNumber(key);
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
        return _dict.getInt(key);
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
        return _dict.getLong(key);
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
        return _dict.getFloat(key);
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
        return _dict.getDouble(key);
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
        return _dict.getBoolean(key);
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
        return _dict.getBlob(key);
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
        return _dict.getDate(key);
    }

    /**
     * Get a property's value as a Array, which is a mapping object of an array value.
     * Returns null if the property doesn't exists, or its value is not an Array.
     *
     * @param key the key
     * @return The Array object or null.
     */
    @Override
    public Array getArray(String key) {
        return _dict.getArray(key);
    }

    /**
     * Get a property's value as a Dictionary, which is a mapping object of
     * a Dictionary value.
     * Returns null if the property doesn't exists, or its value is not a Dictionary.
     *
     * @param key the key
     * @return The Dictionary object or null.
     */
    @Override
    public Dictionary getDictionary(String key) {
        return _dict.getDictionary(key);
    }

    /**
     * Gets content of the current object as an Map. The values contained in the returned
     * Map object are all JSON based values.
     *
     * @return the Map object representing the content of the current object in the JSON format.
     */
    @Override
    public Map<String, Object> toMap() {
        return _dict.toMap();
    }

    /**
     * Tests whether a property exists or not.
     * This can be less expensive than getValue(String),
     * because it does not have to allocate an Object for the property value.
     *
     * @param key the key
     * @return the boolean value representing whether a property exists or not.
     */
    @Override
    public boolean contains(String key) {
        return _dict.contains(key);
    }

    //---------------------------------------------
    // Iterable implementation
    //---------------------------------------------

    /**
     * Gets  an iterator over the keys of the document's properties
     *
     * @return The key iterator
     */
    @Override
    public Iterator<String> iterator() {
        return getKeys().iterator();
    }

    //---------------------------------------------
    // Override
    //---------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Document)) return false;

        Document doc = (Document) o;

        // Step 1: Check Database
        if (_database != null ? !_database.equalsWithPath(doc._database) : doc._database != null)
            return false;

        // Step 2: Check document ID
        // NOTE _id never null?
        if (!_id.equals(doc._id))
            return false;

        // Step 3: Check content
        // NOTE: _dict never null??
        if (!_dict.equals(doc._dict))
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        // NOTE _id and _dict never null
        int result = _database != null && _database.getPath() != null ? _database.getPath().hashCode() : 0;
        result = 31 * result + _id.hashCode();
        result = 31 * result + _dict.hashCode();
        return result;
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
        _root = null;
        if (_c4doc != null) {
            _c4doc.free();
            _c4doc = null;
        }
    }

    // Sets _c4doc and updates my root dictionary
    synchronized void setC4Document(C4Document c4doc) {
        // NOTE: don't call free(), both _c4doc and this._c4doc point to same C4Document.
        this._c4doc = c4doc;
        _data = null;
        if (c4doc != null) {
            if (c4doc != null && !c4doc.deleted())
                _data = c4doc.getSelectedBody2();
        }
        updateDictionary();
    }

    boolean isMutable() {
        // Document overrides this
        return false;
    }

    boolean isEmpty() {
        return _dict.isEmpty();
    }

    void updateDictionary() {
        if (_data != null) {
            _root = new MRoot(new DocContext(_database), _data.toFLValue(), isMutable());
            _dict = (Dictionary) _root.asNative();
        } else {
            _root = null;
            _dict = isMutable() ? new MutableDictionary() : new Dictionary();
        }
    }

    Database getDatabase() {
        return _database;
    }

    void setDatabase(Database database) {
        this._database = database;
    }

    /**
     * Return whether the document exists in the database.
     *
     * @return true if exists, false otherwise.
     */
    boolean exists() {
        return _c4doc != null ? _c4doc.exists() : false;
    }

    // Document overrides this
    long generation() {
        // TODO: c4rev_getGeneration
        return generationFromRevID(getRevID());
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

    C4Document getC4doc() {
        return _c4doc;
    }

    boolean selectConflictingRevision() {
        try {
            _c4doc.selectNextLeafRevision(false, true);
            setC4Document(_c4doc); // self.c4Doc = _c4Doc; // This will update to the selected revision
        } catch (LiteCoreException e) {
            Log.i(TAG, "Failed to selectNextLeaf: doc -> %s", e, _c4doc);
            return false;
        }
        return true;
    }

    boolean selectCommonAncestor(Document doc1, Document doc2) {
        if (!_c4doc.selectCommonAncestorRevision(doc1.getRevID(), doc2.getRevID()))
            return false;
        setC4Document(_c4doc); // self.c4Doc = _c4Doc; // This will update to the selected revision
        return true;
    }

    String getRevID() {
        return _c4doc != null ? _c4doc.getSelectedRevID() : null;
    }

    byte[] encode() throws LiteCoreException {
        encodingError = null;
        FLEncoder encoder = getDatabase().getC4Database().createFleeceEncoder();
        try {
            encoder.setExtraInfo(this); // TODO: Need to consider better value
            _dict.encodeTo(encoder);
            if (encodingError != null) {
                LiteCoreException ex = encodingError;
                encodingError = null;
                throw ex;
            }
            return encoder.finish();
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertRuntimeException(e);
        } finally {
            encoder.setExtraInfo(null);
            encoder.free();
        }
    }

    FLSliceResult encode2() throws LiteCoreException {
        encodingError = null;
        FLEncoder encoder = getDatabase().getC4Database().createFleeceEncoder();
        try {
            encoder.setExtraInfo(this);
            _dict.encodeTo(encoder);

            if (encodingError != null) {
                LiteCoreException ex = encodingError;
                encodingError = null;
                throw ex;
            }
            return encoder.finish2();
        } finally {
            encoder.setExtraInfo(null);
            encoder.free();
        }
    }

    boolean isNewDocument() {
        return getRevID() == null;
    }

    boolean isInvalidated() {
        return invalidated;
    }
}
