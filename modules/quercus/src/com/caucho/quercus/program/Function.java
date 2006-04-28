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
import java.util.IdentityHashMap;

import java.util.logging.Logger;

import com.caucho.java.JavaWriter;

import com.caucho.quercus.env.Env;
import com.caucho.quercus.env.Value;

import com.caucho.quercus.expr.Expr;
import com.caucho.quercus.expr.VarExpr;
import com.caucho.quercus.expr.VarInfo;
import com.caucho.quercus.expr.VarState;
import com.caucho.quercus.expr.NullLiteralExpr;

import com.caucho.util.L10N;

import com.caucho.quercus.env.Var;
import com.caucho.quercus.env.NullValue;

import com.caucho.quercus.gen.PhpWriter;
import com.caucho.quercus.Location;

/**
 * Represents sequence of statements.
 */
public class Function extends AbstractFunction {
  private static final Logger log = Logger.getLogger(Function.class.getName());
  private static final L10N L = new L10N(Function.class);

  private final FunctionInfo _info;
  private final boolean _isReturnsReference;

  private final String _name;
  private final Arg []_args;
  private final Statement _statement;

  private boolean _isStatic = true;

  private boolean _hasReturn;

  Function(Location location,
           String name,
           FunctionInfo info,
           Arg []args,
           Statement []statements)
  {
    _name = name.intern();
    _info = info;
    _isReturnsReference = info.isReturnsReference();
    _args = args;
    _statement = new BlockStatement(location, statements);

    setGlobal(info.isPageStatic());
  }

  public Function(Location location,
                  String name,
                  FunctionInfo info,
                  ArrayList<Arg> argList,
                  ArrayList<Statement> statementList)
  {
    _name = name.intern();
    _info = info;
    _isReturnsReference = info.isReturnsReference();

    _args = new Arg[argList.size()];
    argList.toArray(_args);

    Statement []statements = new Statement[statementList.size()];

    statementList.toArray(statements);

    _statement = new BlockStatement(location, statements);

    setGlobal(info.isPageStatic());
  }

  /**
   * Returns the name.
   */
  public String getName()
  {
    return _name;
  }

  /**
   * Returns the args.
   */
  public Arg []getArgs()
  {
    return _args;
  }

  public void setStatic(boolean isStatic)
  {
    _isStatic = isStatic;
  }

  public boolean isStatic()
  {
    return _isStatic;
  }

  public boolean isObjectMethod()
  {
    return false;
  }

  /**
   * True for a returns reference.
   */
  public boolean isReturnsReference()
  {
    return _isReturnsReference;
  }

  public String getClassName()
  {
    throw new UnsupportedOperationException();
  }

  public Value execute(Env env)
    throws Throwable
  {
    return null;
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
    Expr []expandedArgs;

    if (_args.length == args.length)
      expandedArgs = args;

    else if (_args.length < args.length)
      expandedArgs = args;

    else {
      if (_args[args.length].getDefault() == null) {
        int required;

        for (required = _args.length - 1; required >= 0; required--) {
          if (_args[required].getDefault() == null)
            break;
        }

        env.warning(L.l("function '{0}' has {1} required arguments, but {2} were provided",
                        _name, required + 1, args.length));
      }

      expandedArgs = new Expr[_args.length];

      System.arraycopy(args, 0, expandedArgs, 0, args.length);

      for (int i = args.length; i < expandedArgs.length; i++) {
        Expr defaultExpr = _args[i].getDefault();

        if (defaultExpr != null)
          expandedArgs[i] = defaultExpr;
        else
          expandedArgs[i] = NullLiteralExpr.NULL;
      }
    }

    return expandedArgs;
  }

  public Value eval(Env env, Expr []args)
    throws Throwable
  {
    return evalImpl(env, args, false);
  }

  public Value evalCopy(Env env, Expr []args)
    throws Throwable
  {
    return evalImpl(env, args, false);
  }

