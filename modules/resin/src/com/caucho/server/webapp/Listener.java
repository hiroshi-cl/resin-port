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

package com.caucho.server.webapp;

import java.util.*;

import javax.annotation.*;

import com.caucho.util.L10N;

/**
 * Configuration for the listener
 */
public class Listener {
  static L10N L = new L10N(Listener.class);

  // The listener class
  private Class _listenerClass;

  // The listener object
  private Object _object;

  /**
   * Sets the listener class.
   */
  public void setListenerClass(Class cl)
  {
    _listenerClass = cl;
  }

  /**
   * Gets the listener class.
   */
  public Class getListenerClass()
  {
    return _listenerClass;
  }

  /**
   * Returns the object for initialization.
   */
  public Object createInit()
    throws Exception
  {
    if (_object == null)
      _object = _listenerClass.newInstance();

    return _object;
  }

  /**
   * Initialize.
   */
  @PostConstruct
  public void init()
    throws Exception
  {
    if (_object == null)
      _object = _listenerClass.newInstance();
  }

  /**
   * Returns the listener object.
   */
  public Object getListenerObject()
  {
    return _object;
  }
}
