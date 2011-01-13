/*
 * Copyright 2010 ALM Works Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
** This file contains function and structure declarations from sqlite3.h
** Only declarations passed to SWIG for code generation are present in code
** The functions that are not included in sqlite4java and that are supported
** through manual code are given in comments.
*/


/*
** ******************************************************************
** 1. Declarations for SWIG
** ******************************************************************
*/
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

typedef struct sqlite3 sqlite3;
typedef struct sqlite3_stmt sqlite3_stmt;
typedef struct sqlite3_blob sqlite3_blob;

int sqlite3_initialize(void);
int sqlite3_shutdown(void);
int sqlite3_extended_errcode(sqlite3 *db);
const char *sqlite3_libversion(void);
const char *sqlite3_sourceid(void);
int sqlite3_libversion_number(void);
int sqlite3_compileoption_used(const char *zOptName);
const char *sqlite3_compileoption_get(int N);
int sqlite3_threadsafe(void);
int sqlite3_close(sqlite3 *);
int sqlite3_extended_result_codes(sqlite3*, int onoff);
sqlite3_int64 sqlite3_last_insert_rowid(sqlite3*);
int sqlite3_changes(sqlite3*);
int sqlite3_total_changes(sqlite3*);
void sqlite3_interrupt(sqlite3*);
int sqlite3_complete(const char *sql);
int sqlite3_busy_timeout(sqlite3*, int ms);
sqlite3_int64 sqlite3_memory_used(void);
sqlite3_int64 sqlite3_memory_highwater(int resetFlag);
int sqlite3_errcode(sqlite3 *db);
const char *sqlite3_errmsg(sqlite3*);
int sqlite3_bind_double(sqlite3_stmt*, int, double);
int sqlite3_bind_int(sqlite3_stmt*, int, int);
int sqlite3_bind_int64(sqlite3_stmt*, int, sqlite3_int64);
int sqlite3_bind_null(sqlite3_stmt*, int);
int sqlite3_bind_zeroblob(sqlite3_stmt*, int, int n);
int sqlite3_bind_parameter_count(sqlite3_stmt*);
const char *sqlite3_bind_parameter_name(sqlite3_stmt*, int);
int sqlite3_bind_parameter_index(sqlite3_stmt*, const char *zName);
int sqlite3_clear_bindings(sqlite3_stmt*);
int sqlite3_column_count(sqlite3_stmt *pStmt);
const char *sqlite3_column_name(sqlite3_stmt*, int N);
const char *sqlite3_column_database_name(sqlite3_stmt*,int);
const char *sqlite3_column_table_name(sqlite3_stmt*,int);
const char *sqlite3_column_origin_name(sqlite3_stmt*,int);
const char *sqlite3_column_decltype(sqlite3_stmt *, int i);
int sqlite3_step(sqlite3_stmt*);
int sqlite3_data_count(sqlite3_stmt *pStmt);
double sqlite3_column_double(sqlite3_stmt*, int iCol);
int sqlite3_column_int(sqlite3_stmt*, int iCol);
sqlite3_int64 sqlite3_column_int64(sqlite3_stmt*, int iCol);
int sqlite3_column_type(sqlite3_stmt*, int iCol);
int sqlite3_finalize(sqlite3_stmt *pStmt);
int sqlite3_reset(sqlite3_stmt *pStmt);
int sqlite3_get_autocommit(sqlite3*);
sqlite3 *sqlite3_db_handle(sqlite3_stmt*);
int sqlite3_enable_shared_cache(int);
int sqlite3_release_memory(int);
sqlite3_int64 sqlite3_soft_heap_limit64(sqlite3_int64);
int sqlite3_blob_close(sqlite3_blob *);
int sqlite3_blob_bytes(sqlite3_blob *);
int sqlite3_stmt_readonly(sqlite3_stmt *);
int sqlite3_blob_reopen(sqlite3_blob *, sqlite3_int64);

/*
** ******************************************************************
** 2. Declarations supported manually in sqlite4_wrap_manual
** ******************************************************************
*/

// typedef int (*sqlite3_callback)(void*,int,char**, char**);

