%module sqlite4java
%{
#include "../sqlite/sqlite3.h"
%}

const char *sqlite3_libversion();
int sqlite3_libversion_number();
int sqlite3_threadsafe();

int sqlite3_close(sqlite3* db);

%{
struct HH {
  
}
static int global_callback(void* arg, int columnsNumber, char** columns, char** columnNames) {
  struct HH *hh = (struct HH*)arg;
  // ...
  return 0;
}
%}

%typemap(in) (int (*callback)(void*,int,char**,char**), void* arg) {
  $1 = xxx;
  $2 = yyy;
}

int sqlite3_exec(sqlite3* db, const char *sql,
      int (*callback)(void*,int,char**,char**),  
      void* arg,                                 
	  char **errmsg                              
);
          
