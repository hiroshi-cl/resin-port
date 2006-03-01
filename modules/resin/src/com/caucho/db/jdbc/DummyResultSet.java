/*
 * Copyright (c) 1998-2006 Caucho Technology -- all rights reserved
 *
 * This file is part of Resin(R) Open Source
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Resin Open Source is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * Resin Open Source is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, or any warranty
 * of NON-INFRINGEMENT.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Resin Open Source; if not, write to the
 *   Free SoftwareFoundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.db.jdbc;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.regex.*;
import java.math.*;

import com.caucho.util.*;
import com.caucho.vfs.*;

class DummyResultSet extends AbstractResultSet {
  private static Pattern _csvRegexp;
  
  private ArrayList<DummyColumn> _dummyColumns = new ArrayList<DummyColumn>();
  private ArrayList<DummyRow> _dummyRows = new ArrayList<DummyRow>();
  private int i;

  static {
    try {
      _csvRegexp = Pattern.compile(":");
    } catch (Exception e) {
    }
  }

  public void addColumn(String name, int type)
  {
    _dummyColumns.add(new DummyColumn(name, type));
  }

  public int getColumnCount()
  {
    return _dummyColumns.size();
  }

  public void startRow()
  {
    DummyRow row = new DummyRow();
    
    _dummyRows.add(row);
  }
  
  public DummyResultSet add(String data)
  {
    DummyRow row = _dummyRows.get(_dummyRows.size() - 1);
    
    row.add(data);

    return this;
  }
  
  public DummyResultSet add(int data)
  {
    DummyRow row = _dummyRows.get(_dummyRows.size() - 1);
    
    row.add(String.valueOf(data));

    return this;
  }
  
  public DummyResultSet add()
  {
    DummyRow row = _dummyRows.get(_dummyRows.size() - 1);
    
    row.add("");

    return this;
  }

  public void addRow(String csv)
  {
    startRow();
    String []values;
    synchronized (_csvRegexp) {
      values = _csvRegexp.split(csv);
    }

    for (int i = 0; i < values.length; i++)
      add(values[i]);
  }
  
  protected InputStream getInputStream(int columnIndex)
  {
    throw new UnsupportedOperationException();
  }

  protected long getDateAsLong(int columnIndex)
  {
    return 0;
  }

  public int findColumn(String name)
  {
    for (int i = 0; i < _dummyColumns.size(); i++) {
      DummyColumn column = _dummyColumns.get(i);

      if (column.getColumnName().equals(name))
	return i;
    }

    return -1;
  }

  public boolean wasNull()
  {
    return false;
  }

  public String getString(int column)
  {
    DummyRow row = _dummyRows.get(i);

    return row.getString(column);
  }

  public ResultSetMetaData getMetaData()
  {
    return null;
  }

  public Statement getStatement()
  {
    return null;
  }

  public boolean next()
    throws SQLException
  {
    if (_dummyRows.size() <= i)
      return false;

    i++;

    return true;
  }

  static class DummyColumn {
    String _name;
    int _type;

    DummyColumn(String name, int type)
    {
      _name = name;
      _type = type;
    }

    public String getColumnName()
    {
      return _name;
    }

    public int getColumnType()
    {
      return _type;
    }
  
    public String getColumnTypeName()
    {
      return "VARCHAR";
    }
  }

  static class DummyRow {
    private ArrayList<String> _values = new ArrayList<String>();

    DummyRow()
    {
    }

    void add(String data)
    {
      _values.add(data);
    }

    protected String getString(int columnIndex)
    {
      return _values.get(columnIndex);
    }
  }
}
