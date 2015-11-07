#include <stdio.h>
#include <stdlib.h>
#include <dlfcn.h>
#include <ctype.h>

// ICU4C - icu::Collator
#include <unicode/coll.h>

#include "sqlite3.h"
#include "com_couchbase_lite_android_SQLiteJsonCollator.h"
#include "android/log.h"

class String8 {
    
};

struct SQLiteConnection {
    // Open flags.
    // Must be kept in sync with the constants defined in SQLiteDatabase.java.
    enum {
        OPEN_READWRITE          = 0x00000000,
        OPEN_READONLY           = 0x00000001,
        OPEN_READ_MASK          = 0x00000001,
        NO_LOCALIZED_COLLATORS  = 0x00000010,
        CREATE_IF_NECESSARY     = 0x10000000,
    };
    
    sqlite3* const db;
    const int openFlags;
    const String8 path;
    const String8 label;
    
    volatile bool canceled;
    
    SQLiteConnection(sqlite3* db, int openFlags, const String8& path, const String8& label) :
    db(db), openFlags(openFlags), path(path), label(label), canceled(false) { }
};

#define LOG_TAG "SQLiteJsonCollator"
#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// CouchDB's default collation rules, including Unicode collation for strings
#define kJsonCollator_Unicode ((void*)0)

// CouchDB's "raw" collation rules (which order scalar types differently, beware)
#define kJsonCollator_Raw ((void*)1)

// ASCII mode, which is like CouchDB default except that strings are compared as binary UTF-8
#define kJsonCollator_ASCII ((void*)2)

#define DEFAULT_COLLATOR_LOCALE "en_US"

// For ICU4C Collator
char locale[256];
Collator *coll;

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *jvm, void *reserved)
{
    JNIEnv *env;
    if (jvm->GetEnv((void **)&env, JNI_VERSION_1_2)) {
        return JNI_ERR; /* JNI version not supported */
    }

    // initialize collator and its locale
    strcpy(locale, DEFAULT_COLLATOR_LOCALE);
    coll = NULL;

    return JNI_VERSION_1_2;
}

JNIEXPORT void JNICALL
JNI_OnUnload(JavaVM *jvm, void *reserved)
{
    JNIEnv *env;
    if (jvm->GetEnv((void **)&env, JNI_VERSION_1_2)) {
        return;
    }
    return;
}

/**
 * Core Collation Stuff heavily borrowed from the iOS version
 */

static int cmp(int n1, int n2) {
    int diff = n1 - n2;
    return diff > 0 ? 1 : (diff < 0 ? -1 : 0);
}

static int dcmp(double n1, double n2) {
    double diff = n1 - n2;
    return diff > 0.0 ? 1 : (diff < 0.0 ? -1 : 0);
}

// Maps an ASCII character to its relative priority in the Unicode collation sequence.
static uint8_t kCharPriority[128];
// Same thing but case-insensitive.
static uint8_t kCharPriorityCaseInsensitive[128];

static void initializeCharPriorityMap(void) {
    static const char* const kInverseMap = "\t\n\r `^_-,;:!?.'\"()[]{}@*/\\&#%+<=>|~$0123456789aAbBcCdDeEfFgGhHiIjJkKlLmMnNoOpPqQrRsStTuUvVwWxXyYzZ";
    uint8_t priority = 1;
    for (unsigned i=0; i<strlen(kInverseMap); i++)
        kCharPriority[(uint8_t)kInverseMap[i]] = priority++;
    
    // This table gives lowercase letters the same priority as uppercase:
    memcpy(kCharPriorityCaseInsensitive, kCharPriority, sizeof(kCharPriority));
    for (uint8_t c = 'a'; c <= 'z'; c++)
        kCharPriorityCaseInsensitive[c] = kCharPriority[toupper(c)];
}

// Types of values, ordered according to CouchDB collation order (see view_collation.js tests)
typedef enum {
    kEndArray,
    kEndObject,
    kComma,
    kColon,
    kNull,
    kFalse,
    kTrue,
    kNumber,
    kString,
    kArray,
    kObject,
    kIllegal
} ValueType;

// "Raw" ordering is: 0:number, 1:false, 2:null, 3:true, 4:object, 5:array, 6:string
// (according to view_collation_raw.js)
static int kRawOrderOfValueType[] = { -4, -3, -2, -1, 2, 1, 3, 0, 6, 5, 4, 7 };

