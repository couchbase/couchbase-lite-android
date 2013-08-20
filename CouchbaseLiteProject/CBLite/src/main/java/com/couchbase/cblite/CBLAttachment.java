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

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class CBLAttachment {

    private InputStream contentStream;
    private String contentType;
    private Map<String, Object> metadata;

    public CBLAttachment() {

    }

    public CBLAttachment(InputStream contentStream, String contentType) {
        this.contentStream = contentStream;
        this.contentType = contentType;
        metadata = new HashMap<String, Object>();
        metadata.put("content_type", contentType);
        metadata.put("follows", true);
    }

    public InputStream getContentStream() {
        return contentStream;
    }

    public void setContentStream(InputStream contentStream) {
        this.contentStream = contentStream;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }




}
