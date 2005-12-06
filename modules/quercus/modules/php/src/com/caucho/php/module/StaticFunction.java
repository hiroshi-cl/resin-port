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

package com.caucho.php.module;

import com.caucho.php.Php;

import com.caucho.php.env.Env;
import com.caucho.php.env.Value;
import com.caucho.php.expr.DefaultExpr;
import com.caucho.php.expr.Expr;
import com.caucho.php.expr.NullLiteralExpr;
import com.caucho.php.gen.PhpWriter;
import com.caucho.php.parser.PhpParser;
import com.caucho.php.program.AbstractFunction;
import com.caucho.util.L10N;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Represents the introspected static function information.
 */
public class StaticFunction extends AbstractFunction {
  private static final L10N L = new L10N(StaticFunction.class);

  private final PhpModule _phpModule;

  private final Method _method;
  private final Class []_paramTypes;
  private final boolean _hasEnv;
  private final Expr []_defaultExprs;
  private final Marshall []_marshallArgs;
  private final boolean _hasRestArgs;
  private final boolean _isCallUsesVariableArgs;
  private final boolean _isCallUsesSymbolTable;
  private final boolean _isRestReference;
  private final Marshall _unmarshallReturn;

  /**
   * Creates the statically introspected function.
   *
   * @param method the introspected method.
   */
  public StaticFunction(Php php, PhpModule phpModule, Method method)
  {
    _phpModule = phpModule;
    _method = method;
    
    boolean callUsesVariableArgs = false;
    boolean callUsesSymbolTable = false;

    for (Annotation ann : method.getAnnotations()) {
      if (VariableArguments.class.isAssignableFrom(ann.annotationType())) {
	callUsesVariableArgs = true;
      }
      
      if (UsesSymbolTable.class.isAssignableFrom(ann.annotationType())) {
	callUsesSymbolTable = true;
      }
    }

    _isCallUsesVariableArgs = callUsesVariableArgs;
    _isCallUsesSymbolTable = callUsesSymbolTable;

    Class []param = method.getParameterTypes();

    _paramTypes = param;

    _hasEnv = param.length > 0 && param[0].equals(Env.class);

    Annotation [][]paramAnn = method.getParameterAnnotations();

    boolean hasRestArgs = false;
    boolean isRestReference = false;
    
    if (param.length > 0 && param[param.length - 1].equals(Value[].class)) {
      hasRestArgs = true;

      for (Annotation ann : paramAnn[param.length - 1]) {
	if (Reference.class.isAssignableFrom(ann.annotationType()))
	  isRestReference = true;
      }
    }

    _hasRestArgs = hasRestArgs;
    _isRestReference = isRestReference;

    int argLength = paramAnn.length;

    if (_hasRestArgs)
      argLength -= 1;

    int envOffset = _hasEnv ? 1 : 0;

    _defaultExprs = new Expr[argLength - envOffset];

    _marshallArgs = new Marshall[argLength - envOffset];

    for (int i = 0; i < argLength - envOffset; i++) {
      boolean isReference = false;

      for (Annotation ann : paramAnn[i + envOffset]) {
        if (Optional.class.isAssignableFrom(ann.annotationType())) {
	  Optional opt = (Optional) ann;

	  if (! opt.value().equals("")) {
	    Expr expr = PhpParser.parseDefault(opt.value());
	    
	    _defaultExprs[i] = expr;
	  }
	  else
	    _defaultExprs[i] = new DefaultExpr();
        }
        else if (Reference.class.isAssignableFrom(ann.annotationType())) {
	  isReference = true;
	}
      }

      Class argType = param[i + envOffset];

      if (isReference)
	_marshallArgs[i] = Marshall.MARSHALL_REFERENCE;
      else
	_marshallArgs[i] = Marshall.create(php, argType);
    }

    _unmarshallReturn = Marshall.create(php, method.getReturnType());
  }

  /**
   * Returns the owning module object.
   *
   * @return the module object
   */
  public PhpModule getModule()
  {
    return _phpModule;
  }

  /**
   * Returns the function's method.
   *
   * @return the reflection method.
   */
  public Method getMethod()
  {
    return _method;
  }

  /**
   * Returns true if the environment is an argument.
   */
  public boolean getHasEnv()
  {
    return _hasEnv;
  }

  /**
   * Returns true if the environment has rest-style arguments.
   */
  public boolean getHasRestArgs()
  {
    return _hasRestArgs;
  }

