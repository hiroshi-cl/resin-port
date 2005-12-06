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

package com.caucho.php.program;

import com.caucho.php.expr.VarState;
import com.caucho.php.expr.VarExpr;
import com.caucho.php.expr.Expr;

/**
 * A handle to a statement
 */
public class StatementHandle {
  public static final StatementHandle NULL
    = new StatementHandle(NullStatement.NULL);
  
  private final StatementHandle _parent;
  private final StatementHandle _previous;
  
  private Statement _statement;

  public StatementHandle(StatementHandle parent, StatementHandle previous)
  {
    _parent = parent;
    _previous = previous;
  }

  private StatementHandle(Statement statement)
  {
    _parent = null;
    _previous = null;

    _statement = statement;
  }

  /**
   * Sets the statement.
   */
  public void setStatement(Statement statement)
  {
    _statement = statement;
  }

  /**
   * Gets the statement.
   */
  public Statement getStatement()
  {
    return _statement;
  }
}

