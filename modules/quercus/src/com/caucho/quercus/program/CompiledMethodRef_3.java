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

package com.caucho.quercus.program;

import java.io.IOException;

import java.util.ArrayList;
import java.util.HashMap;

import java.util.logging.Logger;

import com.caucho.quercus.env.Env;
import com.caucho.quercus.env.Value;

import com.caucho.quercus.expr.Expr;

import com.caucho.util.L10N;

import com.caucho.vfs.WriteStream;

import com.caucho.quercus.env.Var;
import com.caucho.quercus.env.NullValue;

import com.caucho.quercus.gen.PhpWriter;

/**
 * Represents a compiled method with 3 args
 */
abstract public class CompiledMethodRef_3 extends CompiledMethodRef {
  private static final Logger log
    = Logger.getLogger(CompiledMethodRef_3.class.getName());
  private static final L10N L = new L10N(CompiledMethodRef_3.class);

  private String _name;
  private Expr _default_0;
  private Expr _default_1;
  private Expr _default_2;

  public CompiledMethodRef_3(String name,
			  Expr default_0,
			  Expr default_1,
			  Expr default_2)
  {
    _name = name;
    _default_0 = default_0;
    _default_1 = default_1;
    _default_2 = default_2;
  }
  
  /**
   * Binds the user's arguments to the actual arguments.
   *
   * @param args the user's arguments
   * @return the user arguments augmented by any defaults
   */
  public Expr []bindArguments(Env env, Expr fun, Expr []args)
  {
    if (args.length != 3)
      env.warning(L.l("incorrect"));

    return args;
  }

  /**
   * Evaluates the method with the given variable arguments.
   */
  public Value evalMethodRef(Env env, Value obj, Value []argValues)
  {
    switch (argValues.length) {
    case 0:
      return evalMethodRef(env,
			obj,
			_default_0.eval(env),
			_default_1.eval(env),
			_default_2.eval(env));
    case 1:
      return evalMethodRef(env,
			obj,
			argValues[0],
			_default_1.eval(env),
			_default_2.eval(env));
    case 2:
      return evalMethodRef(env,
			obj,
			argValues[0],
			argValues[1],
			_default_2.eval(env));
    case 3:
    default:
      return evalMethodRef(env,
			obj,
			argValues[0],
			argValues[1],
			argValues[2]);
    }
  }

  abstract public Value evalMethodRef(Env env,
				   Value obj,
				   Value a1,
				   Value a2,
				      Value a3);

  /**
   * Evaluates the method as a static function
   */
  public Value eval(Env env, Value []argValues)
  {
    env.warning(L.l("can't call '{0}' as a static function", _name));

    return NullValue.NULL;
  }
  
  public String toString()
  {
    return "CompiledMethodRef_3[" + _name + "]";
  }
}

