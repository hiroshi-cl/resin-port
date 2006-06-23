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

package com.caucho.ejb.cfg;

import com.caucho.bytecode.JClass;
import com.caucho.bytecode.JMethod;
import com.caucho.config.Config;
import com.caucho.config.ConfigException;
import com.caucho.config.LineConfigException;
import com.caucho.config.types.FileSetType;
import com.caucho.ejb.AbstractServer;
import com.caucho.ejb.EjbServerManager;
import com.caucho.ejb.amber.AmberConfig;
import com.caucho.ejb.ql.FunExpr;
import com.caucho.java.WorkDir;
import com.caucho.java.gen.JavaClassGenerator;
import com.caucho.loader.Environment;
import com.caucho.loader.EnvironmentClassLoader;
import com.caucho.log.Log;
import com.caucho.util.L10N;
import com.caucho.vfs.Path;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Manages the EJB configuration files.
 */
public class EjbConfig {
  private static final L10N L = new L10N(EjbConfig.class);
  private static final Logger log = Log.open(EjbConfig.class);

  private EjbServerManager _ejbManager;
  
  private ArrayList<FileSetType> _fileSetList = new ArrayList<FileSetType>();
  private ArrayList<Path> _pathList = new ArrayList<Path>();
  
  private HashMap<String,EjbBean> _cfgBeans = new HashMap<String,EjbBean>();
  private ArrayList<CmpRelation> _relations = new ArrayList<CmpRelation>();

  private ArrayList<EjbBean> _pendingBeans = new ArrayList<EjbBean>();
  private ArrayList<EjbBean> _deployingBeans = new ArrayList<EjbBean>();

  private ArrayList<FunctionSignature> _functions =
    new ArrayList<FunctionSignature>();
  
  private String _booleanTrue = "1";
  private String _booleanFalse = "0";

  private boolean _isAllowPOJO;

  public EjbConfig(EjbServerManager ejbManager)
  {
    _ejbManager = ejbManager;

    _functions.addAll(FunExpr.getStandardFunctions());
  }
  
  /**
   * Adds a path for an EJB config file to the config list.
   */
  public void addFileSet(FileSetType fileSet)
  {
    if (_fileSetList.contains(fileSet))
      return;

    _fileSetList.add(fileSet);
  }
  
  /**
   * Adds a path for an EJB config file to the config list.
   */
  public void addEJBPath(String ejbModuleName, Path path)
    throws ConfigException
  {
    if (_pathList.contains(path))
      return;

    _pathList.add(path);

    _ejbManager.addEJBModule(ejbModuleName);

    if (path.getScheme().equals("jar"))
      path.setUserPath(path.getURL());
    
    Environment.addDependency(path);

    EjbJar ejbJar = new EjbJar(this);

    try {
      new Config().configure(ejbJar, path, getSchema());
    } catch (ConfigException e) {
      throw e;
    } catch (Exception e) {
      throw new ConfigException(e);
    }
  }

  /**
   * Returns the schema name.
   */
  public String getSchema()
  {
    return "com/caucho/ejb/cfg/resin-ejb.rnc";
  }
  

  /**
   * Returns the EJB manager.
   */
  public EjbServerManager getEJBManager()
  {
    return _ejbManager;
  }

  /**
   * Sets the boolean true literal.
   */
  public void setBooleanTrue(String trueLiteral)
  {
    _booleanTrue = trueLiteral;
  }

  /**
   * Gets the boolean true literal.
   */
  public String getBooleanTrue()
  {
    return _booleanTrue;
  }

  /**
   * Sets the boolean false literal.
   */
  public void setBooleanFalse(String falseLiteral)
  {
    _booleanFalse = falseLiteral;
  }

  /**
   * Gets the boolean false literal.
   */
  public String getBooleanFalse()
  {
    return _booleanFalse;
  }

  /**
   * Returns the cfg bean with the given name.
   */
  public EjbBean getBeanConfig(String name)
  {
    return _cfgBeans.get(name);
  }

  /**
   * Sets the cfg bean with the given name.
   */
  public void setBeanConfig(String name, EjbBean bean)
  {
    if (name == null || bean == null)
      throw new NullPointerException();

    if (_cfgBeans.get(name) == null)
      _pendingBeans.add(bean);
    
    _cfgBeans.put(name, bean);
  }

  /**
   * Sets true if POJO are allowed.
   */
  public void setAllowPOJO(boolean allowPOJO)
  {
    _isAllowPOJO = allowPOJO;
  }

