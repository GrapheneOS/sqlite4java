%module SQLiteSwigged
%{
#include <sqlite3.h>
%}

%include "sqlite3_swigged.h"
//
//
//%typemap(in) sqlite3** (sqlite3* temp) {
//  $1 = &temp;
//}
//
//%typemap(argout) sqlite3** (jlong temp){
//  temp = *(jlong**)$1;
//  (*env)->SetLongArrayRegion($result, 0, 1, &temp);
//}
//
//%typemap(jni) sqlite3** "jlongArray"
//%typemap(jtype) sqlite3** "long[]"
//%typemap(jstype) sqlite3** "SWIGTYPE_p_sqlite3[]"
//%typemap(javain) sqlite3** "$javainput"

//%apply char[] {unsigned char *};
//%include carrays.i
//%array_class(short, shortArray);
//%array_class(char, charArray);
//%array_class(signed char, byteArray);
//
//%include cpointer.i
//%pointer_functions(sqlite3 *, sqlite3p);
//%pointer_functions(char *, charp);
//%pointer_functions(sqlite3_stmt *, stmtp);
//%pointer_functions(void *, voidp);

//
//#ifdef SQLITE_INT64_TYPE
// typedef SQLITE_INT64_TYPE sqlite_int64;
// typedef unsigned SQLITE_INT64_TYPE sqlite_uint64;
//#elif defined(_MSC_VER) || defined(__BORLANDC__)
// typedef __int64 sqlite_int64;
// typedef unsigned __int64 sqlite_uint64;
//#else
// typedef long long int sqlite_int64;
// typedef unsigned long long int sqlite_uint64;
//#endif
//typedef sqlite_int64 sqlite3_int64;
//typedef sqlite_uint64 sqlite3_uint64;
//
//
//const char *sqlite3_libversion();
//int sqlite3_libversion_number();
//int sqlite3_threadsafe();
//int sqlite3_close(sqlite3* db);
//int sqlite3_extended_result_codes(sqlite3*, int onoff);
//sqlite3_int64 sqlite3_last_insert_rowid(sqlite3*);
//int sqlite3_changes(sqlite3*);
//int sqlite3_total_changes(sqlite3*);
//void sqlite3_interrupt(sqlite3*);
//int sqlite3_busy_timeout(sqlite3*, int ms);
//sqlite3_int64 sqlite3_memory_used(void);
//sqlite3_int64 sqlite3_memory_highwater(int resetFlag);
//int sqlite3_errcode(sqlite3 *db);
//const char *sqlite3_errmsg(sqlite3*);
//
//int sqlite3_bind_double(sqlite3_stmt*, int, double);
//
// todo:
// sqlite3_exec
// sqlite3_busy_handler
// sqlite3_trace
// sqlite3_profile
// sqlite3_progress_handler
// sqlite3_open_v2
// sqlite3_prepare_v2
// sqlite3_bind_blob
