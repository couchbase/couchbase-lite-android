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

/**
 * Same interpretation as HTTP status codes, esp. 200, 201, 404, 409, 500.
 */
public class CBLStatus {

    public static final int UNKNOWN = -1;
    public static final int OK = 200;
    public static final int CREATED = 201;
    public static final int NOT_MODIFIED = 304;
    public static final int BAD_REQUEST = 400;
    public static final int FORBIDDEN = 403;
    public static final int NOT_FOUND = 404;
    public static final int METHOD_NOT_ALLOWED = 405;
    public static final int NOT_ACCEPTABLE = 406;
    public static final int CONFLICT = 409;
    public static final int PRECONDITION_FAILED = 412;
    public static final int BAD_ATTACHMENT = 491;
    public static final int BAD_JSON = 493;
    public static final int INTERNAL_SERVER_ERROR = 500;
    public static final int STATUS_ATTACHMENT_ERROR = 592;

    public static final int DB_ERROR = 590;

    private int code;

    public CBLStatus() {
        this.code = UNKNOWN;
    }

    public CBLStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public boolean isSuccessful() {
        return (code > 0 && code < 400);
    }

    @Override
    public String toString() {
        return "Status: " + code;
    }

}