  /**
   * Return true if POJO are allowed.
   */
  public boolean isAllowPOJO()
  {
    return _isAllowPOJO;
  }

  /**
   * Finds an entity bean by its abstract schema.
   */
  public EjbEntityBean findEntityBySchema(String schemaName)
  {
    for (EjbBean bean : _cfgBeans.values()) {
      if (bean instanceof EjbEntityBean) {
	EjbEntityBean entity = (EjbEntityBean) bean;

	if (schemaName.equals(entity.getAbstractSchemaName()))
	  return entity;
      }
    }

    return null;
  }

  /**
   * Finds an entity bean by its abstract schema.
   */
  public EjbEntityBean findEntityByLocal(JClass cl)
  {
    for (EjbBean bean : _cfgBeans.values()) {
      if (bean instanceof EjbEntityBean) {
	EjbEntityBean entity = (EjbEntityBean) bean;

	if (cl.equals(entity.getLocal()))
	  return entity;
      }
    }

    return null;
  }

  /**
   * Adds a relation.
   */
  public CmpRelation addRelation(String relationName,
                                 String sourceEJB, String sourceField)
  {
    CmpRelation relation = findRelation(relationName, sourceEJB, sourceField);

    if (relation != null)
      return relation;

    relation = new CmpRelation();
    relation.setName(relationName);
    relation.setSourceEJB(sourceEJB);
    relation.setSourceField(sourceField);

    _relations.add(relation);

    return relation;
  }

  /**
   * Adds a relation.
   */
  public CmpRelation findRelation(String relationName,
                                 String sourceEJB, String sourceField)
  {
    for (int i = 0; i < _relations.size(); i++) {
      CmpRelation relation = _relations.get(i);

      if (relationName != null &&
          relationName.equals(relation.getName()))
        return relation;

      if (relation.getSourceEJB().equals(sourceEJB) &&
          relation.getSourceField().equals(sourceField))
        return relation;
    }

    return null;
  }

  public void addRelation(CmpRelation rel)
    throws ConfigException
  {
    CmpRelation oldRel = findRelation(rel.getName(),
				      rel.getSourceEJB(),
				      rel.getSourceField());

    if (oldRel == null) {
      _relations.add(rel);
      
      return;
    }

    if (! rel.getTargetEJB().equals(oldRel.getTargetEJB())) {
      throw new ConfigException(L.l("relationship '{0}.{1}' target EJB '{2}' does not match old target EJB '{3}' from {4}",
				    rel.getSourceEJB(),
				    rel.getSourceField(),
				    rel.getTargetEJB(),
				    oldRel.getTargetEJB(),
				    oldRel.getLocation()));
    }
    else if (rel.getTargetField() != oldRel.getTargetField() &&
	     (rel.getTargetField() == null ||
	      ! rel.getTargetField().equals(oldRel.getTargetField()))) {
      throw new ConfigException(L.l("relationship '{0}.{1}' target field '{2}' does not match old target field '{3}' from {4}",
				    rel.getSourceEJB(),
				    rel.getSourceField(),
				    rel.getTargetEJB(),
				    oldRel.getTargetEJB(),
				    oldRel.getLocation()));
    }

    oldRel.merge(rel);
  }

  public CmpRelation []getRelations()
  {
    return _relations.toArray(new CmpRelation[_relations.size()]);
  }

  /**
   * Adds a function.
   */
  public void addFunction(FunctionSignature sig, String sql)
  {
    _functions.add(sig);
  }

  /**
   * Gets the function list.
   */
  public ArrayList<FunctionSignature> getFunctions()
  {
    return _functions;
  }

  /**
   * Configures the pending beans.
   */
  public void configure()
    throws ConfigException
  {
    findConfigurationFiles();
    
    try {
      ArrayList<EjbBean> beanConfig = new ArrayList<EjbBean>(_pendingBeans);
      _pendingBeans.clear();

      _deployingBeans.addAll(beanConfig);

      EnvironmentClassLoader parentLoader = _ejbManager.getClassLoader();
      
      Path workPath = _ejbManager.getWorkPath();

      JavaClassGenerator javaGen = new JavaClassGenerator();
      // need to be compatible with enhancement
      javaGen.setWorkDir(workPath);
      javaGen.setParentLoader(parentLoader);
      
      configureRelations();
      
      for (EjbBean bean : beanConfig) {
	bean.init();
      }

      Collections.sort(beanConfig, new BeanComparator());

      AmberConfig amberConfig = new AmberConfig(this);

      for (EjbBean bean : beanConfig) {
	bean.configureAmber(amberConfig);
      }

      amberConfig.configureRelations();

      if (_ejbManager.isAutoCompile())
	amberConfig.generate(javaGen);

      for (EjbBean bean : beanConfig) {
	bean.generate(javaGen, _ejbManager.isAutoCompile());
      }

      javaGen.compilePendingJava();
    } catch (RuntimeException e) {
      throw e;
    } catch (ConfigException e) {
      throw e;
    } catch (Exception e) {
      throw new ConfigException(e);
    }
  }


