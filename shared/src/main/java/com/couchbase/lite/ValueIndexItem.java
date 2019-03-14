//
// ValueIndexItem.java
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


/**
 * Value Index Item
 */
public final class ValueIndexItem {
    /**
     * Creates a value index item with the given property.
     *
     * @param property the property name
     * @return The value index item
     */
    @NonNull
    public static ValueIndexItem property(@NonNull String property) {
        if (property == null) {
            throw new IllegalArgumentException("property cannot be null.");
        }
        return new ValueIndexItem(Expression.property(property));
    }

    /**
     * Creates a value index item with the given property.
     *
     * @param expression The expression to index. Typically a property expression.
     * @return The value index item
     */
    @NonNull
    public static ValueIndexItem expression(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return new ValueIndexItem(expression);
    }

    final Expression viExpression;

    private ValueIndexItem(Expression expression) {
        this.viExpression = expression;
    }
}
