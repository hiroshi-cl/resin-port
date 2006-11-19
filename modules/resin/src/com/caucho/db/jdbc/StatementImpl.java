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
 *
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */
package com.caucho.db.jdbc;

import java.io.*;

import java.util.*;

import java.util.logging.Logger;
import java.util.logging.Level;

import java.sql.*;
import javax.sql.*;

import com.caucho.util.L10N;

import com.caucho.vfs.Path;

import com.caucho.log.Log;

import com.caucho.db.Database;

import com.caucho.db.store.Transaction;

import com.caucho.db.sql.Query;
import com.caucho.db.sql.SelectQuery;
import com.caucho.db.sql.QueryContext;

/**
 * The JDBC statement implementation.
 */
public class StatementImpl implements java.sql.Statement {
  private final static L10N L = new L10N(StatementImpl.class);
  
  protected Database _db;
  protected final ConnectionImpl _conn;

  protected ResultSetImpl _rs;

  protected QueryContext _queryContext;
  
  private boolean _isClosed;
  
  StatementImpl(ConnectionImpl conn)
  {
    _conn = conn;
    _db = conn.getDatabase();

    if (_db == null)
      throw new NullPointerException();

    init();
  }

  protected void init()
  {
    if (_queryContext == null)
      _queryContext = QueryContext.allocate();
  }

  protected QueryContext getQueryContext()
  {
    if (_queryContext == null)
      _queryContext = QueryContext.allocate();

    return _queryContext;
  }

  public void addBatch(String sql)
  {
  }

  public void cancel()
  {
  }

  public void clearBatch()
  {
  }

  public void clearWarnings()
  {
  }

  public java.sql.ResultSet executeQuery(String sql)
    throws SQLException
  {
    if (_db == null)
      throw new SQLException(L.l("statement is closed"));
    
    Query query = _db.parseQuery(sql);

    return executeQuery(query);
  }
  
  private java.sql.ResultSet executeQuery(Query query)
    throws SQLException
  {
    Transaction xa = _conn.getTransaction();
    
    boolean isOkay = false;
    try {
      query.execute(_queryContext, xa);
      isOkay = true;
    } finally {
      if (! xa.isAutoCommit()) {
      }
      else if (isOkay)
	xa.commit();
      else
	xa.rollback();
    }
    
    _rs = new ResultSetImpl(this, _queryContext.getResult());

    return _rs;
  }

  /**
   * Executes an update statement with the given SQL.
   *
   * @param sql the SQL to execute.
   *
   * @return the number of rows modified.
   */
  public int executeUpdate(String sql)
    throws SQLException
  {
    Query query = _db.parseQuery(sql);

    return executeUpdate(query);
  }
  
  public int executeUpdate(String sql, int autoGeneratedKeys)
    throws SQLException
  {
    Query query = _db.parseQuery(sql);

    _queryContext.setReturnGeneratedKeys(true);

    return executeUpdate(query);
  }
  
  public int executeUpdate(String sql, int []columnIndexes)
    throws SQLException
  {
    Query query = _db.parseQuery(sql);

    _queryContext.setReturnGeneratedKeys(true);

    return executeUpdate(query);
  }
  
  public int executeUpdate(String sql, String []columnNames)
    throws SQLException
  {
    Query query = _db.parseQuery(sql);

    _queryContext.setReturnGeneratedKeys(true);

    return executeUpdate(query);
  }

  /**
   * Executes the update for the given query.
   *
   * @return the number of rows modified
   */
  private int executeUpdate(Query query)
    throws SQLException
  {
    Transaction xa = _conn.getTransaction();
    boolean isOkay = false;
    _queryContext.setTransaction(xa);

    try {
      query.execute(_queryContext, xa);
      isOkay = true;
    } finally {
      if (! xa.isAutoCommit()) {
      }
      else if (isOkay)
	xa.commit();
      else
	xa.rollback();
    }
    
    return _queryContext.getRowUpdateCount();
  }
  
  public java.sql.ResultSet getGeneratedKeys()
  {
    java.sql.ResultSet rs = _queryContext.getGeneratedKeysResultSet();

    if (rs != null)
      return rs;
    else
      return new NullResultSet();
  }

  public boolean execute(String sql)
    throws SQLException
  {
    Query query = _db.parseQuery(sql);

    if (query instanceof SelectQuery) {
      executeQuery(query);

      return true;
    }
    else {
      executeUpdate(query);

      return false;
    }
  }

  public int[]executeBatch()
    throws SQLException
  {
    return null;
  }

  public java.sql.ResultSet getResultSet()
  {
    return _rs;
  }

  public int getUpdateCount()
  {
    return _queryContext.getRowUpdateCount();
  }

  public java.sql.Connection getConnection()
  {
    return _conn;
  }

  public int getFetchDirection()
  {
    return 0;
  }

  public int getFetchSize()
  {
    return 0;
  }

  public int getMaxFieldSize()
  {
    return 0;
  }

  public int getMaxRows()
  {
    return 0;
  }
  
  public void setMaxRows(int max)
  {
  }

  public boolean getMoreResults()
  {
    return false;
  }

  public int getQueryTimeout()
  {
    return 0;
  }

  public int getResultSetConcurrency()
  {
    return 0;
  }

  public int getResultSetType()
  {
    return 0;
  }

  public SQLWarning getWarnings()
  {
    return null;
  }

  public void setCursorName(String name)
  {
  }

  public void setEscapeProcessing(boolean enable)
  {
  }

  public void setFetchDirection(int direction)
  {
  }

  public void setFetchSize(int rows)
  {
  }

  public void setMaxFieldSize(int max)
  {
  }
  
  public void setQueryTimeout(int seconds)
  {
  }

  // jdk 1.4
  public boolean getMoreResults(int count)
  {
    throw new UnsupportedOperationException();
  }
  
  public boolean execute(String query, int foo)
  {
    throw new UnsupportedOperationException();
  }
  
  public boolean execute(String query, int []foo)
  {
    throw new UnsupportedOperationException();
  }
  
  public boolean execute(String query, String []foo)
  {
    throw new UnsupportedOperationException();
  }

  public int getResultSetHoldability()
  {
    throw new UnsupportedOperationException();
  }

  public void close()
    throws SQLException
  {
    _db = null;

    ResultSetImpl rs = _rs;
    _rs = null;

    QueryContext queryContext = _queryContext;
    _queryContext = null;

    if (rs != null)
      rs.close();

    _conn.closeStatement(this);

    if (queryContext != null)
      QueryContext.free(queryContext);
  }
}
