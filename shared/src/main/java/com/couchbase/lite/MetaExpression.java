package com.couchbase.lite;

import java.util.ArrayList;
import java.util.List;

public class MetaExpression extends Expression {
    private final String keyPath;
    private String columnName;
    private final String from; // Data Source Alias

    private MetaExpression(String keyPath, String from) {
        this.keyPath = keyPath;
        this.from = from;
    }

    MetaExpression(String keyPath, String columnName, String from) {
        this.keyPath = keyPath;
        this.columnName = columnName;
        this.from = from;
    }

    //---------------------------------------------
    // public level access
    //---------------------------------------------
    public Expression from(String alias) {
        return new MetaExpression(this.keyPath, alias);
    }

    //---------------------------------------------
    // package level access
    //---------------------------------------------
    @Override
    Object asJSON() {
        List<Object> json = new ArrayList<>();
        if (from != null)
            json.add("." + from + "." + keyPath);
        else
            json.add("." + keyPath);
        return json;
    }

    String getColumnName() {
        if (columnName == null) {
            String[] pathes = keyPath.split("\\.");
            columnName = pathes[pathes.length - 1];
        }
        return columnName;
    }
}
