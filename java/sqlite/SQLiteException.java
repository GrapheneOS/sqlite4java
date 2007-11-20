package sqlite;

import java.util.logging.Logger;

public class SQLiteException extends Exception {
  private static final Logger logger = Logger.getLogger("sqlite");

  private final int myErrorCode;

  public SQLiteException(int errorCode, String errorMessage) {
    this(errorCode, errorMessage, null);
  }

  public SQLiteException(int errorCode, String errorMessage, Throwable cause) {
    super("[" + errorCode + "] " + (errorMessage == null ? "sqlite error" : errorMessage), cause);
    myErrorCode = errorCode;
    logger.warning(getMessage());
  }

  public int getErrorCode() {
    return myErrorCode;
  }
}
