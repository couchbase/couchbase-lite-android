//
// ReplicatorChange.java
//
// Copyright (c) 2017 Couchbase, Inc All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package com.couchbase.lite;

import android.support.annotation.NonNull;


/**
 * ReplicatorChange contains the replicator status information.
 */
public final class ReplicatorChange {
    private final Replicator replicator;
    private final Replicator.Status status;

    ReplicatorChange(Replicator replicator, Replicator.Status status) {
        this.replicator = replicator;
        this.status = status;
    }

    /**
     * Return the source replicator object.
     */
    @NonNull
    public Replicator getReplicator() {
        return replicator;
    }

    /**
     * Return the replicator status.
     */
    @NonNull
    public Replicator.Status getStatus() {
        return status;
    }

    @NonNull
    @Override
    public String toString() {
        return "ReplicatorChange{" +
            "replicator=" + replicator +
            ", status=" + status +
            '}';
    }
}