  /**
   * Configures the pending beans.
   */
  private void findConfigurationFiles()
    throws ConfigException
  {
    for (FileSetType fileSet : _fileSetList) {
      for (Path path : fileSet.getPaths()) {
	addEJBPath(fileSet.getDir().toString(), path);
      }
    }
  }
  
  /**
   * Configures the pending beans.
   */
  public void deploy()
    throws ConfigException
  {
    try {
      ClassLoader parentLoader = _ejbManager.getClassLoader();
      
      Path workPath = _ejbManager.getWorkPath();

      if (workPath == null)
	workPath = WorkDir.getLocalWorkDir();

      JavaClassGenerator javaGen = new JavaClassGenerator();
      javaGen.setWorkDir(workPath);
      javaGen.setParentLoader(parentLoader);
      
      deployBeans(_deployingBeans, javaGen);
    } catch (RuntimeException e) {
      throw e;
    } catch (ConfigException e) {
      throw e;
    } catch (Throwable e) {
      throw new ConfigException(e);
    }
  }

  /**
   * Configures the pending beans.
   */
  public void deployBeans(ArrayList<EjbBean> beanConfig,
			  JavaClassGenerator javaGen)
    throws Throwable
  {
    Thread thread = Thread.currentThread();
    ClassLoader oldLoader = thread.getContextClassLoader();

    try {
      thread.setContextClassLoader(_ejbManager.getClassLoader());
				   
      _ejbManager.getAmberManager().initEntityHomes();

      for (EjbBean bean : beanConfig) {
	thread.setContextClassLoader(_ejbManager.getClassLoader());
	
	AbstractServer server = bean.deployServer(_ejbManager, javaGen);

	thread.setContextClassLoader(server.getClassLoader());
	
	if (bean.getServerProgram() != null)
	  bean.getServerProgram().configure(server);

	server.init();
	
	_ejbManager.addServer(server);

	/*
	if (bean.getJndiName() != null)
	  _ejbManager.getProtocolManager().deployJNDI(bean.getJndiName(),
						     server);
	*/
      }
    } finally {
      thread.setContextClassLoader(oldLoader);
    }
  }

