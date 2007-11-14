#include <sqlite3.h>
#include "sqlite3_swig.h"

//struct DBHandle {
//  sqlite3* db;
//  int error_code;
//}

void sqlite3_open_v2_wr(
  const char *filename,
  int flags,
  DBHandle* handleOut)
{
  handleOut->error_code = sqlite3_open_v2(filename, &(handleOut->db), flags, NULL);
}

