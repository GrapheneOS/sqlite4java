#include "jni_setup.h"
#include <sqlite3.h>

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jint JNICALL Java_sqlite_internal_SQLiteManualJNI_sqlite3_1open_1v2(JNIEnv *jenv, jclass jcls,
  jstring filename, jlongArray result, jint flags)
{
  if (!filename) return -1;
  if (!result) return -2;
  jsize sz = (*jenv)->GetArrayLength(jenv, result);
  if (sz != 1) return -3;
  const char *fn = (*jenv)->GetStringUTFChars(jenv, filename, 0);
  sqlite3* db = (sqlite3*)0;
  int rc = sqlite3_open_v2(fn, &db, (int)flags, 0);
  if (db) {
    jlong r = 0;
    *((sqlite**)&r) = db;
    (*jenv)->SetLongArrayRegion(jenv, result, 0, 1, &r);
  }
  (*jenv)->ReleaseStringUTFChars(jenv, filename, fn);
  return rc;
}


#ifdef __cplusplus
}
#endif
