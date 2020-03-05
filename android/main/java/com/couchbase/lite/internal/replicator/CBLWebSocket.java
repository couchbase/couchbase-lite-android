//
// Copyright (c) 2019 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.internal.replicator;

import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.system.ErrnoException;

import java.io.EOFException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.util.Map;

import com.couchbase.lite.internal.core.C4Constants;


public class CBLWebSocket extends AbstractCBLWebSocket {
    // Posix errno values with Android.
    // from sysroot/usr/include/asm-generic/errno.h
    private static final int ECONNRESET = 104;    // java.net.SocketException
    private static final int ECONNREFUSED = 111;  // java.net.ConnectException


    public CBLWebSocket(
        long handle,
        String scheme,
        String hostname,
        int port,
        String path,
        Map<String, Object> options)
        throws GeneralSecurityException, URISyntaxException {
        super(handle, scheme, hostname, port, path, options);
    }

    @SuppressWarnings("PMD.CollapsibleIfStatements")
    protected boolean handleClose(@NonNull Throwable error) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (checkErrnoException(error)) { return true; }
        }

        // ConnectException
        if (error instanceof ConnectException) {
            closed(C4Constants.ErrorDomain.POSIX, ECONNREFUSED, null);
            return true;
        }

        // SocketException
        else if (error instanceof SocketException) {
            closed(C4Constants.ErrorDomain.POSIX, ECONNRESET, null);
            return true;
        }

        // EOFException
        if (error instanceof EOFException) {
            closed(C4Constants.ErrorDomain.POSIX, ECONNRESET, null);
            return true;
        }

        return false;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private boolean checkErrnoException(@NonNull Throwable error) {
        Throwable cause = error.getCause();
        if (cause == null) { return false; }

        cause = cause.getCause();
        if (!(cause instanceof ErrnoException)) { return false; }

        closed(C4Constants.ErrorDomain.POSIX, ((ErrnoException) cause).errno, null);

        return true;
    }
}
