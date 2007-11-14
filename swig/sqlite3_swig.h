/*
** Wrapper for sqlite3 calls, to be used in JNI generation with SWIG.
*/

#ifndef _SQLITE3_SWIG_H_
#define _SQLITE3_SWIG_H_

#ifdef __cplusplus
extern "C" {
#endif

typedef struct DBHandle DBHandle;
struct DBHandle {
  sqlite3* db;
  int error_code;
};

void sqlite3_open_v2_wr(
  const char *filename,
  int flags,
  DBHandle* handleOut);

#ifdef __cplusplus
}
#endif
#endif