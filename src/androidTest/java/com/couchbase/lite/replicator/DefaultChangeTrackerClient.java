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
package com.couchbase.lite.replicator;

import java.util.Map;

import okhttp3.OkHttpClient;

/**
 * Created by hideki on 5/17/16.
 */
public class DefaultChangeTrackerClient implements ChangeTrackerClient {
    @Override
    public OkHttpClient getOkHttpClient() {
        return null;
    }

    @Override
    public void changeTrackerReceivedChange(Map<String, Object> change) {
    }

    @Override
    public void changeTrackerStopped(ChangeTracker tracker) {
    }

    @Override
    public void changeTrackerFinished(ChangeTracker tracker) {
    }

    @Override
    public void changeTrackerCaughtUp() {
    }
}
