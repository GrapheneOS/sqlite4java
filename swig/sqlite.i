%module sqlite
%include "sqliteConstants.i"
%{
#include "../sqlite/sqlite3.h"
%}

#ifdef SQLITE_INT64_TYPE
 typedef SQLITE_INT64_TYPE sqlite_int64;
 typedef unsigned SQLITE_INT64_TYPE sqlite_uint64;
#elif defined(_MSC_VER) || defined(__BORLANDC__)
 typedef __int64 sqlite_int64;
 typedef unsigned __int64 sqlite_uint64;
#else
 typedef long long int sqlite_int64;
 typedef unsigned long long int sqlite_uint64;
#endif
typedef sqlite_int64 sqlite3_int64;
typedef sqlite_uint64 sqlite3_uint64;


const char *sqlite3_libversion();
int sqlite3_libversion_number();
int sqlite3_threadsafe();
int sqlite3_close(sqlite3* db);
int sqlite3_extended_result_codes(sqlite3*, int onoff);
sqlite3_int64 sqlite3_last_insert_rowid(sqlite3*);


// todo exec

%{
static int  global_callback(void* arg, int columnsNumber, char** columns, char** columnNames) {
  struct HH *hh = (struct HH*)arg;
  // ...
  return 0;
}
%}

%native (sqlite3_exec) int sqlite3_exec(sqlite3* db, const char *sql, void* callback, char**errmsg);
%{
JNIEXPORT void JNICALL Java_sqlite4java_sqlite4javaJNIsqlite3_1exec(
  sqlite3* db, const char *sql, void* callback, char**errmsg
) {
  jint jresult = 0 ;
  sqlite3 *arg1 = (sqlite3 *) 0 ;
  char *arg2 = (char *) 0 ;
  char **arg5 = (char **) 0 ;
  int result;

  (void)jenv;
  (void)jcls;
  arg1 = *(sqlite3 **)&jarg1;
  arg2 = 0;
  if (jarg2) {
    arg2 = (char *)(*jenv)->GetStringUTFChars(jenv, sql, 0);
    if (!arg2) return 0;
  }
  {
    arg3 = xxx;
    arg4 = yyy;
  }
  arg5 = *(char ***)&jarg5;
  result = (int)sqlite3_exec(arg1,(char const *)arg2,arg3,arg4,arg5);
  jresult = (jint)result;
  if (arg2) (*jenv)->ReleaseStringUTFChars(jenv, jarg2, arg2);
  return jresult;

int sqlite3_exec(sqlite3* db, const char *sql,
      int (*callback)(void*,int,char**,char**),
      void* arg,
	  char **errmsg
);
}
%}

