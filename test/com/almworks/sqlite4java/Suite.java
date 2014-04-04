package com.almworks.sqlite4java;

import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.io.File;
import java.io.FileFilter;
import java.net.URL;

/**
 * @author Igor Sereda
 */
public class Suite extends TestCase {
  private static void addTo(TestSuite suite) {
    File dir = getTestDirectory();

    File[] files = dir.listFiles(new FileFilter() {
      public boolean accept(File pathname) {
        return pathname.getName().endsWith("Tests.class") || pathname.getName().endsWith("Test.class");
      }
    });
    if (files == null) throw new AssertionError("no tests");
    for (File f : files) {
      String name = f.getName();
      String className = Suite.class.getPackage().getName() + "." + name.substring(0, name.length() - 6);
      try {
        Class testClass = Suite.class.getClassLoader().loadClass(className);
        if (testClass.getName().equals(Suite.class.getName())) continue;
        if (TestCase.class.isAssignableFrom(testClass)) {
          suite.addTestSuite(testClass);
        }
      } catch (Exception e) {
        System.err.println("cannot load " + className);
        e.printStackTrace();
      }
    }
  }

  private static File getTestDirectory() {
    String file = Suite.class.getName().replace('.', '/') + ".class";
    URL resource = Suite.class.getClassLoader().getResource(file);
    if (resource == null) throw new AssertionError();
    String url = resource.toExternalForm();
    if (!url.startsWith("file:/")) throw new AssertionError(url);
    url = url.substring(6);
    if (!System.getProperty("os.name").startsWith("windows")) {
      url = "/" + url;
    }
    String name = "/" + Suite.class.getSimpleName() + ".class";
    if (!url.endsWith(name)) throw new AssertionError(url);
    url = url.substring(0, url.length() - name.length());
    File dir = new File(url);
    if (!dir.isDirectory()) throw new AssertionError(dir);
    return dir;
  }

  public static TestSuite suite() {
    TestSuite testSuite = new TestSuite();
    addTo(testSuite);
    return testSuite;
  }
}
