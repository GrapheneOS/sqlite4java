#include "jni_setup.h"
#include "sqlite3_wrap_manual.h"
#include <sqlite3.h>

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jstring JNICALL Java_com_almworks_sqlite4java__1SQLiteManualJNI_wrapper_1version(JNIEnv *jenv, jclass jcls) {
  jstring result = (*jenv)->NewStringUTF(jenv, WRAPPER_VERSION);
  return result;
}

JNIEXPORT jint JNICALL Java_com_almworks_sqlite4java__1SQLiteManualJNI_sqlite3_1open_1v2(JNIEnv *jenv, jclass jcls,
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

JNIEXPORT jint JNICALL Java_com_almworks_sqlite4java__1SQLiteManualJNI_sqlite3_1exec(JNIEnv *jenv, jclass jcls,
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


JNIEXPORT jint JNICALL Java_com_almworks_sqlite4java__1SQLiteManualJNI_sqlite3_1prepare_1v2(JNIEnv *jenv, jclass jcls,
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

//
//JNIEXPORT jint JNICALL Java_com_almworks_sqlite4java__1SQLiteManualJNI_sqlite3_1prepare_1v2_1optimized(JNIEnv *jenv, jclass jcls,
//  jlong jdb, jbyteArray jsql, jint jsqlLength, jlongArray jresult)
//{
//  sqlite3* db = 0;
//  char *sql = 0;
//  sqlite3_stmt* stmt = 0;
//  const char *tail = 0;
//  int rc = 0;
//  jlong r = 0;
//
//  if (!jdb) return WRAPPER_INVALID_ARG_1;
//  if (!jsql) return WRAPPER_INVALID_ARG_2;
//  if (!jresult) return WRAPPER_INVALID_ARG_3;
//  if ((*jenv)->GetArrayLength(jenv, jsql) <= jsqlLength) return WRAPPER_INVALID_ARG_4;
//  if (jsqlLength < 0) return WRAPPER_INVALID_ARG_5;
//  sql = (char*)(*jenv)->GetPrimitiveArrayCritical(jenv, jsql, 0);
//  if (!sql) return WRAPPER_CANNOT_TRANSFORM_STRING;
//  sql[jsqlLength] = 0;
//
//  db = *(sqlite3**)&jdb;
//  stmt = (sqlite3_stmt*)0;
//  tail = 0;
//
//  rc = sqlite3_prepare_v2(db, sql, -1, &stmt, &tail);
//
//  if (stmt) {
//    *((sqlite3_stmt**)&r) = stmt;
//    (*jenv)->SetLongArrayRegion(jenv, jresult, 0, 1, &r);
//  }
//  (*jenv)->ReleasePrimitiveArrayCritical(jenv, jsql, sql, 0);
//
//  return rc;
//}
//

JNIEXPORT jint JNICALL Java_com_almworks_sqlite4java__1SQLiteManualJNI_sqlite3_1bind_1text(JNIEnv *jenv, jclass jcls,
  jlong jstmt, jint jindex, jstring jvalue)
{
  sqlite3_stmt* stmt = *(sqlite3_stmt**)&jstmt;
  int length = 0;
  const jchar *value = 0;
  void (*destructor)(void*) = 0;
  int rc = 0;

  if (!stmt) return WRAPPER_INVALID_ARG_1;
  if (!jvalue) return WRAPPER_INVALID_ARG_3;
  length = (*jenv)->GetStringLength(jenv, jvalue) * sizeof(jchar);
  if (length > 0) {
    value = (*jenv)->GetStringCritical(jenv, jvalue, 0);
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

JNIEXPORT jint JNICALL Java_com_almworks_sqlite4java__1SQLiteManualJNI_sqlite3_1bind_1blob(JNIEnv *jenv, jclass jcls,
  jlong jstmt, jint jindex, jbyteArray jvalue, jint joffset, jint jlength)
{
  sqlite3_stmt* stmt = *(sqlite3_stmt**)&jstmt;
  int length = 0;
  void *value = 0;
  int rc = 0;

  if (!stmt) return WRAPPER_INVALID_ARG_1;
  if (!jvalue) return WRAPPER_INVALID_ARG_2;
  if (joffset < 0) return WRAPPER_INVALID_ARG_3;
  if (jlength < 0) return WRAPPER_INVALID_ARG_4;
  length = (int)(*jenv)->GetArrayLength(jenv, jvalue);
  if (joffset > length) return WRAPPER_INVALID_ARG_5;
  if (joffset + jlength > length) return WRAPPER_INVALID_ARG_6;

  if (jlength == 0) {
    rc = sqlite3_bind_zeroblob(stmt, jindex, 0);
  } else {
    value = (void*)(*jenv)->GetPrimitiveArrayCritical(jenv, jvalue, 0);
    if (!value) return WRAPPER_CANNOT_TRANSFORM_STRING;
    rc = sqlite3_bind_blob(stmt, jindex, (void*)(((unsigned char*)value) + joffset), jlength, SQLITE_TRANSIENT);
    (*jenv)->ReleasePrimitiveArrayCritical(jenv, jvalue, value, 0);
  }
  return rc;
}


JNIEXPORT jint JNICALL Java_com_almworks_sqlite4java__1SQLiteManualJNI_sqlite3_1column_1text(JNIEnv *jenv, jclass jcls,
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

JNIEXPORT jint JNICALL Java_com_almworks_sqlite4java__1SQLiteManualJNI_sqlite3_1column_1blob(JNIEnv *jenv, jclass jcls,
  jlong jstmt, jint jcolumn, jobjectArray joutValue)
{
  sqlite3_stmt* stmt = *(sqlite3_stmt**)&jstmt;
  const void *value = 0;
  sqlite3* db = 0;
  int err = 0;
  int length = 0;
  jbyteArray result = 0;
  void* resultPtr = 0;

  if (!stmt) return WRAPPER_INVALID_ARG_1;
  if (!joutValue) return WRAPPER_INVALID_ARG_3;

  value = sqlite3_column_blob(stmt, jcolumn);
  if (!value) {
    // maybe we're out of memory
    db = sqlite3_db_handle(stmt);
    if (!db) return WRAPPER_WEIRD;
    err = sqlite3_errcode(db);
    if (err == SQLITE_NOMEM) return err;
  } else {
    length = sqlite3_column_bytes(stmt, jcolumn);
    if (length < 0) return WRAPPER_WEIRD_2;
    result = (*jenv)->NewByteArray(jenv, length);
    if (!result) return WRAPPER_CANNOT_ALLOCATE_STRING;
    resultPtr = (void*)(*jenv)->GetPrimitiveArrayCritical(jenv, result, 0);
    if (!resultPtr) return WRAPPER_CANNOT_ALLOCATE_STRING;
    memcpy(resultPtr, value, length);
    (*jenv)->ReleasePrimitiveArrayCritical(jenv, result, resultPtr, 0);
  }
  (*jenv)->SetObjectArrayElement(jenv, joutValue, 0, result);
  return SQLITE_OK;
}


JNIEXPORT jint JNICALL Java_com_almworks_sqlite4java__1SQLiteManualJNI_sqlite3_1blob_1open(JNIEnv *jenv, jclass jcls,
  jlong jdb, jstring jdbname, jstring jtable, jstring jcolumn, jlong jrowid, jboolean jwriteAccess, jlongArray jresult)
{
  sqlite3* db = 0;
  const char *dbname = 0;
  const char *table = 0;
  const char *column = 0;
  int rc = 0;
  sqlite3_blob *blob = 0;
  jlong r = 0;

  if (!jdb) return WRAPPER_INVALID_ARG_1;
  if (!jtable) return WRAPPER_INVALID_ARG_3;
  if (!jcolumn) return WRAPPER_INVALID_ARG_4;
  if (!jresult) return WRAPPER_INVALID_ARG_5;

  db = *(sqlite3**)&jdb;
  dbname = jdbname ? (*jenv)->GetStringUTFChars(jenv, jdbname, 0) : 0;
  table = (*jenv)->GetStringUTFChars(jenv, jtable, 0);
  column = (*jenv)->GetStringUTFChars(jenv, jcolumn, 0);
  if (!table || !column || (!dbname && jdbname)) {
    rc = WRAPPER_CANNOT_TRANSFORM_STRING;
  } else {
    rc = sqlite3_blob_open(db, dbname, table, column, jrowid, jwriteAccess ? 1 : 0, &blob);
    if (blob) {
      *((sqlite3_blob**)&r) = blob;
      (*jenv)->SetLongArrayRegion(jenv, jresult, 0, 1, &r);
    }
  }
  if (dbname) (*jenv)->ReleaseStringUTFChars(jenv, jdbname, dbname);
  if (table) (*jenv)->ReleaseStringUTFChars(jenv, jtable, table);
  if (column) (*jenv)->ReleaseStringUTFChars(jenv, jcolumn, column);
  return rc;
}

JNIEXPORT jint JNICALL Java_com_almworks_sqlite4java__1SQLiteManualJNI_sqlite3_1blob_1read(JNIEnv *jenv, jclass jcls,
  jlong jblob, jint jblobOffset, jbyteArray jbuffer, jint jbufferOffset, jint jlength)
{
  int length = 0;
  sqlite3_blob* blob = 0;
  void *buffer = 0;
  int rc = 0;

  if (!jblob) return WRAPPER_INVALID_ARG_1;
  if (!jbuffer) return WRAPPER_INVALID_ARG_2;
  if (jbufferOffset < 0) return WRAPPER_INVALID_ARG_3;
  if (jlength < 0) return WRAPPER_INVALID_ARG_4;
  if (jlength == 0) return SQLITE_OK;

  length = (int)(*jenv)->GetArrayLength(jenv, jbuffer);
  if (jbufferOffset > length) return WRAPPER_INVALID_ARG_5;
  if (jbufferOffset + jlength > length) return WRAPPER_INVALID_ARG_6;

  blob = *(sqlite3_blob**)&jblob;
  buffer = (void*)(*jenv)->GetPrimitiveArrayCritical(jenv, jbuffer, 0);
  if (!buffer) return WRAPPER_CANNOT_TRANSFORM_STRING;

  rc = sqlite3_blob_read(blob, (void*)(((unsigned char*)buffer) + jbufferOffset), jlength, jblobOffset);
  (*jenv)->ReleasePrimitiveArrayCritical(jenv, jbuffer, buffer, 0);
  return rc;
}

JNIEXPORT jint JNICALL Java_com_almworks_sqlite4java__1SQLiteManualJNI_sqlite3_1blob_1write(JNIEnv *jenv, jclass jcls,
  jlong jblob, jint jblobOffset, jbyteArray jbuffer, jint jbufferOffset, jint jlength)
{
  int length = 0;
  sqlite3_blob* blob = 0;
  void *buffer = 0;
  int rc = 0;

  if (!jblob) return WRAPPER_INVALID_ARG_1;
  if (!jbuffer) return WRAPPER_INVALID_ARG_2;
  if (jbufferOffset < 0) return WRAPPER_INVALID_ARG_3;
  if (jlength < 0) return WRAPPER_INVALID_ARG_4;
  if (jlength == 0) return SQLITE_OK;

  length = (int)(*jenv)->GetArrayLength(jenv, jbuffer);
  if (jbufferOffset > length) return WRAPPER_INVALID_ARG_5;
  if (jbufferOffset + jlength > length) return WRAPPER_INVALID_ARG_6;

  blob = *(sqlite3_blob**)&jblob;
  buffer = (void*)(*jenv)->GetPrimitiveArrayCritical(jenv, jbuffer, 0);
  if (!buffer) return WRAPPER_CANNOT_TRANSFORM_STRING;

  rc = sqlite3_blob_write(blob, (void*)(((unsigned char*)buffer) + jbufferOffset), jlength, jblobOffset);
  (*jenv)->ReleasePrimitiveArrayCritical(jenv, jbuffer, buffer, 0);
  return rc;
}

//typedef struct {
//  char busy;  // if set, the buffer is used
//  char dispose;  // if set, buffer must be freed when it is no longer busy
//  char data[];
//} wrapper_buffer;

JNIEXPORT jint JNICALL Java_com_almworks_sqlite4java__1SQLiteManualJNI_wrapper_1alloc(JNIEnv *jenv, jclass jcls,
  jint size, jlongArray ppBuf, jobjectArray ppByteBuffer)
{
  void *ptr = 0;
  jlong lptr = 0;
  jobject controlBuffer = 0;
  jobject dataBuffer = 0;

  if (size < 3) return WRAPPER_INVALID_ARG_1;
  if (!ppBuf) return WRAPPER_INVALID_ARG_2;
  if (!ppByteBuffer) return WRAPPER_INVALID_ARG_3;

  ptr = sqlite3_malloc(size);
  if (!ptr) return WRAPPER_OUT_OF_MEMORY;

  *((void**)&lptr) = ptr;
  controlBuffer = (*jenv)->NewDirectByteBuffer(jenv, ptr, 2);
  if (!controlBuffer) {
    sqlite3_free(ptr);
    return WRAPPER_OUT_OF_MEMORY;
  }
  dataBuffer = (*jenv)->NewDirectByteBuffer(jenv, (void*)(((unsigned char*)ptr) + 2), size - 2);
  if (!dataBuffer) {
    sqlite3_free(ptr);
    return WRAPPER_OUT_OF_MEMORY;
  }

  memset(ptr, 0, size);

  (*jenv)->SetLongArrayRegion(jenv, ppBuf, 0, 1, &lptr);
  (*jenv)->SetObjectArrayElement(jenv, ppByteBuffer, 0, controlBuffer);
  (*jenv)->SetObjectArrayElement(jenv, ppByteBuffer, 1, dataBuffer);

  return SQLITE_OK;
}

JNIEXPORT jint JNICALL Java_com_almworks_sqlite4java__1SQLiteManualJNI_wrapper_1free(JNIEnv *jenv, jclass jcls,
  jlong jbuffer)
{
  unsigned char *ptr = *(unsigned char**)&jbuffer;
  if (!ptr) return SQLITE_OK;

  // actually free if not in use
  if (!ptr[0]) {
    ptr[1] = -1;
    sqlite3_free((void*)ptr);
  } else {
    ptr[1] = 1;
  }

  return SQLITE_OK;
}

void bind_release(void *ptr);

JNIEXPORT jint JNICALL Java_com_almworks_sqlite4java__1SQLiteManualJNI_wrapper_1bind_1buffer(JNIEnv *jenv, jclass jcls,
  jlong jstmt, jint jindex, jlong jbuffer, jint jlength)
{
  sqlite3_stmt *stmt = *(sqlite3_stmt**)&jstmt;
  unsigned char *buffer = *(unsigned char**)&jbuffer;
  int rc = 0;

  if (!stmt) return WRAPPER_INVALID_ARG_1;
  if (!buffer) return WRAPPER_INVALID_ARG_2;

  // if should be freed...
  if (buffer[1]) return WRAPPER_INVALID_ARG_3;
  // mark as used
  buffer[0]++;

  rc = sqlite3_bind_blob(stmt, jindex, (const void*)(buffer + 2), jlength, &bind_release);
  if (rc != SQLITE_OK) {
    buffer[0]--;
  }

  return rc;
}

void bind_release(void *ptr) {
  unsigned char *buffer;
  if (!ptr) return;
  buffer = ((unsigned char *)ptr) - 2;
  if (buffer[0] > 0) {
    buffer[0]--;
  }
  if (buffer[1] == 1) {
    sqlite3_free((void*)buffer);
  }
}

JNIEXPORT jint JNICALL Java_com_almworks_sqlite4java__1SQLiteManualJNI_wrapper_1column_1buffer(JNIEnv *jenv, jclass jcls,
  jlong jstmt, jint jcolumn, jobjectArray joutBuffer)
{
  sqlite3_stmt* stmt = *(sqlite3_stmt**)&jstmt;
  const void *value = 0;
  sqlite3* db = 0;
  int err = 0;
  int length = 0;
  jobject result = 0;

  if (!stmt) return WRAPPER_INVALID_ARG_1;
  if (!joutBuffer) return WRAPPER_INVALID_ARG_3;

  value = sqlite3_column_blob(stmt, jcolumn);
  if (!value) {
    // maybe we're out of memory
    db = sqlite3_db_handle(stmt);
    if (!db) return WRAPPER_WEIRD;
    err = sqlite3_errcode(db);
    if (err == SQLITE_NOMEM) return err;
  } else {
    length = sqlite3_column_bytes(stmt, jcolumn);
    if (length < 0) return WRAPPER_WEIRD_2;
    result = (*jenv)->NewDirectByteBuffer(jenv, (void*)value, length);
    if (!result) return WRAPPER_CANNOT_ALLOCATE_STRING;
  }
  (*jenv)->SetObjectArrayElement(jenv, joutBuffer, 0, result);
  return SQLITE_OK;
}


int progress_handler(void *ptr);

JNIEXPORT jint JNICALL Java_com_almworks_sqlite4java__1SQLiteManualJNI_install_1progress_1handler(JNIEnv *jenv, jclass jcls,
  jlong jdb, jint steps, jlongArray ppBuf, jobjectArray ppByteBuffer)
{
  sqlite3* db = 0;
  int rc = 0;
  void *ptr = 0;
  jlong lptr = 0;
  jobject buffer = 0;
  int len = 2 * sizeof(jlong);

  if (!jdb) return WRAPPER_INVALID_ARG_1;
  if (!ppBuf) return WRAPPER_INVALID_ARG_2;
  if (!ppByteBuffer) return WRAPPER_INVALID_ARG_3;
  if (steps < 1) return WRAPPER_INVALID_ARG_4;
  db = *(sqlite3**)&jdb;

  ptr = (jlong*)sqlite3_malloc(len);
  if (!ptr) return WRAPPER_OUT_OF_MEMORY;

  *((void**)&lptr) = ptr;
  buffer = (*jenv)->NewDirectByteBuffer(jenv, ptr, len);
  if (!buffer) {
    sqlite3_free(ptr);
    return WRAPPER_OUT_OF_MEMORY;
  }

  memset(ptr, 0, len);

  (*jenv)->SetLongArrayRegion(jenv, ppBuf, 0, 1, &lptr);
  (*jenv)->SetObjectArrayElement(jenv, ppByteBuffer, 0, buffer);

  sqlite3_progress_handler(db, steps, &progress_handler, ptr);

  return SQLITE_OK;
}

JNIEXPORT jint JNICALL Java_com_almworks_sqlite4java__1SQLiteManualJNI_uninstall_1progress_1handler(JNIEnv *jenv, jclass jcls,
  jlong jdb, jlong jptr)
{
  sqlite3* db = 0;
  void *ptr = 0;

  if (!jdb) return WRAPPER_INVALID_ARG_1;
  if (!jptr) return WRAPPER_INVALID_ARG_2;
  db = *(sqlite3**)&jdb;
  ptr = *(void**)&jptr;

  sqlite3_progress_handler(db, 1, 0, 0);
  sqlite3_free(ptr);

  return SQLITE_OK;
}

int progress_handler(void *ptr) {
  jlong* lptr = 0;

  if (!ptr) return 1;
  lptr = *(jlong**)&ptr;

  lptr[1]++;
  if (lptr[0] != 0) return -1;

  return 0;
}

JNIEXPORT jint JNICALL Java_com_almworks_sqlite4java__1SQLiteManualJNI_wrapper_1load_1ints(JNIEnv *jenv, jclass jcls,
  jlong jstmt, jint column, jintArray ppBuf, jint offset, jint count, jintArray ppCount)
{
  sqlite3_stmt* stmt = *(sqlite3_stmt**)&jstmt;
  jint loaded = 0;
  int p = offset;
  int rc = 0;
  jint *buf = 0;
  jint len = 0;

  if (!stmt) return WRAPPER_INVALID_ARG_1;
  if (!ppBuf) return WRAPPER_INVALID_ARG_2;
  if (!ppCount) return WRAPPER_INVALID_ARG_3;
  if (count <= 0) return WRAPPER_INVALID_ARG_4;

  len = (*jenv)->GetArrayLength(jenv, ppBuf);
  if (offset < 0 || offset + count > len) return WRAPPER_INVALID_ARG_4;

  buf = (*jenv)->GetIntArrayElements(jenv, ppBuf, 0);
  if (!buf) return WRAPPER_CANNOT_ALLOCATE_STRING;

  while ((rc = sqlite3_step(stmt)) == SQLITE_ROW) {
    buf[p++] = sqlite3_column_int(stmt, column);
    if (++loaded >= count) {
      break;
    }
  }

  (*jenv)->ReleaseIntArrayElements(jenv, ppBuf, buf, 0);
  (*jenv)->SetIntArrayRegion(jenv, ppCount, 0, 1, &loaded);

  return rc;
}

#ifdef __cplusplus
}
#endif
