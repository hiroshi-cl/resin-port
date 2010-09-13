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

package com.caucho.env.deploy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.config.ConfigException;
import com.caucho.config.types.FileSetType;
import com.caucho.config.types.Period;
import com.caucho.env.repository.Repository;
import com.caucho.env.repository.RepositoryService;
import com.caucho.env.repository.RepositoryTagEntry;
import com.caucho.env.repository.RepositoryTagListener;
import com.caucho.loader.Environment;
import com.caucho.server.util.CauchoSystem;
import com.caucho.util.Alarm;
import com.caucho.util.AlarmListener;
import com.caucho.util.Crc64;
import com.caucho.util.L10N;
import com.caucho.util.WeakAlarm;
import com.caucho.vfs.Path;

/**
 * The generator for the deploy
 */
abstract public class ExpandDeployGenerator<E extends ExpandDeployController<?>>
  extends DeployGenerator<E>
  implements AlarmListener, DeployUpdateListener, RepositoryTagListener
{
  private static final Logger log
    = Logger.getLogger(ExpandDeployGenerator.class.getName());
  private static final L10N L = new L10N(ExpandDeployGenerator.class);

  private static final long MIN_CRON_INTERVAL = 5000L;

  private final String _id;
  
  private Path _path; // default path
  private ClassLoader _loader;

  private Path _containerRootDirectory;
  private Path _archiveDirectory;
  private Path _expandDirectory;

  private final Repository _repository;
  
  private final DeployControllerService _deployService;
  
  private String _entryNamePrefix = "";

  private String _extension = ".jar";
  
  private String _expandPrefix = "";
  private String _expandSuffix = "";

  private boolean _isVersioning;
  
  private ArrayList<String> _requireFiles = new ArrayList<String>();

  private FileSetType _expandCleanupFileSet;
  
  private ExpandDirectoryManager _directoryManager;
  private ExpandArchiveManager _archiveManager;
  private ExpandRepositoryManager _repositoryManager;
  
  private Alarm _alarm;
  private long _cronInterval;

  //
  // runtime values
  //
  
  private ExpandManager _expandManager;
  private Set<String> _deployedKeys = new TreeSet<String>();
  
  private long _lastCheckTime;
  private AtomicBoolean _isChecking = new AtomicBoolean();
  private long _checkInterval = 1000L;
  private long _digest;
  private volatile boolean _isModified;
  private AtomicBoolean _isDeploying = new AtomicBoolean();

  /**
   * Creates the deploy.
   */
  public ExpandDeployGenerator(String id,
                               DeployContainer<E> container,
                               Path containerRootDirectory)
  {
    super(container);
    
    _id = id;

    _containerRootDirectory = containerRootDirectory;

    _alarm = new WeakAlarm(this);

    _cronInterval = Environment.getDependencyCheckInterval();
    if (_cronInterval < MIN_CRON_INTERVAL)
      _cronInterval = MIN_CRON_INTERVAL;

    _loader = Thread.currentThread().getContextClassLoader();
    
    _deployService = DeployControllerService.create();
    _deployService.addUpdateListener(this);
    
    _repository = RepositoryService.getCurrentRepository();
    _repository.addListener(id, this);
  }
  
  public String getId()
  {
    return _id;
  }

  Path getContainerRootDirectory()
  {
    return _containerRootDirectory;
  }

  /**
   * Sets the war expand dir to check for new archive files.
   */
  public void setArchiveDirectory(Path path)
  {
    _archiveDirectory = path;
  }

  /**
   * Gets the war expand directory.
   */
  public Path getArchiveDirectory()
  {
    if (_archiveDirectory != null)
      return _archiveDirectory;
    else
      return _path;
  }

  /**
   * Returns the location for deploying an archive with the specified name.
   *
   * @param name a name, without an extension
   */
  public Path getArchivePath(String name)
  {
    return getArchiveDirectory().lookup(name + getExtension());
  }

  /**
   * Sets the war expand dir to check for new applications.
   */
  public void setExpandPath(Path path)
  {
    log.config("Use <expand-directory> instead of <expand-path>.  <expand-path> is deprecated.");

    setExpandDirectory(path);
  }

  /**
   * Sets the war expand dir to check for new applications.
   */
  public void setExpandDirectory(Path path)
  {
    _expandDirectory = path;
  }

  /**
   * Gets the war expand directory.
   */
  public Path getExpandDirectory()
  {
    if (_expandDirectory != null)
      return _expandDirectory;
    else
      return _path;
  }

  /**
   * Sets the dependency check interval.
   */
  public void setDependencyCheckInterval(Period period)
  {
    _cronInterval = period.getPeriod();

    if (_cronInterval < 0)
      _cronInterval = Period.INFINITE;
    else if (_cronInterval < MIN_CRON_INTERVAL)
      _cronInterval = MIN_CRON_INTERVAL;
  }

  public long getDependencyCheckInterval()
  {
    return _cronInterval;
  }

  /**
   * Sets the expand remove file set.
   */
  public void setExpandCleanupFileset(FileSetType fileSet)
  {
    _expandCleanupFileSet = fileSet;
  }

  /**
   * Sets the extension.
   */
  public void setExtension(String extension)
    throws ConfigException
  {
    if (! extension.startsWith("."))
      throw new ConfigException(L.l("deployment extension '{0}' must begin with '.'",
                                    extension));

    _extension = extension;
  }

  /**
   * Returns the extension.
   */
  public String getExtension()
  {
    return _extension;
  }

  /**
   * Sets the expand prefix to check for new applications.
   */
  public void setExpandPrefix(String prefix)
    throws ConfigException
  {
    if (! prefix.equals("")
        && ! prefix.startsWith("_")
        && ! prefix.startsWith("."))
      throw new ConfigException(L.l("expand-prefix '{0}' must start with '.' or '_'.",
                                    prefix));

    _expandPrefix = prefix;
  }

  /**
   * Gets the expand prefix.
   */
  public String getExpandPrefix()
  {
    return _expandPrefix;
  }

  /**
   * Sets the expand suffix to check for new applications.
   */
  public void setExpandSuffix(String suffix)
    throws ConfigException
  {
    _expandSuffix = suffix;
  }

  /**
   * Gets the expand suffix.
   */
  public String getExpandSuffix()
  {
    return _expandSuffix;
  }

  /**
   * The repository
   */
  public Repository getRepository()
  {
    return _repository;
  }

  public void setEntryNamePrefix(String entryNamePrefix)
  {
    _entryNamePrefix = entryNamePrefix;
  }

  /**
   * Gets the default path.
   */
  public Path getPath()
  {
    return _path;
  }

  /**
   * Sets the deploy directory.
   */
  public void setPath(Path path)
  {
    _path = path;
  }

  /**
   * Adds a required file in the expansion.
   */
  public void addRequireFile(String file)
    throws ConfigException
  {
    _requireFiles.add(file);
  }

  /**
   * Sets true to enable versioning
   */
  public void setVersioning(boolean isVersioning)
  {
    _isVersioning = isVersioning;
  }

  /**
   * Sets true to enable versioning
   */
  public boolean isVersioning()
  {
    return _isVersioning;
  }

  /**
   * Returns the log.
   */
  @Override
  protected Logger getLog()
  {
    return log;
  }

  /**
   * Configuration checks on init.
   */
  @Override
  protected void initImpl()
    throws ConfigException
  {
    super.initImpl();

    if (getExpandDirectory() == null)
      throw new ConfigException(L.l("<expand-directory> must be specified for deployment of archive expansion."));

    if (getArchiveDirectory() == null)
      throw new ConfigException(L.l("<archive-directory> must be specified for deployment of archive expansion."));
    
    String id = getId();
    
    _directoryManager = new ExpandDirectoryManager(id, 
                                                   getExpandDirectory(),
                                                   getExpandPrefix(),
                                                   getExpandSuffix(),
                                                   _requireFiles);
    
    _archiveManager = new ExpandArchiveManager(id,
                                               getArchiveDirectory(),
                                               getExtension());
    
    _repositoryManager = new ExpandRepositoryManager(id);
  }

  /**
   * Starts the deploy.
   */
  @Override
  protected void startImpl()
  {
    super.startImpl();
    
    deploy();
    
    handleAlarm(_alarm);
  }

  /**
   * Returns the location of an expanded archive, or null if no archive with
   * the passed name is deployed.
   *
   * @param name a name, without an extension
   */
  public Path getExpandPath(String name)
  {
    if (! isDeployedKey(nameToKey(name)))
      return null;

    return _directoryManager.getExpandPath(nameToKey(name));

    /*
    if (expandDir.isDirectory())
      return expandDir;

    Path extPath = getExpandDirectory().lookup(name + _extension);
    
    if (extPath.isDirectory())
      return extPath;
    else
      return expandDir;
    */
  }

  /**
   * Returns true if the deployment has modified.
   */
  @Override
  public boolean isModified()
  {
    if (! _isChecking.compareAndSet(false, true)) {
      return _isModified;
    }

    try {
      long now = Alarm.getCurrentTime();
      
      if (now < _lastCheckTime + _checkInterval) {
        return _isModified;
      }

      _lastCheckTime = Alarm.getCurrentTime();

      _isModified = _expandManager.isModified();

      return _isModified;
    } catch (Exception e) {
      log.log(Level.FINE, e.toString(), e);
      
      return false;
    } finally {
      _isChecking.set(false);
    }
  }

  /**
   * Log the reason for modification
   */
  @Override
  public boolean logModified(Logger log)
  {
    return _expandManager.logModified(log);
  }

  /**
   * Returns the deployed keys.
   */
  @Override
  protected void fillDeployedNames(Set<String> names)
  {
    updateIfModified();
    
    for (String key : _deployedKeys) {
      names.add(keyToName(key));
    }
  }

  /**
   * Return true for a matching key.
   */
  protected boolean isDeployedKey(String key)
  {
    return _deployedKeys.contains(key);
  }

  /**
   * Creates a new entry.
   */
  abstract protected E createController(ExpandVersion version);

  protected String keyToName(String key)
  {
    return key;
  }

  protected String nameToKey(String name)
  {
    return name;
  }
  
  /**
   * Redeploys if modified.
   */
  @Override
  public void updateIfModified()
  {
    if (isModified()) {
      deploy();
    }
  }

  /**
   * Deploys the objects.
   */
  @Override
  public final void update()
  {
    if (! _isDeploying.compareAndSet(false, true))
      return;

    Thread thread = Thread.currentThread();
    ClassLoader oldLoader = thread.getContextClassLoader();

    try {
      thread.setContextClassLoader(_loader);
      
      Set<String> oldKeys = _deployedKeys;
      
      deploy();
      
      Set<String> newKeys = _deployedKeys;

      if (oldKeys.equals(newKeys))
        return;

      ArrayList<String> updatedKeys = new ArrayList<String>();

      for (String key : oldKeys) {
        if (! newKeys.contains(key))
          updatedKeys.add(key);
      }

      for (String key : newKeys) {
        if (! oldKeys.contains(key))
          updatedKeys.add(key);
      }

      for (String key : updatedKeys) {
        getDeployContainer().update(keyToName(key));
      }
    } finally {
      thread.setContextClassLoader(oldLoader);
      
      _isDeploying.set(false);
    }
  }
  
  private void deploy()
  {
    try {
      _expandManager = new ExpandManager(getId(),
                                         _directoryManager,
                                         _archiveManager,
                                         _repositoryManager);
      
      _deployedKeys = _expandManager.getBaseKeySet();
      
      _isModified = false;
    } catch (Exception e) {
      log.log(Level.WARNING, e.toString(), e);
    }
  }

  /**
   * Finds the matching entry.
   */
  @Override
  public final E generateController(String name)
  {
    updateIfModified();

    Thread thread = Thread.currentThread();
    ClassLoader oldLoader = thread.getContextClassLoader();
    try {
      thread.setContextClassLoader(getParentClassLoader());
      
      String key = nameToKey(name);
      
      ExpandVersion version = _expandManager.getPrimaryVersion(key);
      
      if (version == null)
        version = _expandManager.getVersion(key);
      
      if (version == null)
        return null;
      
      E controller = createController(version);

      if (controller == null)
        return null;

      controller.setExpandCleanupFileSet(_expandCleanupFileSet);

      // _controllerNames.add(name); // server/1d19
      
      return controller;
    } finally {
      thread.setContextClassLoader(oldLoader);
    }
  }
  
  //
  // DeployNetworkService callbacks
  //
  
  @Override
  public void onUpdate(String tag)
  {
    update();
  }

  public String[] getNames()
  {
    Set<String> deployedKeys = _deployedKeys;
    
    String[] names = new String[deployedKeys.size()];

    int i = 0;

    for (String key : deployedKeys) {
      names[i++] = key;
    }

    return names;
  }

  private String getNamesAsString()
  {
    StringBuilder builder = new StringBuilder();

    for (String name : _deployedKeys) {
      if (builder.length() > 0)
        builder.append(", ");

      builder.append(name);
    }

    builder.insert(0, '[');
    builder.append(']');

    return builder.toString();
  }

  /**
   * Deploy the archive.
   */
  public boolean deploy(String key)
  {
    update();
    
    DeployController<?> controller
      = getDeployContainer().findController(keyToName(key));

    if (controller == null) {
      if (log.isLoggable(Level.FINE))
        log.finer(L.l("{0} can't deploy '{1}' because it's not a known controller: {2}",
                      this, key, getNamesAsString()));

      return false;
    }
    
    return true;
  }

  /**
   * Start the archive.
   */
  public boolean start(String name)
  {
    DeployController<?> controller
      = getDeployContainer().findController(keyToName(name));

    if (controller == null) {
      if (log.isLoggable(Level.FINE))
        log.log(Level.FINE, L.l("{0} unknown name '{1}' in start", this, name));

      if (log.isLoggable(Level.FINER))
        log.log(Level.FINER, L.l("{0} known names are {1} in start", this, getNamesAsString()));

      return false;
    }

    controller.start();
    
    return true;
  }

  /**
   * Returns an exception for the named archive or null if there is no exception
   */
  public Throwable getConfigException(String name)
  {
    ExpandDeployController<?> controller
      = getDeployContainer().findController(keyToName(name));

    if (controller == null) {
      if (log.isLoggable(Level.FINE))
        log.log(Level.FINE, L.l("unknown name '{0}'", name));

      if (log.isLoggable(Level.FINER))
        log.log(Level.FINER, L.l("known names are {0}", getNamesAsString()));

      return new ConfigException(L.l("unknown name '{0}'", name));
    }

    return controller.getConfigException();
  }
  /**
   * Stop the archive.
   */
  public boolean stop(String name)
  {
    DeployController<?> controller
      = getDeployContainer().findController(keyToName(name));

    if (controller == null) {
      if (log.isLoggable(Level.FINE))
        log.log(Level.FINE, L.l("unknown name '{0}'", name));

      if (log.isLoggable(Level.FINER))
        log.log(Level.FINER, L.l("known names are {0}", getNamesAsString()));

      return false;
    }

    controller.stop();
    return true;
  }

  /**
   * Undeploy the archive.
   */
  public boolean undeploy(String name)
  {
    DeployController<?> controller
      = getDeployContainer().findController(keyToName(name));

    if (controller == null) {
      if (log.isLoggable(Level.FINE))
        log.log(Level.FINE, L.l("unknown name '{0}'", name));

      if (log.isLoggable(Level.FINER))
        log.log(Level.FINER, L.l("known names are {0}", getNamesAsString()));

      return false;
    }

    Path archivePath = getArchivePath(name);
    Path expandPath = getExpandPath(name);

    controller.stop();

    try {
      if (log.isLoggable(Level.FINEST))
        log.log(Level.FINEST, L.l("deleting {0}", archivePath));

      archivePath.removeAll();
    }
    catch (IOException ex) {
      if (log.isLoggable(Level.FINE))
        log.log(Level.FINE, ex.toString(), ex);
    }

    try {
      if (expandPath != null) {
        if (log.isLoggable(Level.FINEST))
          log.log(Level.FINEST, L.l("deleting {0}", expandPath));

        expandPath.removeAll();
      }
    }
    catch (IOException ex) {
      if (log.isLoggable(Level.FINE))
        log.log(Level.FINE, ex.toString(), ex);
    }

    getDeployContainer().update(keyToName(name));

    return true;
  }

  @Override
  public void onTagChange(String tag)
  {
    _lastCheckTime = 0;

    alarm();
  }


  /**
   * Checks for updates.
   */
  @Override
  public void handleAlarm(Alarm alarm)
  {
    if (isDestroyed())
      return;
    
    try {
      alarm();
    } catch (Exception e) {
      log.log(Level.WARNING, e.toString(), e);
    } finally {
      _alarm.queue(_cronInterval);
    }
  }
  
  private void alarm()
  {
    // XXX: tck, but no QA test
    // server/10ka
    if (DeployMode.AUTOMATIC.equals(getRedeployMode()) && isActive()) {
      updateIfModified();
    }
  }

  /**
   * Stops the deploy.
   */
  @Override
  protected void stopImpl()
  {
    _alarm.dequeue();
    
    if (_deployService != null)
      _deployService.removeUpdateListener(this);

    super.stopImpl();
  }

  /**
   * Tests for equality.
   */
  @Override
  public boolean equals(Object o)
  {
    if (o == null || ! getClass().equals(o.getClass()))
      return false;

    ExpandDeployGenerator<?> deploy = (ExpandDeployGenerator<?>) o;

    Path expandDirectory = getExpandDirectory();
    Path deployExpandDirectory = deploy.getExpandDirectory();

    if (expandDirectory != deployExpandDirectory &&
        (expandDirectory == null ||
         ! expandDirectory.equals(deployExpandDirectory)))
      return false;

    return true;
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + getExpandDirectory() + "]";
  }
}
