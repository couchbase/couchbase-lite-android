package com.couchbase.lite;

import java.util.HashMap;
import java.util.Map;

public class Collation {

    boolean unicode = false;
    boolean ignoreCase = false;
    boolean ignoreAccents = false;
    String locale = null;

    public static class ASCII extends Collation {
        ASCII() {
            this.unicode = false;
        }

        public ASCII ignoreCase(boolean ignoreCase) {
            this.ignoreCase = ignoreCase;
            return this;
        }
    }

    public static class Unicode extends Collation {
        public Unicode() {
            this.unicode = true;
        }

        public Unicode ignoreCase(boolean ignoreCase) {
            this.ignoreCase = ignoreCase;
            return this;
        }

        public Unicode ignoreAccents(boolean ignoreAccents) {
            this.ignoreAccents = ignoreAccents;
            return this;
        }

        public Unicode locale(String locale) {
            this.locale = locale;
            return this;
        }
    }

    protected Object asJSON() {
        Map<String, Object> json = new HashMap<>();
        json.put("UNICODE", unicode);
        json.put("LOCALE", locale == null ? null : locale);
        json.put("CASE", !ignoreCase);
        json.put("DIAC", !ignoreAccents);
        return json;
    }

    public static ASCII ascii() {
        return new ASCII();
    }

    public static Unicode unicode() {
        return new Unicode();
    }
}
