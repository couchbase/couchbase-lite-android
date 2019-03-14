//
// DocumentChangeNotifier.java
//
// Copyright (c) 2018 Couchbase, Inc All rights reserved.
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

import com.couchbase.lite.internal.core.C4DocumentObserver;
import com.couchbase.lite.internal.core.C4DocumentObserverListener;


class DocumentChangeNotifier extends ChangeNotifier<DocumentChange> {
    private final Database db;
    private final String docID;
    private C4DocumentObserver obs;

    DocumentChangeNotifier(final Database db, final String docID) {
        this.db = db;
        this.docID = docID;
        this.obs = db.c4db.createDocumentObserver(docID, new C4DocumentObserverListener() {
                @Override
                public void callback(
                    final C4DocumentObserver observer,
                    final String docID,
                    final long sequence,
                    final Object context) {
                    db.scheduleOnPostNotificationExecutor(
                        new Runnable() {
                            @Override
                            public void run() {
                                ((DocumentChangeNotifier) context).postChange();
                            }
                        },
                        0);
                }
            },
            this);
    }

    void stop() {
        if (obs != null) {
            obs.free();
            obs = null;
        }
    }

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        stop();
        super.finalize();
    }

    private void postChange() {
        postChange(new DocumentChange(db, docID));
    }
}