// int sqlite3_exec(
//    sqlite3*,                                  /* An open database */
//    const char *sql,                           /* SQL to be evaluted */
//    int (*callback)(void*,int,char**,char**),  /* Callback function */
//    void *,                                    /* 1st argument to callback */
//    char **errmsg                              /* Error msg written here */
//  );

// int sqlite3_busy_handler(sqlite3*, int(*)(void*,int), void*);

//int sqlite3_open_v2(
//  const char *filename,   /* Database filename (UTF-8) */
//  sqlite3 **ppDb,         /* OUT: SQLite db handle */
//  int flags,              /* Flags */
//  const char *zVfs        /* Name of VFS module to use */
//);

//int sqlite3_prepare_v2(
//  sqlite3 *db,            /* Database handle */
//  const char *zSql,       /* SQL statement, UTF-8 encoded */
//  int nByte,              /* Maximum length of zSql in bytes. */
//  sqlite3_stmt **ppStmt,  /* OUT: Statement handle */
//  const char **pzTail     /* OUT: Pointer to unused portion of zSql */
//);

//int sqlite3_bind_text(sqlite3_stmt*, int, const char*, int n, void(*)(void*));
//int sqlite3_column_bytes(sqlite3_stmt*, int iCol);
//const unsigned char *sqlite3_column_text(sqlite3_stmt*, int iCol);
//const void *sqlite3_column_blob(sqlite3_stmt*, int iCol);

//int sqlite3_bind_blob(sqlite3_stmt*, int, const void*, int n, void(*)(void*));

//int sqlite3_blob_open(
//  sqlite3*,
//  const char *zDb,
//  const char *zTable,
//  const char *zColumn,
//  sqlite3_int64 iRow,
//  int flags,
//  sqlite3_blob **ppBlob
//);

//int sqlite3_blob_read(sqlite3_blob *, void *z, int n, int iOffset);
//int sqlite3_blob_write(sqlite3_blob *, const void *z, int n, int iOffset);

// void sqlite3_progress_handler(sqlite3*, int, int(*)(void*), void*);

/*
** ******************************************************************
** 3. Declarations that are not yet supported manually in
** sqlite4_wrap_manual, but probably should be.
** ******************************************************************
*/

//void *sqlite3_trace(sqlite3*, void(*xTrace)(void*,const char*), void*);
//void *sqlite3_profile(sqlite3*,
//   void(*xProfile)(void*,const char*,sqlite3_uint64), void*);

//int sqlite3_table_column_metadata(
//  sqlite3 *db,                /* Connection handle */
//  const char *zDbName,        /* Database name or NULL */
//  const char *zTableName,     /* Table name */
//  const char *zColumnName,    /* Column name */
//  char const **pzDataType,    /* OUTPUT: Declared data type */
//  char const **pzCollSeq,     /* OUTPUT: Collation sequence name */
//  int *pNotNull,              /* OUTPUT: True if NOT NULL constraint exists */
//  int *pPrimaryKey,           /* OUTPUT: True if column part of PK */
//  int *pAutoinc               /* OUTPUT: True if column is auto-increment */
//);
//int sqlite3_limit(sqlite3*, int id, int newVal);
//void *sqlite3_commit_hook(sqlite3*, int(*)(void*), void*);
//void *sqlite3_rollback_hook(sqlite3*, void(*)(void *), void*);

//typedef struct sqlite3_backup sqlite3_backup;
//SQLITE_API sqlite3_backup *sqlite3_backup_init(
//  sqlite3 *pDest,                        /* Destination database handle */
//  const char *zDestName,                 /* Destination database name */
//  sqlite3 *pSource,                      /* Source database handle */
//  const char *zSourceName                /* Source database name */
//);
//SQLITE_API int sqlite3_backup_step(sqlite3_backup *p, int nPage);
//SQLITE_API int sqlite3_backup_finish(sqlite3_backup *p);
//SQLITE_API int sqlite3_backup_remaining(sqlite3_backup *p);
//SQLITE_API int sqlite3_backup_pagecount(sqlite3_backup *p);


/*
** All other functions are not supported
*/