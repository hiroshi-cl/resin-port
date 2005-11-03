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

package com.caucho.amber.table;

import java.util.ArrayList;
import java.util.Collections;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import com.caucho.util.L10N;
import com.caucho.util.CharBuffer;

import com.caucho.config.ConfigException;
import com.caucho.config.LineConfigException;

import com.caucho.amber.AmberManager;
import com.caucho.amber.AmberRuntimeException;

import com.caucho.amber.type.Type;
import com.caucho.amber.type.EntityType;

import com.caucho.amber.connection.AmberConnectionImpl;

import com.caucho.amber.entity.Entity;
import com.caucho.amber.entity.AmberCompletion;
import com.caucho.amber.entity.TableInvalidateCompletion;
import com.caucho.amber.entity.EntityListener;

/**
 * Representation of a database table.
 */
public class Table {
  private static final L10N L = new L10N(Table.class);

  private String _name;

  private String _configLocation;

  private AmberManager _manager;
  
  // The entity type is used to generate primary keys for cascade deletes
  private EntityType _type;

  private ArrayList<Column> _columns = new ArrayList<Column>();

  private ArrayList<LinkColumns> _incomingLinks = new ArrayList<LinkColumns>();
  private ArrayList<LinkColumns> _outgoingLinks = new ArrayList<LinkColumns>();
  
  private ArrayList<Column> _idColumns = new ArrayList<Column>();
  private LinkColumns _dependentIdLink;
  
  private boolean _isReadOnly;
  private long _cacheTimeout = 250;

  private ArrayList<EntityListener> _entityListeners
    = new ArrayList<EntityListener>();

  private TableInvalidateCompletion _invalidateCompletion;
  
  public Table(AmberManager manager, String name)
  {
    _manager = manager;
    _name = name;
  }

  /**
   * Gets the sql table name.
   */
  public String getName()
  {
    return _name;
  }

  /**
   * Sets the config location.
   */
  public void setConfigLocation(String location)
  {
    _configLocation = location;
  }

  /**
   * Returns the location.
   */
  public String getLocation()
  {
    return _configLocation;
  }

  /**
   * Returns the amber manager.
   */
  public AmberManager getAmberManager()
  {
    return _manager;
  }

  /**
   * Sets the entity type.
   */
  public void setType(EntityType type)
  {
    if (_type == null)
      _type = type;
  }

  /**
   * Gets the entity type.
   */
  public EntityType getType()
  {
    return _type;
  }

  /**
   * Returns true if read-only
   */
  public boolean isReadOnly()
  {
    return _isReadOnly;
  }

  /**
   * Sets true if read-only
   */
  public void setReadOnly(boolean isReadOnly)
  {
    _isReadOnly = isReadOnly;
  }

  /**
   * Returns the cache timeout.
   */
  public long getCacheTimeout()
  {
    return _cacheTimeout;
  }

  /**
   * Sets the cache timeout.
   */
  public void setCacheTimeout(long timeout)
  {
    _cacheTimeout = timeout;
  }

  /**
   * Creates a column.
   */
  public Column createColumn(String name, Type type)
  {
    for (int i = 0; i < _columns.size(); i++) {
      Column oldColumn = _columns.get(i);

      if (oldColumn.getName().equals(name))
	return oldColumn;
    }

    Column column = new Column(this, name, type);
    
    _columns.add(column);
    Collections.sort(_columns, new ColumnCompare());

    return column;
  }

  /**
   * Creates a foreign column.
   */
  public ForeignColumn createForeignColumn(String name, Column key)
  {
    for (int i = 0; i < _columns.size(); i++) {
      Column oldColumn = _columns.get(i);

      if (! oldColumn.getName().equals(name)) {
      }
      else if (oldColumn instanceof ForeignColumn) {
	// XXX: check type
	return (ForeignColumn) oldColumn;
      }
      else {
	// XXX: copy props(?)

	ForeignColumn column = new ForeignColumn(this, name, key);
	_columns.set(i, column);
	return column;
      }
    }

    ForeignColumn column = new ForeignColumn(this, name, key);
    
    _columns.add(column);
    Collections.sort(_columns, new ColumnCompare());
    
    return column;
  }

  /**
   * Adds a column.
   */
  public Column addColumn(Column column)
  {
    for (int i = 0; i < _columns.size(); i++) {
      Column oldColumn = _columns.get(i);

      if (! oldColumn.getName().equals(column.getName())) {
      }
      else if (oldColumn instanceof ForeignColumn)
	return oldColumn;
      else if (column instanceof ForeignColumn) {
	_columns.set(i, column);
	return column;
      }
      else
	return oldColumn;
    }
    
    _columns.add(column);
    Collections.sort(_columns, new ColumnCompare());

    return column;
  }

  /**
   * Returns the columns.
   */
  public ArrayList<Column> getColumns()
  {
    return _columns;
  }

  /**
   * Adds an incoming link.
   */
  void addIncomingLink(LinkColumns link)
  {
    assert(! _incomingLinks.contains(link));
    
    _incomingLinks.add(link);
  }

  /**
   * Adds an outgoing link.
   */
  void addOutgoingLink(LinkColumns link)
  {
    assert(! _outgoingLinks.contains(link));

    _outgoingLinks.add(link);
  }

  /**
   * Adds an id column.
   */
  public void addIdColumn(Column column)
  {
    _idColumns.add(column);
  }

  /**
   * Returns the id columns.
   */
  public ArrayList<Column> getIdColumns()
  {
    return _idColumns;
  }

