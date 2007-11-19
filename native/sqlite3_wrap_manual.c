#include "jni_setup.h"
#include "sqlite3_wrap_manual.h"
#include <sqlite3.h>

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jstring JNICALL Java_sqlite_internal_SQLiteManualJNI_wrapper_1version(JNIEnv *jenv, jclass jcls) {
  jstring result = (*jenv)->NewStringUTF(jenv, WRAPPER_VERSION);
  return result;
}

JNIEXPORT jint JNICALL Java_sqlite_internal_SQLiteManualJNI_sqlite3_1open_1v2(JNIEnv *jenv, jclass jcls,
  jstring jfilename, jlongArray jresult, jint jflags)
{
  const char *filename = 0;
  sqlite3* db = 0;
  int rc = 0;
  jlong r = 0;

  if (!jfilename) return WRAPPER_INVALID_ARG_1;
  if (!jresult) return WRAPPER_INVALID_ARG_2;
  filename = (*jenv)->GetStringUTFChars(jenv, jfilename, 0);
  if (!filename) return WRAPPER_CANNOT_TRANSFORM_STRING;

  // todo(maybe) call jresult's getBytes("UTF-8") method to get filename in correct UTF-8
  rc = sqlite3_open_v2(filename, &db, (int)jflags, 0);

  if (db && rc != SQLITE_OK) {
    // on error, open returns db anyway
    sqlite3_close(db);
    db = 0;
  }

  if (db) {
    *((sqlite3**)&r) = db;
    (*jenv)->SetLongArrayRegion(jenv, jresult, 0, 1, &r);
  }
  (*jenv)->ReleaseStringUTFChars(jenv, jfilename, filename);
  return rc;
}

JNIEXPORT jint JNICALL Java_sqlite_internal_SQLiteManualJNI_sqlite3_1exec(JNIEnv *jenv, jclass jcls,
  jlong jdb, jstring jsql, jobjectArray joutError)
{
  sqlite3* db = 0;
  const char *sql = 0;
  char* msg = 0;
  char** msgPtr = (joutError) ? &msg : 0;
  jsize sz = 0;
  jstring err = 0;
  int rc = 0;

  if (!jdb) return WRAPPER_INVALID_ARG_1;
  if (!jsql) return WRAPPER_INVALID_ARG_2;
  db = *(sqlite3**)&jdb;

  // todo(maybe) as in open_v2, convert to correct UTF-8
  sql = (*jenv)->GetStringUTFChars(jenv, jsql, 0);
  if (!sql) return WRAPPER_CANNOT_TRANSFORM_STRING;

  rc = sqlite3_exec(db, sql, 0, 0, msgPtr);

  (*jenv)->ReleaseStringUTFChars(jenv, jsql, sql);
  if (msg) {
    if (joutError) {
      // warning! can fail with exception here if bad array is passed
      sz = (*jenv)->GetArrayLength(jenv, joutError);
      if (sz == 1) {
        err = (*jenv)->NewStringUTF(jenv, msg);
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
  sqlite3* db = 0;
  const char *sql = 0;
  sqlite3_stmt* stmt = 0;
  const char *tail = 0;
  int rc = 0;
  jlong r = 0;

  if (!jdb) return WRAPPER_INVALID_ARG_1;
  if (!jsql) return WRAPPER_INVALID_ARG_2;
  if (!jresult) return WRAPPER_INVALID_ARG_3;
  db = *(sqlite3**)&jdb;
  sql = (*jenv)->GetStringUTFChars(jenv, jsql, 0);
  if (!sql) return WRAPPER_CANNOT_TRANSFORM_STRING;
  stmt = (sqlite3_stmt*)0;
  tail = 0;

  rc = sqlite3_prepare_v2(db, sql, -1, &stmt, &tail);

  if (stmt) {
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
  int length = 0;
  jboolean copied = 0;
  const jchar *value = 0;
  void (*destructor)(void*) = 0;
  int rc = 0;

  if (!stmt) return WRAPPER_INVALID_ARG_1;
  if (!jvalue) return WRAPPER_INVALID_ARG_3;
  length = (*jenv)->GetStringLength(jenv, jvalue) * sizeof(jchar);
  if (length > 0) {
    value = (*jenv)->GetStringCritical(jenv, jvalue, &copied);
    destructor = SQLITE_TRANSIENT;
  } else {
    value = (const jchar*)"";
    destructor = SQLITE_STATIC;
  }
  if (!value) return WRAPPER_CANNOT_TRANSFORM_STRING;

  rc = sqlite3_bind_text16(stmt, jindex, value, length, destructor);

  if (length > 0) {
    (*jenv)->ReleaseStringCritical(jenv, jvalue, value);
  }
  return rc;
}

JNIEXPORT jint JNICALL Java_sqlite_internal_SQLiteManualJNI_sqlite3_1column_1text(JNIEnv *jenv, jclass jcls,
  jlong jstmt, jint jcolumn, jobjectArray joutValue)
{
  sqlite3_stmt* stmt = *(sqlite3_stmt**)&jstmt;
  const jchar *text = 0;
  jstring result = 0;
  sqlite3* db = 0;
  int err = 0;
  int length = 0;

  if (!stmt) return WRAPPER_INVALID_ARG_1;
  if (!joutValue) return WRAPPER_INVALID_ARG_3;
  text = sqlite3_column_text16(stmt, jcolumn);
  if (!text) {
    // maybe we're out of memory
    db = sqlite3_db_handle(stmt);
    if (!db) return WRAPPER_WEIRD;
    err = sqlite3_errcode(db);
    if (err == SQLITE_NOMEM) return err;
  } else {
    length = sqlite3_column_bytes16(stmt, jcolumn);
    if (length < 0) return WRAPPER_WEIRD_2;
    result = (*jenv)->NewString(jenv, text, length / sizeof (jchar));
    if (!result) return WRAPPER_CANNOT_ALLOCATE_STRING;
  }
  (*jenv)->SetObjectArrayElement(jenv, joutValue, 0, result);
  return SQLITE_OK;
}


#ifdef __cplusplus
}
#endif
