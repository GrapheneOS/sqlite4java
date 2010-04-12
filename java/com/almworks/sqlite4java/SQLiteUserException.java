package com.almworks.sqlite4java;

public class SQLiteUserException extends SQLiteException {
  public SQLiteUserException() {
    super(SQLiteConstants.Wrapper.WRAPPER_USER_ERROR, "");
  }

  public SQLiteUserException(String errorMessage) {
    super(SQLiteConstants.Wrapper.WRAPPER_USER_ERROR, errorMessage);
  }

  public SQLiteUserException(String errorMessage, Throwable cause) {
    super(SQLiteConstants.Wrapper.WRAPPER_USER_ERROR, errorMessage, cause);
  }
}
