package com.almworks.sqlite4java;

import static com.almworks.sqlite4java.SQLiteConstants.Result;

public class SQLiteBusyException extends SQLiteException {
  public SQLiteBusyException(int errorCode, String errorMessage) {
    super(errorCode, errorMessage);
    assert errorCode == Result.SQLITE_BUSY || errorCode == Result.SQLITE_IOERR_BLOCKED : errorCode;
  }
}
