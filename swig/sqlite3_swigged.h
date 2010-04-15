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
typedef struct sqlite3 sqlite3;
typedef struct sqlite3_stmt sqlite3_stmt;
typedef struct sqlite3_blob sqlite3_blob;

int sqlite3_initialize(void);
int sqlite3_shutdown(void);
int sqlite3_extended_errcode(sqlite3 *db);
const char *sqlite3_libversion(void);
int sqlite3_libversion_number(void);
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
void sqlite3_soft_heap_limit(int);
int sqlite3_blob_close(sqlite3_blob *);
int sqlite3_blob_bytes(sqlite3_blob *);

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

/*
** ******************************************************************
** 4. Declarations that will not be supported.
** ******************************************************************
*/

// typedef struct sqlite3_file sqlite3_file;
// typedef struct sqlite3_io_methods sqlite3_io_methods;
// typedef struct sqlite3_mutex sqlite3_mutex;
// typedef struct sqlite3_vfs sqlite3_vfs;


// int sqlite3_complete16(const void *sql);

// int sqlite3_get_table(
//  sqlite3*,              /* An open database */
//  const char *sql,       /* SQL to be executed */
//  char ***resultp,       /* Result written to a char *[]  that this points to */
//  int *nrow,             /* Number of result rows written here */
//  int *ncolumn,          /* Number of result columns written here */
//  char **errmsg          /* Error msg written here */
// );
// void sqlite3_free_table(char **result);
// char *sqlite3_mprintf(const char*,...);
// char *sqlite3_vmprintf(const char*, va_list);
// char *sqlite3_snprintf(int,char*,const char*, ...);
// void *sqlite3_malloc(int);
// void *sqlite3_realloc(void*, int);
// void sqlite3_free(void*);
// int sqlite3_set_authorizer(
//  sqlite3*,
//  int (*xAuth)(void*,int,const char*,const char*,const char*,const char*),
//  void *pUserData
// );
// int sqlite3_open16(
//   const void *filename,   /* Database filename (UTF-16) */
//   sqlite3 **ppDb          /* OUT: SQLite db handle */
// );
// int sqlite3_open_v2(
//   const char *filename,   /* Database filename (UTF-8) */
//   sqlite3 **ppDb,         /* OUT: SQLite db handle */
//   int flags,              /* Flags */
//   const char *zVfs        /* Name of VFS module to use */
// );
// const void *sqlite3_errmsg16(sqlite3*);
//int sqlite3_prepare(
//  sqlite3 *db,            /* Database handle */
//  const char *zSql,       /* SQL statement, UTF-8 encoded */
//  int nByte,              /* Maximum length of zSql in bytes. */
//  sqlite3_stmt **ppStmt,  /* OUT: Statement handle */
//  const char **pzTail     /* OUT: Pointer to unused portion of zSql */
//);
//int sqlite3_prepare16(
//  sqlite3 *db,            /* Database handle */
//  const void *zSql,       /* SQL statement, UTF-16 encoded */
//  int nByte,              /* Maximum length of zSql in bytes. */
//  sqlite3_stmt **ppStmt,  /* OUT: Statement handle */
//  const void **pzTail     /* OUT: Pointer to unused portion of zSql */
//);
//int sqlite3_prepare16_v2(
//  sqlite3 *db,            /* Database handle */
//  const void *zSql,       /* SQL statement, UTF-16 encoded */
//  int nByte,              /* Maximum length of zSql in bytes. */
//  sqlite3_stmt **ppStmt,  /* OUT: Statement handle */
//  const void **pzTail     /* OUT: Pointer to unused portion of zSql */
//);

