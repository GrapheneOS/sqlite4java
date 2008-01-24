package sqlite;

import java.util.ArrayList;
import java.util.List;

public class SQLParts {
  private final List<String> myParts;
  private int myHash;

  public SQLParts() {
    myParts = new ArrayList<String>(5);
  }

  public SQLParts(SQLParts copyFrom) {
    myParts = new ArrayList<String>(copyFrom.myParts.size());
    myParts.addAll(copyFrom.myParts);
  }

  public SQLParts(String sql) {
    myParts = new ArrayList<String>(1);
    append(sql);
  }

  public int hashCode() {
    if (myHash == 0)
      myHash = calcHash();
    return myHash;
  }

  private int calcHash() {
    int r = 0;
    for (int i = 0; i < myParts.size(); i++)
      r = 31 * r + myParts.get(i).hashCode();
    return r;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    List<String> other = ((SQLParts) o).myParts;
    if (myParts.size() != other.size())
      return false;
    for (int i = 0; i < myParts.size(); i++)
      if (!myParts.get(i).equals(other.get(i)))
        return false;
    return true;
  }

  public void clear() {
    myParts.clear();
    myHash = 0;
  }

  public void append(String part) {
    if (part != null && part.length() > 0) {
      myParts.add(part);
      myHash = 0;
    }
  }

  public String toString() {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < myParts.size(); i++) {
      builder.append(myParts.get(i));
    }
    return builder.toString();
  }
}