  public Value evalRef(Env env, Expr []args)
    throws Throwable
  {
    return evalImpl(env, args, true);
  }

  private Value evalImpl(Env env, Expr []args, boolean isRef)
    throws Throwable
  {
    HashMap<String,Var> map = new HashMap<String,Var>();

    Value []values = new Value[args.length];

    for (int i = 0; i < args.length; i++) {
      Arg arg = null;

      if (i < _args.length) {
        arg = _args[i];
      }

      if (arg == null) {
        values[i] = args[i].eval(env).copy();
      }
      else if (arg.isReference()) {
        values[i] = args[i].evalRef(env);

        map.put(arg.getName(), values[i].toRefVar());
      }
      else {
        // php/0d04
        values[i] = args[i].eval(env);

        Var var = values[i].toVar();

        map.put(arg.getName(), var);

        values[i] = var.toValue();
      }
    }

    for (int i = args.length; i < _args.length; i++) {
      Arg arg = _args[i];

      Expr defaultExpr = arg.getDefault();

      if (defaultExpr == null)
        return env.error("expected default expression");
      else if (arg.isReference())
        map.put(arg.getName(), defaultExpr.evalRef(env).toVar());
      else {
        map.put(arg.getName(), defaultExpr.eval(env).copy().toVar());
      }
    }

    HashMap<String,Var> oldMap = env.pushEnv(map);
    Value []oldArgs = env.setFunctionArgs(values); // php/0476

    try {
      Value value = _statement.execute(env);

      if (value == null)
        return NullValue.NULL;
      else if (_isReturnsReference && isRef)
        return value;
      else
        return value.copyReturn();
    } finally {
      env.restoreFunctionArgs(oldArgs);
      env.popEnv(oldMap);
    }
  }

  public Value eval(Env env, Value []args)
    throws Throwable
  {
    return evalImpl(env, args, false);
  }

  public Value evalCopy(Env env, Value []args)
    throws Throwable
  {
    return evalImpl(env, args, false);
  }

  public Value evalRef(Env env, Value []args)
    throws Throwable
  {
    return evalImpl(env, args, true);
  }

  private Value evalImpl(Env env, Value []args, boolean isRef)
    throws Throwable
  {
    HashMap<String,Var> map = new HashMap<String,Var>();

    for (int i = 0; i < args.length; i++) {
      Arg arg = null;

      if (i < _args.length) {
        arg = _args[i];
      }

      if (arg == null) {
      }
      else if (arg.isReference())
        map.put(arg.getName(), args[i].toRefVar());
      else {
        // quercus/0d04
        map.put(arg.getName(), args[i].copy().toVar());
      }
    }

    for (int i = args.length; i < _args.length; i++) {
      Arg arg = _args[i];

      Expr defaultExpr = arg.getDefault();

      if (defaultExpr == null)
        return env.error("expected default expression");
      else if (arg.isReference())
        map.put(arg.getName(), defaultExpr.evalRef(env).toVar());
      else {
        map.put(arg.getName(), defaultExpr.eval(env).copy().toVar());
      }
    }

    HashMap<String,Var> oldMap = env.pushEnv(map);
    Value []oldArgs = env.setFunctionArgs(args);

    try {
      Value value = _statement.execute(env);

      if (value == null)
        return NullValue.NULL;
      else if (_isReturnsReference && isRef)
        return value;
      else
        return value.copyReturn();
    } finally {
      env.restoreFunctionArgs(oldArgs);
      env.popEnv(oldMap);
    }
  }

  //
  // Java generation code.
  //

  /**
   * Analyzes the function.
   */
  public void analyze()
  {
    AnalyzeInfo info = new AnalyzeInfo(_info);

    for (int i = 0; i < _args.length; i++) {
      Arg arg = _args[i];

      VarExpr var = new VarExpr(_info.createVar(arg.getName()));
      var.setVarState(VarState.VALID);

      info.addVar(var);
    }

    _hasReturn = ! _statement.analyze(info);
  }

