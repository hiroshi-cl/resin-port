/*
 * Copyright (c) 1998-2010 Caucho Technology -- all rights reserved
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

package com.caucho.ejb.cfg;

import java.util.*;
import java.util.logging.*;

import com.caucho.config.*;
import com.caucho.config.inject.InjectManager;
import com.caucho.ejb.manager.EjbManager;
import com.caucho.loader.*;
import com.caucho.util.*;
import com.caucho.vfs.*;

/**
 * Manages the EJB configuration files.
 */
public class EjbConfigManager extends EjbConfig {
  private static final L10N L = new L10N(EjbConfigManager.class);
  private static final Logger log
    = Logger.getLogger(EjbConfigManager.class.getName());

  private final HashMap<Path,EjbRootConfig> _rootConfigMap
    = new HashMap<Path,EjbRootConfig>();

  private final ArrayList<EjbRootConfig> _rootPendingList
    = new ArrayList<EjbRootConfig>();
  
  private final ArrayList<Path> _pathPendingList = new ArrayList<Path>();

  public EjbConfigManager(EjbManager ejbContainer)
  {
    super(ejbContainer);
  }

  /**
   * Returns an EjbRootConfig for a class-loader root.
   */
  public EjbRootConfig createRootConfig(Path root)
  {
    EjbRootConfig rootConfig = _rootConfigMap.get(root);

    if (rootConfig == null) {
      rootConfig = new EjbRootConfig(root);
      _rootConfigMap.put(root, rootConfig);
      _rootPendingList.add(rootConfig);

      String ejbModuleName = getEjbModuleName(root);

      Path ejbJarXml = root.lookup("META-INF/ejb-jar.xml");

      if (ejbJarXml.canRead()) {
        EjbJar ejbJar = configurePath(root, ejbModuleName);

        rootConfig.setModuleName(ejbJar.getModuleName());
      }
      else {
        rootConfig.setModuleName(ejbModuleName);
      }
    }

    return rootConfig;
  }
  
  public void configureRootPath(Path root)
  {
    String ejbModuleName = getEjbModuleName(root);

    Path ejbJarXml = root.lookup("META-INF/ejb-jar.xml");

    if (ejbJarXml.canRead()) {
      EjbJar ejbJar = configurePath(root, ejbModuleName);
    }
  }
  
  public void start()
  {
    InjectManager.create().update();
    
    ArrayList<EjbRootConfig> pendingList
      = new ArrayList<EjbRootConfig>(_rootPendingList);
    _rootPendingList.clear();

    /*
    for (EjbRootConfig rootConfig : pendingList) {
      for (String className : rootConfig.getClassNameList()) {
        addIntrospectableClass(className, rootConfig.getModuleName());
      }
    }
    */

    configurePaths();

    configure();

    deploy();
  }

  /**
   * Adds a path for an EJB config file to the config list.
   */
  @Override
  public void addEjbPath(Path root)
  {
    if (_pathPendingList.contains(root))
      return;

    _pathPendingList.add(root);
  }

  private String getEjbModuleName(Path root)
  {
    if (root instanceof JarPath) {
      String jarName = ((JarPath) root).getContainer().getTail();

      return jarName.substring(0, jarName.length() - ".jar".length());
    }

    return root.getTail();
  }

  private EjbJar configurePath(Path root)
  {
    return configurePath(root, getEjbModuleName(root));
  }

  private EjbJar configurePath(Path root, String ejbModuleName)
  {
    if (root.getScheme().equals("jar"))
      root.setUserPath(root.getURL());

    Path path = root.lookup("META-INF/ejb-jar.xml");

    Environment.addDependency(path);

    EjbJar ejbJar = new EjbJar(this, ejbModuleName);

    try {
      if (log.isLoggable(Level.FINE))
        log.fine(this + " reading " + root.getURL());

      new Config().configure(ejbJar, path, getSchema());

      return ejbJar;
    } catch (ConfigException e) {
      throw e;
    } catch (Exception e) {
      throw ConfigException.create(e);
    }
  }

  private void configurePaths()
  {
    ArrayList<Path> pathList = new ArrayList<Path>(_pathPendingList);
    _pathPendingList.clear();

    for (Path path : pathList) {
      configurePath(path);
    }
  }
}
