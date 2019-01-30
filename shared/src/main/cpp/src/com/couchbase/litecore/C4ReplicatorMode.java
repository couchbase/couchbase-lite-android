//
// C4ReplicatorMode.java
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
package com.couchbase.litecore;

// How to replicate, in either direction
public interface C4ReplicatorMode {
    int kC4Disabled = 0;   // Do not allow this direction
    int kC4Passive = 1;    // Allow peer to initiate this direction
    int kC4OneShot = 2;    // Replicate, then stop
    int kC4Continuous = 3; // Keep replication active until stopped by application
}
