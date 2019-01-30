//
// C4WebSocketCloseCode.java
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

// Standard WebSocket close status codes, for use in C4Errors with WebSocketDomain.
// These are defined at <http://tools.ietf.org/html/rfc6455#section-7.4.1>
public interface C4WebSocketCloseCode {
    int kWebSocketCloseNormal = 1000;
    int kWebSocketCloseGoingAway = 1001; // Peer has to close, e.g. because host app is quitting
    int kWebSocketCloseProtocolError = 1002; // Protocol violation: invalid framing data
    int kWebSocketCloseDataError = 1003; // Message payload cannot be handled
    int kWebSocketCloseNoCode = 1005; // Never sent, only received
    int kWebSocketCloseAbnormal = 1006; // Never sent, only received
    int kWebSocketCloseBadMessageFormat = 1007; // Unparseable message
    int kWebSocketClosePolicyError = 1008;
    int kWebSocketCloseMessageTooBig = 1009;
    int kWebSocketCloseMissingExtension = 1010; // Peer doesn't provide a necessary extension
    int kWebSocketCloseCantFulfill = 1011; // Can't fulfill request due to "unexpected condition"
    int kWebSocketCloseTLSFailure = 1015; // Never sent, only received

    int kWebSocketCloseFirstAvailable = 4000;   // First unregistered code for freeform use
    int kWebSocketCloseUserTransient = 4001;    // For user-defined transient error
    int kWebSocketCloseUserPermanent = 4002;    // For user-defined permanent error
}
