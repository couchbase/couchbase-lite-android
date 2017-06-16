package com.couchbase.lite;

class QueryChange {
    //private List<QueryRow> rows;
    private ResultSet rs;
    private Throwable error;

    /*package*/ QueryChange(ResultSet rs) {
        this(rs, null);
    }

    /*package*/ QueryChange(Throwable error) {
        this(null, error);
    }

    /*package*/ QueryChange(ResultSet rs, Throwable error) {
        this.rs = rs;
        this.error = error;
    }

    public ResultSet getRS() {
        return rs;
    }
}
