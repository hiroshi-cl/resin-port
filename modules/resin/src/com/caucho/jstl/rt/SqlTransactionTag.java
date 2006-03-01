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

package com.caucho.jstl.rt;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.logging.*;

import javax.sql.*;
import javax.naming.*;

import javax.servlet.jsp.*;
import javax.servlet.jsp.tagext.*;
import javax.servlet.jsp.jstl.core.*;
import javax.servlet.jsp.jstl.sql.Result;
import javax.servlet.jsp.jstl.sql.SQLExecutionTag;

import com.caucho.log.Log;
import com.caucho.util.*;
import com.caucho.vfs.*;
import com.caucho.jstl.ResultImpl;
import com.caucho.jsp.*;

public class SqlTransactionTag extends TagSupport implements TryCatchFinally  {
  private static final Logger log = Log.open(SqlTransactionTag.class);
  private static final L10N L = new L10N(SqlTransactionTag.class);
  
  private Object _dataSource;
  private String _isolation;

  private Connection _conn;
  private int _oldIsolation;

  /**
   * Sets the data source
   */
  public void setDataSource(Object dataSource)
  {
    _dataSource = dataSource;
  }

  /**
   * Sets the JSP-EL expression for the isolation.
   */
  public void setIsolation(String isolation)
  {
    _isolation = isolation;
  }

  public int doStartTag() throws JspException
  {
    if (pageContext.getAttribute("caucho.jstl.sql.conn") != null)
      throw new JspTagException(L.l("nexted sql:transaction are forbidden"));

    try {
      DataSource ds;

      ds = SqlQueryTag.getDataSource(pageContext, _dataSource);

      int isolationCode = -1;
      if (_isolation == null) {
      }
      else if (_isolation.equals("read_committed"))
        isolationCode = Connection.TRANSACTION_READ_COMMITTED;
      else if (_isolation.equals("read_uncommitted"))
        isolationCode = Connection.TRANSACTION_READ_UNCOMMITTED;
      else if (_isolation.equals("repeatable_read"))
        isolationCode = Connection.TRANSACTION_REPEATABLE_READ;
      else if (_isolation.equals("serializable"))
        isolationCode = Connection.TRANSACTION_SERIALIZABLE;
      else
        throw new JspTagException(L.l("unknown sql:transaction isolation ~{0}'", _isolation));

      _conn = ds.getConnection();

      _oldIsolation = _conn.getTransactionIsolation();

      _conn.setAutoCommit(false);
      
      if (_isolation != null && isolationCode != _oldIsolation)
        _conn.setTransactionIsolation(isolationCode);

      pageContext.setAttribute("caucho.jstl.sql.conn", _conn);
    } catch (JspException e) {
      throw e;
    } catch (Exception e) {
      throw new JspException(e);
    }

    return EVAL_PAGE;
  }

  public void doCatch(Throwable t) throws Throwable
  {
    if (_conn != null)
      _conn.rollback();

    throw t;
  }

  public void doFinally()
  {
    try {
      pageContext.removeAttribute("caucho.jstl.sql.conn");
      
      if (_conn != null) {
        Connection conn = _conn;
        _conn = null;

        try {
          conn.commit();
        } finally {
          try {
            conn.setTransactionIsolation(_oldIsolation);
          } catch (SQLException e) {
          }
          
          try {
            conn.close();
          } catch (SQLException e) {
          }
        }
      }
    } catch (Exception e) {
      log.log(Level.FINE, e.toString(), e);
    }
  }
}