  /**
   * Returns true if the function can generate the call directly.
   */
  public boolean canGenerateCall(Expr []args)
  {
    // can only handle case where the arg length matches
    return args.length <= _args.length;
  }

  /**
   * Generates code to evaluate the expression.
   *
   * @param out the writer to the Java source code.
   */
  public void generate(PhpWriter out, Expr funExpr, Expr []args)
    throws IOException
  {
    generateImpl(out, funExpr, args, false);
  }

  /**
   * Generates code to evaluate the expression.
   *
   * @param out the writer to the Java source code.
   */
  public void generateRef(PhpWriter out, Expr funExpr, Expr []args)
    throws IOException
  {
    generateImpl(out, funExpr, args, true);
  }

  /**
   * Generates code to evaluate the expression.
   *
   * @param out the writer to the Java source code.
   */
  private void generateImpl(PhpWriter out, Expr funExpr,
                            Expr []args, boolean isRef)
    throws IOException
  {
    out.print("fun_" + _name + "(env");

    if (! isVariableArgs()) {
    }
    else if (args.length == 0 && _args.length == 0)
      out.print(", Value.NULL_ARGS");
    else
      out.print(", new Value[] {");

    for (int i = 0; i < _args.length; i++) {
      if (i != 0 || ! isVariableArgs())
        out.print(", ");

      if (i < args.length) {
        Expr arg = args[i];

        if (_args[i].isReference()) {
          arg.generateRef(out);
        }
        else {
          arg.generateArg(out);
        }
      }
      else if (_args[i].getDefault() != null)
        _args[i].getDefault().generateArg(out);
      else
        out.print("NullValue.NULL");
    }

    for (int i = _args.length; i < args.length; i++) {
      if (i != 0)
        out.print(", ");
      args[i].generateArg(out);
    }

    if (isVariableArgs() && (_args.length > 0 || args.length > 0))
      out.print("}");

    out.print(")");

    /* php/3a70 - should only be generated from generateCopy
    if (! (isRef && _isReturnsReference))
      out.print(".copyReturn()");
    */
  }

  private boolean isVariableArgs()
  {
    return _info.isVariableArgs() || _args.length > 5;
  }

  private boolean isVariableMap()
  {
    // return _info.isVariableVar();
    // php/3254
    return _info.isUsesSymbolTable() || _info.isVariableVar();
  }

  /**
   * Generates the code for the class component.
   *
   * @param out the writer to the output stream.
   */
  public void generate(PhpWriter out)
    throws IOException
  {
    if (isVariableArgs())
      generateVariableArgs(out);
    else
      generateFixedArgs(out);
  }

