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

package com.caucho.php.env;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashMap;

import java.io.IOException;

import com.caucho.php.Quercus;
import com.caucho.php.PhpRuntimeException;

import com.caucho.php.program.AbstractFunction;
import com.caucho.php.program.Function;

import com.caucho.php.module.Marshall;
import com.caucho.php.module.JavaMarshall;

import com.caucho.php.expr.Expr;

import com.caucho.php.gen.PhpWriter;

/**
 * Represents an introspected Java class.
 */
public class JavaClassDefinition {
  private final String _name;
  private final Class _type;
  
  private final HashMap<String,JavaMethod> _functionMap
    = new HashMap<String,JavaMethod>();

  private final Marshall _marshall;

  public JavaClassDefinition(String name, Class type)
  {
    _name = name;

    _type = type;

    _marshall = new JavaMarshall(this);
  }
  
  /**
   * Returns the class name.
   */
  public String getName()
  {
    return _name;
  }

  /**
   * Returns the marshall instance.
   */
  public Marshall getMarshall()
  {
    return _marshall;
  }

  /**
   * Creates a new instance.
   */
  public Value newInstance()
  {
    try {
      Object obj = _type.newInstance();

      return new JavaValue(_type.newInstance(), this);
    } catch (Exception e) {
      throw new PhpRuntimeException(e);
    }
      
  }

  /**
   * Eval new
   */
  public Value evalNew(Env env, Expr []args)
    throws Throwable
  {
    return new JavaValue(_type.newInstance(), this);
  }

  /**
   * Eval new
   */
  public Value evalNew(Env env, Value []args)
    throws Throwable
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Eval a method
   */
  public Value evalMethod(Env env, Object obj, String name, Expr []args)
    throws Throwable
  {
    JavaMethod method = _functionMap.get(name);

    if (method == null) {
      env.warning("'" + name + "' is an unknown method.");

      return NullValue.NULL;
    }

    return method.eval(env, obj, args);
  }

  /**
   * Eval a method
   */
  public Value evalMethod(Env env, Object obj, String name, Value []args)
    throws Throwable
  {
    return getMethod(env, name).eval(env, obj, args);
  }

  /**
   * Eval a method
   */
  public Value evalMethod(Env env, Object obj, String name)
    throws Throwable
  {
    return getMethod(env, name).eval(env, obj);
  }

  /**
   * Eval a method
   */
  public Value evalMethod(Env env, Object obj, String name, Value a1)
    throws Throwable
  {
    return getMethod(env, name).eval(env, obj, a1);
  }

  private JavaMethod getMethod(Env env, String name)
  {
    JavaMethod method = _functionMap.get(name);

    if (method == null) {
      env.error("'" + name + "' is an unknown method.");
    }

    return method;
  }

  /**
   * Introspects the Java class.
   */
  public void introspect(Quercus quercus)
  {
    introspectMethods(quercus, _type);
  }

  /**
   * Introspects the Java class.
   */
  private void introspectMethods(Quercus quercus, Class type)
  {
    if (type == null || type.equals(Object.class))
      return;
    
    Class []ifcs = type.getInterfaces();
    
    for (int i = 0; i < ifcs.length; i++) {
      introspectMethods(quercus, ifcs[i]);
    }

    Method []methods = type.getDeclaredMethods();

    for (int i = 0; i < methods.length; i++) {
      Method method = methods[i];

      if (_functionMap.get(method.getName()) != null)
	continue;
      else if (! Modifier.isPublic(method.getModifiers()))
	continue;

      JavaMethod javaMethod = new JavaMethod(quercus, method);

      _functionMap.put(method.getName(), javaMethod);
    }

    introspectMethods(quercus, type.getSuperclass());
  }
}

