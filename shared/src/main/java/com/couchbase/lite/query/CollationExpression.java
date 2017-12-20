package com.couchbase.lite.query;

import com.couchbase.lite.Expression;

import java.util.ArrayList;
import java.util.List;

public class CollationExpression extends Expression {
    private Expression operand;
    private Collation collation;

    public CollationExpression(Expression operand, Collation collation) {
        this.operand = operand;
        this.collation = collation;
    }

    @Override
    public Object asJSON() {
        List<Object> json = new ArrayList<>(3);
        json.add("COLLATE");
        json.add(collation.asJSON());
        json.add(operand.asJSON());
        return json;
    }
}
