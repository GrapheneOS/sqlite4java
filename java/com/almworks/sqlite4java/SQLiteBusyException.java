package com.almworks.sqlite4java;

public class SQLiteBusyException extends SQLiteException {
  public SQLiteBusyException(int errorCode, String errorMessage) {
    super(errorCode, errorMessage);
    assert errorCode == SQLiteConstants.Result.SQLITE_BUSY || errorCode == SQLiteConstants.Result.SQLITE_IOERR_BLOCKED : errorCode;
  }
}