static ValueType valueTypeOf(char c) {
    switch (c) {
        case 'n':
            return kNull;
        case 'f':
            return kFalse;
        case 't':
            return kTrue;
        case '0' ... '9':
        case '-':
            return kNumber;
        case '"':
            return kString;
        case ']':
            return kEndArray;
        case '}':
            return kEndObject;
        case ',':
            return kComma;
        case ':':
            return kColon;
        case '[':
            return kArray;
        case '{':
            return kObject;
        default:
            LOGW("Unexpected character '%c' parsing JSON", c);
            return kIllegal;
    }
}

/**
 * Defining my own digittoint because Android ctype.h doesn't do it for me
 */
static int digittoint(int c) {
    if(!isxdigit(c)) {
        return 0;
    }
    if(c > 'a') {
        return 10 + c - 'a';
    }
    else if(c > 'A') {
        return 10 + c - 'A';
    }
    else {
        return c - '0';
    }
}

static char convertEscape(const char **in) {
    char c = *++(*in);
    switch (c) {
        case 'u': {
            // \u is a Unicode escape; 4 hex digits follow.
            const char* digits = *in + 1;
            *in += 4;
            int uc = (digittoint(digits[0]) << 12) | (digittoint(digits[1]) << 8) |
            (digittoint(digits[2]) <<  4) | (digittoint(digits[3]));
            if (uc > 127)
                LOGW("SQLiteJsonCollator can't correctly compare \\u%.4s", digits);
            return (char)uc;
        }
        case 'b':   return '\b';
        case 'n':   return '\n';
        case 'r':   return '\r';
        case 't':   return '\t';
        default:    return c;
    }
}

static int compareStringsASCII(const char** in1, const char** in2) {
    const char* str1 = *in1, *str2 = *in2;
    while (true) {
        char c1 = *++str1;
        char c2 = *++str2;
        
        // If one string ends, the other is greater; if both end, they're equal:
        if (c1 == '"') {
            if (c2 == '"')
                break;
            else
                return -1;
        } else if (c2 == '"')
            return 1;
        
        // Un-escape the next character after a backslash:
        if (c1 == '\\')
            c1 = convertEscape(&str1);
        if (c2 == '\\')
            c2 = convertEscape(&str2);
        
        // Compare the next characters:
        int s = cmp(c1, c2);
        if (s)
            return s;
    }
    
    // Strings are equal, so update the positions:
    *in1 = str1 + 1;
    *in2 = str2 + 1;
    return 0;
}

// Unicode collation, but fails (returns -2) if non-ASCII characters are found.
// Basic rule is to compare case-insensitively, but if the strings compare equal, let the one that's
// higher case-sensitively win (where uppercase is _greater_ than lowercase, unlike in ASCII.)
static int compareStringsUnicodeFast(const char** in1, const char** in2) {
    const char* str1 = *in1, *str2 = *in2;
    int resultIfEqual = 0;
    while(true) {
        char c1 = *++str1;
        char c2 = *++str2;
        
        // If one string ends, the other is greater; if both end, they're equal:
        if (c1 == '"') {
            if (c2 == '"')
                break;
            else
                return -1;
        } else if (c2 == '"')
            return 1;
        
        // Handle escape sequences:
        if (c1 == '\\')
            c1 = convertEscape(&str1);
        if (c2 == '\\')
            c2 = convertEscape(&str2);
        
        if ((c1 & 0x80) || (c2 & 0x80))
            return -2; // fail: I only handle ASCII
        
        // Compare the next characters, according to case-insensitive Unicode character priority:
        int s = cmp(kCharPriorityCaseInsensitive[(uint8_t)c1],
                    kCharPriorityCaseInsensitive[(uint8_t)c2]);
        if (s)
            return s;
        
        // Remember case-sensitive result too
        if (resultIfEqual == 0 && c1 != c2)
            resultIfEqual = cmp(kCharPriority[(uint8_t)c1], kCharPriority[(uint8_t)c2]);
    }
    
    if (resultIfEqual)
        return resultIfEqual;
    
    // Strings are equal, so update the positions:
    *in1 = str1 + 1;
    *in2 = str2 + 1;
    return 0;
}