  /**
   * Match up the relations.
   */
  protected void configureRelations()
    throws ConfigException
  {
    for (CmpRelation relation : _relations) {
      try {
	CmpRelationRole sourceRole = relation.getSourceRole();
	CmpRelationRole targetRole = relation.getTargetRole();

	String sourceEJB = sourceRole.getEJBName();
	EjbEntityBean sourceEntity = (EjbEntityBean) _cfgBeans.get(sourceEJB);

	if (sourceEntity == null)
	  throw new ConfigException(L.l("'{0}' is an unknown EJB bean.",
					sourceEJB));
      
	String sourceField = sourceRole.getFieldName();
	JMethod sourceMethod = sourceEntity.getFieldGetter(sourceField);

	JMethod sourceMapMethod = null;

	if (sourceField != null)
	  sourceMapMethod = getMapMethod(sourceEntity, sourceField);

	if (sourceField != null &&
	    sourceMethod == null && sourceMapMethod == null)
	  throw new ConfigException(L.l("{0}: relation field '{1}' does not have a corresponding getter method.  cmr-relations must define abstract getter methods returning a local interface.",
					sourceEntity.getEJBClass().getName(),
					sourceField));

	String targetEJB = targetRole.getEJBName();
	EjbEntityBean targetEntity = (EjbEntityBean) _cfgBeans.get(targetEJB);

	if (targetEntity == null)
	  throw new ConfigException(L.l("'{0}' is an unknown EJB bean.",
					targetEJB));
      
	String targetField = targetRole.getFieldName();
	JMethod targetMethod = targetEntity.getFieldGetter(targetField);

	JMethod targetMapMethod = null;

	if (targetField != null)
	  targetMapMethod = getMapMethod(targetEntity, targetField);

	if (targetField != null &&
	    targetMethod == null && targetMapMethod == null)
	  throw new ConfigException(L.l("{0}: relation field '{1}' does not have a corresponding getter method.  cmr-relations must define abstract getter methods returning a local interface.",
					targetEntity.getEJBClass().getName(),
					targetField));

	boolean sourceOneToMany = false;
	boolean sourceManyToOne = false;
	boolean sourceMap = false;

	if (sourceMethod == null) {
	}
	else if (sourceMethod.getReturnType().isAssignableTo(Collection.class))
	  sourceOneToMany = true;
	else if (sourceMethod.getReturnType().isAssignableTo(Map.class))
	  sourceMap = true;
	else
	  sourceManyToOne = true;

	boolean targetOneToMany = false;
	boolean targetManyToOne = false;
	boolean targetMap = false;

	if (targetMapMethod != null)
	  targetMap = true;

	if (targetMethod == null) {
	}
	else if (targetMethod.getReturnType().isAssignableTo(Collection.class))
	  targetOneToMany = true;
	else if (targetMethod.getReturnType().isAssignableTo(Map.class))
	  targetMap = true;
	else
	  targetManyToOne = true;

	if (sourceMap) {
	  createMap(targetEntity, targetField, targetRole,
		    sourceEntity, sourceField, sourceRole, sourceMapMethod);
	}
	else if (targetMap) {
	  createMap(sourceEntity, sourceField, sourceRole,
		    targetEntity, targetField, targetRole, targetMapMethod);
	}
	else if (sourceOneToMany && targetManyToOne) {
	  CmrOneToMany srcRel = new CmrOneToMany(sourceEntity,
						 sourceField,
						 targetEntity,
						 targetField);

	  srcRel.setSQLColumns(sourceRole.getSQLColumns());
	  srcRel.setOrderBy(sourceRole.getOrderBy());
	  // srcRel.setCascadeDelete(sourceRole.getCascadeDelete());
	  
	  sourceEntity.addRelation(srcRel);
	
	  CmrManyToOne dstRel = new CmrManyToOne(targetEntity,
						 targetField,
						 sourceEntity);

	  dstRel.setSQLColumns(targetRole.getSQLColumns());
	  dstRel.setSourceCascadeDelete(targetRole.getCascadeDelete());
	  dstRel.setTargetCascadeDelete(sourceRole.getCascadeDelete());
      
	  targetEntity.addRelation(dstRel);

	  srcRel.setTargetRelation(dstRel);
	  dstRel.setTargetRelation(srcRel);
	}
	else if (sourceOneToMany && targetOneToMany) {
	  CmrManyToMany srcRel = new CmrManyToMany(sourceEntity,
						   sourceField,
						   targetEntity,
						   targetField);

	  srcRel.setLocation(relation.getLocation());
	  
	  srcRel.setRelationName(relation.getName());
	  srcRel.setSQLTable(relation.getSQLTable());
	  srcRel.setOrderBy(sourceRole.getOrderBy());

	  srcRel.setKeySQLColumns(sourceRole.getSQLColumns());
	  srcRel.setDstSQLColumns(targetRole.getSQLColumns());
      
	  sourceEntity.addRelation(srcRel);
	  
	  CmrManyToMany dstRel = new CmrManyToMany(targetEntity,
						   targetField,
						   sourceEntity,
						   sourceField);

	  dstRel.setLocation(relation.getLocation());

	  dstRel.setRelationName(relation.getName());
	  dstRel.setSQLTable(relation.getSQLTable());
	  dstRel.setOrderBy(targetRole.getOrderBy());

	  dstRel.setKeySQLColumns(targetRole.getSQLColumns());
	  dstRel.setDstSQLColumns(sourceRole.getSQLColumns());
      
	  targetEntity.addRelation(dstRel);
	  /*
	  
	  srcRel.setTargetRelation(dstRel);
	  dstRel.setTargetRelation(srcRel);
	  CmrOneToMany srcRel = new CmrOneToMany(sourceEntity,
						 sourceField,
						 targetEntity,
						 targetField);

	  // manyToOne.setSQLColumns(sourceRole.getSQLColumns());
      
	  sourceEntity.addRelation(srcRel);
	
	  CmrOneToMany dstRel = new CmrOneToMany(targetEntity,
						 targetField,
						 sourceEntity,
						 sourceField);

	  // dstRel.setSQLColumns(sourceRole.getSQLColumns());
      
	  targetEntity.addRelation(dstRel);

	  srcRel.setTargetRelation(dstRel);
	  dstRel.setTargetRelation(srcRel);
	  */
	}
	else if (sourceOneToMany) {
	  CmrManyToMany srcRel = new CmrManyToMany(sourceEntity,
						   sourceField,
						   targetEntity,
						   targetField);

	  srcRel.setLocation(relation.getLocation());
	  
	  if (relation.getName() != null)
	    srcRel.setRelationName(relation.getName());
	  else if (relation.getSQLTable() != null)
	    srcRel.setRelationName(relation.getSQLTable());
	  else
	    srcRel.setRelationName(sourceField);

	  if (relation.getSQLTable() != null || relation.getName() != null)
	    srcRel.setSQLTable(relation.getSQLTable());
	  else
	    srcRel.setSQLTable(sourceField);
	  
	  srcRel.setOrderBy(sourceRole.getOrderBy());

	  srcRel.setKeySQLColumns(sourceRole.getSQLColumns());
	  srcRel.setDstSQLColumns(targetRole.getSQLColumns());
	  
	  srcRel.setTargetUnique("One".equals(sourceRole.getMultiplicity()));

	  sourceEntity.addRelation(srcRel);
	}
	else if (sourceManyToOne && targetManyToOne) {
	  if (relation.getSQLTable() != null)
	    throw new ConfigException(L.l("cmr-field '{0}' may not have a sql-table '{1}'.  one-to-one relations do not have association tables.",
					  sourceField,
					  relation.getSQLTable()));
	  
	  CmrManyToOne srcRel = new CmrManyToOne(sourceEntity,
						 sourceField,
						 targetEntity);

	  srcRel.setLocation(relation.getLocation());
	  
	  srcRel.setSQLColumns(sourceRole.getSQLColumns());

	  /*
	  if (targetRole.getCascadeDelete() &&
	      "Many".equals(sourceRole.getMultiplicity()))
	    throw new ConfigException(L.l("'{0}' may not set cascade-delete because '{0}' has multiplicity 'Many'",
					  targetField,
					  sourceField));
	  */
					  
	      
	  srcRel.setSourceCascadeDelete(sourceRole.getCascadeDelete());
	  srcRel.setTargetCascadeDelete(targetRole.getCascadeDelete());

	  sourceEntity.addRelation(srcRel);

	  CmrManyToOne dstRel = new CmrManyToOne(targetEntity,
						 targetField,
						 sourceEntity);
	  
	  dstRel.setLocation(relation.getLocation());
	  
	  dstRel.setSQLColumns(targetRole.getSQLColumns());
      
	  targetEntity.addRelation(dstRel);
	  
	  if ((sourceRole.getSQLColumns() == null ||
	       sourceRole.getSQLColumns().length == 0) &&
	      targetRole.getSQLColumns() != null &&
	      targetRole.getSQLColumns().length > 0) {
	    srcRel.setDependent(true);
	    
	    dstRel.setSourceCascadeDelete(targetRole.getCascadeDelete());
	    dstRel.setTargetCascadeDelete(sourceRole.getCascadeDelete());
	  }

	  if ((targetRole.getSQLColumns() == null ||
	       targetRole.getSQLColumns().length == 0)) {
	    // ejb/06h4
	    // ejb/06hm
	    dstRel.setDependent(true);
	    
	    srcRel.setSourceCascadeDelete(sourceRole.getCascadeDelete());
	    srcRel.setTargetCascadeDelete(targetRole.getCascadeDelete());
	  }
	  
	  srcRel.setTargetRelation(dstRel);
	  dstRel.setTargetRelation(srcRel);
	}
	else if (sourceManyToOne && targetOneToMany) {
	  CmrManyToOne srcRel = new CmrManyToOne(sourceEntity,
						 sourceField,
						 targetEntity);

	  srcRel.setLocation(relation.getLocation());
	  
	  srcRel.setSQLColumns(sourceRole.getSQLColumns());
      
	  sourceEntity.addRelation(srcRel);
	
	  CmrOneToMany dstRel = new CmrOneToMany(targetEntity,
						 targetField,
						 sourceEntity,
						 sourceField);

	  dstRel.setLocation(relation.getLocation());
	  dstRel.setSQLColumns(sourceRole.getSQLColumns());
	  dstRel.setOrderBy(targetRole.getOrderBy());

	  targetEntity.addRelation(dstRel);

	  srcRel.setTargetRelation(dstRel);
	  dstRel.setTargetRelation(srcRel);

	  if (targetRole.getCascadeDelete() &&
	      "Many".equals(sourceRole.getMultiplicity()))
	    throw new ConfigException(L.l("'{0}' may not set cascade-delete because '{1}' has multiplicity 'Many'",
					  targetField,
					  sourceField));
	}
	else if (sourceManyToOne) {
	  CmrManyToOne srcRel = new CmrManyToOne(sourceEntity,
						 sourceField,
						 targetEntity);

	  srcRel.setSQLColumns(sourceRole.getSQLColumns());
      
	  srcRel.setSourceCascadeDelete(sourceRole.getCascadeDelete());
	  srcRel.setTargetCascadeDelete(targetRole.getCascadeDelete());

	  sourceEntity.addRelation(srcRel);
	}
	else if (targetManyToOne) {
	  CmrManyToOne dstRel = new CmrManyToOne(targetEntity,
						 targetField,
						 sourceEntity);

	  dstRel.setSQLColumns(targetRole.getSQLColumns());
	  
	  dstRel.setSourceCascadeDelete(targetRole.getCascadeDelete());
	  dstRel.setTargetCascadeDelete(sourceRole.getCascadeDelete());
      
	  targetEntity.addRelation(dstRel);
	}
	else if (targetOneToMany) {
	  CmrOneToMany dstRel = new CmrOneToMany(targetEntity,
						 targetField,
						 sourceEntity,
						 sourceField);

	  dstRel.setSQLColumns(targetRole.getSQLColumns());
	  dstRel.setOrderBy(targetRole.getOrderBy());
      
	  targetEntity.addRelation(dstRel);
	}
	else {
	  throw new ConfigException(L.l("unsupported relation"));
	}
      } catch (LineConfigException e) {
	throw e;
      } catch (ConfigException e) {
	throw new LineConfigException(relation.getLocation() + e.getMessage(), e);
      }
    }
  }

