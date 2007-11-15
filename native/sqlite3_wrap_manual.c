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
  jsize sz = (*jenv)->GetArrayLength(jenv, jresult);
  if (sz != 1) return -3;
  const char *fn = (*jenv)->GetStringUTFChars(jenv, jfilename, 0);
  sqlite3* db = (sqlite3*)0;

  int rc = sqlite3_open_v2(fn, &db, (int)jflags, 0);

  if (db) {
    jlong r = 0;
    *((sqlite3**)&r) = db;
    (*jenv)->SetLongArrayRegion(jenv, jresult, 0, 1, &r);
  }
  (*jenv)->ReleaseStringUTFChars(jenv, jfilename, fn);
  return rc;
}

JNIEXPORT jint JNICALL Java_sqlite_internal_SQLiteManualJNI_sqlite3_1exec(JNIEnv *jenv, jclass jcls,
  jlong jdb, jstring jsql, jstringArray jparseError)
{
  if (!jdb) return -1;
  if (!jsql) return -2;
  sqlite3* db = *(sqlite3**)&jdb;
  const char *sql = (*jenv)->GetStringUTFChars(jenv, jsql, 0);
  char* msg = 0;
  char** msgPtr = (jparseError) ? &msg : 0;

  int rc = sqlite3_exec(db, sql, 0, 0, msgPtr);

  (*jenv)->ReleaseStringUTFChars(jenv, jsql, sql);
  if (msg) {
    if (jparseError) {
      jsize sz = (*jenv)->GetArrayLength(jenv, jparseError);
      if (sz == 1) {
        jstring err = (*jenv)->NewStringUTF(jenv, msg);
        (*jenv)->SetStringArrayRegion(jenv, jparseError, 0, 1, &err);
      }
    }
    sqlite3_free(msg);
  }
  
  return rc;
}

#ifdef __cplusplus
}
#endif
