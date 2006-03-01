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

package com.caucho.sql;

import java.io.*;
import java.sql.*;
import java.util.*;

import javax.sql.*;
import javax.transaction.xa.*;
import javax.transaction.*;

import com.caucho.util.*;
import com.caucho.vfs.*;
import com.caucho.transaction.*;

/**
 * Represents a pooled connection, managed by the Resin pool.
 *
 * <p>Each PoolItem encapsulates the driver's PooledConnection and
 * 
 */
class StatementOverflowException extends SQLException {
  public StatementOverflowException()
  {
  }
  
  public StatementOverflowException(String msg)
  {
    super(msg);
  }
}
