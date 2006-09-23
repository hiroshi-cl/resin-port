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
 * @author Scott Ferguson
 */

package com.caucho.amber.gen;

import java.io.*;
import java.util.*;

import com.caucho.util.L10N;

import com.caucho.make.PersistentDependency;

import com.caucho.java.JavaWriter;

import com.caucho.java.gen.ClassComponent;

import com.caucho.loader.Environment;

import com.caucho.amber.type.EntityType;
import com.caucho.amber.type.SubEntityType;
import com.caucho.amber.type.Type;

import com.caucho.amber.field.AmberField;
//import com.caucho.amber.field.Field;
//import com.caucho.amber.field.FieldType;
import com.caucho.amber.field.Id;
import com.caucho.amber.field.IdField;
import com.caucho.amber.field.StubMethod;
import com.caucho.amber.field.VersionField;

import com.caucho.amber.table.Table;
import com.caucho.amber.table.Column;

import com.caucho.bytecode.JMethod;

/**
 * Generates the Java code for the wrapped object.
 */
public class EntityComponent extends ClassComponent {
  private static final L10N L = new L10N(EntityComponent.class);

  private String _baseClassName;
  private String _extClassName;

  private EntityType _entityType;

  private ArrayList<PersistentDependency> _dependencies =
    new ArrayList<PersistentDependency>();

  public EntityComponent()
  {
  }

  /**
   * Sets the bean info for the generator
   */
  public void setEntityType(EntityType entityType)
  {
    _entityType = entityType;

    _dependencies.addAll(entityType.getDependencies());

    for (int i = 0; i < _dependencies.size(); i++)
      Environment.addDependency(_dependencies.get(i));
  }

  /**
   * Sets the base class name
   */
  public void setBaseClassName(String baseClassName)
  {
    _baseClassName = baseClassName;
  }

  /**
   * Gets the base class name
   */
  public String getBaseClassName()
  {
    return _baseClassName;
  }

  /**
   * Sets the ext class name
   */
  public void setExtClassName(String extClassName)
  {
    _extClassName = extClassName;
  }

  /**
   * Sets the ext class name
   */
  public String getClassName()
  {
    return _extClassName;
  }

  /**
   * Get bean class name.
   */
  public String getBeanClassName()
  {
    // return _entityType.getBeanClass().getName();
    return _baseClassName;
  }

  /**
   * Returns the dependencies.
   */
  public ArrayList<PersistentDependency> getDependencies()
  {
    return _dependencies;
  }

  /**
   * Starts generation of the Java code
   */
  public void generate(JavaWriter out)
    throws IOException
  {
    try {
      generateHeader(out);

      if (!_entityType.isEmbeddable()) {
        generateInit(out);

        HashSet<Object> completedSet = new HashSet<Object>();

        generatePrologue(out, completedSet);
      }

      generateGetEntityType(out);

      if (!_entityType.isEmbeddable())
        generateMatch(out);

      generateFields(out);

      generateMethods(out);

      if (!_entityType.isEmbeddable()) {
        generateDetach(out);

        generateLoad(out);

        int min = 0;
        if (_entityType.getParentType() != null)
          min = _entityType.getParentType().getLoadGroupIndex() + 1;
        int max = _entityType.getLoadGroupIndex();

        for (int i = min; i <= max; i++)
          generateLoadGroup(out, i);

        if (!_entityType.isEmbeddable())
          generateResultSetLoad(out);

        generateSetQuery(out);

        // generateLoadFromObject();

        // generateCopy();

        generateCopy(out);

        generateMakePersistent(out);

        generateCreate(out);

        generateDelete(out);

        generateDeleteForeign(out);

        generateFlush(out);

        generateAfterCommit(out);

        generateAfterRollback(out);

        generateHome(out);
      }

      generateInternals(out);

      // printDependList(out, _dependencies);
    } catch (IOException e) {
      throw e;
    }
  }

  /**
   * Generates the class header for the generated code.
   */
  private void generateHeader(JavaWriter out)
    throws IOException
  {
    out.println("/*");
    out.println(" * Generated by Resin Amber");
    out.println(" * " + com.caucho.Version.VERSION);
    out.println(" */");
    out.print("private static final java.util.logging.Logger __caucho_log = ");
    out.println("java.util.logging.Logger.getLogger(\"" + getBeanClassName() + "\");");

    if (_entityType.getParentType() == null) {
      out.println();
      out.println("protected transient com.caucho.amber.type.EntityType __caucho_home;");
      out.println("public transient com.caucho.amber.entity.EntityItem __caucho_item;");
      out.println("protected transient com.caucho.amber.manager.AmberConnection __caucho_session;");
      out.println("protected transient int __caucho_state;");

      int loadCount = _entityType.getLoadGroupIndex();
      for (int i = 0; i <= loadCount / 64; i++) {
        out.println("protected transient long __caucho_loadMask_" + i + ";");
      }

      int dirtyCount = _entityType.getDirtyIndex();

      for (int i = 0; i <= dirtyCount / 64; i++) {
        out.println("protected transient long __caucho_dirtyMask_" + i + ";");
        out.println("protected transient long __caucho_updateMask_" + i + ";");
      }

      out.println("protected transient boolean __caucho_inc_version;");
    }
  }