  /**
   * Returns true if the function uses variable args.
   */
  public boolean isCallUsesVariableArgs()
  {
    return _isCallUsesVariableArgs;
  }

  /**
   * Returns true if the function uses the symbol table.
   */
  public boolean isCallUsesSymbolTable()
  {
    return _isCallUsesSymbolTable;
  }

  /**
   * Returns true if the rest-arguments are a reference.
   */
  public boolean isRestReference()
  {
    return _isRestReference;
  }

  /**
   * Returns true if the result is a boolean.
   */
  public boolean isBoolean()
  {
    return _unmarshallReturn.isBoolean();
  }

  /**
   * Returns true if the result is a string.
   */
  public boolean isString()
  {
    return _unmarshallReturn.isString();
  }

  /**
   * Returns true if the result is a long.
   */
  public boolean isLong()
  {
    return _unmarshallReturn.isLong();
  }

  /**
   * Returns true if the result is a double.
   */
  public boolean isDouble()
  {
    return _unmarshallReturn.isDouble();
  }

  /**
   * Binds the user's arguments to the actual arguments.
   *
   * @param args the user's arguments
   * @return the user arguments augmented by any defaults
   */
  public Expr []bindArguments(Env env, Expr fun, Expr []args)
    throws Exception
  {
    if (_defaultExprs.length == args.length)
      return args;

    int length = _defaultExprs.length;

    if (_defaultExprs.length < args.length) {
      if (_hasRestArgs)
	length = args.length;
      else {
	env.warning(L.l("function '{0}' has {1} required arguments, but only {2} were provided",
			_method.getName(), _defaultExprs.length, args.length));

	return null;
      }
    }
    else if (_defaultExprs[args.length] == null) {
      int required;

      for (required = args.length;
	   required < _defaultExprs.length;
	   required++) {
	if (_defaultExprs[required] != null)
	  break;
      }

      env.warning(L.l("function '{0}' has {1} required arguments, but only {2} were provided",
		      _method.getName(), required, args.length));

      return null;
    }

    Expr []expandedArgs = new Expr[length];

    System.arraycopy(args, 0, expandedArgs, 0, args.length);

    if (args.length < expandedArgs.length) {
      for (int i = args.length; i < expandedArgs.length; i++) {
	Expr defaultExpr = _defaultExprs[i];

	if (defaultExpr == null)
	  defaultExpr = NullLiteralExpr.NULL;

	expandedArgs[i] = defaultExpr;
      }
    }

    return expandedArgs;
  }

  /**
   * Evalutes the function.
   */
  public Value eval(Env env, Expr []exprs)
    throws Throwable
  {
    int len = _defaultExprs.length + (_hasEnv ? 1 : 0) + (_hasRestArgs ? 1 : 0);
    
    Object []values = new Object[len];

    int k = 0;

    if (_hasEnv)
      values[k++] = env;

    for (int i = 0; i < _marshallArgs.length; i++) {
      if (exprs[i] != null) {
	values[k] = _marshallArgs[i].marshall(env, exprs[i], _paramTypes[k]);
      }

      k++;
    }

    if (_hasRestArgs) {
      Value []rest = new Value[exprs.length - _marshallArgs.length];

      for (int i = _marshallArgs.length; i < exprs.length; i++) {
	if (_isRestReference)
	  rest[i - _marshallArgs.length] = exprs[i].evalRef(env);
	else
	  rest[i - _marshallArgs.length] = exprs[i].eval(env);
      }

      values[values.length - 1] = rest;
    }

    try {
      Object result = _method.invoke(_phpModule, values);

      return _unmarshallReturn.unmarshall(env, result);
    } catch (InvocationTargetException e) {
      if (e.getCause() != null)
	throw e.getCause();
      else
	throw e;
    }
  }

