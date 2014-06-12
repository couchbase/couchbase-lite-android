package com.couchbase.lite.replicator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MockChangedDoc {

    private int seq;
    private String docId;
    private List<String> changedRevIds;

    public MockChangedDoc setSeq(int seq) {
        this.seq = seq;
        return this;
    }

    public MockChangedDoc setDocId(String docId) {
        this.docId = docId;
        return this;
    }

    public MockChangedDoc setChangedRevIds(List<String> changedRevIds) {
        this.changedRevIds = changedRevIds;
        return this;
    }

    public int getSeq() {
        return seq;
    }

    public String getDocId() {
        return docId;
    }

    public List<String> getChangedRevIds() {
        return changedRevIds;
    }

    /**
     * Export as a map
     *
     * @return map, eg {"seq":2,"id":"doc2","changes":[{"rev":"1-5e38"}]}
     */
    public Map<String, Object> exportAsMap() {
        Map<String, Object> exported = new HashMap<String, Object>();
        exported.put("seq", getSeq());
        exported.put("id", getDocId());
        List changes = new ArrayList();
        for (String changeRevId : changedRevIds) {
            Map<String, Object> revIdMap = new HashMap<String, Object>();
            revIdMap.put("rev", changeRevId);
            changes.add(revIdMap);
        }
        exported.put("changes", changes);
        return exported;
    }

}
