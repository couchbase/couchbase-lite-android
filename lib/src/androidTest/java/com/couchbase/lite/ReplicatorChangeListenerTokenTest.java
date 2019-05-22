//
// ReplicatorChangeListenerTokenTest.java
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

import java.util.concurrent.Executor;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;


public class ReplicatorChangeListenerTokenTest {
    @Test
    public void testIllegalArgumentException() {
        try {
            new ReplicatorChangeListenerToken(null, null);
            fail();
        }
        catch (IllegalArgumentException ex) {
            // ok
        }
    }

    @Test
    public void testGetExecutor() {
        Executor executor = new Executor() {
            @Override
            public void execute(Runnable runnable) { }
        };

        ReplicatorChangeListener listener = new ReplicatorChangeListener() {
            @Override
            public void changed(ReplicatorChange change) { }
        };

        // custom Executor
        ReplicatorChangeListenerToken token = new ReplicatorChangeListenerToken(executor, listener);
        assertEquals(executor, token.getExecutor());

        // UI thread Executor
        token = new ReplicatorChangeListenerToken(null, listener);
        assertEquals(CouchbaseLite.getExecutionService().getMainExecutor(), token.getExecutor());
    }
}