  /**
   * Generates the code for the class component.
   *
   * @param out the writer to the output stream.
   */
  public void generateFixedArgs(PhpWriter out)
    throws IOException
  {
    out.println();

    out.print("public ");

    // quercus/3960
    if (isStatic())
      out.print("static ");

    out.print("Value fun_");
    out.print(_name);
    out.print("(Env env");

    if (! isStatic())
      out.print(", Value quercus_this_arg");

    for (int i = 0; i < _args.length; i++) {
      out.print(", ");

      out.print("Value v_");
      out.print(_args[i].getName());
    }
    out.println(")");

    out.println("  throws Throwable");
    out.println("{");
    out.pushDepth();

    if (! isStatic()) {
      out.println("final CompiledObjectValue q_this = (quercus_this_arg != null ? quercus_this_arg.toObjectValue() : null);");
    }

    generateBody(out);

    out.popDepth();
    out.println("}");

    String funName = "__quercus_fun_" + _name;

    out.println();
    out.print("final ");

    if (isStatic())
      out.print("static ");

    out.println("AbstractFunction " + funName);

    String ref = _isReturnsReference ? "Ref" : "";

    if (isStatic()) {
      out.print(" = new CompiledFunction" + ref + "_" + _args.length);
    }
    else {
      out.print(" = new CompiledMethod" + ref + "_" + _args.length);
    }

    out.print("(\"");
    out.printJavaString(_name);
    out.print("\"");

    for (int i = 0; i < _args.length; i++) {
      out.print(", ");

      Expr defaultExpr = _args[i].getDefault();

      defaultExpr.generateExpr(out);
    }
    out.println(") {");

    out.pushDepth();

    if (isStatic())
      out.print("public Value eval" + ref + "(Env env");
    else
      out.print("public Value evalMethod" + ref + "(Env env, Value quercus_this");

    for (int i = 0; i < _args.length; i++) {
      out.print(", Value a" + i);
    }
    out.println(")");

    out.println("  throws Throwable");
    out.println("{");

    out.print("  return ");

    out.print("fun_" + _name + "(env");

    if (! isStatic())
      out.print(", quercus_this");

    for (int i = 0; i < _args.length; i++) {
      out.print(", a" + i);
    }
    out.print(")");

    // php/3a5x, php/37a2

    if (! _isReturnsReference)
      out.print(".toValue()");

    out.println(";");

    out.println("}");
    out.popDepth();
    out.println("};");

    if (! isGlobal()) {
      out.println();
      out.println("{ " + funName + ".setGlobal(false); }");
    }
  }

  /**
   * Generates the code for the class component.
   *
   * @param out the writer to the output stream.
   */
  public void generateVariableArgs(PhpWriter out)
    throws IOException
  {
    out.println();
    out.print("public ");

    if (isStatic())
      out.print("static ");
    
    out.print("Value fun_");
    out.print(_name);

    if (isStatic())
      out.println("(Env env, Value []args)");
    else
      out.println("(Env env, Value q_this_arg, Value []args)");

    out.println("  throws Throwable");
    out.println("{");
    out.pushDepth();

    if (! isStatic())
      out.println("CompiledObjectValue q_this = q_this_arg.toObjectValue();");
    
    // XXX: try to optimize-away the map

    if (_info.isVariableArgs()) {
      out.println("Value []quercus_oldArgs = env.setFunctionArgs(args);");
      out.println("try {");
      out.pushDepth();
    }

    generateBody(out);

    if (_info.isVariableArgs()) {
      out.popDepth();
      out.println("} finally {");
      out.pushDepth();
      out.println("env.restoreFunctionArgs(quercus_oldArgs);");
      out.popDepth();
      out.println("}");
    }

    out.popDepth();
    out.println("}");

    out.println();
    if (isStatic())
      out.println("final static AbstractFunction __quercus_fun_" + _name);
    else
      out.println("final AbstractFunction __quercus_fun_" + _name);

    String ref = _isReturnsReference ? "Ref" : "";

    if (isStatic())
      out.print(" = new CompiledFunction" + ref + "_N(");
    else
      out.print(" = new CompiledMethod" + ref + "_N(");

    out.print("\"");
    out.printJavaString(_name);
    out.print("\", new Expr[] {");

    for (int i = 0; i < _args.length; i++) {
      if (i != 0)
        out.print(", ");

      Expr defaultExpr = _args[i].getDefault();

      if (defaultExpr != null)
        defaultExpr.generateExpr(out);
      else
        out.print("null");
    }
    out.println("}");
    /*
    out.print(", new boolean [] {");

    for (int i = 0; i < _args.length; i++) {
      if (i != 0)
	out.print(", ");

      out.print(_args[i].isReference());
    }
    out.print("}");
    */
    out.println(") {");

    out.pushDepth();

    /*
    if (! isGlobal()) {
      out.println();
      out.println("boolean isGlobal() { return false; }");
    }
    */

    if (isStatic())
      out.println("public Value eval" + ref + "Impl(Env env, Value []args)");
    else
      out.println("public Value evalMethod" + ref + "Impl(Env env, Value quercus_this, Value []args)");

    out.println("  throws Throwable");
    out.println("{");
    out.pushDepth();

    if (isStatic())
      out.print("return fun_" + _name + "(env, args)");
    else
      out.print("return fun_" + _name + "(env, quercus_this, args)");

    // php/3a5x, php/37aq

    if (! _isReturnsReference)
      out.print(".toValue()"); // php/37aq

    out.println(";");

    out.popDepth();
    out.println("}");
    out.popDepth();
    out.println("};");
  }