  /**
   * Generates the init generated code.
   */
  private void generateInit(JavaWriter out)
    throws IOException
  {
    if (_entityType.getParentType() != null)
      return;

    out.println();
    out.println("public void __caucho_setPrimaryKey(Object key)");
    out.println("{");
    out.pushDepth();

    Id id = _entityType.getId();
    if (id == null)
      throw new IllegalStateException(L.l("`{0}' is missing a key.",
                                          _entityType.getName()));

    id.generateSet(out, id.generateCastFromObject("key"));

    out.popDepth();
    out.println("}");

    out.println();
    out.println("public Object __caucho_getPrimaryKey()");
    out.println("{");
    out.pushDepth();

    out.print("return ");
    out.print(id.toObject(id.generateGetProperty("super")));
    out.println(";");

    out.popDepth();
    out.println("}");

    /*
      println();
      println("public com.caucho.amber.entity.EntityItem __caucho_getItem()");
      println("{");
      pushDepth();

      println("return __caucho_item;");

      popDepth();
      println("}");

      println();
      println("public void __caucho_setItem(com.caucho.amber.entity.EntityItem item)");
      println("{");
      pushDepth();

      println("__caucho_item = item;");

      popDepth();
      println("}");
    */

    out.println();
    out.println("public void __caucho_setConnection(com.caucho.amber.manager.AmberConnection aConn)");
    out.println("{");
    out.pushDepth();

    out.println("__caucho_session = aConn;");

    out.popDepth();
    out.println("}");

    generateExpire(out);
  }

  /**
   * Generates the expire code.
   */
  private void generateExpire(JavaWriter out)
    throws IOException
  {
    out.println();
    out.println("public void __caucho_expire()");
    out.println("{");

    int loadCount = _entityType.getLoadGroupIndex();
    for (int i = 0; i <= loadCount / 64; i++) {
      out.println("  __caucho_loadMask_" + i + " = 0L;");
    }

    _entityType.generateExpire(out);

    out.println("}");
  }

  /**
   * Generates the match code.
   */
  private void generateMatch(JavaWriter out)
    throws IOException
  {
    if (_entityType.getParentType() != null)
      return;

    out.println();
    out.println("public boolean __caucho_match(String className, Object key)");
    out.println("{");
    out.pushDepth();

    /*
      out.println("if (! (" + getBeanClassName() + ".class.isAssignableFrom(cl)))");
      out.println("  return false;");
    */
    out.println("if (! (\"" + getBeanClassName() + "\".equals(className)))");
    out.println("  return false;");
    out.println("else {");
    out.pushDepth();

    Id id = _entityType.getId();
    id.generateMatch(out, id.generateCastFromObject("key"));

    out.popDepth();
    out.println("}");

    out.popDepth();
    out.println("}");
  }

  /**
   * Generates the prologue.
   */
  private void generatePrologue(JavaWriter out, HashSet<Object> completedSet)
    throws IOException
  {
    for (Column column : _entityType.getColumns()) {
      column.generatePrologue(out);
    }

    Id id = _entityType.getId();

    if (id != null)
      id.generatePrologue(out, completedSet);

    ArrayList<AmberField> _fields = _entityType.getFields();

    for (int i = 0; i < _fields.size(); i++) {
      AmberField prop = _fields.get(i);

      prop.generatePrologue(out, completedSet);
    }
  }

  /**
   * Generates the entity type
   */
  private void generateGetEntityType(JavaWriter out)
    throws IOException
  {
    out.println();
    out.println("public com.caucho.amber.type.EntityType __caucho_getEntityType()");
    out.println("{");
    out.pushDepth();

    out.println("return __caucho_home;");

    out.popDepth();
    out.println("}");
  }

  /**
   * Generates the fields.
   */
  private void generateFields(JavaWriter out)
    throws IOException
  {
    ArrayList<AmberField> _fields = _entityType.getFields();

    for (int i = 0; i < _fields.size(); i++) {
      AmberField prop = _fields.get(i);

      prop.generateSuperGetter(out);
      prop.generateGetProperty(out);

      prop.generateSuperSetter(out);
      prop.generateSetProperty(out);
    }
  }

  /**
   * Generates the stub methods (needed for EJB)
   */
  private void generateMethods(JavaWriter out)
    throws IOException
  {
    for (StubMethod method : _entityType.getMethods()) {
      method.generate(out);
    }
  }

