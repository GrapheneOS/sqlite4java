%module _SQLiteSwigged
%pragma(java) moduleclassmodifiers="class"
%typemap(javaclassmodifiers)   SWIGTYPE, SWIGTYPE *, SWIGTYPE &, SWIGTYPE [], SWIGTYPE (CLASS::*) "class"
//%include "various.i"

/*
 * char **SQLITE3_STRING_OUT typemaps.
 * These are typemaps for returning strings when using a C char ** parameter type in sqlite3.
 * The returned string appears in the 1st element of the passed in Java String array.
 * Original string will be freed by sqlite3_free after copying it's content to array.
 *
 * Example usage wrapping:
 *   void foo(char **string_out);
 *
 * Java usage:
 *   String stringOutArray[] = { "" };
 *   modulename.foo(stringOutArray);
 *   System.out.println( stringOutArray[0] );
 */

%typemap(jni) char **SQLITE3_STRING_OUT "jobjectArray"
%typemap(jtype) char **SQLITE3_STRING_OUT "String[]"
%typemap(jstype) char **SQLITE3_STRING_OUT "String[]"
%typemap(javain) char **SQLITE3_STRING_OUT "$javainput"

%typemap(in) char **SQLITE3_STRING_OUT($*1_ltype temp) {
  if (!$input) {
    SWIG_JavaThrowException(jenv, SWIG_JavaNullPointerException, "array null");
    return $null;
  }
  if (JCALL1(GetArrayLength, jenv, $input) == 0) {
    SWIG_JavaThrowException(jenv, SWIG_JavaIndexOutOfBoundsException, "Array must contain at least 1 element");
    return $null;
  }
  $1 = &temp;
}

%typemap(argout) char **SQLITE3_STRING_OUT {
  jstring jnewstring = NULL;
  if ($1) {
     jnewstring = JCALL1(NewStringUTF, jenv, *$1);
     sqlite3_free(*$1);
  }
  JCALL3(SetObjectArrayElement, jenv, $input, 0, jnewstring);
}

%{
#include <sqlite3.h>
%}





%include "sqlite3_swigged.h"
