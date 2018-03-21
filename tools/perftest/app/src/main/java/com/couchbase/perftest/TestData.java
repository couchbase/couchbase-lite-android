package com.couchbase.perftest;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class TestData {
    static Map<String, Map<String, Object>> generateProducts(int numDocs) {
        Map<String, Map<String, Object>> items = new HashMap<>();
        for (int i = 0; i < numDocs; i++) {
            String prodNum = String.format(Locale.ENGLISH, "%08d", i);
            Map<String, Object> item = new HashMap<>();
            item.put("ProdNum", prodNum);
            item.put("ProdType", "1001");
            item.put("IsInvCtrl", true);
            item.put("ProdSubType", "");
            item.put("UPCUnitCode", "");
            item.put("Price", 100.00);
            item.put("Mfg", "");
            item.put("ModelNum", "");
            item.put("IsBatch", false);
            item.put("IsSerialized", false);
            Map<String, Object> dict = new HashMap<>();
            dict.put("en", "This is the English translation of the product description");
            dict.put("es", "This is the Spanish translation of the product description");
            item.put("Desc", dict);
            items.put(prodNum, item);
        }
        return items;
    }
}
