package com.couchbase.lite;

import java.util.ArrayList;
import java.util.List;

/**
 * Variable expression
 */
public final class VariableExpression extends Expression {
    private String name;

    VariableExpression(String name) {
        this.name = name;
    }

    String getName() {
        return name;
    }

    @Override
    Object asJSON() {
        List<Object> json = new ArrayList<>();
        json.add("?" + name);
        return json;
    }
}