  private void generateBody(PhpWriter out)
    throws IOException
  {
    out.println("env.checkTimeout();");
    out.println();

    for (VarInfo var : _info.getVariables()) {
      String varName = "v_" + var.getName();
      String argName = varName;

      if (! var.isArgument())
        out.print("Value ");
      else if (isVariableArgs()) {
        out.print("Value ");

        argName = "args[" + var.getArgumentIndex() + "]";
      }

      if (var.isArgument()) {
        if (isVariableMap()) {
          // XXX: need to distinguish ref
          out.println(varName + " = " + argName + ".toVar();");
        }
        else if (var.isReadOnly() && var.isValue()) {
          // php/3a70, php/343k
          out.println(varName + " = " + argName + ".toArgValue();");
        }
        else if (var.isReadOnly()) {
          // php/3783
          out.println(varName + " = " + argName + ".toRefValue();");
        }
        else if (var.isRefArgument()) {
          // php/344r, 3a57
          out.println(varName + " = " + argName + ".toRefVar();");
        }
        else if (var.isAssigned() && var.isReference()) {
          out.println(varName + " = " + argName + ".toVar();");
        }
        else {
          // php/399j
          out.println(varName + " = " + argName + ".toArgValue();");
        }
      }
      else {
        // local variable, i.e. not argument

        if (var.isValue())
          out.println(varName + " = NullValue.NULL;");
        else
          out.println(varName + " = null;");
      }
    }

    for (String var : _info.getTempVariables()) {
      out.println("Value " + var + ";");
    }

    if (isVariableMap()) {
      out.println("java.util.HashMap<String,Var> _quercus_map = new java.util.HashMap<String,Var>();");
      out.println("java.util.HashMap<String,Var> _quercus_oldMap = env.pushEnv(_quercus_map);");

      if (! isStatic())
	out.println("Value q_oldThis = env.setThis(quercus_this_arg);");
      
      out.println("try {");
      out.pushDepth();

      for (int i = 0; i < _args.length; i++) {
        out.print("_quercus_map.put(\"");
        out.printJavaString(_args[i].getName());
        out.println("\", (Var) v_" + _args[i].getName() + ");");
      }
    }

    if (_info.isOutUsed())
      out.println("com.caucho.vfs.WriteStream _quercus_out = env.getOut();");

    _statement.generate(out);

    if (isVariableMap()) {
      out.popDepth();
      out.println("} finally {");
      out.pushDepth();
      // out.println("env.setFunctionArgs(_quercus_oldArgs);");
      out.println("env.popEnv(_quercus_oldMap);");

      if (! isStatic())
	out.println("env.setThis(q_oldThis);");
      
      out.popDepth();
      out.println("}");
    }

    if (_statement.fallThrough() != Statement.RETURN)
      out.println("return com.caucho.quercus.env.NullValue.NULL;");
  }

  /**
   * Generates the code for the initialization component.
   *
   * @param out the writer to the output stream.
   */
  public void generateInit(PhpWriter out)
    throws IOException
  {
    out.println();
    out.print("addFunction(\"");
    out.printJavaString(_name.toLowerCase());
    out.println("\", __quercus_fun_" + _name + ");");
  }

  /**
   * Disassembly.
   */
  public void debug(JavaWriter out)
    throws IOException
  {
    out.println("function " + _name + "()");
    _statement.debug(out);
  }

  public String toString()
  {
    return "Function[" + _name + "]";
  }
}