  /**
   * Generates the load
   */
  private void generateLoad(JavaWriter out)
    throws IOException
  {
    // commented out: jpa/0l03
    // if (_entityType.getParentType() != null)
    //   return;

    if (_entityType.getParentType() == null) {
      out.println();
      out.println("public boolean __caucho_makePersistent(com.caucho.amber.manager.AmberConnection aConn, com.caucho.amber.type.EntityType home)");
      out.println("  throws java.sql.SQLException");
      out.println("{");
      out.pushDepth();

      out.println("__caucho_session = aConn;");
      out.println("if (home != null)");
      out.println("  __caucho_home = home;");

      out.println("__caucho_state = com.caucho.amber.entity.Entity.P_NON_TRANSACTIONAL;");

      int loadCount = _entityType.getLoadGroupIndex();
      for (int i = 0; i <= loadCount / 64; i++) {
        out.println("  __caucho_loadMask_" + i + " = 0L;");
      }

      int dirtyCount = _entityType.getDirtyIndex();
      for (int i = 0; i <= dirtyCount / 64; i++) {
        out.println("  __caucho_dirtyMask_" + i + " = 0L;");
        out.println("  __caucho_updateMask_" + i + " = 0L;");
      }

      out.println();
      out.println("return true;");

      out.popDepth();
      out.println("}");
    }

    if (_entityType.getFields().size() > 0) {
      int index = _entityType.getLoadGroupIndex();

      out.println();
      out.println("public void __caucho_retrieve(com.caucho.amber.manager.AmberConnection aConn)");
      out.println("  throws java.sql.SQLException");
      out.println("{");
      out.pushDepth();

      out.println("__caucho_load_" + index + "(aConn);");

      out.popDepth();
      out.println("}");

      out.println();
      out.println("public void __caucho_retrieve(com.caucho.amber.manager.AmberConnection aConn, java.util.Map preloadedProperties)");
      out.println("  throws java.sql.SQLException");
      out.println("{");
      out.pushDepth();

      out.println();
      out.println("__caucho_load_" + index + "(aConn, preloadedProperties);");

      out.popDepth();
      out.println("}");
    }
  }

  /**
   * Generates the detach
   */
  private void generateDetach(JavaWriter out)
    throws IOException
  {
    if (_entityType.getParentType() != null)
      return;

    out.println();
    out.println("public void __caucho_detach()");
    out.println("{");
    out.pushDepth();

    out.println("__caucho_session = null;");
    out.println("__caucho_home = null;");

    out.println("__caucho_state = com.caucho.amber.entity.Entity.TRANSIENT;");

    out.popDepth();
    out.println("}");
  }

  /**
   * Generates the load group.
   */
  private void generateLoadGroup(JavaWriter out, int groupIndex)
    throws IOException
  {
    if (_entityType.hasLoadGroup(groupIndex)) {
      new LoadGroupGenerator(_extClassName, _entityType, groupIndex).generate(out);
    }
  }

  /**
   * Generates the load
   */
  private void generateResultSetLoad(JavaWriter out)
    throws IOException
  {
    if (_entityType.getParentType() != null)
      return;

    out.println();
    out.println("public int __caucho_load(com.caucho.amber.manager.AmberConnection aConn, java.sql.ResultSet rs, int index)");
    out.println("  throws java.sql.SQLException");
    out.println("{");
    out.pushDepth();

    int index = _entityType.generateLoad(out, "rs", "index", 0, 0);

    out.println("__caucho_loadMask_0 |= 1L;");

    int dirtyCount = _entityType.getDirtyIndex();

    for (int i = 0; i <= dirtyCount / 64; i++) {
      out.println("__caucho_dirtyMask_" + i + " = 0;");

      // ejb/0645
      // out.println("__caucho_updateMask_" + i + " = 0;");
    }

    out.println();
    out.println("if (__caucho_state == com.caucho.amber.entity.Entity.P_TRANSACTIONAL || __caucho_state == com.caucho.amber.entity.Entity.P_NEW) {");
    out.println("}");
    out.println("else if (__caucho_session == null ||");
    out.println("         ! __caucho_session.isInTransaction()) {");
    out.println("  __caucho_state = com.caucho.amber.entity.Entity.P_NON_TRANSACTIONAL;");
    out.println("  if (__caucho_item != null)");
    out.println("    __caucho_item.save(this);");
    out.println("}");
    out.println("else {");
    out.println("  __caucho_state = com.caucho.amber.entity.Entity.P_TRANSACTIONAL;");
    out.println("  aConn.makeTransactional(this);");
    out.println("}");

    if (_entityType.getHasLoadCallback()) {
      out.println();
      out.println("__caucho_load_callback();");
    }

    out.println("return " + index + ";");
    out.popDepth();
    out.println("}");
  }

  /**
   * Generates the load
   */
  private void generateSetQuery(JavaWriter out)
    throws IOException
  {
    if (_entityType.getParentType() != null)
      return;

    out.println();
    out.println("public void __caucho_setKey(java.sql.PreparedStatement pstmt, int index)");
    out.println("  throws java.sql.SQLException");
    out.println("{");
    out.pushDepth();

    _entityType.generateSet(out, "pstmt", "index", "super");

    out.popDepth();
    out.println("}");
  }

