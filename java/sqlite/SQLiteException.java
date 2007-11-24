package sqlite;

public class SQLiteException extends Exception {
  private final int myErrorCode;

  public SQLiteException(int errorCode, String errorMessage) {
    this(errorCode, errorMessage, null);
  }

  public SQLiteException(int errorCode, String errorMessage, Throwable cause) {
    super("[" + errorCode + "] " + (errorMessage == null ? "sqlite error" : errorMessage), cause);
    myErrorCode = errorCode;
    if (Internal.isFineLogging()) {
      Internal.logFine(getClass(), getMessage());
    }
  }

  public int getErrorCode() {
    return myErrorCode;
  }
}
