//
// PropertyExpression.java
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

import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Property expression
 */
public final class PropertyExpression extends Expression {
    final static String kCBLAllPropertiesName = "";

    private final String keyPath;
    private String columnName;
    private final String from; // Data Source Alias

    PropertyExpression(String keyPath) {
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

    /**
     * Specifies an alias name of the data source to query the data from.
     *
     * @param alias The alias name of the data source.
     * @return The property Expression with the given data source alias name.
     */
    @NonNull
    public Expression from(String alias) {
        return new PropertyExpression(this.keyPath, alias);
    }

    //---------------------------------------------
    // package level access
    //---------------------------------------------

    static PropertyExpression allFrom(String from) {
        // Use data source alias name as the column name if specified:
        String colName = from != null ? from : kCBLAllPropertiesName;
        return new PropertyExpression(kCBLAllPropertiesName, colName, from);
    }

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
