//
// StringUtils.java
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
package com.couchbase.lite.internal.utils;

public final class StringUtils {
    private StringUtils() { }

    // NSString - stringByDeletingLastPathComponent
    // https://developer.apple.com/reference/foundation/nsstring/1411141-stringbydeletinglastpathcomponen
    public static String stringByDeletingLastPathComponent(final String str) {
        String path = str;
        int start = str.length() - 1;
        while (path.charAt(start) == '/') { start--; }

        final int index = path.lastIndexOf('/', start);
        path = (index < 0) ? "" : path.substring(0, index);


        if (path.length() == 0 && str.charAt(0) == '/') { return "/"; }

        return path;
    }

    // NSString - lastPathComponent
    // https://developer.apple.com/reference/foundation/nsstring/1416528-lastpathcomponent
    public static String lastPathComponent(final String str) {
        final String[] segments = str.split("/");
        if (segments != null && segments.length > 0) { return segments[segments.length - 1]; }
        else { return str; }
    }
}
