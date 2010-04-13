import org.apache.tools.ant.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TaskSvnVersion extends Task {
  private String myPropertyName = "svn.version";
  private String myDirectory = ".";

  public void setProperty(String propertyName) {
    myPropertyName = propertyName;
  }

  public void setDir(String directory) {
    myDirectory = directory;
  }

  public void execute() throws BuildException {
    String info = exec("svn", "info", myDirectory);
    Matcher matcher = Pattern.compile("Revision: (\\d+)").matcher(info);
    String version = "0";
    if (matcher.find()) {
      version = matcher.group(1);
      String status = exec("svn", "status", myDirectory).trim();
      if (status.length() > 0) {
        String[] lines = status.split("\n+");
        boolean unclear = false;
        for (String line : lines) {
          if (line.length() > 0 && !line.startsWith("?")) {
            unclear = true;
            break;
          }
        }
        if (unclear) {
          getProject().log(this + ": not clear status [\n" + status + "\n]", Project.MSG_WARN);
          version += "+";
        }
      }
    }
    getProject().setProperty(myPropertyName, version);
  }

  private static String exec(String... cmd) {
    try {
      ProcessBuilder pb = new ProcessBuilder(cmd);
      pb.redirectErrorStream(true);
      Process p = pb.start();
      BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
      StringBuilder sb = new StringBuilder();
      String s;
      while (true) {
        s = reader.readLine();
        if (s == null) break;
        else sb.append(s).append('\n');
      }
      reader.close();
      p.waitFor();
      return sb.toString();
    } catch (IOException e) {
      e.printStackTrace();
      return "";
    } catch (InterruptedException e) {
      e.printStackTrace();
      return "";
    }
  }
}