  /**
   * Generates the flush
   */
  private void generateFlush(JavaWriter out)
    throws IOException
  {
    out.println();
    out.println("public void __caucho_increment_version()");
    out.println("{");
    out.pushDepth();
    out.println("if (__caucho_inc_version)");
    out.println("  return;");
    out.println();
    out.println("__caucho_inc_version = true;");

    VersionField version = _entityType.getVersionField();

    if (version != null)
      version.generateIncrementVersion(out);

    out.popDepth();
    out.println("}");

    out.println();
    out.println("protected void __caucho_flush_callback()");
    out.println("  throws java.sql.SQLException");
    out.println("{");
    out.println("}");

    out.println();
    out.println("public boolean __caucho_flush()");
    out.println("  throws java.sql.SQLException");
    out.println("{");
    out.pushDepth();

    out.println("if (__caucho_state == com.caucho.amber.entity.Entity.P_DELETED) {");
    out.println("  __caucho_delete_int();");
    out.println("  return true;");
    out.println("}");
    out.println();
    out.println("boolean isDirty = false;");

    int dirtyCount = _entityType.getDirtyIndex();

    for (int i = 0; i <= dirtyCount / 64; i++) {
      out.println("long mask_" + i + " = __caucho_dirtyMask_" + i + ";");
      out.println("__caucho_dirtyMask_" + i + " = 0L;");
      out.println("__caucho_updateMask_" + i + " |= mask_" + i + ";");

      out.println();
      out.println("if (mask_" + i + " != 0L)");
      out.println("  isDirty = true;");
    }

    out.println("if (! isDirty)");
    out.println("  return true;");

    generateCallbacks(out, "this", _entityType.getPreUpdateCallbacks());

    out.println();
    out.println("__caucho_flush_callback();");

    out.println();
    out.println("com.caucho.util.CharBuffer cb = new com.caucho.util.CharBuffer();");
    out.println("__caucho_home.generateUpdateSQLPrefix(cb);");
    out.println("boolean isFirst = true;");

    for (int i = 0; i <= dirtyCount / 64; i++) {
      out.println("if (mask_" + i + " != 0L)");
      out.println("  isFirst = __caucho_home.generateUpdateSQLComponent(cb, " + i + ", mask_" + i + ", isFirst);");
    }
    out.println("__caucho_home.generateUpdateSQLSuffix(cb);");

    out.println("java.sql.PreparedStatement pstmt = __caucho_session.prepareStatement(cb.toString());");

    out.println("int index = 1;");

    ArrayList<AmberField> fields = _entityType.getFields();
    for (int i = 0; i < fields.size(); i++) {
      AmberField field = fields.get(i);

      field.generateUpdate(out, "mask", "pstmt", "index");
    }

    out.println();
    _entityType.getId().generateSet(out, "pstmt", "index");

    if (version != null) {
      out.println();
      version.generateSet(out, "pstmt", "index");
    }

    out.println();
    out.println("int updateCount = pstmt.executeUpdate();");
    out.println();

    if (version != null) {
      out.println("if (updateCount == 0) {");
      out.println("  throw new javax.persistence.OptimisticLockException(this);");
      out.println("} else {");
      out.pushDepth();
      String value = version.generateGet("super");
      Type type = version.getColumn().getType();
      out.println(version.generateSuperSetter(type.generateIncrementVersion(value)) + ";");
      out.popDepth();
      out.println("}");
      out.println();
    }

    generateCallbacks(out, "this", _entityType.getPostUpdateCallbacks());

    out.println();
    out.println("if (__caucho_log.isLoggable(java.util.logging.Level.FINE))");
    out.println("  __caucho_log.fine(\"amber update \" + this);");

    out.println();
    out.println("__caucho_inc_version = false;");
    out.println();
    out.println("return false;");
    out.popDepth();
    out.println("}");
  }

  /**
   * Generates the update
   */
  private void generateFlushUpdate(JavaWriter out)
    throws IOException
  {
    out.println();
    out.println("protected void __caucho_flushUpdate(long mask, com.caucho.amber.type.EntityType home)");
    out.println("  throws java.sql.SQLException");
    out.println("{");
    out.pushDepth();

    if (_entityType.getParentType() != null) {
      out.println("super.__caucho_flushUpdate(mask, home.getParentType());");
    }

    EntityType type = _entityType;

    out.println("String sql = home.generateUpdateSQL(mask);");

    out.println("if (sql != null) {");
    out.pushDepth();

    out.println("java.sql.PreparedStatement pstmt = __caucho_session.prepareStatement(sql);");

    out.println("int index = 1;");

    ArrayList<AmberField> fields = _entityType.getFields();
    for (int i = 0; i < fields.size(); i++) {
      AmberField field = fields.get(i);

      field.generateUpdate(out, "mask", "pstmt", "index");
    }

    out.println();
    _entityType.getId().generateSet(out, "pstmt", "index");

    out.println();
    out.println("pstmt.executeUpdate();");

    out.println();
    out.println("if (__caucho_log.isLoggable(java.util.logging.Level.FINE))");
    out.println("  __caucho_log.fine(\"amber update \" + this);");

    // println();
    // println("pstmt.close();");

    out.popDepth();
    out.println("}");

    out.popDepth();
    out.println("}");
  }

