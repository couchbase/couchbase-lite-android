//
// DefaultConflictResolver.java
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

class DefaultConflictResolver implements ConflictResolver {
    /**
     * Default resolution algorithm:
     * 1. DELETE always wins.
     * 2. Most active wins (Higher generation number).
     * 3. Higher RevID wins.
     */
    @Override
    public Document resolve(Conflict conflict) {
        Document mine = conflict.getMine();
        Document theirs = conflict.getTheirs();
        if (theirs.isDeleted())
            return theirs;
        else if (mine.isDeleted())
            return mine;
        else if (mine.generation() > theirs.generation())
            return mine;
        else if (mine.generation() < theirs.generation())
            return theirs;
        else if (mine.getRevID() != null && mine.getRevID().compareTo(theirs.getRevID()) > 0)
            return mine;
        else
            return theirs;
    }
}
