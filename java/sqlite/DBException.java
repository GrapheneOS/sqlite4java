package sqlite;

import java.util.logging.Logger;

public class DBException extends Exception {
  private static final Logger logger = Logger.getLogger("sqlite");

  private final int myErrorCode;

  public DBException(int errorCode, String errorMessage) {
    this(errorCode, errorMessage, null);
  }

  public DBException(int errorCode, String errorMessage, Throwable cause) {
    super("[" + errorCode + "] " + (errorMessage == null ? "sqlite error" : errorMessage), cause);
    myErrorCode = errorCode;
    logger.warning(getMessage());
  }

  public int getErrorCode() {
    return myErrorCode;
  }
}
