package com.couchbase.lite;

import java.util.HashMap;
import java.util.Map;

public class Collation {

    boolean unicode = false;
    boolean ignoreCase = false;
    boolean ignoreAccents = false;
    String locale = null;

    public final static class ASCII extends Collation {
        ASCII() {
            this.unicode = false;
        }

        public ASCII ignoreCase(boolean ignoreCase) {
            this.ignoreCase = ignoreCase;
            return this;
        }
    }

    public final static class Unicode extends Collation {
        Unicode() {
            this.unicode = true;
            // NOTE: System.getProperty("user.country") returns null for country code
            this.locale = System.getProperty("user.language");
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

    private Collation() {
    }

    Object asJSON() {
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

    @Override
    public String toString() {
        return "Collation{" +
                "unicode=" + unicode +
                ", ignoreCase=" + ignoreCase +
                ", ignoreAccents=" + ignoreAccents +
                ", locale='" + locale + '\'' +
                '}';
    }
}
