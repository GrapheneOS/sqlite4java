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

/**
 This file defines virtual table module for tables that represent a memory array.
 The module is loosely based on test_intarray.[ch] from SQLite distribution.
*/

#include "intarray.h"
#include <string.h>
#include <assert.h>

#define MODULE_NAME "INTARRAY"

/* Objects used internally by the virtual table implementation */
typedef struct intarray_vtab intarray_vtab;
typedef struct intarray_cursor intarray_cursor;

struct sqlite3_intarray_module {
  /* link to the sqlite session */
  sqlite3 *db;

  /* link to the array that's being initialized - so that pointer to the created 
  ** vtable may be stored there */
  sqlite3_intarray *initializingArray;
};

/*
** Definition of the sqlite3_intarray object.
**
** The internal representation of an intarray object is subject
** to change, is not externally visible, and should be used by
** the implementation of intarray only.  This object is opaque
** to users.
*/
struct sqlite3_intarray {
  sqlite3_intarray_module *module;
  const char *zName;

  /* when virtual table is created, it writes pointer to itself here
     when it is destroyed, the pointer is cleared */
  intarray_vtab *table; 
};

/* A intarray table object */
struct intarray_vtab {
  /* base class */
  sqlite3_vtab base;            

  sqlite3_intarray *pHandle; 
  int n;                    /* Number of elements in the array */
  sqlite3_int64 *a;         /* Contents of the array */
  void (*xFree)(void*);     /* Function used to free a[] */
  int ordered;              /* If true, the elements in a[] are guaranteed to be ordered */
  int unique;               /* If true, the elements in a[] are guaranteed to be unique */
  int useCount;             /* Number of open cursors */
};

/* A intarray cursor object */
struct intarray_cursor {
  sqlite3_vtab_cursor base;    /* Base class */
  int i;                       /* Current cursor position */
};

/* clear data from vtable */
int drop_vtable_content(intarray_vtab *table) {
  if (!table) return SQLITE_OK;
  if (table->useCount) return INTARRAY_INUSE;
  if (table->xFree) table->xFree(table->a);
  table->xFree = 0;
  table->a = 0;
  table->n = 0;
  table->ordered = 0;
  table->unique = 0;
  return SQLITE_OK;
}

/* create a new vtable */
int create_vtable(sqlite3_intarray *pIntArray) {
  int rc = SQLITE_OK;
  char *zSql;
  zSql = sqlite3_mprintf("CREATE VIRTUAL TABLE temp.%Q USING INTARRAY", pIntArray->zName);
  pIntArray->module->initializingArray = pIntArray;
  rc = sqlite3_exec(pIntArray->module->db, zSql, 0, 0, 0);
  pIntArray->module->initializingArray = 0;
  sqlite3_free(zSql);
  return rc;
}

/* drop vtable - clears pIntArray->table through intarrayDestroy method */
int drop_vtable(sqlite3_intarray *pIntArray) {
  int rc = SQLITE_OK;
  char *zSql;
  zSql = sqlite3_mprintf("DROP TABLE IF EXISTS temp.%Q", pIntArray->zName);
  rc = sqlite3_exec(pIntArray->module->db, zSql, 0, 0, 0);
  sqlite3_free(zSql);
  return rc;
}


/*
** None of this works unless we have virtual tables.
*/
#ifndef SQLITE_OMIT_VIRTUALTABLE

/*
** Table destructor for the intarray module.
*/
static int intarrayDestroy(sqlite3_vtab *p) {
  intarray_vtab *table = *((intarray_vtab**)&p);
  int rc = drop_vtable_content(table);
  if (rc != SQLITE_OK) return rc;
  if (table->pHandle) table->pHandle->table = 0;
  sqlite3_free(table);
  return SQLITE_OK;
}

/*
** Table constructor for the intarray module.
*/
static int intarrayCreate(sqlite3 *db, void *pAux, int argc, const char *const*argv, sqlite3_vtab **ppVtab, char **pzErr) {
  int rc = SQLITE_NOMEM;
  sqlite3_intarray_module *module = *((sqlite3_intarray_module**)&pAux);
  intarray_vtab *table;

  if (!module) return INTARRAY_INITERR;
  if (!module->initializingArray) {
    *pzErr = sqlite3_mprintf("INTARRAY tables can be created through API only");
    return SQLITE_ERROR;
  }

  table = (intarray_vtab*)sqlite3_malloc(sizeof(intarray_vtab));
  if (table) {
    memset(table, 0, sizeof(intarray_vtab));
    table->pHandle = module->initializingArray;
    module->initializingArray->table = table;
    rc = sqlite3_declare_vtab(db, "CREATE TABLE x(value INTEGER)");
  }
  *ppVtab = (sqlite3_vtab *)table;
  return rc;
}

/*
** Open a new cursor on the intarray table.
*/
static int intarrayOpen(sqlite3_vtab *pVTab, sqlite3_vtab_cursor **ppCursor){
  int rc = SQLITE_NOMEM;
  intarray_cursor *pCur;
  pCur = (intarray_cursor*)sqlite3_malloc(sizeof(intarray_cursor));
  if (pCur) {
    memset(pCur, 0, sizeof(intarray_cursor));
    *ppCursor = (sqlite3_vtab_cursor *)pCur;
    ((intarray_vtab*)pVTab)->useCount++;
    rc = SQLITE_OK;
  }
  return rc;
}

/*
** Close a intarray table cursor.
*/
static int intarrayClose(sqlite3_vtab_cursor *cur){
  intarray_cursor *pCur = (intarray_cursor *)cur;
  ((intarray_vtab*)(cur->pVtab))->useCount--;
  sqlite3_free(pCur);
  return SQLITE_OK;
}

