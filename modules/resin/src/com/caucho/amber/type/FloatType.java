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

package com.caucho.amber.type;

import java.io.IOException;

import java.sql.ResultSet;
import java.sql.Types;
import java.sql.SQLException;

import com.caucho.amber.AmberManager;

import com.caucho.java.JavaWriter;

import com.caucho.util.L10N;

/**
 * Represents a java.util.Float type
 */
public class FloatType extends Type {
  private static final L10N L = new L10N(FloatType.class);

  private static final FloatType FLOAT_TYPE = new FloatType();

  private FloatType()
  {
  }

  /**
   * Returns the singleton Float type.
   */
  public static FloatType create()
  {
    return FLOAT_TYPE;
  }

  /**
   * Returns the type name.
   */
  public String getName()
  {
    return "java.lang.Float";
  }

  /**
   * Generates the type for the table.
   */
  public String generateCreateTableSQL(AmberManager manager, int length)
  {
    return manager.getCreateTableSQL(Types.REAL, length);
  }

  /**
   * Generates a string to load the property.
   */
  public int generateLoad(JavaWriter out, String rs,
			  String indexVar, int index)
    throws IOException
  {
    out.print("com.caucho.amber.type.FloatType.toFloat(" +
	      rs + ".getFloat(" + indexVar + " + " + index + "), " +
	      rs + ".wasNull())");

    return index + 1;
  }

  /**
   * Generates a string to set the property.
   */
  public void generateSet(JavaWriter out, String pstmt,
			  String index, String value)
    throws IOException
  {
    out.println("if (" + value + " == null)");
    out.println("  " + pstmt + ".setNull(" + index + "++, java.sql.Types.REAL);");
    out.println("else");
    out.println("  " + pstmt + ".setFloat(" + index + "++, " +
		value + ".floatValue());");
  }

  /**
   * Generates a string to set the property.
   */
  public void generateSetNull(JavaWriter out, String pstmt,
			      String index)
    throws IOException
  {
    out.println(pstmt + ".setNull(" + index + "++, java.sql.Types.REAL);");
  }

  /**
   * Converts a value to a int.
   */
  public static Float toFloat(float value, boolean wasNull)
  {
    if (wasNull)
      return null;
    else
      return new Float(value);
  }

  /**
   * Gets the value.
   */
  public Object getObject(ResultSet rs, int index)
    throws SQLException
  {
    float value = rs.getFloat(index);
    
    return rs.wasNull() ? null : new Float(value);
  }
}
