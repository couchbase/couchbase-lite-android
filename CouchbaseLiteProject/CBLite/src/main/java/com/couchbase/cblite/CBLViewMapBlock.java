/**
 * Original iOS version by  Jens Alfke
 * Ported to Android by Marty Schoch
 *
 * Copyright (c) 2012 Couchbase, Inc. All rights reserved.
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

package com.couchbase.cblite;

import java.util.Map;

/**
 * Block container for the map callback function
 */
public interface CBLViewMapBlock {

    /**
     * A "map" function called when a document is to be added to a view.
     * @param document The contents of the document being analyzed.
     * @param emitter A block to be called to add a key/value pair to the view. Your block can call it zero, one or multiple times.
     */
    void map(Map<String,Object> document, CBLViewMapEmitBlock emitter);

}