  /**
   * Generates the after-commit
   */
  private void generateAfterCommit(JavaWriter out)
    throws IOException
  {
    out.println();
    out.println("public void __caucho_afterCommit()");
    out.println("{");
    out.pushDepth();

    out.println("int state = __caucho_state;");
    int dirtyCount = _entityType.getDirtyIndex();
    for (int i = 0; i <= dirtyCount / 64; i++) {
      out.println("long updateMask_" + i + " = __caucho_updateMask_" + i + ";");
    }

    if (_entityType.getParentType() != null) {
      out.println();
      out.println("super.__caucho_afterCommit();");
    }

    out.println();
    out.println("__caucho_state = com.caucho.amber.entity.Entity.P_NON_TRANSACTIONAL;");

    for (int i = 0; i <= dirtyCount / 64; i++) {
      out.println("__caucho_updateMask_" + i + " = 0L;");
    }

    out.print("if (updateMask_0 != 0L");
    for (int i = 1; i <= dirtyCount / 64; i++)
      out.print(" || updateMask_" + i + " != 0L");
    out.println(")");
    out.println("  __caucho_session.update(this);");

    out.println("if (__caucho_item != null) {");
    out.pushDepth();

    out.println(_extClassName + " item = (" + _extClassName + ") __caucho_item.getEntity();");

    if (_entityType.getParentType() != null) {
      // if loaded in transaction, then copy results
      // ejb/0a06, ejb/0893
      out.println("if ((__caucho_loadMask_0 & 1L) != 0) {");
      out.pushDepth();
      out.println("item.__caucho_loadMask_0 = 1L;");

      _entityType.generateCopyLoadObject(out, "item", "super", 0);
    }

    for (int i = 1; i < _entityType.getLoadGroupIndex(); i++) {
      String loadVar = "__caucho_loadMask_" + (i / 64);
      long mask = (1L << (i % 64));

      if (_entityType.isLoadGroupOwnedByType(i)) {
        out.println("if ((" + loadVar + " & " + mask + "L) != 0) {");
        out.pushDepth();

        _entityType.generateCopyLoadObject(out, "item", "super", i);

        out.println("item." + loadVar + " |= " + mask + "L;");

        out.popDepth();
        out.println("}");
      }
    }

    if (_entityType.getParentType() != null) {
      out.popDepth();
      out.println("}");
    }

    for (int i = 0; i < _entityType.getDirtyIndex(); i++) {
      int group = i / 64;
      long mask = (1L << (i % 64));

      if (_entityType.isDirtyIndexOwnedByType(i)) {
        out.println("if ((updateMask_" + group + " & " + mask + "L) != 0) {");
        out.pushDepth();

        _entityType.generateCopyUpdateObject(out, "item", "super", i);

        out.popDepth();
        out.println("}");
      }
    }

    out.popDepth();
    out.println("}");

    out.popDepth();
    out.println("}");
  }

  /**
   * Generates the after-rollback
   */
  private void generateAfterRollback(JavaWriter out)
    throws IOException
  {
    out.println();
    out.println("public void __caucho_afterRollback()");
    out.println("{");
    out.pushDepth();

    out.println("__caucho_state = com.caucho.amber.entity.Entity.P_NON_TRANSACTIONAL;");
    int loadCount = _entityType.getLoadGroupIndex();
    for (int i = 0; i <= loadCount / 64; i++) {
      out.println("__caucho_loadMask_" + i + " = 0L;");
    }

    int dirtyCount = _entityType.getDirtyIndex();
    for (int i = 0; i <= dirtyCount / 64; i++) {
      out.println("__caucho_dirtyMask_" + i + " = 0L;");
    }

    out.popDepth();
    out.println("}");
  }

  private String getDebug()
  {
    return "this";
  }