static int compareStringsUnicode(const char** in1, const char** in2) {
    int result = compareStringsUnicodeFast(in1, in2);
    if (result > -2)
        return result;

    // ICU4C - Collator
    if(coll == NULL){
        UErrorCode status = U_ZERO_ERROR; 
        coll = Collator::createInstance(locale, status);
        if(!U_SUCCESS(status)) {
            LOGE("Failed to create Collator instance: locale=%s, status=%d", locale, status);
            LOGW("Create Collator instance with Locale: %s", DEFAULT_COLLATOR_LOCALE);
            coll = Collator::createInstance(DEFAULT_COLLATOR_LOCALE, status);
            if(!U_SUCCESS(status)) {
                LOGE("Failed to create Collator instance: locale=%s, status=%d", DEFAULT_COLLATOR_LOCALE, status);
                return 0;
            }

        }
    }
    result = (int)coll->compare(*in1, *in2);

    return result;
}

static double readNumber(const char* start, const char* end, char** endOfNumber) {
    // First copy the string into a zero-terminated buffer so we can safely call strtod:
    size_t len = end - start;
    char buf[50];
    char* str = (len < sizeof(buf)) ? buf : (char*) malloc(len + 1);
    if (!str) {
        return 0.0;
    }
    memcpy(str, start, len);
    str[len] = '\0';
    
    char* endInStr;
    double result = strtod(str, &endInStr);
    *endOfNumber = (char*)start + (endInStr - str);
    if (str != buf) {
        free(str);
    }
    return result;
}

/** SQLite collation function for JSON-formatted strings.
 The "context" parameter should be one of the three collation mode constants below.
 WARNING: This function *only* works on valid JSON with no whitespace.
 If called on non-JSON strings it is quite likely to crash! */

int CollateJSON(void *context, int len1, const void * chars1, int len2, const void * chars2) {
    
    static bool charPriorityMapInitialized = false;
    if(!charPriorityMapInitialized){
        initializeCharPriorityMap();
        charPriorityMapInitialized = true;
    }
    
    const char* str1 = (const char*) chars1;
    const char* str2 = (const char*) chars2;
    int depth = 0;
    
    do {
        
        // Get the types of the next token in each string:
        ValueType type1 = valueTypeOf(*str1);
        ValueType type2 = valueTypeOf(*str2);
        // If types don't match, stop and return their relative ordering:
        if (type1 != type2) {
            if (context != kJsonCollator_Raw)
                return cmp(type1, type2);
            else
                return cmp(kRawOrderOfValueType[type1],
                           kRawOrderOfValueType[type2]);
            
            // If types match, compare the actual token values:
        } else
            switch (type1) {
                case kNull:
                case kTrue:
                    str1 += 4;
                    str2 += 4;
                    break;
                case kFalse:
                    str1 += 5;
                    str2 += 5;
                    break;
                case kNumber: {
                    char* next1, *next2;
                    int diff;
                    if (depth == 0) {
                        diff = dcmp( readNumber(str1, str1 + len1, &next1),
                                    readNumber(str2, str2 + len2, &next2) );
                    } else {
                        diff = dcmp(strtod(str1, &next1), strtod(str2, &next2));
                    }
                    if (diff) {
                        return diff; // Numbers don't match
                    }
                    str1 = next1;
                    str2 = next2;
                    break;
                }
                case kString: {
                    int diff;
                    if (context == kJsonCollator_Unicode)
                        diff = compareStringsUnicode(&str1, &str2);
                    else
                        diff = compareStringsASCII(&str1, &str2);
                    if (diff)
                        return diff; // Strings don't match
                    break;
                }
                case kArray:
                case kObject:
                    ++str1;
                    ++str2;
                    ++depth;
                    break;
                case kEndArray:
                case kEndObject:
                    ++str1;
                    ++str2;
                    --depth;
                    break;
                case kComma:
                case kColon:
                    ++str1;
                    ++str2;
                    break;
                case kIllegal:
                    return 0;
            }
    } while (depth > 0); // Keep going as long as we're inside an array or object
    return 0;
}

