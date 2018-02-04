//
// DocumentChangeListenerTokenTest.java
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

import org.junit.Test;

import java.util.concurrent.Executor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class DocumentChangeListenerTokenTest {
    @Test
    public void testIllegalArgumentException() {
        try {
            new DocumentChangeListenerToken(null, null, "doc1");
            fail();
        } catch (IllegalArgumentException ex) {
            // ok
        }

        try {
            new DocumentChangeListenerToken(null, new DocumentChangeListener() {
                @Override
                public void changed(DocumentChange change) {
                }
            }, null);
            fail();
        } catch (IllegalArgumentException ex) {
            // ok
        }
    }

    @Test
    public void testGetExecutor() {
        Executor executor = new Executor() {
            @Override
            public void execute(Runnable runnable) {
            }
        };

        DocumentChangeListener listener = new DocumentChangeListener() {
            @Override
            public void changed(DocumentChange change) {
            }
        };

        // custom Executor
        DocumentChangeListenerToken token = new DocumentChangeListenerToken(executor, listener, "docID");
        assertEquals(executor, token.getExecutor());

        // UI thread Executor
        token = new DocumentChangeListenerToken(null, listener, "docID");
        assertEquals(DefaultExecutor.instance(), token.getExecutor());
    }
}