  /**
   * Generates the update
   */
  private void generateCreate(JavaWriter out)
    throws IOException
  {
    ArrayList<IdField> fields = _entityType.getId().getKeys();
    IdField idField = fields.size() > 0 ? fields.get(0) : null;

    if (! _entityType.getPersistenceUnit().hasReturnGeneratedKeys() &&
        idField != null && idField.getType().isAutoIncrement()) {
      out.println();
      out.println("private static com.caucho.amber.field.Generator __caucho_id_gen;");
      out.println("static {");
      out.pushDepth();
      out.println("com.caucho.amber.field.MaxGenerator gen = new com.caucho.amber.field.MaxGenerator();");
      out.println("gen.setColumn(\"" + idField.getColumns().get(0).generateInsertName() + "\");");
      out.println("gen.setTable(\"" + _entityType.getName() + "\");");
      out.println("gen.init();");
      out.popDepth();
      out.println("}");
    }

    out.println();
    out.println("public boolean __caucho_create(com.caucho.amber.manager.AmberConnection aConn, com.caucho.amber.type.EntityType home)");
    out.println("  throws java.sql.SQLException");
    out.println("{");
    out.pushDepth();

    out.println("if (__caucho_session != null)");
    out.println("  throw new com.caucho.amber.AmberException(\"object \" + " + getDebug() + " + \" is already persistent.\");");

    out.println("__caucho_state = com.caucho.amber.entity.Entity.P_NEW;");

    int loadCount = _entityType.getLoadGroupIndex();
    for (int i = 0; i <= loadCount / 64; i++) {
      out.println("__caucho_loadMask_" + i + " = " + _entityType.getCreateLoadMask(i) + ";");
    }

    for (JMethod method : _entityType.getPrePersistCallbacks()) {
      out.println(method.getName() + "();");
    }

    int dirtyCount = _entityType.getDirtyIndex();
    for (int i = 0; i <= dirtyCount / 64; i++) {
      out.println("__caucho_dirtyMask_" + i + " = 0L;");
    }

    Table table = _entityType.getTable();

    out.print("String sql = \"");
    out.print(_entityType.generateCreateSQL(table));
    out.println("\";");

    _entityType.getId().generateCheckCreateKey(out);

    out.println("java.sql.PreparedStatement pstmt = aConn.prepareInsertStatement(sql);");

    out.println("int index = 1;");

    out.println();
    _entityType.getId().generateSetInsert(out, "pstmt", "index");

    _entityType.generateInsertSet(out, table, "pstmt", "index", "super");

    out.println();
    out.println("pstmt.executeUpdate();");

    out.println();
    _entityType.getId().generateSetGeneratedKeys(out, "pstmt");

    for (Table subTable : _entityType.getSecondaryTables()) {
      out.println();
      out.print("sql = \"");
      out.print(_entityType.generateCreateSQL(subTable));
      out.println("\";");

      out.println("pstmt = aConn.prepareStatement(sql);");

      out.println("index = 1;");

      out.println();
      _entityType.getId().generateSetInsert(out, "pstmt", "index");

      _entityType.generateInsertSet(out, subTable, "pstmt", "index", "super");

      out.println();
      out.println("pstmt.executeUpdate();");

      out.println();
      _entityType.getId().generateSetGeneratedKeys(out, "pstmt");
    }

    out.println();
    out.println("__caucho_session = aConn;");
    out.println("__caucho_home = home;");

    // println("pstmt.close();");

    out.println("__caucho_item = new com.caucho.amber.entity.CacheableEntityItem(home.getHome(), new " + getClassName() + "());");
    out.println(getClassName() + " entity = (" + getClassName() + ") __caucho_item.getEntity();");
    out.println("entity.__caucho_home = home;");

    ArrayList<IdField> keys = _entityType.getId().getKeys();
    for (IdField key : keys) {
      String value = key.generateGet("super");

      out.println(key.generateSet("entity", value) + ";");
    }

    for (int i = 0; i < 1; i++) {
      _entityType.generateCopyUpdateObject(out, "entity", "super", i);
    }

    for (int i = 0; i <= loadCount / 64; i++) {
      out.print("entity.__caucho_loadMask_" + i + " = ");
      out.println(_entityType.getCreateLoadMask(i) + ";");
    }

    out.println();
    out.println("if (__caucho_log.isLoggable(java.util.logging.Level.FINE))");
    out.println("  __caucho_log.fine(\"amber create \" + this);");
    out.println();
    out.println("if (aConn.isInTransaction()) {");
    out.println("  __caucho_state = com.caucho.amber.entity.Entity.P_TRANSACTIONAL;");
    out.println("  aConn.makeTransactional(this);");
    out.println("}");
    out.println();

    for (JMethod method : _entityType.getPostPersistCallbacks()) {
      out.println(method.getName() + "();");
    }

    out.println();
    out.println("return false;");

    out.popDepth();
    out.println("}");
  }

  /**
   * Generates the delete
   */
  private void generateDelete(JavaWriter out)
    throws IOException
  {
    out.println();
    out.println("public void __caucho_delete()");
    out.println("{");
    out.pushDepth();

    out.println("if (__caucho_state >= com.caucho.amber.entity.Entity.P_DELETING)");
    out.println("  return;");

    _entityType.generatePreDelete(out);
    out.println("__caucho_state = com.caucho.amber.entity.Entity.P_DELETING;");

    out.println("if (__caucho_session != null) {");
    out.pushDepth();
    out.println("__caucho_session.update(this);");
    out.println("__caucho_home.getTable().beforeEntityDelete(__caucho_session, this);");
    out.println("__caucho_state = com.caucho.amber.entity.Entity.P_DELETED;");
    _entityType.generatePostDelete(out);
    out.popDepth();
    out.println("}");
    out.println("else");
    out.println("  __caucho_state = com.caucho.amber.entity.Entity.P_DELETED;");

    out.popDepth();
    out.println("}");

    Id id = _entityType.getId();

    out.println();
    out.println("private void __caucho_delete_int()");
    out.println("  throws java.sql.SQLException");
    out.println("{");
    out.pushDepth();

    generateCallbacks(out, "this", _entityType.getPreRemoveCallbacks());

    out.print("__caucho_home.delete(__caucho_session, ");
    out.print(id.toObject(id.generateGetProperty("this")));
    out.println(");");

    out.println("__caucho_session.removeEntity(this);");

    String table = _entityType.getTable().getName();
    String where = _entityType.getId().generateMatchArgWhere(null);

    String sql = "delete from " + table + " where " + where;

    out.println("String sql = \"" + sql + "\";");

    out.println();
    out.println("java.sql.PreparedStatement pstmt = __caucho_session.prepareStatement(sql);");

    out.println("int index = 1;");
    id.generateSet(out, "pstmt", "index", "this");

    out.println();
    out.println("pstmt.executeUpdate();");

    generateCallbacks(out, "this", _entityType.getPostRemoveCallbacks());

    out.popDepth();
    out.println("}");
  }