JNIEXPORT void JNICALL Java_com_couchbase_lite_android_SQLiteJsonCollator_nativeRegister
(JNIEnv *env, jclass cls, jobject sqliteDatabase, jstring sqliteDatabaseClassName, jint sdkVersion) {
    int (*sqlite3_create_collation)(sqlite3*,const char *,int,void *,int (*)(void*, int, const void*, int, const void*)) = NULL;
    void* handle = NULL;
    bool useSQLCipher = false;
    const char* cSqliteDatabaseClassName = env->GetStringUTFChars(sqliteDatabaseClassName, NULL);
    if (strcmp(cSqliteDatabaseClassName, "android/database/sqlite/SQLiteDatabase") == 0) {
        handle = dlopen("/system/lib/libsqlite.so", RTLD_LAZY);
    } else if (strcmp(cSqliteDatabaseClassName, "net/sqlcipher/database/SQLiteDatabase") == 0) {
        handle = dlopen("libsqlcipher_android.so", RTLD_LAZY);
        useSQLCipher = true;
    } else {
        LOGE("Unknown SQLiteDatabase Class Name");
        return;
    }
    
    *(void **)(&sqlite3_create_collation) = dlsym(handle, "sqlite3_create_collation");
    if(!sqlite3_create_collation) {
        LOGE("Failed to find sqlite3_create_collation: %s", dlerror());
        return;
    }
    
    // Find the SQLiteDatabase class:
    jclass clazz = env->FindClass(cSqliteDatabaseClassName);
    if (!clazz) {
        LOGE("Can't find SQLiteDatabase class");
        return;
    }
    env->ReleaseStringUTFChars(sqliteDatabaseClassName, cSqliteDatabaseClassName);
    
    bool useOldApi = useSQLCipher || sdkVersion < 16;
    
    // Find the field holding the sqlite3 handle:
    sqlite3 *sqliteHandle = NULL;
    if (useOldApi) {
        jfieldID offset_db_handle = env->GetFieldID(clazz, "mNativeHandle", "I");
        if (offset_db_handle == NULL) {
            LOGE("Can't find SQLiteDatabase.mNativeHandle\n");
            return;
        }
        sqliteHandle = (sqlite3 *)env->GetIntField(sqliteDatabase, offset_db_handle);
    } else {
        jfieldID offset_tl = env->GetFieldID(clazz, "mThreadSession", "Ljava/lang/ThreadLocal;");
        if(offset_tl == NULL) {
            LOGE("Can't find SQLiteDatabae.mThreadSession\n");
            return;
        }
        jobject tl = env->GetObjectField(sqliteDatabase, offset_tl);
        
        jclass tl_clazz = env->FindClass("java/lang/ThreadLocal");
        if (tl_clazz == NULL) {
            LOGE("Can't find java/lang/ThreadLocal\n");
            return;
        }
        
        jmethodID get_mid = env->GetMethodID(tl_clazz, "get", "()Ljava/lang/Object;");
        if (get_mid == NULL) {
            LOGE("Can't find ThreadLocal.get\n");
            return;
        }
        jobject session = env->CallObjectMethod(tl, get_mid);
        
        jclass sqls_clazz = env->FindClass("android/database/sqlite/SQLiteSession");
        if (sqls_clazz == NULL) {
            LOGE("Can't find android/database/sqlite/SQLiteSession\n");
            return;
        }
        
        jfieldID offset_mConnectionPool = env->GetFieldID(sqls_clazz, "mConnectionPool", "Landroid/database/sqlite/SQLiteConnectionPool;");
        if(offset_mConnectionPool == NULL) {
            LOGE("Can't find SQLiteSession.mConnectionPool");
            return;
        }
        jobject mcp = env->GetObjectField(session, offset_mConnectionPool);
        if(mcp == NULL) {
            LOGE("mConnectionPool was NULL");
            return;
        }
        
        jclass sqlcp_clazz = env->FindClass("android/database/sqlite/SQLiteConnectionPool");
        if (sqlcp_clazz == NULL) {
            LOGE("Can't find android/database/sqlite/SQLiteConnectionPool\n");
            return;
        }
        
        jfieldID offset_mMainConnection = env->GetFieldID(sqlcp_clazz, "mAvailablePrimaryConnection", "Landroid/database/sqlite/SQLiteConnection;");
        if(offset_mMainConnection == NULL) {
            LOGE("Can't find SQLiteConnectionPool.mAvailablePrimaryConnection");
            return;
        }
        
        jobject mc = env->GetObjectField(mcp, offset_mMainConnection);
        
        jclass sqlc_clazz = env->FindClass("android/database/sqlite/SQLiteConnection");
        if (sqlc_clazz == NULL) {
            LOGE("Can't find android/database/sqlite/SQLiteConnection\n");
            return;
        }
        
        SQLiteConnection *connection;
        
        // On Android-L and later, mConnectionPtr is a long
        jfieldID offset_db_handle = env->GetFieldID(sqlc_clazz, "mConnectionPtr", "J");
        if(offset_db_handle != NULL) {
            jlong connectionPtr = env->GetLongField(mc, offset_db_handle);
            connection = reinterpret_cast<SQLiteConnection*>(connectionPtr);
        } else {
            // trying to get mConnectionPtr as a long will cause an exception,
            // so we must clear it before doing anything else.
            env->ExceptionClear();
            
            // On previous versions of Android, it's, an int
            offset_db_handle = env->GetFieldID(sqlc_clazz, "mConnectionPtr", "I");
            
            jint connectionPtr = env->GetIntField(mc, offset_db_handle);
            
            connection = reinterpret_cast<SQLiteConnection*>(connectionPtr);
        }
        
        sqliteHandle = connection->db;
    }
    
    sqlite3_create_collation(sqliteHandle, "JSON", SQLITE_UTF8,
                             kJsonCollator_Unicode, CollateJSON);
    sqlite3_create_collation(sqliteHandle, "JSON_RAW", SQLITE_UTF8,
                             kJsonCollator_Raw, CollateJSON);
    sqlite3_create_collation(sqliteHandle, "JSON_ASCII", SQLITE_UTF8,
                             kJsonCollator_ASCII, CollateJSON);
}

