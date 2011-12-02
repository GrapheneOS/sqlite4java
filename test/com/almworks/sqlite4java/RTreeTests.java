package com.almworks.sqlite4java;


public class RTreeTests extends SQLiteConnectionFixture {

  public void testRTree() throws SQLiteException {
    SQLiteConnection connection = memDb().open();
    connection.exec("create virtual table rtree_example using rtree(id, minX, maxX, minY, maxY)");
    final int count = 1234;
    Box surroundingBox = new Box(-100, 100, -100, 100);
    fillRTree(connection, count, surroundingBox);
    int realCount = countObjectsInBox(connection, surroundingBox);
    assertEquals(count, realCount);
  }

  private void fillRTree(SQLiteConnection connection, int count, Box surroundingBox) throws SQLiteException {
    SQLiteStatement fillStatement = connection.prepare("insert into rtree_example values (?,?,?,?,?)");

    for (int i = 0; i < count; i++) {
      Box newNode = generateNode(surroundingBox);
      fillStatement.bind(1, i);
      fillStatement.bind(2, newNode.getMinX());
      fillStatement.bind(3, newNode.getMaxX());
      fillStatement.bind(4, newNode.getMinY());
      fillStatement.bind(5, newNode.getMaxY());
      fillStatement.step();
      fillStatement.reset();
    }

    fillStatement.dispose();
  }

  private Box generateNode(Box surroundingBox) {
    double nodeHeight = surroundingBox.getHeight() / 1e6;
    double nodeWidth = surroundingBox.getWidth() / 1e6;

    double newCenterX = Math.random() * surroundingBox.getWidth() + surroundingBox.getMinX();
    double newCenterY = Math.random() * surroundingBox.getHeight() + surroundingBox.getMinY();

    return new Box(newCenterX - nodeWidth, newCenterX + nodeWidth, newCenterY - nodeHeight, newCenterY + nodeHeight);
  }

  private int countObjectsInBox(SQLiteConnection connection, Box surroundingBox) throws SQLiteException {
    SQLiteStatement selectStatement = connection.prepare("select count(*) from rtree_example where minX >= ? and maxX <= ? and minY >=? and maxY <= ?");

    selectStatement.bind(1, surroundingBox.getMinX());
    selectStatement.bind(2, surroundingBox.getMaxX());
    selectStatement.bind(3, surroundingBox.getMinY());
    selectStatement.bind(4, surroundingBox.getMaxY());

    selectStatement.step();
    int count = selectStatement.columnInt(0);
    selectStatement.dispose();

    return count;
  }

  private class Box {

    double myMinX;
    double myMinY;
    double myMaxX;
    double myMaxY;

    Box(double minX, double maxX, double minY, double maxY) {
      myMinX = minX;
      myMinY = minY;
      myMaxX = maxX;
      myMaxY = maxY;
    }

    public double getMinX() {
      return myMinX;
    }

    public double getMinY() {
      return myMinY;
    }

    public double getMaxX() {
      return myMaxX;
    }

    public double getMaxY() {
      return myMaxY;
    }

    public double getWidth() {
      return myMaxX - myMinX;
    }

    public double getHeight() {
      return myMaxY - myMinY;
    }
  }
}
