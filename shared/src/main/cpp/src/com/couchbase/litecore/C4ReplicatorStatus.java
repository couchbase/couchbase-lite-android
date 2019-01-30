//
// C4ReplicatorStatus.java
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


public class C4ReplicatorStatus {
    public interface C4ReplicatorActivityLevel {
        int kC4Stopped = 0;
        int kC4Offline = 1;
        int kC4Connecting = 2;
        int kC4Idle = 3;
        int kC4Busy = 4;
    }

    private int activityLevel = -1;      // C4ReplicatorActivityLevel
    private long progressUnitsCompleted = 0L; // C4Progress.unitsCompleted
    private long progressUnitsTotal = 0L;     // C4Progress.unitsTotal
    private long progressDocumentCount = 0L;     // C4Progress.documentCount
    private int errorDomain = 0;         // C4Error.domain
    private int errorCode = 0;           // C4Error.code
    private int errorInternalInfo = 0;   // C4Error.internal_info

    public C4ReplicatorStatus() {

    }

    public C4ReplicatorStatus(int activityLevel) {
        this.activityLevel = activityLevel;
    }

    public C4ReplicatorStatus(int activityLevel, int errorDomain, int errorCode) {
        this.activityLevel = activityLevel;
        this.errorDomain = errorDomain;
        this.errorCode = errorCode;
    }

    public C4ReplicatorStatus(int activityLevel, long progressUnitsCompleted,
                              long progressUnitsTotal, long progressDocumentCount,
                              int errorDomain, int errorCode, int errorInternalInfo) {
        this.activityLevel = activityLevel;
        this.progressUnitsCompleted = progressUnitsCompleted;
        this.progressUnitsTotal = progressUnitsTotal;
        this.progressDocumentCount = progressDocumentCount;
        this.errorDomain = errorDomain;
        this.errorCode = errorCode;
        this.errorInternalInfo = errorInternalInfo;
    }

    public int getActivityLevel() {
        return activityLevel;
    }

    public void setActivityLevel(int activityLevel) {
        this.activityLevel = activityLevel;
    }

    public long getProgressUnitsCompleted() {
        return progressUnitsCompleted;
    }

    public long getProgressUnitsTotal() {
        return progressUnitsTotal;
    }

    public long getProgressDocumentCount() {
        return progressDocumentCount;
    }

    public int getErrorDomain() {
        return errorDomain;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public int getErrorInternalInfo() {
        return errorInternalInfo;
    }

    public C4Error getC4Error() {
        return new C4Error(errorDomain, errorCode, errorInternalInfo);
    }

    @Override
    public String toString() {
        return "C4ReplicatorStatus{" +
                "activityLevel=" + activityLevel +
                ", progressUnitsCompleted=" + progressUnitsCompleted +
                ", progressUnitsTotal=" + progressUnitsTotal +
                ", progressDocumentCount=" + progressDocumentCount +
                ", errorDomain=" + errorDomain +
                ", errorCode=" + errorCode +
                ", errorInternalInfo=" + errorInternalInfo +
                '}';
    }

    public C4ReplicatorStatus copy() {
        return new C4ReplicatorStatus(activityLevel, progressUnitsCompleted, progressUnitsTotal,
                progressDocumentCount, errorDomain, errorCode, errorInternalInfo);
    }
}