/**
 * implement a test method that can be called from java
 */
JNIEXPORT jint JNICALL Java_com_couchbase_lite_android_SQLiteJsonCollator_testCollateJSON
(JNIEnv *env, jclass clazz, jint mode, jint len1, jstring string1, jint len2, jstring string2) {
    jboolean isCopy;
    const char* cstring1 = env->GetStringUTFChars(string1, &isCopy);
    const char* cstring2 = env->GetStringUTFChars(string2, &isCopy);
    
    int result = CollateJSON((void *) mode, (int)len1, cstring1, (int)len2, cstring2);
    
    env->ReleaseStringUTFChars(string1, cstring1);
    env->ReleaseStringUTFChars(string2, cstring2);
    
    return result;
}

JNIEXPORT jchar JNICALL Java_com_couchbase_lite_android_SQLiteJsonCollator_testEscape
(JNIEnv *env, jclass clazz, jstring source) {
    jboolean isCopy;
    const char* cstring = env->GetStringUTFChars(source, &isCopy);
    char result = convertEscape(&cstring);
    return result;
}

JNIEXPORT jint JNICALL Java_com_couchbase_lite_android_SQLiteJsonCollator_testDigitToInt
(JNIEnv *env, jclass clazz, jint digit) {
    int result = digittoint(digit);
    return result;
}

JNIEXPORT void JNICALL Java_com_couchbase_lite_android_SQLiteJsonCollator_setICURoot
    (JNIEnv *env, jclass clazz, jstring jICURoot)
{
    if(jICURoot == NULL) return;
    char const * ICURootPath = env->GetStringUTFChars(jICURoot, NULL);
    setenv("CBL_ICU_PREFIX", ICURootPath, 1);
    env->ReleaseStringUTFChars(jICURoot, ICURootPath);
}

JNIEXPORT void JNICALL Java_com_couchbase_lite_android_SQLiteJsonCollator_setLocale
    (JNIEnv *env, jclass clazz, jstring jlocale)
{
    if(jlocale == NULL) return;
    char const * localeStr = env->GetStringUTFChars(jlocale, NULL);
    if(strlen(localeStr) < 256)
        strcpy(locale, localeStr);
    env->ReleaseStringUTFChars(jlocale, localeStr);
}

JNIEXPORT void JNICALL Java_com_couchbase_lite_android_SQLiteJsonCollator_releaseICU
    (JNIEnv *env, jclass clazz)
{
    if(coll != NULL){
        delete coll;
        coll = NULL;
    }
}
