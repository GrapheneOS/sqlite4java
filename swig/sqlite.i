%module _SQLiteSwigged
%pragma(java) moduleclassmodifiers="class"
%typemap(javaclassmodifiers)   SWIGTYPE, SWIGTYPE *, SWIGTYPE &, SWIGTYPE [], SWIGTYPE (CLASS::*) "class"

%{
#include <sqlite3.h>
%}

%include "sqlite3_swigged.h"