  /**
   * Evalutes the function.
   */
  public Value eval(Env env, Value []phpArgs)
    throws Throwable
  {
    int len = _paramTypes.length;
    
    Object []javaArgs = new Object[len];

    int k = 0;

    if (_hasEnv)
      javaArgs[k++] = env;

    int sublen = _marshallArgs.length;
    if (phpArgs.length < sublen)
      sublen = phpArgs.length;

    for (int i = 0; i < sublen; i++) {
      javaArgs[k] = _marshallArgs[i].marshall(env, phpArgs[i], _paramTypes[k]);

      k++;
    }

    for (int i = sublen; i < _marshallArgs.length; i++) {
      // XXX: need QA
      Value value = _defaultExprs[i].eval(env);

      javaArgs[k] = _marshallArgs[i].marshall(env, value, _paramTypes[k]);

      k++;
    }
    
    if (_hasRestArgs) {
      Value []rest = new Value[phpArgs.length - _marshallArgs.length];

      for (int i = _marshallArgs.length; i < phpArgs.length; i++) {
	rest[i - _marshallArgs.length] = phpArgs[i];
      }

      javaArgs[k++] = rest;
    }
    
    Object result = _method.invoke(_phpModule, javaArgs);

    return _unmarshallReturn.unmarshall(env, result);
  }

  //
  // Java generation code.
  //

  /**
   * Generates code to evaluate the expression.
   *
   * @param out the writer to the Java source code.
   */
  public void generate(PhpWriter out, Expr funExpr, Expr []args)
    throws IOException
  {
    _unmarshallReturn.generateResultStart(out);

    generateImpl(out, funExpr, args);

    out.print(")");
  }

  /**
   * Generates code to evaluate the expression.
   *
   * @param out the writer to the Java source code.
   */
  public void generateBoolean(PhpWriter out, Expr funExpr, Expr []args)
    throws IOException
  {
    if (_unmarshallReturn.isBoolean())
      generateImpl(out, funExpr, args);
    else
      super.generateBoolean(out, funExpr, args);
  }

  /**
   * Generates code to evaluate the expression.
   *
   * @param out the writer to the Java source code.
   */
  public void generateLong(PhpWriter out, Expr funExpr, Expr []args)
    throws IOException
  {
    if (_unmarshallReturn.isLong())
      generateImpl(out, funExpr, args);
    else
      super.generateLong(out, funExpr, args);
  }

  /**
   * Generates code to evaluate the expression.
   *
   * @param out the writer to the Java source code.
   */
  public void generateDouble(PhpWriter out, Expr funExpr, Expr []args)
    throws IOException
  {
    if (_unmarshallReturn.isLong() || _unmarshallReturn.isDouble())
      generateImpl(out, funExpr, args);
    else
      super.generateDouble(out, funExpr, args);
  }

  /**
   * Generates code to evaluate the expression.
   *
   * @param out the writer to the Java source code.
   */
  public void generateString(PhpWriter out, Expr funExpr, Expr []args)
    throws IOException
  {
    if (_unmarshallReturn.isString())
      generateImpl(out, funExpr, args);
    else
      super.generateString(out, funExpr, args);
  }

  /**
   * Generates code to evaluate the expression.
   *
   * @param out the writer to the Java source code.
   */
  public void generateTop(PhpWriter out, Expr funExpr, Expr []args)
    throws IOException
  {
    generateImpl(out, funExpr, args);
  }

  /**
   * Generates code to evaluate the expression.
   *
   * @param out the writer to the Java source code.
   */
  private void generateImpl(PhpWriter out, Expr funExpr, Expr []args)
    throws IOException
  {
    String var = out.addModule(_phpModule);
    
    if (Modifier.isStatic(_method.getModifiers()))
      out.print(_method.getDeclaringClass().getName());
    else
      out.print(var);

    out.print("." + _method.getName() + "(");

    Class []param = _method.getParameterTypes();

    boolean isFirst = true;

    if (_hasEnv) {
      out.print("env");
      isFirst = false;
    }

    for (int i = 0; i < _defaultExprs.length; i++) {
      if (! isFirst)
	out.print(", ");
      isFirst = false;
	
      if (i < args.length)
	_marshallArgs[i].generate(out, args[i], param[_hasEnv ? i + 1 : i]);
      else if (_defaultExprs[i] != null)
	_marshallArgs[i].generate(out, _defaultExprs[i], param[_hasEnv ? i + 1 : i]);
      else if (! _hasRestArgs)
	throw new IOException(L.l(funExpr.getLocation() +
				  "argument length mismatch for '{0}'",
				  _method.getName()));
    }

    if (_hasRestArgs) {
      if (! isFirst)
	out.print(", ");
      isFirst = false;

      out.print("new Value[] {");

      for (int i = _marshallArgs.length; i < args.length; i++) {
	if (_isRestReference)
	  args[i].generateRef(out);
	else
	  args[i].generate(out);

	out.print(", ");
      }
      out.print("}");
    }

    out.print(")");
  }
}
