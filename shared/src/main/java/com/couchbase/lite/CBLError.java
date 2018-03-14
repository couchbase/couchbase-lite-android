//
// CBLError.java
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
package com.couchbase.lite;

interface CBLError {
    // Error Domain
    interface Domain {
        String CBLErrorDomain = "CouchbaseLite";
        String SQLiteErrorDomain = "CouchbaseLite.SQLite";
        String FleeceErrorDomain = "CouchbaseLite.Fleece";
    }

    // Error Code
    interface Code {
        int CBLErrorAssertionFailed = 1;         // Internal assertion failure
        int CBLErrorUnimplemented = 2;           // Oops, an unimplemented API call
        int CBLErrorNoSequences = 3;             // This KeyStore does not support sequences
        int CBLErrorUnsupportedEncryption = 4;   // Unsupported encryption algorithm
        int CBLErrorNoTransaction = 5;           // Function must be called within a transaction
        int CBLErrorBadRevisionID = 6;           // Invalid revision ID syntax
        int CBLErrorBadVersionVector = 7;        // Invalid version vector syntax
        int CBLErrorCorruptRevisionData = 8;     // Revision contains corrupted/unreadable data
        int CBLErrorCorruptIndexData = 9;        // Index contains corrupted/unreadable data
        int CBLErrorTokenizerError = 10; /*10*/  // can't create text tokenizer for FTS
        int CBLErrorNotOpen = 11;                // Database/KeyStore/index is not open
        int CBLErrorNotFound = 12;               // Document not found
        int CBLErrorDeleted = 13;                // Document has been deleted
        int CBLErrorConflict = 14;               // Document update conflict
        int CBLErrorInvalidParameter = 15;       // Invalid function parameter or struct value
        int CBLErrorDatabaseError = 16;          // Lower-level database error (SQLite)
        int CBLErrorUnexpectedError = 17;        // Internal unexpected C++ exception
        int CBLErrorCantOpenFile = 18;           // Database file can't be opened; may not exist
        int CBLErrorIOError = 19;                // File I/O error
        int CBLErrorCommitFailed = 20; /*20*/    // Transaction commit failed
        int CBLErrorMemoryError = 21;            // Memory allocation failed (out of memory?)
        int CBLErrorNotWriteable = 22;           // File is not writeable
        int CBLErrorCorruptData = 23;            // Data is corrupted
        int CBLErrorBusy = 24;                   // Database is busy/locked
        int CBLErrorNotInTransaction = 25;       // Function cannot be called while in a transaction
        int CBLErrorTransactionNotClosed = 26;   // Database can't be closed while a transaction is open
        int CBLErrorIndexBusy = 27;              // (unused)
        int CBLErrorUnsupported = 28;            // Operation not supported in this database
        int CBLErrorUnreadableDatabase = 29;     // File is not a database, or encryption key is wrong
        int CBLErrorWrongFormat = 30; /*30*/     // Database exists but not in the format/storage requested
        int CBLErrorCrypto = 31;                 // Encryption/decryption error
        int CBLErrorInvalidQuery = 32;           // Invalid query
        int CBLErrorMissingIndex = 33;           // No such index, or query requires a nonexistent index
        int CBLErrorInvalidQueryParam = 34;      // Unknown query param name, or param number out of range
        int CBLErrorRemoteError = 35;            // Unknown error from remote server
        int CBLErrorDatabaseTooOld = 36;         // Database file format is older than what I can open
        int CBLErrorDatabaseTooNew = 37;         // Database file format is newer than what I can open
        int CBLErrorBadDocID = 38;               // Invalid document ID
        int CBLErrorCantUpgradeDatabase = 29;    // Database can't be upgraded (might be unsupported dev version)
        // Note: These are equivalent to the C4Error codes declared in LiteCore's c4Base.h

        // Network error codes (higher level than POSIX, lower level than HTTP.)
        int CBLErrorNetworkBase = 5000;           // --- Network status codes start here
        int CBLErrorDNSFailure = 5001;            // DNS lookup failed
        int CBLErrorUnknownHost = 5002;           // DNS server doesn't know the hostname
        int CBLErrorTimeout = 5003;               // socket timeout during an operation
        int CBLErrorInvalidURL = 5004;            // the provided url is not valid
        int CBLErrorTooManyRedirects = 5005;      // too many HTTP redirects for the HTTP client to handle
        int CBLErrorTLSHandshakeFailed = 5006;    // failure during TLS handshake process
        int CBLErrorTLSCertExpired = 5007;        // the provided tls certificate has expired
        int CBLErrorTLSCertUntrusted = 5008;      // Cert isn't trusted for other reason
        int CBLErrorTLSClientCertRequired = 5009; // a required client certificate was not provided
        int CBLErrorTLSClientCertRejected = 5010; // client certificate was rejected by the server
        int CBLErrorTLSCertUnknownRoot = 5011;    // Self-signed cert, or unknown anchor cert

        int CBLErrorHTTPBase = 10000;                   // ---- HTTP status codes start here
        int CBLErrorHTTPAuthRequired = 10401;           // Missing or incorrect user authentication
        int CBLErrorHTTPForbidden = 10403;              // User doesn't have permission to access resource
        int CBLErrorHTTPNotFound = 10404;               // Resource not found
        int CBLErrorHTTPConflict = 10409;               // Update conflict
        int CBLErrorHTTPProxyAuthRequired = 10407;      // HTTP proxy requires authentication
        int CBLErrorHTTPEntityTooLarge = 10413;         // Data is too large to upload
        int CBLErrorHTTPImATeapot = 10418;              // HTCPCP/1.0 error (RFC 2324)
        int CBLErrorHTTPInternalServerError = 10500;    // Something's wrong with the server
        int CBLErrorHTTPNotImplemented = 10501;         // Unimplemented server functionality
        int CBLErrorHTTPServiceUnavailable = 10503;     // Service is down temporarily(?)

        int CBLErrorWebSocketBase = 11000;              // ---- WebSocket status codes start here
        int CBLErrorWebSocketGoingAway = 11001;         // Peer has to close, e.g. because host app is quitting
        int CBLErrorWebSocketProtocolError = 11002;     // Protocol violation: invalid framing data
        int CBLErrorWebSocketDataError = 11003;         // Message payload cannot be handled
        int CBLErrorWebSocketAbnormalClose = 11006;     // TCP socket closed unexpectedly
        int CBLErrorWebSocketBadMessageFormat = 11007;  // Unparseable WebSocket message
        int CBLErrorWebSocketPolicyError = 11008;       // Message violated unspecified policy
        int CBLErrorWebSocketMessageTooBig = 11009;     // Message is too large for peer to handle
        int CBLErrorWebSocketMissingExtension = 11010;  // Peer doesn't provide a necessary extension
        int CBLErrorWebSocketCantFulfill = 11011;       // Can't fulfill request due to "unexpected condition"
    }
}
