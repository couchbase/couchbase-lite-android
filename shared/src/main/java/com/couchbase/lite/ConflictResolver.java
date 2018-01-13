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

/**
 * Interface for an application-defined object that can resolve a conflict between two revisions of
 * a document. Called when saving a Document, when there is a newer revision already in the database;
 * and also when the replicator pulls a remote revision that conflicts with a locally-saved revision.
 */
public interface ConflictResolver {
    /**
     * Resolves the given conflict. Returning a nil document means giving up the conflict resolution
     * and will result to a conflicting error returned when saving the document.
     *
     * @param conflict The conflict object.
     * @return The result document of the conflict resolution.
     */
    Document resolve(Conflict conflict);
}
