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

package com.caucho.server.dispatch;

import java.util.*;
import java.util.logging.*;
import java.io.*;

import javax.servlet.*;
import javax.servlet.http.*;

import com.caucho.util.L10N;
import com.caucho.util.CauchoSystem;

import com.caucho.vfs.WriteStream;

import com.caucho.log.Log;

import com.caucho.jsp.QServlet;

/**
 * Manages dispatching: servlets and filters.
 */
public class ServletMapper {
  static final Logger log = Log.open(ServletMapper.class);
  static final L10N L = new L10N(ServletMapper.class);

  private ServletContext _servletContext;
  
  private ServletManager _servletManager;
  
  private UrlMap<String> _servletMap = new UrlMap<String>();
  
  private ArrayList<String> _welcomeFileList = new ArrayList<String>();
  
  private HashMap<String,ServletMapping> _regexpMap
    = new HashMap<String,ServletMapping>();
  
  private ArrayList<String> _ignorePatterns = new ArrayList<String>();
  
  private String _defaultServlet;

  /**
   * Sets the servlet context.
   */
  public void setServletContext(ServletContext servletContext)
  {
    _servletContext = servletContext;
  }

  /**
   * Gets the servlet context.
   */
  public ServletContext getServletContext()
  {
    return _servletContext;
  }

  /**
   * Returns the servlet manager.
   */
  public ServletManager getServletManager()
  {
    return _servletManager;
  }

  /**
   * Sets the servlet manager.
   */
  public void setServletManager(ServletManager manager)
  {
    _servletManager = manager;
  }

  /**
   * Adds a servlet mapping
   */
  public void addServletMapping(ServletMapping mapping)
    throws ServletException
  {
    try {
      if (mapping.getURLRegexp() != null) {
	String regexp = mapping.getURLRegexp();

	_servletMap.addRegexp(regexp, regexp);
	_regexpMap.put(regexp, mapping);
	return;
      }
      
      String servletName = mapping.getServletName();

      if (servletName == null)
	servletName = mapping.getServletClassName();

      if (servletName == null) {
	throw new ServletConfigException(L.l("servlet needs a servlet-name."));
      }
      else if (servletName.equals("invoker")) {
        // special case
      }
      else if (servletName.equals("plugin_match") ||
	       servletName.equals("plugin-match")) {
        // special case
      }
      else if (servletName.equals("plugin_ignore") ||
	       servletName.equals("plugin-ignore")) {
	if (mapping.getURLPattern() != null)
	  _ignorePatterns.add(mapping.getURLPattern());
	
	return;
      }
      else if (_servletManager.getServlet(servletName) == null)
        throw new ServletConfigException(L.l("`{0}' is an unknown servlet-name.  servlet-mapping requires that the named servlet be defined in a <servlet> configuration before the <servlet-mapping>.", servletName));

      if ("/".equals(mapping.getURLPattern())) {
        _defaultServlet = servletName;
      }
      else if (mapping.isStrictMapping()) {
        _servletMap.addStrictMap(mapping.getURLPattern(), null, servletName);
      }
      else
        _servletMap.addMap(mapping.getURLPattern(), servletName);

      log.config("servlet-mapping " + mapping.getURLPattern() +
                 " -> " + servletName);
    } catch (ServletException e) {
      throw e;
    } catch (Exception e) {
      throw new ServletException(e);
    }
  }

  /**
   * Adds a servlet mapping
   */
  public void addServletRegexp(ServletMapping servletRegexp)
    throws ServletException
  {
    try {
      String regexp = servletRegexp.getURLRegexp();

      _servletMap.addRegexp(regexp, regexp);
      _regexpMap.put(regexp, servletRegexp);
    } catch (Exception e) {
      throw new ServletException(e);
    }
  }
  
  /**
   * Sets the default servlet.
   */
  public void setDefaultServlet(String servletName)
    throws ServletException
  {
    _defaultServlet = servletName;
  }
  
  /**
   * Adds a welcome-file
   */
  public void addWelcomeFile(String fileName)
  {
    _welcomeFileList.add(fileName);
  }
  
  /**
   * Sets the welcome-file list
   */
  public void setWelcomeFileList(ArrayList<String> list)
  {
    _welcomeFileList.clear();
    _welcomeFileList.addAll(list);
  }

