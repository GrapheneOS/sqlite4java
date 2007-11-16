package sqlite;

import java.util.logging.Logger;

public class DBException extends Exception {
  private static final Logger logger = Logger.getLogger("sqlite");

  private final int myErrorCode;

  public DBException(int errorCode, String errorMessage) {
    super("[" + errorCode + "] " + (errorMessage == null ? "sqlite error" : errorMessage));
    myErrorCode = errorCode;
    logger.warning(getMessage());
  }

  public int getErrorCode() {
    return myErrorCode;
  }
}