// typedef struct Mem sqlite3_value;
// typedef struct sqlite3_context sqlite3_context;
// int sqlite3_bind_text16(sqlite3_stmt*, int, const void*, int, void(*)(void*));
// int sqlite3_bind_value(sqlite3_stmt*, int, const sqlite3_value*);
// const void *sqlite3_column_name16(sqlite3_stmt*, int N);
// const void *sqlite3_column_decltype16(sqlite3_stmt*,int);
// const void *sqlite3_column_text16(sqlite3_stmt*, int iCol);
// sqlite3_value *sqlite3_column_value(sqlite3_stmt*, int iCol);
//int sqlite3_create_function(
//  sqlite3 *,
//  const char *zFunctionName,
//  int nArg,
//  int eTextRep,
//  void*,
//  void (*xFunc)(sqlite3_context*,int,sqlite3_value**),
//  void (*xStep)(sqlite3_context*,int,sqlite3_value**),
//  void (*xFinal)(sqlite3_context*)
//);
//int sqlite3_create_function16(
//  sqlite3*,
//  const void *zFunctionName,
//  int nArg,
//  int eTextRep,
//  void*,
//  void (*xFunc)(sqlite3_context*,int,sqlite3_value**),
//  void (*xStep)(sqlite3_context*,int,sqlite3_value**),
//  void (*xFinal)(sqlite3_context*)
//);
//int sqlite3_aggregate_count(sqlite3_context*);
//int sqlite3_expired(sqlite3_stmt*);
//int sqlite3_transfer_bindings(sqlite3_stmt*, sqlite3_stmt*);
//int sqlite3_global_recover(void);
//void sqlite3_thread_cleanup(void);
//int sqlite3_memory_alarm(void(*)(void*,sqlite3_int64,int),void*,sqlite3_int64);
//const void *sqlite3_value_blob(sqlite3_value*);
//int sqlite3_value_bytes(sqlite3_value*);
//int sqlite3_value_bytes16(sqlite3_value*);
//double sqlite3_value_double(sqlite3_value*);
//int sqlite3_value_int(sqlite3_value*);
//sqlite3_int64 sqlite3_value_int64(sqlite3_value*);
//const unsigned char *sqlite3_value_text(sqlite3_value*);
//const void *sqlite3_value_text16(sqlite3_value*);
//const void *sqlite3_value_text16le(sqlite3_value*);
//const void *sqlite3_value_text16be(sqlite3_value*);
//int sqlite3_value_type(sqlite3_value*);
//int sqlite3_value_numeric_type(sqlite3_value*);
//void *sqlite3_aggregate_context(sqlite3_context*, int nBytes);
// void *sqlite3_user_data(sqlite3_context*);
// void *sqlite3_get_auxdata(sqlite3_context*, int);
// void sqlite3_set_auxdata(sqlite3_context*, int, void*, void (*)(void*));
// void sqlite3_result_blob(sqlite3_context*, const void*, int, void(*)(void*));
// void sqlite3_result_double(sqlite3_context*, double);
// void sqlite3_result_error(sqlite3_context*, const char*, int);
//void sqlite3_result_error16(sqlite3_context*, const void*, int);
//void sqlite3_result_error_toobig(sqlite3_context*);
//void sqlite3_result_error_nomem(sqlite3_context*);
//void sqlite3_result_int(sqlite3_context*, int);
//void sqlite3_result_int64(sqlite3_context*, sqlite3_int64);
//void sqlite3_result_null(sqlite3_context*);
//void sqlite3_result_text(sqlite3_context*, const char*, int, void(*)(void*));
//void sqlite3_result_text16(sqlite3_context*, const void*, int, void(*)(void*));
//void sqlite3_result_text16le(sqlite3_context*, const void*, int,void(*)(void*));
//void sqlite3_result_text16be(sqlite3_context*, const void*, int,void(*)(void*));
//void sqlite3_result_value(sqlite3_context*, sqlite3_value*);
//void sqlite3_result_zeroblob(sqlite3_context*, int n);
//int sqlite3_create_collation(
//  sqlite3*,
//  const char *zName,
//  int eTextRep,
//  void*,
//  int(*xCompare)(void*,int,const void*,int,const void*)
//);
//int sqlite3_create_collation_v2(
//  sqlite3*,
//  const char *zName,
//  int eTextRep,
//  void*,
//  int(*xCompare)(void*,int,const void*,int,const void*),
//  void(*xDestroy)(void*)
//);
//int sqlite3_create_collation16(
//  sqlite3*,
//  const char *zName,
//  int eTextRep,
//  void*,
//  int(*xCompare)(void*,int,const void*,int,const void*)
//);
//int sqlite3_collation_needed(
//  sqlite3*,
//  void*,
//  void(*)(void*,sqlite3*,int eTextRep,const char*)
//);
//int sqlite3_collation_needed16(
//  sqlite3*,
//  void*,
//  void(*)(void*,sqlite3*,int eTextRep,const void*)
//);
//int sqlite3_key(
//  sqlite3 *db,                   /* Database to be rekeyed */
//  const void *pKey, int nKey     /* The key */
//);
//int sqlite3_rekey(
//  sqlite3 *db,                   /* Database to be rekeyed */
//  const void *pKey, int nKey     /* The new key */
//);
// int sqlite3_sleep(int);
//void *sqlite3_commit_hook(sqlite3*, int(*)(void*), void*);
//void *sqlite3_rollback_hook(sqlite3*, void(*)(void *), void*);
//void *sqlite3_update_hook(
//  sqlite3*,
//  void(*)(void *,int ,char const *,char const *,sqlite3_int64),
//  void*
//);
//int sqlite3_load_extension(
//  sqlite3 *db,          /* Load the extension into this database connection */
//  const char *zFile,    /* Name of the shared library containing extension */
//  const char *zProc,    /* Entry point.  Derived from zFile if 0 */
//  char **pzErrMsg       /* Put error message here if not 0 */
//);
// int sqlite3_enable_load_extension(sqlite3 *db, int onoff);
//int sqlite3_auto_extension(void *xEntryPoint);
//void sqlite3_reset_auto_extension(void);
//typedef struct sqlite3_vtab sqlite3_vtab;
//typedef struct sqlite3_index_info sqlite3_index_info;
//typedef struct sqlite3_vtab_cursor sqlite3_vtab_cursor;
//typedef struct sqlite3_module sqlite3_module;
//int sqlite3_create_module(
//  sqlite3 *db,               /* SQLite connection to register module with */
//  const char *zName,         /* Name of the module */
//  const sqlite3_module *,    /* Methods for the module */
//  void *                     /* Client data for xCreate/xConnect */
//);
//int sqlite3_create_module_v2(
//  sqlite3 *db,               /* SQLite connection to register module with */
//  const char *zName,         /* Name of the module */
//  const sqlite3_module *,    /* Methods for the module */
//  void *,                    /* Client data for xCreate/xConnect */
//  void(*xDestroy)(void*)     /* Module destructor function */
//);
//struct sqlite3_vtab {
//  const sqlite3_module *pModule;  /* The module for this virtual table */
//  int nRef;                       /* Used internally */
//  char *zErrMsg;                  /* Error message from sqlite3_mprintf() */
//  /* Virtual table implementations will typically add additional fields */
//};
//struct sqlite3_vtab_cursor {
//  sqlite3_vtab *pVtab;      /* Virtual table of this cursor */
//  /* Virtual table implementations will typically add additional fields */
//};
//int sqlite3_declare_vtab(sqlite3*, const char *zCreateTable);
//int sqlite3_overload_function(sqlite3*, const char *zFuncName, int nArg);
//sqlite3_vfs *sqlite3_vfs_find(const char *zVfsName);
//int sqlite3_vfs_register(sqlite3_vfs*, int makeDflt);
//int sqlite3_vfs_unregister(sqlite3_vfs*);
//sqlite3_mutex *sqlite3_mutex_alloc(int);
//void sqlite3_mutex_free(sqlite3_mutex*);
//void sqlite3_mutex_enter(sqlite3_mutex*);
//int sqlite3_mutex_try(sqlite3_mutex*);
//void sqlite3_mutex_leave(sqlite3_mutex*);
//int sqlite3_mutex_held(sqlite3_mutex*);
//int sqlite3_mutex_notheld(sqlite3_mutex*);
//int sqlite3_file_control(sqlite3*, const char *zDbName, int op, void*);
