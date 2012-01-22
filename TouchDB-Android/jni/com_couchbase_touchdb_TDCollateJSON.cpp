#include <stdio.h>
#include <stdlib.h>
#include <dlfcn.h>
#include <ctype.h>

#include "sqlite3.h"
#include "com_couchbase_touchdb_TDCollateJSON.h"
#include "android/log.h"

#define LOG_TAG "TDCollateJSON"
#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// CouchDB's default collation rules, including Unicode collation for strings
#define kTDCollateJSON_Unicode ((void*)0)

// CouchDB's "raw" collation rules (which order scalar types differently, beware)
#define kTDCollateJSON_Raw ((void*)1)

// ASCII mode, which is like CouchDB default except that strings are compared as binary UTF-8
#define kTDCollateJSON_ASCII ((void*)2)

/**
 * Core JNI stuff to cache class and method references for faster use later
 */

JavaVM *cached_jvm;
jclass TDCollateJSONClass;
jmethodID compareMethod;
JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *jvm, void *reserved)
{
	JNIEnv *env;
	jclass cls;
	cached_jvm = jvm; /* cache the JavaVM pointer */

	if (jvm->GetEnv((void **)&env, JNI_VERSION_1_2)) {
		return JNI_ERR; /* JNI version not supported */
	}
	cls = env->FindClass("com/couchbase/touchdb/TDCollateJSON");
	if (cls == NULL) {
		return JNI_ERR;
	}
	/* Use weak global ref to allow C class to be unloaded */
	TDCollateJSONClass = reinterpret_cast<jclass>(env->NewGlobalRef(cls));
	if (TDCollateJSONClass == NULL) {
		return JNI_ERR;
	}
	/* Compute and cache the method ID */
	compareMethod = env->GetStaticMethodID(cls, "compareStringsUnicode", "(Ljava/lang/String;Ljava/lang/String;)I");
	if (compareMethod == NULL) {
		return JNI_ERR;
	}

	return JNI_VERSION_1_2;
}

JNIEnv *getEnv() {
	JNIEnv *env;
	cached_jvm->GetEnv((void **) &env, JNI_VERSION_1_2);
	return env;
}

