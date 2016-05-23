/**
 * Copyright (c) 2016 Couchbase, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package com.couchbase.lite;

import com.couchbase.lite.util.Log;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by hideki on 8/13/15.
 */
public class SimpleCRUDTest extends LiteTestCaseWithDB {
    public static final String TAG = SimpleCRUDTest.class.getSimpleName();

    public void testHelloWorld() throws Exception{

        Log.w(TAG, "Create a document");

        // get the current date and time
        SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd");
        Calendar calendar = GregorianCalendar.getInstance();
        String currentTimeString = dateFormatter.format(calendar.getTime());

        // create an object that contains data for a document
        Map<String, Object> docContent = new HashMap<String, Object>();
        docContent.put("message", "Hello Couchbase Lite");
        docContent.put("creationDate", currentTimeString);

        // display the data for the new document
        Log.w(TAG, "new docContent=" + String.valueOf(docContent));

        // create an empty document
        Document document = database.createDocument();
        assertNotNull(document);

        // write the document to the database
        try {
            document.putProperties(docContent);
            Log.w(TAG, "Document written to database named " + database.getName() + " with ID = " + document.getId());
        } catch (CouchbaseLiteException e) {
            Log.e(TAG, "Cannot write document to database", e);
        }

        // save the ID of the new document
        String docID = document.getId();
        assertNotNull(docID);
        assertNotSame("", docID);

        Log.w(TAG, "Retrieve a document");


        // retrieve the document from the database
        Document retrievedDocument = database.getDocument(docID);
        assertNotNull(retrievedDocument);

        // display the retrieved document
        Log.w(TAG, "retrievedDocument=" + String.valueOf(retrievedDocument.getProperties()));

        Log.w(TAG, "Update a document");

        // update the document
        Map<String, Object> updatedProperties = new HashMap<String, Object>();
        updatedProperties.putAll(retrievedDocument.getProperties());
        updatedProperties.put ("message", "We're having a heat wave!");
        updatedProperties.put ("temperature", "95");

        // display the data for the update document
        Log.w(TAG, "update docContent=" + String.valueOf(updatedProperties));

        try {
            retrievedDocument.putProperties(updatedProperties);
            Log.w(TAG, "updated retrievedDocument=" + String.valueOf(retrievedDocument.getProperties()));
        } catch (CouchbaseLiteException e) {
            Log.e (TAG, "Cannot update document", e);
        }

        Log.w(TAG, "Delete a document");

        // delete the document
        try {
            retrievedDocument.delete();
            Log.w (TAG, "Deleted document, deletion status = " + retrievedDocument.isDeleted());
        } catch (CouchbaseLiteException e) {
            Log.e (TAG, "Cannot delete document", e);
        }
    }
}
