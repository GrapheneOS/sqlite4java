#include <sqlite3ext.h>
SQLITE_EXTENSION_INIT1

static void halfFunc(sqlite3_context *context, int argc, sqlite3_value **argv ) {
  sqlite3_result_double(context, 0.5*sqlite3_value_double(argv[0]));
}

int sqlite3_extension_init(sqlite3 *db, char **pzErrMsg, const sqlite3_api_routines *pApi) {
  SQLITE_EXTENSION_INIT2(pApi)
  sqlite3_create_function(db, "half", 1, SQLITE_ANY, 0, halfFunc, 0, 0);
  return 0;
}

