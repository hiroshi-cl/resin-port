/*
 * Copyright (c) 1998-2011 Caucho Technology -- all rights reserved
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

package com.caucho.server.distcache;

import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;

import com.caucho.config.ConfigException;
import com.caucho.config.types.Period;
import com.caucho.env.distcache.DistCacheSystem;
import com.caucho.util.L10N;
import com.caucho.vfs.Path;

/**
 * Configuration distributed stores.
 */
public class PersistentStoreConfig
{
  private static final L10N L = new L10N(PersistentStoreConfig.class);

  private String _type;

  private DataSource _dataSource;
  private String _tableName;
  
  private boolean _isBackup = true;
  private boolean _isTriplicate = true;
  private boolean _isAlwaysSave = false;

  /**
   * Sets the persistent store name.
   */
  public void setJndiName(String name)
  {
  }
  
  /**
   * Sets the persistent store type.
   */
  public void setType(String type)
    throws ConfigException
  {
    _type = type;
  }

  public void setDataSource(DataSource dataSource)
  {
    if (dataSource == null)
      throw new NullPointerException();
    
    _dataSource = dataSource;
  }

  @Deprecated
  public void setPath(Path path)
  {
  }
  
  public void setTableName(String tableName)
  {
    _tableName = tableName;
  }

  public void setAlwaysSave(boolean isAlwaysSave)
  {
    _isAlwaysSave = isAlwaysSave;
  }

  public boolean isAlwaysSave()
  {
    return _isAlwaysSave;
  }

  public void setAlwaysLoad(boolean isAlwaysLoad)
  {
  }

  public void setBackup(boolean isBackup)
  {
    _isBackup = isBackup;
  }

  public void setSaveBackup(boolean isBackup)
  {
    setBackup(isBackup);
  }

  public void setTriplicate(boolean isTriplicate)
  {
    _isTriplicate = isTriplicate;
  }

  public boolean isSaveTriplicate()
  {
    return _isTriplicate;
  }

  public boolean isSaveBackup()
  {
    return _isBackup;
  }

  public void setWaitForAcknowledge(boolean isWait)
  {
  }

  public void setMaxIdleTime(Period period)
  {
  }

  public PersistentStoreConfig createInit()
  {
    return this;
  }

  @PostConstruct
  public void init()
    throws Exception
  {
    if ("jdbc".equals(_type) && _dataSource == null)
      throw new ConfigException(L.l("'jdbc' persistent-store requires a data-source"));
    
    if (_dataSource != null) {
      DistCacheSystem system = DistCacheSystem.getCurrent();

      system.setJdbcDataSource(_dataSource);
      /*
      if (system.getJdbcCacheManager() == null) {
        ResinSystem resinSystem = ResinSystem.getCurrent();
        
        JdbcCacheManager jdbcManager
          = new JdbcCacheManager(resinSystem, _dataSource);
        
        system.setJdbcCacheManager(jdbcManager);
      }
      */
    }
    /*
    if (_name.startsWith("java:comp"))
      Jndi.bindDeep(_name, _store);
    else
      Jndi.bindDeep("java:comp/env/" + _name, _store);
    */
  }
}
