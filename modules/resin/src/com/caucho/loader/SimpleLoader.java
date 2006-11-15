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
 *   Free SoftwareFoundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.loader;

import java.io.IOException;

import java.net.URL;

import java.util.logging.Logger;
import java.util.logging.Level;

import java.security.CodeSource;

import java.security.cert.Certificate;

import javax.annotation.*;

import com.caucho.log.Log;

import com.caucho.util.CharBuffer;

import com.caucho.config.ConfigException;

import com.caucho.vfs.*;

/**
 * Class loader which checks for changes in class files and automatically
 * picks up new jars.
 */
public class SimpleLoader extends Loader {
  private static final Logger log = Log.open(SimpleLoader.class);
  
  // The class directory
  private Path _path;
  private String _prefix;
  private String _pathPrefix;

  private CodeSource _codeSource;

  /**
   * Null constructor for the simple loader.
   */
  public SimpleLoader()
  {
  }

  /**
   * Creates the simple loader with the specified path.
   *
   * @param path specifying the root of the resources
   */
  public SimpleLoader(Path path)
  {
    setPath(path);
  }

  /**
   * Creates the simple loader with the specified path and prefix.
   *
   * @param path specifying the root of the resources
   * @param prefix the prefix that the resources must match
   */
  public SimpleLoader(Path path, String prefix)
  {
    setPath(path);
    setPrefix(prefix);
  }

  /**
   * Create a class loader based on the SimpleLoader
   *
   * @param parent parent class loader
   * @param path traditional classpath
   * @param prefix the class prefix restriction
   *
   * @return the new ClassLoader
   */
  public static DynamicClassLoader create(ClassLoader parent,
                                          Path path,
                                          String prefix)
  {
    DynamicClassLoader loader = new DynamicClassLoader(parent, false);

    loader.addLoader(new SimpleLoader(path, prefix));

    loader.init();

    return loader;
  }

  /**
   * Create a class loader based on the SimpleLoader
   *
   * @param parent parent class loader
   * @param path traditional classpath
   *
   * @return the new ClassLoader
   */
  public static DynamicClassLoader create(ClassLoader parent,
                                          Path path)
  {
    DynamicClassLoader loader = new DynamicClassLoader(parent, false);

    loader.addLoader(new SimpleLoader(path));

    loader.init();
    
    return loader;
  }

  /**
   * Create a class loader based on the SimpleLoader
   *
   * @param path traditional classpath
   *
   * @return the new ClassLoader
   */
  public static DynamicClassLoader create(Path path)
  {
    ClassLoader parent = Thread.currentThread().getContextClassLoader();

    return create(parent, path);
  }

  /**
   * Sets the resource directory.
   */
  public void setPath(Path path)
  {
    if (path.getPath().endsWith(".jar") ||
        path.getPath().endsWith(".zip"))
      path = JarPath.create(path);

    _path = path;
  }

  /**
   * Gets the resource path.
   */
  public Path getPath()
  {
    return _path;
  }

  /**
   * Sets the resource prefix
   */
  public void setPrefix(String prefix)
  {
    _prefix = prefix;
    
    if (prefix != null)
      _pathPrefix = prefix.replace('.', '/');
  }

  /**
   * Gets the resource prefix
   */
  public String getPrefix()
  {
    return _prefix;
  }

  /**
   * Sets the owning class loader.
   */
  public void setLoader(DynamicClassLoader loader)
  {
    super.setLoader(loader);

    loader.addURL(_path);
  }

  /**
   * Initializes the loader.
   */
  @PostConstruct
  public void init()
    throws ConfigException
  {
    try {
      _codeSource = new CodeSource(new URL(_path.getURL()), (Certificate []) null);
    } catch (Exception e) {
      log.log(Level.FINE, e.toString(), e);
    }
  }

  /**
   * Given a class or resource name, returns a patch to that resource.
   *
   * @param name the class or resource name.
   *
   * @return the path representing the class or resource.
   */
  public Path getPath(String name)
  {
    if (_prefix != null && _pathPrefix == null)
      _pathPrefix = _prefix.replace('.', '/');

    if (_pathPrefix != null && ! name.startsWith(_pathPrefix))
      return null;

    return _path.lookup(name);
  }

  /**
   * Returns the code source for the directory.
   */
  protected CodeSource getCodeSource(Path path)
  {
    return _codeSource;
  }

  /**
   * Adds the class of this resource.
   */
  protected String getClassPath(String head)
  {
    CharBuffer cb = new CharBuffer();

    if (! head.equals("")) {
      cb.append(head);
      cb.append(Path.getPathSeparatorChar());
    }
    
    if (_path instanceof JarPath)
      cb.append(((JarPath) _path).getContainer().getNativePath());
    else if (_path.isDirectory())
      cb.append(_path.getNativePath());

    return cb.toString();
  }

  /**
   * Returns a printable representation of the loader.
   */
  public String toString()
  {
    if (_prefix != null)
      return "SimpleLoader[" + _path + ",prefix=" + _prefix + "]";
    else
      return "SimpleLoader[" + _path + "]";
  }
}
