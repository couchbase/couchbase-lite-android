//
// ChangeListenerToken.java
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

import java.util.concurrent.Executor;


class ChangeListenerToken<T> implements ListenerToken {
    private final ChangeListener<T> listener;
    private final Executor executor;
    private Object key;

    ChangeListenerToken(Executor executor, ChangeListener<T> listener) {
        this.executor = executor;
        this.listener = listener;
    }

    public Object getKey() {
        return key;
    }

    public void setKey(Object key) {
        this.key = key;
    }

    void postChange(final T change) {
        getExecutor().execute(new Runnable() {
            @Override
            public void run() {
                listener.changed(change);
            }
        });
    }

    private Executor getExecutor() {
        return executor != null ? executor : DefaultExecutor.getInstance();
    }
}
