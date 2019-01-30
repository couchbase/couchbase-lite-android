//
// FLConstants.java
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
package com.couchbase.litecore.fleece;

public class FLConstants {
    // Types of Fleece values. Basically JSON, with the addition of Data (raw blob).
    public interface FLValueType {
        int kFLUndefined = -1; // Type of a nullptr FLValue (i.e. no such value)
        int kFLNull = 0;
        int kFLBoolean = 1;
        int kFLNumber = 2;
        int kFLString = 3;
        int kFLData = 4;
        int kFLArray = 5;
        int kFLDict = 6;
    }

    public interface FLError {
        int NoError = 0;
        int MemoryError = 1;        // Out of memory, or allocation failed
        int OutOfRange = 2;        // Array index or iterator out of range
        int InvalidData = 3;        // Bad input data (NaN, non-string key, etc.)
        int EncodeError = 4;        // Structural error encoding (missing value, too many ends, etc.)
        int JSONError = 5;        // Error parsing JSON
        int UnknownValue = 6;       // Unparseable data in a Value (corrupt? Or from some distant future?)
        int InternalError = 7;      // Something that shouldn't happen
        int NotFound = 8;
    }
}