  public FilterChain mapServlet(ServletInvocation invocation)
    throws ServletException
  {
    String contextURI = invocation.getContextURI();

    String servletName = null;
    ArrayList<String> vars = new ArrayList<String>();

    invocation.setClassLoader(Thread.currentThread().getContextClassLoader());

    if (_servletMap != null) {
      servletName = _servletMap.map(contextURI, vars);

      ServletMapping servletRegexp = _regexpMap.get(servletName);
      if (servletRegexp != null) {
	servletName = servletRegexp.initRegexp(_servletContext,
					       _servletManager,
					       vars);
      }
    }

    if (servletName == null) {
      try {
        InputStream is;
        is = _servletContext.getResourceAsStream(contextURI);

        if (is != null) {
          is.close();

          servletName = _defaultServlet;
        }
      } catch (Exception e) {
      }
    }

    if (servletName == null) {
      for (int i = 0; i < _welcomeFileList.size(); i++) {
        String file = _welcomeFileList.get(i);

        try {
          InputStream is;
          is = _servletContext.getResourceAsStream(contextURI + "/" + file);

          if (is != null)
            is.close();
          
          if (is == null) {
          }
          else if (! contextURI.endsWith("/")) {
            String contextPath = invocation.getContextPath();
            String queryString = invocation.getQueryString();
            
            if (queryString != null)
              return new RedirectFilterChain(contextPath + contextURI + "/?" + queryString);
            else
              return new RedirectFilterChain(contextPath + contextURI + "/");
          }
          else {
            String uri = contextURI + file;

            servletName = _servletMap.map(uri, vars);

            if (servletName != null || _defaultServlet != null) {
              contextURI = uri;
              if (invocation instanceof Invocation) {
                Invocation inv = (Invocation) invocation;

                inv.setContextURI(contextURI);
                inv.setRawURI(inv.getRawURI() + file);
              }
              break;
            }
          }
        } catch (Throwable e) {
        }
      }
    }

    if (servletName == null) {
      servletName = _defaultServlet;
      vars.clear();
      vars.add(contextURI);
    }

    if (servletName == null) {
      log.fine(L.l("no default servlet defined for URL: '{0}'", contextURI));
      
      return new ErrorFilterChain(404);
    }

    String servletPath = vars.get(0);

    invocation.setServletPath(servletPath);

    if (servletPath.length() < contextURI.length())
      invocation.setPathInfo(contextURI.substring(servletPath.length()));
    else
      invocation.setPathInfo(null);

    ServletMapping regexp = _regexpMap.get(servletName);

    /*
    if (regexp != null)
      servletName = regexp.initRegexp(_servletManager, vars);
    */

    if (servletName.equals("invoker"))
      servletName = handleInvoker(invocation);

    invocation.setServletName(servletName);
    
    if (log.isLoggable(Level.FINE))
      log.fine("invoke (uri:" + contextURI + " -> " + servletName + ")");

    ServletConfigImpl config = _servletManager.getServlet(servletName);

    invocation.setSecurityRoleMap(config.getRoleMap());

    FilterChain chain = _servletManager.createServletChain(servletName);

    if (chain instanceof PageFilterChain) {
      PageFilterChain pageChain = (PageFilterChain) chain;

      chain = PrecompilePageFilterChain.create(invocation, pageChain);
    }

    return chain;
  }
  
  private String handleInvoker(ServletInvocation invocation)
    throws ServletException
  {
    String tail;
    
    if (invocation.getPathInfo() != null)
      tail = invocation.getPathInfo();
    else
      tail = invocation.getServletPath();

      // XXX: this is really an unexpected, internal error that should never
      //      happen
    if (! tail.startsWith("/")) {
      throw new ServletException("expected '/' starting " +
                                 " sp:" + invocation.getServletPath() +
                                 " pi:" + invocation.getPathInfo() +
                                 " sn:invocation" + invocation);
    }

    int next = tail.indexOf('/', 1);
    String servletName;

    if (next < 0)
      servletName = tail.substring(1);
    else
      servletName = tail.substring(1, next);

    // XXX: This should be generalized, possibly with invoker configuration
    if (servletName.startsWith("com.caucho")) {
      throw new ServletConfigException(L.l("servlet `{0}' forbidden from invoker. com.caucho.* classes must be defined explicitly in a <servlet> declaration.",
                                     servletName));
    }
    else if (servletName.equals("")) {
      throw new ServletConfigException(L.l("invoker needs a servlet name in URL `{0}'.",
                                           invocation.getContextURI()));
    }
      
    addServlet(servletName);

    String servletPath = invocation.getServletPath();
    if (invocation.getPathInfo() == null) {
    }
    else if (next < 0) {
      invocation.setServletPath(servletPath + tail);
      invocation.setPathInfo(null);
    }
    else if (next < tail.length()) {
      
      invocation.setServletPath(servletPath + tail.substring(0, next));
      invocation.setPathInfo(tail.substring(next));
    }
    else {
      invocation.setServletPath(servletPath + tail);
      invocation.setPathInfo(null);
    }

    return servletName;
  }

  public String getServletPattern(String uri)
  {
    ArrayList<String> vars = new ArrayList<String>();

    Object value = null;
    
    if (_servletMap != null)
      value = _servletMap.map(uri, vars);

    if (value == null)
      return null;
    else
      return uri;
  }

  /**
   * Returns the servlet matching patterns.
   */
  public ArrayList<String> getURLPatterns()
  {
    ArrayList<String> patterns = _servletMap.getURLPatterns();

    return patterns;
  }

  /**
   * Returns the servlet plugin_ignore patterns.
   */
  public ArrayList<String> getIgnorePatterns()
  {
    return _ignorePatterns;
  }

  private void addServlet(String servletName)
    throws ServletException
  {
    if (_servletManager.getServlet(servletName) != null)
      return;

    ServletConfigImpl config = new ServletConfigImpl();
    config.setServletContext(_servletContext);
    config.setServletName(servletName);

    try {
      config.setServletClass(servletName);
    } catch (Exception e) {
      throw new ServletException(e);
    }

    config.init();

    _servletManager.addServlet(config);
  }
    
  public void destroy()
  {
    _servletManager.destroy();
  }
}
