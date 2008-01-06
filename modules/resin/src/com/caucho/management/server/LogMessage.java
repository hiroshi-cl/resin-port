/*
 * Copyright (c) 1998-2008 Caucho Technology -- all rights reserved
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

package com.caucho.management.server;

/**
 * Persistent logging.
 */
public class LogMessage implements java.io.Serializable
{
  private long _timestamp;
  private String _level;
  private String _message;

  private String _name;
  private String _className;
  private String _methodName;

  public LogMessage()
  {
  }

  public void setTimestamp(long timestamp)
  {
    _timestamp = timestamp;
  }

  public long getTimestamp()
  {
    return _timestamp;
  }

  public void setLevel(String level)
  {
    _level = level;
  }

  public String getLevel()
  {
    return _level;
  }

  public void setMessage(String message)
  {
    _message = message;
  }

  public String getMessage()
  {
    return _message;
  }

  public String getName()
  {
    return _name;
  }

  public void setName(String name)
  {
    _name = name;
  }

  public String getClassName()
  {
    return _className;
  }

  public void setClassName(String className)
  {
    _className = className;
  }

  public String getMethodName()
  {
    return _methodName;
  }

  public void setMethodName(String methodName)
  {
    _methodName = methodName;
  }

  public String toString()
  {
    return "LogMessage[" + _name + ", " + _className + ", " + _methodName + ", " + _message + "]";
  }
}
