package com.couchbase.lite;

import java.util.Date;

/* package */ class DataUtils {

    public final static Object NULL = new Object();

    public static final int DEFAULT_INT = 0;
    public static final long DEFAULT_LONG = 0l;
    public static final float DEFAULT_FLOAT = 0.0f;
    public static final double DEFAULT_DOUBLE = 0.0d;
    public static final boolean DEFAULT_BOOLEAN = false;

    public static Date toDate(String string) {
        Date date = null;
        return date;
    }

    public static String toString(Object object) {
        String string = null;
        if (object instanceof String) {
            string = (String) object;
        }
        return string;
    }

    public static Number toNumber(Object object) {
        Number number = null;
        if (object instanceof Number) {
            number = (Number) object;
        }
        return number;
    }

    public static int toInt(Object object) {
        int intValue = DataUtils.DEFAULT_INT;
        if (object instanceof Number) {
            intValue = ((Number) object).intValue();
        }
        return intValue;
    }

    public static long toLong(Object object) {
        long longValue = DataUtils.DEFAULT_LONG;
        if (object instanceof Number) {
            longValue = ((Number) object).longValue();
        }
        return longValue;
    }

    public static float toFloat(Object object) {
        float floatValue = DataUtils.DEFAULT_FLOAT;
        if (object instanceof Number) {
            floatValue = ((Number) object).floatValue();
        }
        return floatValue;
    }

    public static double toDouble(Object object) {
        double doubleValue = DataUtils.DEFAULT_DOUBLE;
        if (object instanceof Number) {
            doubleValue = ((Number) object).doubleValue();
        }
        return doubleValue;
    }

    public static boolean toBoolean(Object object) {
        boolean booleanValue = DataUtils.DEFAULT_BOOLEAN;
        if (object instanceof Boolean) {
            booleanValue = (Boolean) object;
        }
        return booleanValue;
    }

    public static Date toDate(Object object) {
        Date date = null;
        if (object instanceof String) {
            String string = (String) object;
            date = DataUtils.toDate(string);
        } else if (object instanceof Date) {
            date = (Date) object;
        }
        return date;
    }

    public static Blob toBlob(Object object) {
        Blob blob = null;
        if (object instanceof Blob) {
            blob = (Blob) object;
        }
        return blob;
    }

    public static Array toArray(Object object) {
        Array array = null;
        if (object instanceof Array) {
            array = (Array) object;
        }
        return array;
    }

    public static ReadOnlyArray toReadOnlyArray(Object object) {
        ReadOnlyArray array = null;
        if (object instanceof ReadOnlyArray) {
            array = (ReadOnlyArray) object;
        }
        return array;
    }

    public static Dictionary toDictionary(Object object) {
        Dictionary dictionary = null;
        if (object instanceof Dictionary) {
            dictionary = (Dictionary) object;
        }
        return dictionary;
    }

    public static ReadOnlyDictionary toReadOnlyDictionary(Object object) {
        ReadOnlyDictionary dictionary = null;
        if (object instanceof ReadOnlyDictionary) {
            dictionary = (ReadOnlyDictionary) object;
        }
        return dictionary;
    }
}
