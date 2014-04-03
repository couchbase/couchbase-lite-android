#include <stdio.h>
#include <stdlib.h>
#include <dlfcn.h>
#include <ctype.h>

#include "sqlite3.h"
#include "com_couchbase_touchdb_RevCollator.h"
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

#define LOG_TAG "RevCollator"
#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/**
 * Core JNI stuff to cache class and method references for faster use later
 */

JavaVM *cached_jvm;
JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *jvm, void *reserved)
{
	JNIEnv *env;
	cached_jvm = jvm; /* cache the JavaVM pointer */

	if (jvm->GetEnv((void **)&env, JNI_VERSION_1_2)) {
		return JNI_ERR; /* JNI version not supported */
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

	return;
}

static inline int sgn(int n) {
    return n>0 ? 1 : (n<0 ? -1 : 0);
}

static int min(int a, int b) {
	return a < b ? a : b;
}

static int defaultCollate(const char* str1, int len1, const char* str2, int len2) {
    int result = memcmp(str1, str2, min(len1, len2));
    return sgn(result ?: (len1 - len2));
}

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

static int parseDigits(const char* str, const char* end) {
    int result = 0;
    for (; str < end; ++str) {
        if (!isdigit(*str))
            return 0;
        result = 10*result + digittoint(*str);
    }
    return result;
}

/* A proper revision ID consists of a generation number, a hyphen, and an arbitrary suffix.
   Compare the generation numbers numerically, and then the suffixes lexicographically.
   If either string isn't a proper rev ID, fall back to lexicographic comparison. */
int collateRevIDs(void *context,
				  int len1, const void * chars1,
				  int len2, const void * chars2)
{
    const char* rev1 = (const char*)chars1;
    const char* rev2 = (const char*)chars2;
    const char* dash1 = (const char*)memchr(rev1, '-', len1);
    const char* dash2 = (const char*)memchr(rev2, '-', len2);
    if ((dash1==rev1+1 && dash2==rev2+1)
            || dash1 > rev1+8 || dash2 > rev2+8
            || dash1==NULL || dash2==NULL)
    {
        // Single-digit generation #s, or improper rev IDs; just compare as plain text:
        return defaultCollate(rev1,len1, rev2,len2);
    }
    // Parse generation numbers. If either is invalid, revert to default collation:
    int gen1 = parseDigits(rev1, dash1);
    int gen2 = parseDigits(rev2, dash2);
    if (!gen1 || !gen2)
        return defaultCollate(rev1,len1, rev2,len2);
    
    // Compare generation numbers; if they match, compare suffixes:
    return sgn(gen1 - gen2) ?: defaultCollate(dash1+1, len1-(int)(dash1+1-rev1),
                                              dash2+1, len2-(int)(dash2+1-rev2));
}

JNIEXPORT void JNICALL Java_com_couchbase_touchdb_RevCollator_nativeRegister
  (JNIEnv *env, jclass cls, jobject sqliteDatabase, jint version) {
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
	sqlite3 * sqliteHandle;
	if(version < 16) {
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

		jfieldID offset_db_handle = env->GetFieldID(sqlc_clazz, "mConnectionPtr", "I");
		if(offset_db_handle == NULL) {
			LOGE("Can't find SQLiteConnection.mConnectionPtr");
			return;
		}

		jint connectionPtr = env->GetIntField(mc, offset_db_handle);

		SQLiteConnection* connection = reinterpret_cast<SQLiteConnection*>(connectionPtr);

		sqliteHandle = connection->db;
	}

	// Install a custom collator
	sqlite3_create_collation(sqliteHandle, "REVID", SQLITE_UTF8, NULL, collateRevIDs);
}

JNIEXPORT jint JNICALL Java_com_couchbase_touchdb_RevCollator_testCollateRevIds
    (JNIEnv *env, jclass cls, jstring string1, jstring string2) {
    jboolean isCopy;
    const char* cstring1 = env->GetStringUTFChars(string1, &isCopy);
    const char* cstring2 = env->GetStringUTFChars(string2, &isCopy);

    int result = collateRevIDs(NULL, (int)strlen(cstring1), cstring1, (int)strlen(cstring2), cstring2);

    env->ReleaseStringUTFChars(string1, cstring1);
    env->ReleaseStringUTFChars(string2, cstring2);

    return result;
}
