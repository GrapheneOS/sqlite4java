package com.almworks.sqlite4java;

import junit.framework.TestCase;

import java.util.logging.Level;
import java.util.logging.Logger;

// see http://code.google.com/p/sqlite4java/issues/detail?id=18
public class RegressionIssue18Tests extends TestCase {
  private volatile boolean disposeCalled;

  public void testConcurrentDisposeCrash() throws SQLiteException {
    Logger.getLogger("com.almworks.sqlite4java").setLevel(Level.SEVERE);
    for (int i = 0; i < 50; i++) {
      final SQLiteConnection c = new SQLiteConnection().open();
      disposeCalled = false;
      new Thread() {
        @Override
        public void run() {
          try {
            Thread.sleep(50);
            c.dispose();
          } catch (Throwable e) {
          } finally {
            disposeCalled = true;
          }
        }
      }.start();
      try {
        while (!disposeCalled) {
          // we need sufficiently long method
          c.createArray();
        }
      } catch (SQLiteException e) {
        // ok
      }
    }
  }
}
