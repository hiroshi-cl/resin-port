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

package com.caucho.server.webapp;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.util.logging.*;

import javax.servlet.*;
import javax.servlet.http.HttpServletResponse;

import com.caucho.log.Log;

import com.caucho.util.L10N;
import com.caucho.util.LruCache;
import com.caucho.util.CauchoSystem;

import com.caucho.vfs.WriteStream;
import com.caucho.vfs.LogStream;
import com.caucho.vfs.Path;
import com.caucho.vfs.Vfs;

import com.caucho.loader.DynamicClassLoader;
import com.caucho.loader.EnvironmentClassLoader;
import com.caucho.loader.Environment;
import com.caucho.loader.EnvironmentListener;
import com.caucho.loader.ClassLoaderListener;

import com.caucho.config.BuilderProgram;
import com.caucho.config.ConfigException;

import com.caucho.make.Dependency;
import com.caucho.make.AlwaysModified;

import com.caucho.transaction.TransactionManagerImpl;

import com.caucho.server.log.AccessLog;

import com.caucho.server.dispatch.Invocation;
import com.caucho.server.dispatch.InvocationDecoder;
import com.caucho.server.dispatch.DispatchServer;
import com.caucho.server.dispatch.DispatchBuilder;
import com.caucho.server.dispatch.ErrorFilterChain;
import com.caucho.server.dispatch.ExceptionFilterChain;

import com.caucho.server.deploy.DeployGenerator;
import com.caucho.server.deploy.DeployContainer;

import com.caucho.server.resin.ServletServer;

import com.caucho.server.session.SessionManager;

import com.caucho.lifecycle.Lifecycle;

import com.caucho.server.e_app.EarConfig;
import com.caucho.server.e_app.EarDeployGenerator;
import com.caucho.server.e_app.EarDeployController;
import com.caucho.server.e_app.EarSingleDeployGenerator;

/**
 * Resin's application implementation.
 */