/*
** Retrieve a column of data.
*/
static int intarrayColumn(sqlite3_vtab_cursor *cur, sqlite3_context *ctx, int column) {
  intarray_cursor *pCur = (intarray_cursor*)cur;
  intarray_vtab *table = (intarray_vtab*)cur->pVtab;
  if (pCur->i >= 0 && pCur->i < table->n) {
    sqlite3_result_int64(ctx, table->a[pCur->i]);
  }
  return SQLITE_OK;
}

/*
** Retrieve the current rowid.
*/
static int intarrayRowid(sqlite3_vtab_cursor *cur, sqlite_int64 *pRowid){
  intarray_cursor *pCur = (intarray_cursor *)cur;
  *pRowid = pCur->i;
  return SQLITE_OK;
}

static int intarrayEof(sqlite3_vtab_cursor *cur){
  intarray_cursor *pCur = (intarray_cursor *)cur;
  intarray_vtab *table = (intarray_vtab *)cur->pVtab;
  return pCur->i >= table->n;
}

/*
** Advance the cursor to the next row.
*/
static int intarrayNext(sqlite3_vtab_cursor *cur){
  intarray_cursor *pCur = (intarray_cursor *)cur;
  pCur->i++;
  return SQLITE_OK;
}

/*
** Reset a intarray table cursor.
*/
static int intarrayFilter(sqlite3_vtab_cursor *pVtabCursor, int idxNum, const char *idxStr, int argc, sqlite3_value **argv) {
  intarray_cursor *pCur = (intarray_cursor *)pVtabCursor;
  pCur->i = 0;
  return SQLITE_OK;
}

/*
** Analyse the WHERE condition.
*/
static int intarrayBestIndex(sqlite3_vtab *tab, sqlite3_index_info *pIdxInfo) {
  return SQLITE_OK;
}

/*
** A virtual table module that merely echos method calls into TCL
** variables.
*/
static sqlite3_module intarrayModule = {
  0,                           /* iVersion */
  intarrayCreate,              /* xCreate - create a new virtual table */
  intarrayCreate,              /* xConnect - connect to an existing vtab */
  intarrayBestIndex,           /* xBestIndex - find the best query index */
  intarrayDestroy,             /* xDisconnect - disconnect a vtab */
  intarrayDestroy,             /* xDestroy - destroy a vtab */
  intarrayOpen,                /* xOpen - open a cursor */
  intarrayClose,               /* xClose - close a cursor */
  intarrayFilter,              /* xFilter - configure scan constraints */
  intarrayNext,                /* xNext - advance a cursor */
  intarrayEof,                 /* xEof */
  intarrayColumn,              /* xColumn - read data */
  intarrayRowid,               /* xRowid - read data */
  0,                           /* xUpdate */
  0,                           /* xBegin */
  0,                           /* xSync */
  0,                           /* xCommit */
  0,                           /* xRollback */
  0,                           /* xFindMethod */
  0,                           /* xRename */
};

#endif /* !defined(SQLITE_OMIT_VIRTUALTABLE) */

int sqlite3_intarray_register(sqlite3 *db, sqlite3_intarray_module **ppReturn) {
  int rc = SQLITE_OK;
#ifndef SQLITE_OMIT_VIRTUALTABLE
  sqlite3_intarray_module *p;
  p = (sqlite3_intarray_module*)sqlite3_malloc(sizeof(*p));
  if (!p) return SQLITE_NOMEM;
  p->db = db;
  p->initializingArray = 0;
  rc = sqlite3_create_module_v2(db, MODULE_NAME, &intarrayModule, p, sqlite3_free);
  if (rc == SQLITE_OK) {
    *ppReturn = p;
  }
#endif
  return rc;
}

int sqlite3_intarray_create(sqlite3_intarray_module *module, const char *zName, sqlite3_intarray **ppReturn) {
  int rc = SQLITE_OK;
#ifndef SQLITE_OMIT_VIRTUALTABLE
  sqlite3_intarray *p;
  p = (sqlite3_intarray*)sqlite3_malloc(sizeof(*p));
  if (!p) return SQLITE_NOMEM;
  p->module = module;
  p->zName = zName;
  p->table = 0;
  rc = create_vtable(p);
  if (rc != SQLITE_OK) {
    sqlite3_free(p);
  } else {
    *ppReturn = p;
  }
#endif
  return rc;
}

int sqlite3_intarray_bind(sqlite3_intarray *pIntArray, int nElements, sqlite3_int64 *aElements, void (*xFree)(void*), int bOrdered, int bUnique) {
  int rc = SQLITE_OK;
#ifndef SQLITE_OMIT_VIRTUALTABLE
  intarray_vtab *table = pIntArray->table;
  if (!table) {
    rc = create_vtable(pIntArray);
    table = pIntArray->table;
  } else {
    rc = drop_vtable_content(table);
  }
  if (rc != SQLITE_OK) return rc;
  if (!table) return INTARRAY_NOTABLE;
  table->n = nElements;
  table->a = aElements;
  table->xFree = xFree;
  table->ordered = bOrdered;
  table->unique = bUnique;
#endif
  return rc;
}

int sqlite3_intarray_destroy(sqlite3_intarray *array) {
  int rc = SQLITE_OK;
#ifndef SQLITE_OMIT_VIRTUALTABLE
  intarray_vtab *table = array->table;
  if (table) {
    rc = drop_vtable(array);
  }
  sqlite3_free(array);
#endif
  return rc;
}


