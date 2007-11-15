#include "jni_setup.h"
#include <sqlite3.h>

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jint JNICALL Java_sqlite_internal_SQLiteManualJNI_sqlite3_1open_1v2(JNIEnv *jenv, jclass jcls,
  jstring jfilename, jlongArray jresult, jint jflags)
{
  if (!jfilename) return -1;
  if (!jresult) return -2;
  const char *filename = (*jenv)->GetStringUTFChars(jenv, jfilename, 0);
  if (!filename) return -3;
  sqlite3* db = 0;

  // todo(maybe) call jresult's getBytes("UTF-8") method to get filename in correct UTF-8
  int rc = sqlite3_open_v2(filename, &db, (int)jflags, 0);

  if (db && rc != SQLITE_OK) {
    // on error, open returns db anyway
    sqlite3_close(db);
    db = 0;
  }

  if (db) {
    jlong r = 0;
    *((sqlite3**)&r) = db;
    (*jenv)->SetLongArrayRegion(jenv, jresult, 0, 1, &r);
  }
  (*jenv)->ReleaseStringUTFChars(jenv, jfilename, filename);
  return rc;
}

JNIEXPORT jint JNICALL Java_sqlite_internal_SQLiteManualJNI_sqlite3_1exec(JNIEnv *jenv, jclass jcls,
  jlong jdb, jstring jsql, jobjectArray joutError)
{
  if (!jdb) return -1;
  if (!jsql) return -2;
  sqlite3* db = *(sqlite3**)&jdb;

  // todo(maybe) as in open_v2, convert to correct UTF-8
  const char *sql = (*jenv)->GetStringUTFChars(jenv, jsql, 0);
  if (!sql) return -3;

  char* msg = 0;
  char** msgPtr = (joutError) ? &msg : 0;

  int rc = sqlite3_exec(db, sql, 0, 0, msgPtr);

  (*jenv)->ReleaseStringUTFChars(jenv, jsql, sql);
  if (msg) {
    if (joutError) {
      // warning! can fail with exception here if bad array is passed
      jsize sz = (*jenv)->GetArrayLength(jenv, joutError);
      if (sz == 1) {
        jstring err = (*jenv)->NewStringUTF(jenv, msg);
        if (err) {
          (*jenv)->SetObjectArrayElement(jenv, joutError, 0, err);
        }
      }
    }
    sqlite3_free(msg);
  }
  
  return rc;
}

JNIEXPORT jint JNICALL Java_sqlite_internal_SQLiteManualJNI_sqlite3_1prepare_1v2(JNIEnv *jenv, jclass jcls,
  jlong jdb, jstring jsql, jlongArray jresult)
{
  if (!jdb) return -1;
  if (!jsql) return -2;
  if (!jresult) return -3;
  sqlite3* db = *(sqlite3**)&jdb;
  const char *sql = (*jenv)->GetStringUTFChars(jenv, jsql, 0);
  if (!sql) return -4;
  sqlite3_stmt* stmt = (sqlite3_stmt*)0;
  const char *tail = 0;

  int rc = sqlite3_prepare_v2(db, sql, -1, &stmt, &tail);

  if (stmt) {
    jlong r = 0;
    *((sqlite3_stmt**)&r) = stmt;
    (*jenv)->SetLongArrayRegion(jenv, jresult, 0, 1, &r);
  }
  (*jenv)->ReleaseStringUTFChars(jenv, jsql, sql);

  return rc;
}

JNIEXPORT jint JNICALL Java_sqlite_internal_SQLiteManualJNI_sqlite3_1bind_1text(JNIEnv *jenv, jclass jcls,
  jlong jstmt, jint jindex, jstring jvalue)
{
  sqlite3_stmt* stmt = *(sqlite3_stmt**)&jstmt;
  if (!stmt) return -1;
  if (!jvalue) return -2;
  int length = (*jenv)->GetStringLength(jenv, jvalue) * sizeof(jchar);
  jboolean copied;
  const jchar *value;
  void (*destructor)(void*);
  if (length > 0) {
    value = (*jenv)->GetStringCritical(jenv, jvalue, &copied);
    destructor = SQLITE_TRANSIENT;
  } else {
    value = (jchar*)"";
    destructor = SQLITE_STATIC;
  }
  if (!value) return -3;

  int rc = sqlite3_bind_text16(stmt, jindex, value, length, destructor);

  if (length > 0) {
    (*jenv)->ReleaseStringCritical(jenv, jvalue, value);
  }

  return rc;
}


#ifdef __cplusplus
}
#endif