public class ApplicationContainer
  implements DispatchBuilder, ClassLoaderListener, EnvironmentListener {
  static final L10N L = new L10N(Application.class);
  static final Logger log = Log.open(ApplicationContainer.class);

  // The owning dispatch server
  private DispatchServer _dispatchServer;

  // The context class loader
  private EnvironmentClassLoader _classLoader;

  // The root directory.
  private Path _rootDir;

  // The document directory.
  private Path _docDir;

  // dispatch mapping
  private RewriteInvocation _rewriteInvocation;

  // List of default ear application configurations
  private ArrayList<EarConfig> _earDefaultList
    = new ArrayList<EarConfig>();

  // List of ear-deploy
  //private ArrayList<EarDeployGeneratorGenerator> _earDeployList = new ArrayList<EarDeployGeneratorGenerator>();
  
  private DeployContainer<EarDeployController> _earDeploy;
  private DeployContainer<WebAppController> _appDeploy;
  private WebAppExpandDeployGenerator _warGenerator;
  
  private boolean _hasWarGenerator;

  // LRU cache for the application lookup
  private LruCache<String,WebAppController> _uriToAppCache
    = new LruCache<String,WebAppController>(8192);

  // include dispatch cache
  /*
  private LruCache<String,Invocation> _includeCache
    = new LruCache<String,Invocation>(4096);
  */

  // List of default application configurations
  private ArrayList<WebAppConfig> _webAppDefaultList
    = new ArrayList<WebAppConfig>();
  
  // url-regexp apps
  //private ArrayList<WebAppConfig> _regexpApps = new ArrayList<WebAppConfig>();
  
  private HashMap<String,WebAppConfig> _configAppMap
    =  new HashMap<String,WebAppConfig>();

  private AccessLog _accessLog;
  private ErrorPageManager _errorPageManager;

  private long _startWaitTime = 10000L;
  
  private Throwable _configException;

  // lifecycle
  private final Lifecycle _lifecycle = new Lifecycle();

  /**
   * Creates the application with its environment loader.
   */
  public ApplicationContainer()
  {
    this((EnvironmentClassLoader) Thread.currentThread().getContextClassLoader());
  }

  /**
   * Creates the application with its environment loader.
   */
  public ApplicationContainer(EnvironmentClassLoader loader)
  {
    _rootDir = Vfs.lookup();
    _docDir = Vfs.lookup();

    _classLoader = loader;
    _errorPageManager = new ErrorPageManager();
    _errorPageManager.setApplicationContainer(this);

    /*
    Environment.addEnvironmentListener(this, loader);
    Environment.addClassLoaderListener(this, loader);
    */

    Thread thread = Thread.currentThread();
    ClassLoader oldLoader = thread.getContextClassLoader();
    try {
      thread.setContextClassLoader(loader);

      // These need to be in the proper class loader so they can
      // register themselves with the environment
      _earDeploy = new DeployContainer<EarDeployController>();
      
      _appDeploy = new DeployContainer<WebAppController>();

      _warGenerator = new WebAppExpandDeployGenerator(_appDeploy);
      _warGenerator.setContainer(this);
    } finally {
      thread.setContextClassLoader(oldLoader);
    }
  }
  
  /**
   * Sets the dispatch server.
   */
  public void setDispatchServer(DispatchServer server)
  {
    _dispatchServer = server;
  }

  /**
   * Gets the dispatch server.
   */
  public DispatchServer getDispatchServer()
  {
    return _dispatchServer;
  }

  /**
   * Gets the class loader.
   */
  public ClassLoader getClassLoader()
  {
    return _classLoader;
  }

  /**
   * sets the class loader.
   */
  public void setEnvironmentClassLoader(EnvironmentClassLoader loader)
  {
    _classLoader = loader;
  }

  /**
   * Gets the root directory.
   */
  public Path getRootDirectory()
  {
    return _rootDir;
  }

  /**
   * Sets the root directory.
   */
  public void setRootDirectory(Path path)
  {
    _rootDir = path;

    Vfs.setPwd(path, getClassLoader());
  }

  /**
   * Gets the document directory.
   */
  public Path getDocumentDirectory()
  {
    return _docDir;
  }

  /**
   * Sets the document directory.
   */
  public void setDocumentDirectory(Path path)
  {
    _docDir = path;
  }

  /**
   * Sets the document directory.
   */
  public void setDocDir(Path path)
  {
    setDocumentDirectory(path);
  }

  /**
   * Sets the access log.
   */
  public void setAccessLog(AccessLog log)
  {
    _accessLog = log;
    
    Environment.setAttribute("caucho.server.access-log", log);
  }

  /**
   * Adds an error page
   */
  public void addErrorPage(ErrorPage errorPage)
  {
    _errorPageManager.addErrorPage(errorPage);
  }

  /**
   * Returns the error page manager
   */
  public ErrorPageManager getErrorPageManager()
  {
    return _errorPageManager;
  }

  /**
   * Sets a configuration exception.
   */
  public void setConfigException(Throwable e)
  {
    _configException = e;
  }

  /**
   * Returns the application generator
   */
  public DeployContainer<WebAppController> getApplicationGenerator()
  {
    return _appDeploy;
  }

  /**
   * Returns the container's session manager.
   */
  public SessionManager getSessionManager()
  {
    return null;
  }

  /**
   * Adds rewrite-dispatch.
   */
  public RewriteInvocation createRewriteDispatch()
  {
    if (_rewriteInvocation == null)
      _rewriteInvocation = new RewriteInvocation();

    return _rewriteInvocation;
  }

  /**
   * Returns true if modified.
   */
  public boolean isModified()
  {
    return _lifecycle.isDestroyed() || _classLoader.isModified();
  }

  /**
   * Adds an application.
   */
  public void addWebApp(WebAppConfig config)
    throws Exception
  {
    if (config.getURLRegexp() != null) {
      DeployGenerator<WebAppController> deploy = new WebAppRegexpDeployGenerator(_appDeploy,
							  this, config);
      _appDeploy.add(deploy);
      return;
    }

    WebAppController oldEntry = _appDeploy.findController(config.getContextPath());

    if (oldEntry != null && oldEntry.getSourceType().equals("single")) {
      throw new ConfigException(L.l("duplicate web-app '{0}' forbidden.",
				    config.getId()));
    }

    WebAppSingleDeployGenerator deploy = new WebAppSingleDeployGenerator(_appDeploy,
						       this, config);
    deploy.deploy();

    _appDeploy.add(deploy);
  }

  /**
   * Removes an application.
   */
  void removeWebApp(WebAppController entry)
  {
    _appDeploy.remove(entry.getContextPath());

    clearCache();
  }

  /**
   * Adds a web-app default
   */
  public void addWebAppDefault(WebAppConfig init)
  {
    _webAppDefaultList.add(init);
  }

  /**
   * Returns the list of web-app defaults
   */
  public ArrayList<WebAppConfig> getWebAppDefaultList()
  {
    return _webAppDefaultList;
  }

  /**
   * Sets the war-expansion
   */
  public WebAppExpandDeployGenerator createWebAppDeploy()
  {
    return new WebAppExpandDeployGenerator(_appDeploy);
  }

  /**
   * Sets the war-expansion
   */
  public void addWebAppDeploy(WebAppExpandDeployGenerator deploy)
    throws ConfigException
  {
    WebAppExpandDeployGenerator webAppDeploy = (WebAppExpandDeployGenerator) deploy;

    webAppDeploy.setContainer(this);

    if (! _hasWarGenerator) {
      _hasWarGenerator = true;
      _warGenerator = webAppDeploy;
    }

    _appDeploy.add(deploy);
  }

  /**
   * Sets the war-expansion
   */
  public void addDeploy(DeployGenerator deploy)
    throws ConfigException
  {
    if (deploy instanceof WebAppExpandDeployGenerator)
      addWebAppDeploy((WebAppExpandDeployGenerator) deploy);
    else
      _appDeploy.add(deploy);
  }

  /**
   * Removes a web-app-generator.
   */
  public void removeWebAppDeploy(DeployGenerator deploy)
  {
    _appDeploy.remove(deploy);
  }

  /**
   * Updates a WebApp deploy
   */
  public void updateWebAppDeploy(String name)
  {
    clearCache();
    
    _appDeploy.update();
    _appDeploy.update(name);
  }

  /**
   * Adds an enterprise application.
   */
  public void addApplication(EarConfig config)
  {
    DeployGenerator<EarDeployController> deploy = new EarSingleDeployGenerator(_earDeploy, this, config);

    _earDeploy.add(deploy);
  }
     
  /**
   * Updates an ear deploy
   */
  public void updateEarDeploy(String name)
  {
    clearCache();
    
    _earDeploy.update();
    EarDeployController entry = _earDeploy.update(name);

    if (entry != null)
      entry.start();
  }
     
  /**
   * Updates an ear deploy
   */
  public void expandEarDeploy(String name)
  {
    clearCache();
    
    _earDeploy.update();
    EarDeployController entry = _earDeploy.update(name);

    if (entry != null)
      entry.start();
  }
     
  /**
   * Start an ear
   */
  public void startEarDeploy(String name)
  {
    clearCache();
    
    _earDeploy.update();
    EarDeployController entry = _earDeploy.update(name);

    if (entry != null)
      entry.start();
  }

  /**
   * Adds an ear default
   */
  public void addEarDefault(EarConfig config)
  {
    _earDefaultList.add(config);
  }

  /**
   * Returns the list of ear defaults
   */
  public ArrayList<EarConfig> getEarDefaultList()
  {
    return _earDefaultList;
  }

  /**
   * Sets the ear-expansion
   */
  public EarDeployGenerator createEarDeploy()
    throws Exception
  {
    DeployContainer<EarDeployController> container
      = new DeployContainer<EarDeployController>();
      
    return new EarDeployGenerator(container, this);
  }

  /**
   * Adds the ear-expansion
   */
  public void addEarDeploy(EarDeployGenerator earDeploy)
    throws Exception
  {
    earDeploy.getDeployContainer().add(earDeploy);
    
    // server/26cc - _appDeploy must be added first, because the
    // _earDeploy addition will automaticall register itself
    _appDeploy.add(new WebAppEarDeployGenerator(_appDeploy, this, earDeploy));

    /*
    _earDeploy.add(earDeploy);
    */
  }

  /**
   * Returns the URL for the container.
   */
  public String getURL()
  {
    return "";
  }

  /**
   * Returns the host name for the container.
   */
  public String getHostName()
  {
    return "";
  }

  // backwards compatibility
  
  /**
   * Sets the war-dir for backwards compatibility.
   */
  public void setWarDir(Path warDir)
    throws ConfigException
  {
    _warGenerator.setPath(warDir);

    if (! _hasWarGenerator) {
      _hasWarGenerator = true;
      addWebAppDeploy(_warGenerator);
    }
  }

  /**
   * Gets the war-dir.
   */
  public Path getWarDir()
  {
    return _warGenerator.getPath();
  }

  /**
   * Sets the war-expand-dir.
   */
  public void setWarExpandDir(Path warDir)
  {
    _warGenerator.setExpandDirectory(warDir);
  }

  /**
   * Gets the war-expand-dir.
   */
  public Path getWarExpandDir()
  {
    return _warGenerator.getExpandDirectory();
  }

  /**
   * Init the container.
   */
  public void init()
    throws Exception
  {
    if (! _lifecycle.toInitializing())
      return;
    
    log.fine(this + " initializing");

    _lifecycle.toInit();
  }

  /**
   * Starts the container.
   */
  protected void start()
  {
    if (! _lifecycle.toActive())
      return;

    /*
    try {
      _earDeploy.start();
    } catch (Throwable e) {
      log.log(Level.WARNING, e.toString(), e);
    }
    */
    
    try {
      _appDeploy.start();
    } catch (Throwable e) {
      log.log(Level.WARNING, e.toString(), e);
    }
  }

  /**
   * Clears the cache
   */
  public void clearCache()
  {
    if (_dispatchServer != null)
      _dispatchServer.clearCache();

    _uriToAppCache.clear();
  }

  /**
   * Creates the invocation.
   */
  public void buildInvocation(Invocation invocation)
    throws Exception
  {
    if (_configException != null) {
      FilterChain chain = new ExceptionFilterChain(_configException);
      invocation.setFilterChain(chain);
      invocation.setDependency(AlwaysModified.create());
      return;
    }
    else if (! _lifecycle.waitForActive(_startWaitTime)) {
      int code = HttpServletResponse.SC_SERVICE_UNAVAILABLE;
      FilterChain chain = new ErrorFilterChain(code);
      invocation.setFilterChain(chain);

      if (_dispatchServer instanceof ServletServer) {
	ServletServer servletServer = (ServletServer) _dispatchServer;
	invocation.setApplication(servletServer.getErrorApplication());
      }

      invocation.setDependency(AlwaysModified.create());
      return;
    }

    if (_rewriteInvocation != null) {
      FilterChain chain = _rewriteInvocation.map(invocation.getURI(),
						 invocation);

      if (chain != null) {
	invocation.setFilterChain(chain);
	return;
      }
    }

    Application app = getApplication(invocation, true);

    if (app != null)
      app.buildInvocation(invocation);
    else {
      int code = HttpServletResponse.SC_NOT_FOUND;
      FilterChain chain = new ErrorFilterChain(code);
      ContextFilterChain contextChain = new ContextFilterChain(chain);
      contextChain.setErrorPageManager(_errorPageManager);
      invocation.setFilterChain(contextChain);
      invocation.setDependency(AlwaysModified.create());
    }
  }

  /**
   * Returns a dispatcher for the named servlet.
   */
  public RequestDispatcher getRequestDispatcher(String url)
  {
    // Currently no caching since this is only used for the error-page directive at the host level
    
    if (url == null)
      throw new IllegalArgumentException(L.l("request dispatcher url can't be null."));
    else if (! url.startsWith("/"))
      throw new IllegalArgumentException(L.l("request dispatcher url `{0}' must be absolute", url));

    Invocation includeInvocation = new Invocation();
    Invocation forwardInvocation = new Invocation();
    Invocation errorInvocation = new Invocation();
    InvocationDecoder decoder = new InvocationDecoder();

    String rawURI = url;

    try {
      decoder.splitQuery(includeInvocation, rawURI);
      decoder.splitQuery(forwardInvocation, rawURI);
      decoder.splitQuery(errorInvocation, rawURI);

      buildIncludeInvocation(includeInvocation);
      buildForwardInvocation(forwardInvocation);
      buildErrorInvocation(errorInvocation);
      
      RequestDispatcher disp = new RequestDispatcherImpl(includeInvocation,
							 forwardInvocation,
							 errorInvocation,
							 getApplication(includeInvocation, false));

      return disp;
    } catch (Exception e) {
      log.log(Level.FINE, e.toString(), e);

      return null;
    }
  }

  /**
   * Creates the invocation.
   */
  public void buildIncludeInvocation(Invocation invocation)
    throws ServletException
  {
    Application app = buildSubInvocation(invocation);

    if (app != null)
      app.buildIncludeInvocation(invocation);
  }

  /**
   * Creates the invocation.
   */
  public void buildForwardInvocation(Invocation invocation)
    throws ServletException
  {
    Application app = buildSubInvocation(invocation);

    if (app != null)
      app.buildForwardInvocation(invocation);
  }

  /**
   * Creates the error invocation.
   */
  public void buildErrorInvocation(Invocation invocation)
    throws ServletException
  {
    Application app = buildSubInvocation(invocation);
    
    if (app != null)
      app.buildErrorInvocation(invocation);
  }

  /**
   * Creates the invocation.
   */
  public void buildLoginInvocation(Invocation invocation)
    throws ServletException
  {
   Application app = buildSubInvocation(invocation);

    if (app != null)
      app.buildErrorInvocation(invocation);
  }

  /**
   * Creates a sub invocation, handing unmapped URLs and stopped applications.
   */
  private Application buildSubInvocation(Invocation invocation)
  {
    if (! _lifecycle.waitForActive(_startWaitTime)) {
      UnavailableException e;
      e = new UnavailableException(invocation.getURI());

      FilterChain chain = new ExceptionFilterChain(e);
      invocation.setFilterChain(chain);
      invocation.setDependency(AlwaysModified.create());
      return null;
    }

    WebAppController appController = getWebAppController(invocation);

    if (appController == null) {
      String url = invocation.getURI();

      FileNotFoundException e = new FileNotFoundException(url);

      FilterChain chain = new ExceptionFilterChain(e);
      invocation.setFilterChain(chain);
      invocation.setDependency(AlwaysModified.create());
      return null;
    }

    Application app = appController.subrequest();

    if (app == null) {
      UnavailableException e;
      e = new UnavailableException(invocation.getURI());

      FilterChain chain = new ExceptionFilterChain(e);
      invocation.setFilterChain(chain);
      invocation.setDependency(AlwaysModified.create());
      return null;
    }

    return app;
  }

  /**
   * Returns the application for the current request.
   */
  private Application getApplication(Invocation invocation,
				     boolean enableRedeploy)
    throws ServletException
  {
    try {
      WebAppController controller = getWebAppController(invocation);

      if (controller != null) {
        Application app;

        if (enableRedeploy)
          app = controller.request();
        else
          app = controller.subrequest();

	if (app == null) {
	  return null;
	}

        invocation.setApplication(app);

        return app;
      }
      else {
        return null;
      }
    } catch (Exception e) {
      throw new ServletException(e);
    }
  }

  /**
   * Returns the application controller for the current request.  Side effect
   * of filling in the invocation's context path and context uri.
   *
   * @param invocation the request's invocation
   *
   * @return the controller or null if none match the url.
   */
  private WebAppController getWebAppController(Invocation invocation)
  {
    WebAppController controller = findByURI(invocation.getURI());

    if (controller == null)
      return null;

    String invocationURI = invocation.getURI();

    String contextPath = controller.getContextPath(invocationURI);

    invocation.setContextPath(invocationURI.substring(0, contextPath.length()));

    String uri = invocationURI.substring(contextPath.length());
    invocation.setContextURI(uri);

    return controller;
  }

  /**
   * Creates the invocation.
   */
  public Application findApplicationByURI(String uri)
    throws Exception
  {
    WebAppController controller = findByURI(uri);

    if (controller != null)
      return controller.request();
    else
      return null;
  }

  /**
   * Creates the invocation.
   */
  public Application findSubApplicationByURI(String uri)
    throws Exception
  {
    WebAppController controller = findByURI(uri);

    if (controller != null)
      return controller.subrequest();
    else
      return null;
  }

  /**
   * Finds the web-app matching the current entry.
   */
  public WebAppController findByURI(String uri)
  {
    if (CauchoSystem.isCaseInsensitive())
      uri = uri.toLowerCase();

    return findByURIImpl(uri);
  }

  /**
   * Finds the web-app for the entry.
   */
  private WebAppController findByURIImpl(String subURI)
  {
    WebAppController controller = _uriToAppCache.get(subURI);

    if (controller != null)
      return controller;
    
    controller = _appDeploy.findController(subURI);

    if (controller != null) {
      _uriToAppCache.put(subURI, controller);
      
      return controller;
    }

    int p = subURI.lastIndexOf('/');

    if (p >= 0) {
      controller = findByURIImpl(subURI.substring(0, p));

      if (controller != null)
	_uriToAppCache.put(subURI, controller);
    }

    return controller;
  }

  /**
   * Returns a list of the applications.
   */
  public ArrayList<WebAppController> getApplicationList()
  {
    return _appDeploy.getControllers();
  }

  /**
   * Returns a list of the applications.
   */
  public ArrayList<EarDeployController> getEntAppList()
  {
    return _earDeploy.getControllers();
  }

  /**
   * Returns true if the application container has been closed.
   */
  public final boolean isDestroyed()
  {
    return _lifecycle.isDestroyed();
  }

  /**
   * Returns true if the application container is active
   */
  public final boolean isActive()
  {
    return _lifecycle.isActive();
  }

  /**
   * Closes the container.
   */
  public boolean stop()
  {
    if (! _lifecycle.toStop())
      return false;

    _earDeploy.stop();
    _appDeploy.stop();

    return true;
  }

  /**
   * Closes the container.
   */
  public void destroy()
  {
    stop();
    
    if (! _lifecycle.toDestroy())
      return;

    _earDeploy.destroy();
    _appDeploy.destroy();
  }

  /**
   * Handles the case where a class loader has completed initialization
   */
  public void classLoaderInit(DynamicClassLoader loader)
  {
  }
  
  /**
   * Handles the case where a class loader is dropped.
   */
  public void classLoaderDestroy(DynamicClassLoader loader)
  {
    destroy();
  }

  /**
   * Handles the case where the environment is starting (after init).
   */
  public void environmentStart(EnvironmentClassLoader loader)
  {
  }
  
  /**
   * Handles the case where the environment is stopping
   */
  public void environmentStop(EnvironmentClassLoader loader)
  {
    stop();
  }
}
