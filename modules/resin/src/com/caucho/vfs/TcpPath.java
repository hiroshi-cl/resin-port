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
 *   Free SoftwareFoundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.vfs;

import java.io.*;
import java.net.*;
import java.util.*;

import com.caucho.util.*;

/**
 * Implements a tcp stream, essentially just a socket pair.
 */
public class TcpPath extends Path {
  protected String _host;
  protected int _port;
  protected long _timeout;

  TcpPath(TcpPath root, String userPath, Map<String,Object> newAttributes,
	  String host, int port)
  {
    super(root);

    setUserPath(userPath);

    _host = host;
    _port = port == 0 ? 80 : port;
  }

  /**
   * Lookup the new path assuming we're the scheme root.
   */
  protected Path schemeWalk(String userPath,
                            Map<String,Object> newAttributes,
			    String uri,
                            int offset)
  {
    int length = uri.length();

    if (length < 2 + offset ||
        uri.charAt(offset) != '/' ||
        uri.charAt(1 + offset) != '/')
      throw new RuntimeException("bad scheme");

    CharBuffer buf = new CharBuffer();
    int i = 2 + offset;
    int ch = 0;
    for (; i < length && (ch = uri.charAt(i)) != ':' && ch != '/' && ch != '?';
	 i++) {
      buf.append((char) ch);
    }

    String host = buf.toString();
    if (host.length() == 0)
      throw new RuntimeException("bad host");

    int port = 0;
    if (ch == ':') {
      for (i++; i < length && (ch = uri.charAt(i)) >= '0' && ch <= '9'; i++) {
	port = 10 * port + uri.charAt(i) - '0';
      }
    }

    return create(this, userPath, newAttributes, host, port);
  }

  protected TcpPath create(TcpPath root,
			   String userPath, Map<String,Object> newAttributes,
			   String host, int port)
  {
    return new TcpPath(root, userPath, newAttributes, host, port);
  }

  public String getScheme()
  {
    return "tcp";
  }

  public String getURL()
  {
    return (getScheme() + "://" + getHost() + ":" + getPort());
  }

  public String getPath()
  {
    return "";
  }

  public String getHost()
  {
    return _host;
  }

  public int getPort()
  {
    return _port;
  }

  /*
  public void setAttribute(String attr, Object value)
    throws IOException
  {
    if (attr.equals("timeout")) {
      Long timeout = (Long) value;
      _timeout = timeout.longValue();
    }
    else
      super.setAttribute(attr, value);
  }
  */

  public StreamImpl openReadImpl() throws IOException
  {
    return TcpStream.openRead(this, _timeout);
  }

  public StreamImpl openReadWriteImpl() throws IOException
  {
    return TcpStream.openReadWrite(this, _timeout);
  }

  public String toString()
  {
    return getURL();
  }
}