  /**
   * Sets the id link for a dependent table.
   */
  public void setDependentIdLink(LinkColumns link)
  {
    _dependentIdLink = link;
  }

  /**
   * Gets the id link for a dependent table.
   */
  public LinkColumns getDependentIdLink()
  {
    return _dependentIdLink;
  }

  /**
   * Creates the table if missing.
   */
  public void createDatabaseTable(AmberManager amberManager)
    throws ConfigException
  {
    try {
      DataSource ds = amberManager.getDataSource();
      Connection conn = ds.getConnection();
      try {
	Statement stmt = conn.createStatement();

	try {
	  // If the table exists, return
	  
	  String sql = "SELECT 1 FROM " + getName() + " o WHERE 1=0";

	  ResultSet rs = stmt.executeQuery(sql);
	  rs.close();
	  return;
	} catch (SQLException e) {
	}

	String createSQL = generateCreateTableSQL(amberManager);

	stmt.executeUpdate(createSQL);

	stmt.close();
      } finally {
	conn.close();
      }
    } catch (Exception e) {
      throw error(e);
    }
  }

  /**
   * Generates the SQL to create the table.
   */
  private String generateCreateTableSQL(AmberManager amberManager)
  {
    CharBuffer cb = new CharBuffer();

    cb.append("CREATE TABLE " + getName() + "(");

    boolean hasColumn = false;
    for (Column column : _columns) {
      String columnSQL = column.generateCreateTableSQL(amberManager);

      if (columnSQL == null) {
      }
      else if (! hasColumn) {
	hasColumn = true;
	cb.append("\n  " + columnSQL);
      }
      else {
	cb.append(",\n  " + columnSQL);
      }
    }

    cb.append("\n)");

    return cb.close();
  }

  /**
   * Creates the table if missing.
   */
  public void validateDatabaseTable(AmberManager amberManager)
    throws ConfigException
  {
    try {
      DataSource ds = amberManager.getDataSource();
      Connection conn = ds.getConnection();
      try {
	Statement stmt = conn.createStatement();

	try {
	  // If the table exists, return
	  
	  String sql = "SELECT 1 FROM " + getName() + " o WHERE 1=0";

	  ResultSet rs = stmt.executeQuery(sql);
	  rs.close();
	} catch (SQLException e) {
	  throw error(L.l("'{0}' is not a valid database table.  Either the table needs to be created or the create-database-tables attribute must be set.\n\n{1}",
					getName(), e.toString()), e);
	}
      } finally {
	conn.close();
      }
    } catch (ConfigException e) {
      throw e;
    } catch (Exception e) {
      throw error(e);
    }
    
    for (Column column : _columns) {
      column.validateDatabase(amberManager);
    }
  }

  /**
   * Returns the table's invalidation.
   */
  public AmberCompletion getInvalidateCompletion()
  {
    if (_invalidateCompletion == null)
      _invalidateCompletion = new TableInvalidateCompletion(getName());

    return _invalidateCompletion;
  }

  /**
   * Returns the table's invalidation.
   */
  public AmberCompletion getUpdateCompletion()
  {
    return getInvalidateCompletion();
  }

  /**
   * Returns the table's invalidation.
   */
  public AmberCompletion getDeleteCompletion()
  {
    return getInvalidateCompletion();
  }

  /**
   * Adds a listener for create/delete events
   */
  public void addEntityListener(EntityListener listener)
  {
    if (! _entityListeners.contains(listener))
      _entityListeners.add(listener);
  }

  /**
   * Returns true if there are any listeners.
   */
  public boolean hasListeners()
  {
    return _entityListeners.size() > 0;
  }

  /**
   * Returns true if any deletes of this object are cascaded.
   */
  public boolean isCascadeDelete()
  {
    // check if any of the incoming links have a target cascade delete
    for (int i = 0; i < _incomingLinks.size(); i++) {
      LinkColumns link = _incomingLinks.get(i);

      if (link.isSourceCascadeDelete())
	return true;
    }
    
    // check if any of the outgoing links have a source cascade delete
    for (int i = 0; i < _outgoingLinks.size(); i++) {
      LinkColumns link = _outgoingLinks.get(i);

      if (link.isTargetCascadeDelete())
	return true;
    }

    return false;
  }
  
  /**
   * Called before the entity is deleted.
   */
  public void beforeEntityDelete(AmberConnectionImpl aConn, Entity entity)
  {
    try {
      for (int i = 0; i < _entityListeners.size(); i++) {
	EntityListener listener = _entityListeners.get(i);

	listener.beforeEntityDelete(aConn, entity);
      }
      // getHome().completeDelete(aConn, key);

      for (int i = 0; i < _incomingLinks.size(); i++) {
	LinkColumns link = _incomingLinks.get(i);

	link.beforeTargetDelete(aConn, entity);
      }

      aConn.addCompletion(getDeleteCompletion());
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new AmberRuntimeException(e);
    }
  }

  protected ConfigException error(String msg, Throwable e)
  {
    if (_configLocation != null)
      return new LineConfigException(_configLocation + msg, e);
    else
      return new ConfigException(msg, e);
  }

  protected ConfigException error(Throwable e)
  {
    if (_configLocation != null)
      return new LineConfigException(_configLocation + e.getMessage(), e);
    else
      return new ConfigException(e);
  }

  /**
   * Printable version of the entity.
   */
  public String toString()
  {
    return "Table[" + getName() + "]";
  }
}
