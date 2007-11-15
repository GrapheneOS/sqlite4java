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
  const char *fn = (*jenv)->GetStringUTFChars(jenv, jfilename, 0);
  sqlite3* db = 0;

  int rc = sqlite3_open_v2(fn, &db, (int)jflags, 0);

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
  (*jenv)->ReleaseStringUTFChars(jenv, jfilename, fn);
  return rc;
}

JNIEXPORT jint JNICALL Java_sqlite_internal_SQLiteManualJNI_sqlite3_1exec(JNIEnv *jenv, jclass jcls,
  jlong jdb, jstring jsql, jobjectArray joutError)
{
  if (!jdb) return -1;
  if (!jsql) return -2;
  sqlite3* db = *(sqlite3**)&jdb;
  const char *sql = (*jenv)->GetStringUTFChars(jenv, jsql, 0);
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
        (*jenv)->SetObjectArrayElement(jenv, joutError, 0, err);
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

int sqlite3_prepare_v2(
  sqlite3 *db,            /* Database handle */
  const char *zSql,       /* SQL statement, UTF-8 encoded */
  int nByte,              /* Maximum length of zSql in bytes. */
  sqlite3_stmt **ppStmt,  /* OUT: Statement handle */
  const char **pzTail     /* OUT: Pointer to unused portion of zSql */
);


#ifdef __cplusplus
}
#endif