  /**
   * Generates the foreign delete
   */
  private void generateDeleteForeign(JavaWriter out)
    throws IOException
  {
    out.println();
    out.println("public void __caucho_invalidate_foreign(String table, Object key)");
    out.println("{");
    out.pushDepth();

    _entityType.generateInvalidateForeign(out);

    out.popDepth();
    out.println("}");
  }


  /**
   * Generates the cache load
   */
  private void generateLoadFromObject(JavaWriter out)
    throws IOException
  {
    out.println();
    out.println("public void __caucho_loadFromObject(Object src)");
    out.println("{");
    out.pushDepth();

    out.println(getClassName() + " o = (" + getClassName() + ") src;");

    if (_entityType.getParentType() != null)
      out.println("super.__caucho_loadFromObject(src);");
    else {
      int loadCount = _entityType.getLoadGroupIndex();
      for (int i = 0; i <= loadCount / 64; i++) {
        out.println("__caucho_loadMask_" + i + " = o.__caucho_loadMask_" + i + ";");
      }
    }

    int dirtyCount = _entityType.getDirtyIndex();
    for (int i = 0; i <= dirtyCount / 64; i++) {
      out.println("__caucho_dirtyMask_" + i + " = 0;");
    }

    _entityType.generateLoadFromObject(out, "o");

    /* XXX:
       if (Lifecycle.class.isAssignableFrom(_entityType.getBeanClass())) {
       println("if (src instanceof com.caucho.ormap.Lifecycle)");
       println("  ((com.caucho.ormap.Lifecycle) src).afterLoad(__caucho_session, " +
       _entityType.getId().getGetterName() + "());");
       }
    */

    out.popDepth();
    out.println("}");
  }

  /**
   * Generates the copy
   */
  /*
    private void generateCopy(JavaWriter out)
    throws IOException
    {
    out.println();
    out.println("public com.caucho.amber.entity.Entity __caucho_copy()");
    out.println("{");
    out.pushDepth();

    out.println(getClassName() + " o = new " + getClassName() + "();");

    // can't load because it doesn't handle timeouts
    out.println("o.__caucho_loadFromObject(this);");
    out.println("o.__caucho_loadMask = 0;");

    out.println("return o;");

    out.popDepth();
    out.println("}");
    }
  */

  /**
   * Generates the create
   */
  private void generateCopy(JavaWriter out)
    throws IOException
  {
    out.println();
    out.println("public com.caucho.amber.entity.Entity __caucho_copy(com.caucho.amber.manager.AmberConnection aConn,");
    out.println("                                                    com.caucho.amber.entity.EntityItem item)");
    out.println("{");
    out.pushDepth();

    out.println(getClassName() + " o = new " + getClassName() + "();");

    out.println("o.__caucho_home = __caucho_home;");
    out.println("o.__caucho_item = item;");

    ArrayList<IdField> keys = _entityType.getId().getKeys();

    for (int i = 0; i < keys.size(); i++) {
      IdField key = keys.get(i);

      out.println(key.generateSet("o", key.generateGet("super")) + ";");
    }

    _entityType.generateCopyLoadObject(out, "o", "super", 0);

    out.println("o.__caucho_session = aConn;");
    out.println("o.__caucho_state = __caucho_state;"); // com.caucho.amber.entity.Entity.P_NON_TRANSACTIONAL;");
    out.println("o.__caucho_loadMask_0 = __caucho_loadMask_0;"); // & 1L;");

    generateCallbacks(out, "o", _entityType.getPostLoadCallbacks());

    out.println();
    out.println("return o;");

    out.popDepth();
    out.println("}");
  }

  /**
   * Generates the copy
   */
  private void generateMakePersistent(JavaWriter out)
    throws IOException
  {
    out.println();
    out.println("public void __caucho_makePersistent(com.caucho.amber.manager.AmberConnection aConn,");
    out.println("                                    com.caucho.amber.entity.EntityItem item)");
    out.println("{");
    out.pushDepth();

    out.println(_extClassName + " entity = (" + _extClassName + ") item.getEntity();");

    out.println("__caucho_home = entity.__caucho_home;");
    out.println("if (__caucho_home == null) throw new NullPointerException();");
    out.println("__caucho_item = item;");

    ArrayList<IdField> keys = _entityType.getId().getKeys();

    for (int i = 0; i < keys.size(); i++) {
      IdField key = keys.get(i);

      out.println(key.generateSet("super", key.generateGet("entity")) + ";");
    }

    out.println("__caucho_session = aConn;");

    // out.println("if (__caucho_state != com.caucho.amber.entity.Entity.P_TRANSACTIONAL) {");
    out.println("__caucho_state = com.caucho.amber.entity.Entity.P_NON_TRANSACTIONAL;");
    // out.println("}");

    out.popDepth();
    out.println("}");
  }

  /**
   * Generates the home methods
   */
  private void generateHome(JavaWriter out)
    throws IOException
  {
    generateHomeFind(out);

    if (! (_entityType instanceof SubEntityType)) {
      generateHomeNew(out);
    }
  }

