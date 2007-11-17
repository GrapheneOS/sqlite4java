package sqlite;

import java.util.logging.Logger;

public final class DBGlobal {
  static final Logger logger = Logger.getLogger("sqlite");
  static int lastConnectionNumber = 0;

  static void recoverableError(Object source, String message, boolean throwAssertion) {
    message = source + " " + message;
    assert !throwAssertion : message;
    logger.warning(message);
  }
}
