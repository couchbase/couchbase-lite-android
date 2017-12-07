package com.couchbase.lite.internal.query.expression;

import com.couchbase.lite.Expression;

import java.util.ArrayList;
import java.util.List;

public class PropertyExpression extends Expression {
    public final static String kCBLAllPropertiesName = "";

    private final String keyPath;
    private String columnName;
    private final String from; // Data Source Alias

    public PropertyExpression(String keyPath) {
        this.keyPath = keyPath;
        this.from = null;
    }

    private PropertyExpression(String keyPath, String from) {
        this.keyPath = keyPath;
        this.from = from;
    }

    private PropertyExpression(String keyPath, String columnName, String from) {
        this.keyPath = keyPath;
        this.columnName = columnName;
        this.from = from;
    }

    //---------------------------------------------
    // public level access
    //---------------------------------------------

    public Expression from(String alias) {
        return new PropertyExpression(this.keyPath, alias);
    }

    public static PropertyExpression allFrom(String from) {
        // Use data source alias name as the column name if specified:
        String colName = from != null ? from : kCBLAllPropertiesName;
        return new PropertyExpression(kCBLAllPropertiesName, colName, from);
    }

    @Override
    public Object asJSON() {
        List<Object> json = new ArrayList<>();
        if (from != null)
            json.add("." + from + "." + keyPath);
        else
            json.add("." + keyPath);
        return json;
    }

    public String getColumnName() {
        if (columnName == null) {
            String[] pathes = keyPath.split("\\.");
            columnName = pathes[pathes.length - 1];
        }
        return columnName;
    }
}