  /**
   * Generates the home methods
   */
  private void generateHomeFind(JavaWriter out)
    throws IOException
  {
    out.println();
    out.print("public com.caucho.amber.entity.EntityItem __caucho_home_find(");
    out.print("com.caucho.amber.manager.AmberConnection aConn,");
    out.print("com.caucho.amber.entity.AmberEntityHome home,");
    out.println("java.sql.ResultSet rs, int index)");
    out.println("  throws java.sql.SQLException");
    out.println("{");
    out.pushDepth();


    out.print("Object key = ");
    int index = _entityType.getId().generateLoadForeign(out, "rs", "index", 0);
    out.println(";");

    if (_entityType.getDiscriminator() == null) {
      out.println("return home.findEntityItem(aConn, key, false);");
    }
    else {
      out.println("String discriminator = rs.getString(index + " + index + ");");
      out.println();
      out.println("return home.findDiscriminatorEntityItem(aConn, key, discriminator);");
    }

    out.popDepth();
    out.println("}");
  }

  /**
   * Generates the home methods
   */
  private void generateHomeNew(JavaWriter out)
    throws IOException
  {
    out.println();
    out.print("public com.caucho.amber.entity.Entity __caucho_home_new(");
    out.print("com.caucho.amber.manager.AmberConnection aConn,");
    out.print("com.caucho.amber.entity.AmberEntityHome home,");
    out.print("Object key)");
    out.println("  throws java.sql.SQLException");
    out.println("{");
    out.println("  return __caucho_home_new(aConn, home, key, true);");
    out.println("}");

    out.println();
    out.print("public com.caucho.amber.entity.Entity __caucho_home_new(");
    out.print("com.caucho.amber.manager.AmberConnection aConn,");
    out.print("com.caucho.amber.entity.AmberEntityHome home,");
    out.print("Object key,");
    out.print("boolean loadFromResultSet)");
    out.println("  throws java.sql.SQLException");
    out.println("{");
    out.pushDepth();

    Column discriminator = _entityType.getDiscriminator();

    if (discriminator == null) {
      out.println(getClassName() + " entity = new " + getClassName() + "();");

      out.println("entity.__caucho_home = home.getEntityType();");
      out.println("entity.__caucho_setPrimaryKey(key);");

      out.println("return entity;");
    }
    else {

      String rootTableName = _entityType.getRootTableName();

      generateHomeNewLoading(out, rootTableName);

      out.println("com.caucho.amber.entity.EntityItem item = home.findDiscriminatorEntityItem(aConn, key, rs" + rootTableName + ".getString(1));");

      /* jpa/0l03
         out.println("if (loadFromResultSet) {");
         out.pushDepth();

         generateHomeNewLoading(out, null);

         out.println("item.getEntity().__caucho_load(aConn, rs, 1);");

         out.popDepth();
         out.println("}");
      */

      out.println(getClassName() + " entity = (" + getClassName() + ") item.copy(aConn);");

      out.println("rs" + rootTableName + ".close();");
      out.println("return entity;");
    }

    out.popDepth();
    out.println("}");
  }

  /**
   * Generates the loading for home_new
   */
  private void generateHomeNewLoading(JavaWriter out,
                                      String rootTableName)
    throws IOException
  {
    String varSuffix = rootTableName == null ? "" : rootTableName;

    out.print("String sql" + varSuffix + " = \"select ");
    out.print(_entityType.generateLoadSelect("o"));
    out.print(" from ");

    if (rootTableName == null)
      out.print(_entityType.getTable().getName());
    else
      out.print(rootTableName);

    out.print(" o where ");
    out.print(_entityType.getId().generateMatchArgWhere("o"));
    out.println("\";");

    out.println("java.sql.PreparedStatement pstmt" + varSuffix + " = aConn.prepareStatement(sql" + varSuffix + ");");

    String keyType = _entityType.getId().getForeignTypeName();

    out.println(keyType + " " + "keyValue" + varSuffix + " = (" + keyType + ") key;");

    out.println("int index" + varSuffix + " = 1;");
    _entityType.getId().generateSetKey(out, "pstmt"+varSuffix,
                                       "index"+varSuffix, "keyValue"+varSuffix);

    out.println("java.sql.ResultSet rs" + varSuffix + " = pstmt" + varSuffix + ".executeQuery();");
    out.println("if (! rs" + varSuffix + ".next()) {");
    out.println("  rs" + varSuffix + ".close();");
    out.println("  throw new com.caucho.amber.AmberException(key + \" has no matching object\");");
    out.println("}");
  }

  private void generateCallbacks(JavaWriter out,
                                 String object,
                                 ArrayList<JMethod> callbacks)
    throws IOException
  {
    if (callbacks.size() == 0)
      return;

    out.println();
    for (JMethod method : callbacks) {
      out.println(object + "." + method.getName() + "();");
    }
  }

  private void generateInternals(JavaWriter out)
    throws IOException
  {
    out.println();
    out.println("private void __caucho_setInternalString(java.sql.PreparedStatement pstmt, int index, String s)");
    out.println("  throws java.sql.SQLException");
    out.println("{");
    out.pushDepth();
    out.println("if (s == null)");
    out.println("  pstmt.setNull(index, java.sql.Types.OTHER);");
    out.println("else");
    out.println("  pstmt.setString(index, s);");
    out.popDepth();
    out.println("}");
  }
}