JNIEXPORT void JNICALL
JNI_OnUnload(JavaVM *jvm, void *reserved)
{
	JNIEnv *env;
	if (jvm->GetEnv((void **)&env, JNI_VERSION_1_2)) {
		return;
	}
	env->DeleteWeakGlobalRef(TDCollateJSONClass);
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
                LOGW("TDCollateJSON can't correctly compare \\u%.4s", digits);
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

static jstring createJavaStringFromJSON(const char** in) {
	// Scan the JSON string to find its end and whether it contains escapes:
	const char* start = ++*in;
	unsigned escapes = 0;
	const char* str;
	for (str = start; *str != '"'; ++str) {
		if (*str == '\\') {
			++str;
			if (*str == 'u') {
				escapes += 5;  // \uxxxx adds 5 bytes
				str += 4;
			} else
				escapes += 1;
		}
	}
	*in = str + 1;
	size_t length = str - start;

	char* buf = NULL;
	length -= escapes;
	buf = (char*) malloc(length + 1);
	char* dst = buf;
	char c;
	for (str = start; (c = *str) != '"'; ++str) {
		if (c == '\\')
			c = convertEscape(&str);
		*dst++ = c;
	}
	*dst++ = 0; //null terminate
	start = buf;
	//LOGV("After stripping escapes string is: %s", start);

	JNIEnv *env = getEnv();
	jstring result = env->NewStringUTF(start);
	if (buf != NULL) {
		free(buf);
	}
	if (result == NULL) {
		LOGE("Failed to convert to string: start=%p, length=%u", start, length);
	}
	return result;
}

static int compareStringsUnicode(const char** in1, const char** in2) {
	jstring str1 = createJavaStringFromJSON(in1);
	jstring str2 = createJavaStringFromJSON(in2);
	JNIEnv *env = getEnv();
	int result = env->CallStaticIntMethod(TDCollateJSONClass, compareMethod,
			str1, str2);
	return result;
}

/** SQLite collation function for JSON-formatted strings.
 The "context" parameter should be one of the three collation mode constants below.
 WARNING: This function *only* works on valid JSON with no whitespace.
 If called on non-JSON strings it is quite likely to crash! */

int TDCollateJSON(void *context, int len1, const void * chars1, int len2,
		const void * chars2) {
	const char* str1 = (const char*) chars1;
	const char* str2 = (const char*) chars2;
	int depth = 0;

	do {
		// Get the types of the next token in each string:
		ValueType type1 = valueTypeOf(*str1);
		ValueType type2 = valueTypeOf(*str2);
		// If types don't match, stop and return their relative ordering:
		if (type1 != type2) {
			if (context != kTDCollateJSON_Raw)
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
				int diff = dcmp(strtod(str1, &next1), strtod(str2, &next2));
				if (diff)
					return diff; // Numbers don't match
				str1 = next1;
				str2 = next2;
				break;
			}
			case kString: {
				int diff;
				if (context == kTDCollateJSON_Unicode)
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

JNIEXPORT void JNICALL Java_com_couchbase_touchdb_TDCollateJSON_nativeRegisterCustomCollators
(JNIEnv *env, jclass cls, jobject sqliteDatabase) {

	int (*sqlite3_create_collation)(sqlite3*,const char *,int,void *,int (*)(void*, int, const void*, int, const void*)) = NULL;

	void* handle = dlopen("/system/lib/libsqlite.so", RTLD_LAZY);

	*(void **)(&sqlite3_create_collation) = dlsym(handle, "sqlite3_create_collation");
	if(!sqlite3_create_collation) {
		LOGE("Failed to find sqlite3_create_collation: %s", dlerror());
		return;
	}

	// find the SQLiteDatabase class
	jclass clazz = env->FindClass("android/database/sqlite/SQLiteDatabase");
	if (clazz == NULL) {
		LOGE("Can't find android/database/sqlite/SQLiteDatabase\n");
		return;
	}

	// find the field holding the handl
	jfieldID offset_db_handle = env->GetFieldID(clazz, "mNativeHandle", "I");
	if (offset_db_handle == NULL) {
		LOGE("Can't find SQLiteDatabase.mNativeHandle\n");
		return;
	}

	// get the native handle
	sqlite3 * sqliteHandle = (sqlite3 *)env->GetIntField(sqliteDatabase, offset_db_handle);
	LOGV("SQLite3 handle is %d", sqliteHandle);

	//try and install a custom collator

	sqlite3_create_collation(sqliteHandle, "JSON", SQLITE_UTF8,
			kTDCollateJSON_Unicode, TDCollateJSON);
	sqlite3_create_collation(sqliteHandle, "JSON_RAW", SQLITE_UTF8,
			kTDCollateJSON_Raw, TDCollateJSON);
	sqlite3_create_collation(sqliteHandle, "JSON_ASCII", SQLITE_UTF8,
			kTDCollateJSON_ASCII, TDCollateJSON);

}

/**
 * implement a test method that can be called from java
 */
JNIEXPORT jint JNICALL Java_com_couchbase_touchdb_TDCollateJSON_testCollateJSON(
	JNIEnv *env, jclass clazz, jint mode, jint len1, jstring string1, jint len2,
	jstring string2) {

jboolean isCopy;
const char* cstring1 = env->GetStringUTFChars(string1, &isCopy);
const char* cstring2 = env->GetStringUTFChars(string2, &isCopy);

int result = TDCollateJSON((void *) mode, 0, cstring1, 0, cstring2);

env->ReleaseStringUTFChars(string1, cstring1);
env->ReleaseStringUTFChars(string2, cstring2);

return result;
}

JNIEXPORT jchar JNICALL Java_com_couchbase_touchdb_TDCollateJSON_testEscape
  (JNIEnv *env, jclass clazz, jstring source) {
	jboolean isCopy;
	const char* cstring = env->GetStringUTFChars(source, &isCopy);
	char result = convertEscape(&cstring);
	return result;
}

JNIEXPORT jint JNICALL Java_com_couchbase_touchdb_TDCollateJSON_testDigitToInt
  (JNIEnv *env, jclass clazz, jint digit) {
	int result = digittoint(digit);
	return result;
}
