/*
 * Copyright (c) 1998-2005 Caucho Technology -- all rights reserved
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
 * @author Sam
 */


package com.caucho.quercus.env;

import com.caucho.quercus.env.Value;

import javax.management.Attribute;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.openmbean.*;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CompositeDataValue extends Value {
  private static final Logger log
    = Logger.getLogger(CompositeDataValue.class.getName());

  private CompositeData _data;

  public CompositeDataValue(CompositeData data)
  {
    _data = data;
  }

  /**
   * Returns an attribute.
   */
  @Override
  public Value getField(Env env, String attrName)
  {
    try {
      return env.wrapJava(_data.get(attrName));
    } catch (InvalidKeyException e) {
      env.warning(e);
    }
  }

  /**
   * Convert to java
   */
  @Override
  public Object toJavaObject()
  {
    return _data;
  }

  public String toString()
  {
    return _data.getCompositeType().toString();
  }
}
