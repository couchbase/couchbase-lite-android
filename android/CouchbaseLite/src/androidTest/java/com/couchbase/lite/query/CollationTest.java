package com.couchbase.lite.query;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class CollationTest {
    @Test
    public void testGenerateJSONCollation() {
        Collation[] collations = {
                Collation.ascii().ignoreCase(false),
                Collation.ascii().ignoreCase(true),
                Collation.unicode().locale(null).ignoreCase(false).ignoreAccents(false),
                Collation.unicode().locale(null).ignoreCase(true).ignoreAccents(false),
                Collation.unicode().locale(null).ignoreCase(true).ignoreAccents(true),
                Collation.unicode().locale("en").ignoreCase(false).ignoreAccents(false),
                Collation.unicode().locale("en").ignoreCase(true).ignoreAccents(false),
                Collation.unicode().locale("en").ignoreCase(true).ignoreAccents(true)
        };

        List<Map<String, Object>> expected = new ArrayList<>();
        Map<String, Object> json1 = new HashMap<>();
        json1.put("UNICODE", false);
        json1.put("LOCALE", null);
        json1.put("CASE", true);
        json1.put("DIAC", true);
        expected.add(json1);
        Map<String, Object> json2 = new HashMap<>();
        json2.put("UNICODE", false);
        json2.put("LOCALE", null);
        json2.put("CASE", false);
        json2.put("DIAC", true);
        expected.add(json2);
        Map<String, Object> json3 = new HashMap<>();
        json3.put("UNICODE", true);
        json3.put("LOCALE", null);
        json3.put("CASE", true);
        json3.put("DIAC", true);
        expected.add(json3);
        Map<String, Object> json4 = new HashMap<>();
        json4.put("UNICODE", true);
        json4.put("LOCALE", null);
        json4.put("CASE", false);
        json4.put("DIAC", true);
        expected.add(json4);
        Map<String, Object> json5 = new HashMap<>();
        json5.put("UNICODE", true);
        json5.put("LOCALE", null);
        json5.put("CASE", false);
        json5.put("DIAC", false);
        expected.add(json5);
        Map<String, Object> json6 = new HashMap<>();
        json6.put("UNICODE", true);
        json6.put("LOCALE", "en");
        json6.put("CASE", true);
        json6.put("DIAC", true);
        expected.add(json6);
        Map<String, Object> json7 = new HashMap<>();
        json7.put("UNICODE", true);
        json7.put("LOCALE", "en");
        json7.put("CASE", false);
        json7.put("DIAC", true);
        expected.add(json7);
        Map<String, Object> json8 = new HashMap<>();
        json8.put("UNICODE", true);
        json8.put("LOCALE", "en");
        json8.put("CASE", false);
        json8.put("DIAC", false);
        expected.add(json8);
        for (int i = 0; i < collations.length; i++)
            assertEquals(expected.get(i), collations[i].asJSON());
    }
}
