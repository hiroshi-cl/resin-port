/*
 * Copyright (c) 1998-2004 Caucho Technology -- all rights reserved
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

package com.caucho.db.sql;

import java.io.InputStream;
import java.io.IOException;

import java.util.ArrayList;

import java.util.logging.Logger;
import java.util.logging.Level;

import java.sql.SQLException;

import com.caucho.util.L10N;
import com.caucho.util.QDate;

import com.caucho.vfs.WriteStream;

import com.caucho.log.Log;

import com.caucho.sql.SQLExceptionWrapper;

import com.caucho.db.table.Table;
import com.caucho.db.table.TableIterator;
import com.caucho.db.table.Column;

abstract public class Expr {
  protected static final L10N L = new L10N(Expr.class);
  private static final Logger log = Log.open(Expr.class);

  public static final int UNKNOWN = -1;
  public static final int FALSE = 0;
  public static final int TRUE = 1;

  final static long COST_INVALID  = Long.MAX_VALUE / 1000;
  final static long COST_NO_TABLE = Integer.MAX_VALUE;
  final static long COST_SCAN     = 1000000L;
  final static long COST_UNIQUE   = 10000L;
  final static long COST_INDEX    = 100L;
  final static long COST_CONSTANT = 0L;
  
  private static QDate _gmtDate = new QDate();

  protected Expr bind(Query query)
    throws SQLException
  {
    return this;
  }

  /**
   * Returns the expected result type of the expression.
   */
  public Class getType()
  {
    return Object.class;
  }

  /**
   * Returns true if the expression returns a long.
   */
  public boolean isLong()
  {
    Class type = getType();

    return (int.class.equals(type) || long.class.equals(type) ||
	    java.sql.Date.class.equals(type));
  }

  /**
   * Returns true if the expression returns a double.
   */
  public boolean isDouble()
  {
    Class type = getType();
    
    return isLong() || double.class.isAssignableFrom(type);
  }

  /**
   * Returns true if the expression returns a boolean.
   */
  public boolean isBoolean()
  {
    return boolean.class.equals(getType());
  }

  /**
   * Returns true if the expression returns a long.
   */
  public boolean isBinaryStream()
  {
    Class type = getType();

    return (InputStream.class.equals(type));
  }

  /**
   * Returns any column name.
   */
  public String getName()
  {
    return "";
  }

  /**
   * Returns the table.
   */
  public Table getTable()
  {
    return null;
  }

  /**
   * Splits the expr into and blocks.
   */
  public void splitAnd(ArrayList<Expr> andProduct)
  {
    andProduct.add(this);
  }

  /**
   * Returns the cost based on the given FromList.
   */
  public long cost(ArrayList<FromItem> fromList)
  {
    return subCost(fromList);
  }

  /**
   * Returns the cost based on the given FromList.
   */
  public long subCost(ArrayList<FromItem> fromList)
  {
    return COST_INVALID;
  }

  /**
   * Returns an index expression if available.
   */
  public RowIterateExpr getIndexExpr(FromItem fromItem)
  {
    return null;
  }

  /**
   * Returns the order.
   */
  public Order createOrder(int index)
  {
    if (int.class.equals(getType()))
      return new IntOrder(index);
    else if (isLong())
      return new LongOrder(index);
    else if (isDouble())
      return new DoubleOrder(index);
    else
      return new StringOrder(index);
  }
  
  /**
   * Returns true if result is null
   *
   * @param rows the current database tuple
   *
   * @return true if null
   */
  public boolean isNull(QueryContext context)
    throws SQLException
  {
    return false;
  }

  /**
   * Returns true if the expression selects the row.
   *
   * @param rows the current database tuple
   *
   * @return the boolean value
   */
  public boolean isSelect(QueryContext context)
    throws SQLException
  {
    return evalBoolean(context) == TRUE;
  }

  /**
   * Evaluates the expression as a boolean.
   *
   * @param rows the current database tuple
   *
   * @return the boolean value
   */
  public int evalBoolean(QueryContext context)
    throws SQLException
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /**
   * Evaluates the expression as a string.
   *
   * @param rows the current database tuple
   *
   * @return the string value
   */
  public String evalString(QueryContext context)
    throws SQLException
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /**
   * Evaluates the expression as a long.
   *
   * @param rows the current database tuple
   *
   * @return the long value
   */
  public long evalLong(QueryContext context)
    throws SQLException
  {
    String strValue = evalString(context);

    if (strValue == null)
      return 0;
    else
      return Long.parseLong(strValue);
  }

  /**
   * Evaluates the expression as a double.
   *
   * @param rows the current database tuple
   *
   * @return the double value
   */
  public double evalDouble(QueryContext context)
    throws SQLException
  {
    String strValue = evalString(context);

    if (strValue == null)
      return 0;
    else
      return Double.parseDouble(strValue);
  }

  /**
   * Evaluates the expression as a date.
   *
   * @param rows the current database tuple
   *
   * @return the double value
   */
  public long evalDate(QueryContext context)
    throws SQLException
  {
    String dateString = evalString(context);

    try {
      synchronized (_gmtDate) {
	return _gmtDate.parseDate(dateString);
      }
    } catch (Exception e) {
      throw new SQLExceptionWrapper(e);
    }
  }

  /**
   * Evaluates the expression, writing to the result stream.
   *
   * @param result the output result
   */
  public void evalToResult(QueryContext context, SelectResult result)
    throws SQLException
  {
    String s = evalString(context);

    if (s == null) {
      result.writeNull();
      return;
    }

    result.writeString(s);
  }

  /**
   * Evaluates the expression as a string.
   *
   * @param rows the current database tuple
   *
   * @return the string value
   */
  public InputStream evalStream(QueryContext context)
    throws SQLException
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /**
   * Evaluates the expression to a buffer
   *
   * @param result the result buffer
   *
   * @return the length of the result
   */
  public int evalToBuffer(QueryContext context,
			  byte []buffer,
			  int columnType)
    throws SQLException
  {
    switch (columnType) {
    case Column.INT:
      {
	int v = (int) evalLong(context);

	buffer[0] = (byte) (v >> 24);
	buffer[1] = (byte) (v >> 16);
	buffer[2] = (byte) (v >> 8);
	buffer[3] = (byte) (v);

	return 4;
      }
      
    case Column.LONG:
    case Column.DATE:
      {
	long v = evalLong(context);

	buffer[0] = (byte) (v >> 56);
	buffer[1] = (byte) (v >> 48);
	buffer[2] = (byte) (v >> 40);
	buffer[3] = (byte) (v >> 32);
	
	buffer[4] = (byte) (v >> 24);
	buffer[5] = (byte) (v >> 16);
	buffer[6] = (byte) (v >> 8);
	buffer[7] = (byte) (v);

	return 8;
      }

    case Column.VARCHAR:
      {
	String v = evalString(context);

	if (v == null)
	  return -1;
	
	int stringLength = v.length();
	int length = 0;

	buffer[length++] = (byte) stringLength;
	for (int i = 0; i < stringLength; i++) {
	  char ch = v.charAt(i);

	  buffer[length++] = (byte) (ch >> 8);
	  buffer[length++] = (byte) (ch);
	}
	
	buffer[length++] = 0;
	buffer[length++] = 0;

	return length;
      }
      
      
    default:
      throw new UnsupportedOperationException();
    }
  }

  /**
   * Initializes aggregate functions during the group phase.
   *
   * @param context the current database tuple
   */
  public void initGroup(QueryContext context)
    throws SQLException
  {
  }

  /**
   * Evaluates aggregate functions during the group phase.
   *
   * @param context the current database tuple
   */
  public void evalGroup(QueryContext context)
    throws SQLException
  {
  }
}