  private void createMap(EjbEntityBean sourceEntity,
			 String idField,
			 CmpRelationRole sourceRole,
			 EjbEntityBean targetEntity,
			 String targetField,
			 CmpRelationRole targetRole,
			 JMethod targetMapMethod)
    throws ConfigException
  {
    CmrManyToOne srcRel = new CmrManyToOne(sourceEntity,
					   idField,
					   targetEntity);

    srcRel.setSQLColumns(sourceRole.getSQLColumns());
    /*
    dstRel.setSQLColumns(targetRole.getSQLColumns());
    dstRel.setCascadeDelete(targetRole.getCascadeDelete());
    */
      
    sourceEntity.addRelation(srcRel);
    
    CmrMap map = new CmrMap(targetEntity, targetField,
			    sourceEntity, srcRel);

    map.setMapMethod(targetMapMethod);

    targetEntity.addRelation(map);
  }

  /**
   * Returns the map method.
   */
  public JMethod getMapMethod(EjbEntityBean entityBean, String field)
  {
    String methodName = ("get" +
			 Character.toUpperCase(field.charAt(0)) +
			 field.substring(1));

    JMethod []methods = entityBean.getMethods(entityBean.getEJBClassWrapper());

    for (int i = 0; i < methods.length; i++) {
      JMethod method = methods[i];

      if (! method.getName().equals(methodName))
	continue;
      else if (method.getParameterTypes().length != 1)
	continue;
      else if ("void".equals(method.getReturnType().getName()))
	continue;
      else if (! method.isAbstract())
	continue;
      else
	return method;
    }

    return null;
  }

  static class BeanComparator implements Comparator {
    public int compare(Object a, Object b)
    {
      if (a == b)
	return 0;

      EjbBean beanA = (EjbBean) a;
      EjbBean beanB = (EjbBean) b;

      if (! (a instanceof EjbEntityBean) && ! (b instanceof EjbEntityBean))
	return beanA.getEJBName().compareTo(beanB.getEJBName());
      else if (! (a instanceof EjbEntityBean))
	return 1;
      else if (! (b instanceof EjbEntityBean))
	return -1;

      EjbEntityBean entityA = (EjbEntityBean) a;
      EjbEntityBean entityB = (EjbEntityBean) b;

      if (entityB.dependsOn(entityA))
	return -1;
      else if (entityA.dependsOn(entityB))
	return 1;
      else
	return entityA.getEJBName().compareTo(entityB.getEJBName());
    }
  }
}

