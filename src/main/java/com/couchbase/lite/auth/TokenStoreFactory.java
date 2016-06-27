//
// Copyright (c) 2016 Couchbase, Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
// except in compliance with the License. You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under the
// License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions
// and limitations under the License.
//
package com.couchbase.lite.auth;


import android.os.Build;

import com.couchbase.lite.Context;
import com.couchbase.lite.android.AndroidContext;

/**
 * Created by hideki on 6/22/16.
 */
public class TokenStoreFactory {
    private static final boolean hasKeyStore = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2; // API 18
    private static final boolean hasKeyGenerator = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M; // API 23

    public static TokenStore build(Context context) {
        android.content.Context androidContext = null;
        if (context instanceof AndroidContext)
            androidContext = ((AndroidContext) context).getWrappedContext();
        if (androidContext == null)
            return new MemTokenStore();
        return hasKeyGenerator ? new AESSecureTokenStore(androidContext) :
                (hasKeyStore ? new RSASecureTokenStore(androidContext) : new MemTokenStore());
    }
}
