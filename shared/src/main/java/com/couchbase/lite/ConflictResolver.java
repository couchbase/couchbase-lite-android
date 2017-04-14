/**
 * Copyright (c) 2017 Couchbase, Inc. All rights reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package com.couchbase.lite;

import java.util.Map;

/**
 * Interface for an application-defined object that can resolve a conflict between two revisions of
 * a document. Called when saving a Document, when there is a newer revision already in the database;
 * and also when the replicator pulls a remote revision that conflicts with a locally-saved revision.
 */
public interface ConflictResolver {
    /**
     * Resolves conflicting edits of a document against their common base.
     *
     * @param localProperties       The revision that is being saved, or the revision in the local
     *                              database for which there is a server side conflict.
     * @param conflictingProperties The conflicting revision that is already stored in the database,
     *                              or on the server.
     * @param baseProperties        The common parent revision of these two revisions, if available.
     * @return the resolved set of properties for the document to store, or null to give up if
     * automatic resolution isn't possible.
     */
    Map<String, Object> resolve(Map<String, Object> localProperties,
                                Map<String, Object> conflictingProperties,
                                Map<String, Object> baseProperties);
}
