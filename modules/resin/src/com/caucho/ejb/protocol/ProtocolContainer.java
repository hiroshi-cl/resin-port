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

package com.caucho.ejb.protocol;

import java.io.*;
import java.util.*;
import java.rmi.*;

import java.sql.*;

import javax.ejb.*;
import javax.sql.*;
import javax.naming.*;
import javax.transaction.*;

import com.caucho.util.*;
import com.caucho.vfs.*;

import com.caucho.config.ConfigException;

import com.caucho.ejb.*;

/**
 * Server containing all the EJBs for a given configuration.
 *
 * <p>Each protocol will extend the container to override Handle creation.
 */
public class ProtocolContainer {
  private static final L10N L = new L10N(ProtocolContainer.class);

  protected EjbProtocolManager _manager;
  protected String _urlPrefix;
  private Path _workPath;

  public void setServerManager(EnvServerManager manager)
  {
    _manager = manager.getProtocolManager();
  }

  public EjbProtocolManager getProtocolManager()
  {
    return _manager;
  }

  public void setProtocolManager(EjbProtocolManager manager)
  {
    _manager = manager;
  }

  public String getName()
  {
    return "jvm";
  }

  public void setURLPrefix(String urlPrefix)
  {
    if (urlPrefix.endsWith("/"))
      urlPrefix = urlPrefix.substring(urlPrefix.length() - 1);

    _urlPrefix = urlPrefix;
  }

  public String getURLPrefix()
  {
    return _urlPrefix;
  }

  public void setWorkPath(Path workPath)
  {
    _workPath = workPath;
  }

  public Path getWorkPath()
  {
    return _workPath;
  }

  /**
   * Adds a server to the protocol.
   */
  public void addServer(AbstractServer server)
  {
  }

  /**
   * Removes a server from the protocol.
   */
  public void removeServer(AbstractServer server)
  {
  }
  
  protected HandleEncoder createHandleEncoder(AbstractServer server,
                                              Class primaryKeyClass)
    throws ConfigException
  {
    if (_urlPrefix != null)
      return new HandleEncoder(server, _urlPrefix + server.getEJBName());
    else
      return new HandleEncoder(server, server.getEJBName());
  }

  /**
   * Returns the skeleton
   */
  public Skeleton getSkeleton(String uri, String queryString)
    throws Exception
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Returns the error
   */
  public Skeleton getExceptionSkeleton()
    throws Exception
  {
    throw new UnsupportedOperationException();
  }
}
