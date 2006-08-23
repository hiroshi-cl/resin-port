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
 *
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Adam Megacz, Emil Ong
 */

package com.caucho.server.dispatch;

import com.caucho.soap.servlets.WebServiceServlet;
import com.caucho.server.dispatch.*;
import com.caucho.server.webapp.*;

import com.caucho.config.types.InitParam;
import com.caucho.config.types.InitProgram;
import com.caucho.config.types.RawString;

import javax.servlet.*;
import javax.servlet.Servlet;

import javax.servlet.jsp.el.ELException;

/**
 * A Web Service entry in web.xml (Caucho-specific)
 */
public class SoapServletMapping extends ServletMapping {
  private SoapInterface _soapInterface;
  private String _namespace = null;
  private boolean _wrapped = true;
  private WebService _webService;

  /**
   * Creates a new web service object.
   */
  public SoapServletMapping(SoapInterface soapInterface)
    throws ServletException
  {
    super.setServletClass(WebServiceServlet.class.getName());

    _soapInterface = soapInterface;
  }

  public void setNamespace(String namespace)
  {
    _namespace = namespace;
  }

  public void setWrapped(boolean wrapped)
  {
    _wrapped = wrapped;
  }

  void configureServlet(Servlet servlet)
    throws Throwable
  {
    super.configureServlet(servlet);

    Object service = _soapInterface.getWebService().getServiceInstance();
    Class interfaceClass = _soapInterface.getInterfaceClass();

    WebServiceServlet webServiceServlet = (WebServiceServlet) servlet;

    webServiceServlet.setInterface(interfaceClass);
    webServiceServlet.setService(service);
    webServiceServlet.setNamespace(_namespace);
    webServiceServlet.setWrapped(_wrapped);
  }
}
