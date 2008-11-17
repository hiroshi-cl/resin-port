/*
 * Copyright (c) 1998-2008 Caucho Technology -- all rights reserved
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

package com.caucho.ejb.gen;

import com.caucho.java.JavaWriter;
import com.caucho.util.L10N;
import com.caucho.webbeans.component.*;
import com.caucho.webbeans.manager.*;

import java.io.*;
import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;
import javax.annotation.security.*;
import javax.ejb.*;
import javax.interceptor.*;
import javax.webbeans.*;
import javax.webbeans.manager.Bean;

/**
 * Represents the interception
 */
public class InterceptorCallChain extends AbstractCallChain {
  private static final L10N L = new L10N(InterceptorCallChain.class);

  private static final Annotation []NULL_ANN_LIST = new Annotation[0];

  private View _view;
  private BusinessMethodGenerator _next;

  private String _uniqueName;
  private Method _implMethod;
  
  private ArrayList<Class> _defaultInterceptors = new ArrayList<Class>();
  private ArrayList<Class> _classInterceptors = new ArrayList<Class>();
  private ArrayList<Class> _methodInterceptors = new ArrayList<Class>();

  private boolean _isExcludeDefaultInterceptors;
  private boolean _isExcludeClassInterceptors;
  
  private ArrayList<Class> _interceptors = new ArrayList<Class>();

  // map from the interceptor class to the local variable for the interceptor
  private HashMap<Class,String> _interceptorVarMap
    = new HashMap<Class,String>();
  
  // interceptors we're responsible for initializing
  private ArrayList<Class> _ownInterceptors = new ArrayList<Class>();

  // decorators
  private Class _decoratorType;

  private String _decoratorBeanVar;
  private String _decoratorInstanceVar;
  private String _decoratorClass;
  private String _decoratorLocalVar;
  private String _delegateVar;
  private String _decoratorApiVar;

  public InterceptorCallChain(BusinessMethodGenerator next,
			      View view)
  {
    super(next);
    
    _next = next;
    _view = view;
  }
  
  /**
   * Returns true if the method is an interceptor
   */
  public boolean isEnhanced()
  {
    if (_view.getBean().isAnnotationPresent(Interceptor.class)
	|| _view.getBean().isAnnotationPresent(Decorator.class)) {
      return false;
    }
    else {
      return (_defaultInterceptors.size() > 0
	      || (! _isExcludeDefaultInterceptors
		  && _view.getBean().getDefaultInterceptors().size() > 0)
	      || _classInterceptors.size() > 0
	      || _methodInterceptors.size() > 0
	      || _decoratorType != null
	      || getAroundInvokeMethod() != null);
    }
  }

  public ArrayList<Class> getInterceptors()
  {
    return _interceptors;
  }

  public Method getAroundInvokeMethod()
  {
    return _view.getAroundInvokeMethod();
  }

  /**
   * Introspects the @Interceptors annotation on the method
   * and the class.
   */
  public void introspect(Method apiMethod, Method implMethod)
  {
    if (implMethod == null)
      return;

    Class apiClass = apiMethod.getDeclaringClass();
    
    Class implClass = implMethod.getDeclaringClass();

    _implMethod = implMethod;

    // interceptors aren't intercepted
    if (_view.getBean().isAnnotationPresent(Interceptor.class)
	|| _view.getBean().isAnnotationPresent(Decorator.class)) {
      return;
    }

    Interceptors iAnn;
    
    iAnn = (Interceptors) apiClass.getAnnotation(Interceptors.class);

    if (iAnn != null) {
      for (Class iClass : iAnn.value())
	_classInterceptors.add(iClass);
    }

    if (implClass != null) {
      iAnn = (Interceptors) implClass.getAnnotation(Interceptors.class);

      if (apiMethod != implMethod && iAnn != null) {
	for (Class iClass : iAnn.value())
	  _classInterceptors.add(iClass);
      }
    }

    Annotation []apiAnnList = getMethodAnnotations(apiMethod);
    Annotation []implAnnList = getMethodAnnotations(implMethod);
    
    iAnn = getAnnotation(apiAnnList, Interceptors.class);

    if (iAnn != null) {
      for (Class iClass : iAnn.value())
	_methodInterceptors.add(iClass);
    }

    iAnn = getAnnotation(implAnnList, Interceptors.class);

    if (apiMethod != implMethod && iAnn != null) {
      for (Class iClass : iAnn.value())
	_methodInterceptors.add(iClass);
    }

    if (isAnnotationPresent(apiAnnList, ExcludeClassInterceptors.class))
      _isExcludeClassInterceptors = true;

    if (isAnnotationPresent(implAnnList, ExcludeClassInterceptors.class))
      _isExcludeClassInterceptors = true;

    if (isAnnotationPresent(apiAnnList, ExcludeDefaultInterceptors.class))
      _isExcludeDefaultInterceptors = true;

    if (isAnnotationPresent(implAnnList, ExcludeDefaultInterceptors.class))
      _isExcludeDefaultInterceptors = true;

    // webbeans annotations
    WebBeansContainer webBeans = WebBeansContainer.create();
    
    ArrayList<Annotation> interceptorTypes = new ArrayList<Annotation>();
    
    if (isValidMethod() && _view.getInterceptorBindings() != null)
      interceptorTypes.addAll(_view.getInterceptorBindings());
    
    for (Annotation ann : implAnnList) {
      Class annType = ann.annotationType();
      
      if (annType.isAnnotationPresent(InterceptorBindingType.class)) {
	if (! interceptorTypes.contains(ann))
	  interceptorTypes.add(ann);
      }
    }

    if (interceptorTypes.size() > 0) {
      ArrayList<Class> interceptors
	= webBeans.findInterceptors(interceptorTypes);
      
      if (interceptors != null)
	_methodInterceptors.addAll(interceptors);
    }

    if (isValidMethod()) {
      ArrayList<Class> decorators = _view.getBean().getDecoratorTypes();

      for (Class decorator : decorators) {
	for (Method method : decorator.getMethods()) {
	  if (isMatch(method, apiMethod))
	    _decoratorType = decorator;
	}
      }
    }
  }

  protected Annotation []getMethodAnnotations(Method method)
  {
    if (method == null)
      return NULL_ANN_LIST;
    
    Annotation []annList = _view.getBean().getMethodAnnotations(method);

    if (annList != null)
      return annList;
    else
      return method.getAnnotations();
  }

  protected boolean isAnnotationPresent(Annotation[]annList, Class type)
  {
    for (Annotation ann : annList) {
      if (ann.annotationType().equals(type))
	return true;
    }

    return false;
  }

  protected <T> T getAnnotation(Annotation[]annList, Class<T> type)
  {
    for (Annotation ann : annList) {
      if (ann.annotationType().equals(type))
	return (T) ann;
    }

    return null;
  }

  protected boolean isValidMethod()
  {
    if (Modifier.isStatic(_implMethod.getModifiers()))
      return false;
    
    if (Modifier.isFinal(_implMethod.getModifiers()))
      return false;
    
    if (! Modifier.isPublic(_implMethod.getModifiers())
	&& ! Modifier.isProtected(_implMethod.getModifiers()))
      return false;

    return true;
  }

  private boolean isMatch(Method methodA, Method methodB)
  {
    if (! methodA.getName().equals(methodB.getName()))
      return false;

    Class []paramA = methodA.getParameterTypes();
    Class []paramB = methodB.getParameterTypes();

    if (paramA.length != paramB.length)
      return false;

    for (int i = 0; i < paramA.length; i++) {
      if (! paramA[i].equals(paramB[i]))
	return false;
    }

    return true;
  }

  @Override
  public void generatePrologue(JavaWriter out, HashMap map)
    throws IOException
  {
    if (! isEnhanced()) {
      _next.generatePrologue(out, map);
      return;
    }

    if (map.get("__caucho_manager") == null) {
      map.put("__caucho_manager", true);
      
      out.println();
      out.print("private static ");
      out.printClass(WebBeansContainer.class);
      out.print(" __caucho_manager = ");
      out.printClass(WebBeansContainer.class);
      out.println(".create();");
    }

    if (! _isExcludeDefaultInterceptors)
      _interceptors.addAll(_view.getBean().getDefaultInterceptors());

    // ejb/0fb6
    if (! _isExcludeClassInterceptors && _interceptors.size() == 0)
      _interceptors.addAll(_classInterceptors);
    
    _interceptors.addAll(_methodInterceptors);

    if (_interceptors.size() > 0 || getAroundInvokeMethod() != null)
      generateInterceptorPrologue(out, map);

    if (_decoratorType != null)
      generateDecoratorPrologue(out, map);
    
    _next.generatePrologue(out, map);
  }

  public void generateInterceptorPrologue(JavaWriter out, HashMap map)
    throws IOException
  {
    _uniqueName = "_v" + out.generateId();
    
    out.println();
    out.println("private static java.lang.reflect.Method " + _uniqueName + "_method;");
    out.println("private static java.lang.reflect.Method " + _uniqueName + "_implMethod;");

    boolean isAroundInvokePrologue = false;
    if (getAroundInvokeMethod() != null
	&& map.get("ejb.around-invoke") == null) {
      isAroundInvokePrologue = true;
      map.put("ejb.around-invoke", "_caucho_aroundInvokeMethod");
      
      out.println("private static java.lang.reflect.Method __caucho_aroundInvokeMethod;");
    }
    
    out.println("private static java.lang.reflect.Method []" + _uniqueName + "_methodChain;");
    out.println("private transient Object []" + _uniqueName + "_objectChain");
    out.println("  = com.caucho.ejb.util.EjbUtil.NULL_OBJECT_ARRAY;");

    Class cl = _implMethod.getDeclaringClass();
    
    out.println();
    out.println("static {");
    out.pushDepth();
    
    out.println("try {");
    out.pushDepth();
    
    out.print(_uniqueName + "_method = ");
    generateGetMethod(out,
		      _implMethod.getDeclaringClass().getName(),
		      _implMethod.getName(),
		      _implMethod.getParameterTypes());
    out.println(";");
    out.println(_uniqueName + "_method.setAccessible(true);");
    
    out.print(_uniqueName + "_implMethod = ");
    generateGetMethod(out,
		      _next.getView().getBeanClassName(),
		      "__caucho_" + _implMethod.getName(),
		      _implMethod.getParameterTypes());
    out.println(";");
    out.println(_uniqueName + "_implMethod.setAccessible(true);");

    if (isAroundInvokePrologue) {
      Method aroundInvoke = getAroundInvokeMethod();
      
      out.print("__caucho_aroundInvokeMethod = ");
      generateGetMethod(out,
			aroundInvoke.getDeclaringClass().getName(),
			aroundInvoke.getName(),
			aroundInvoke.getParameterTypes());
      out.println(";");
      out.println("__caucho_aroundInvokeMethod.setAccessible(true);");
    }

    generateMethodChain(out);
    
    out.popDepth();
    out.println("} catch (Exception e) {");
    out.println("  throw new RuntimeException(e);");
    out.println("}");
    out.popDepth();
    out.println("}");

    for (Class iClass : _interceptors) {
      String var = (String) map.get("interceptor-" + iClass.getName());
      if (var == null) {
	var = "__caucho_i" + out.generateId();

	out.println();
	out.print("private static ");
	out.printClass(Bean.class);
	out.println(" " + var + "_f;");
	
	out.print("private transient ");
	out.printClass(iClass);
	out.println(" " + var + ";");

	map.put("interceptor-" + iClass.getName(), var);

	_ownInterceptors.add(iClass);
      }

      _interceptorVarMap.put(iClass, var);
    }
  }

  public void generatePostConstruct(JavaWriter out, HashMap map)
    throws IOException
  {
    out.println("" + _uniqueName + "_objectChain = null;");
  }

  public void generateDecoratorPrologue(JavaWriter out, HashMap map)
    throws IOException
  {
    if (_uniqueName == null)
      _uniqueName = "_v" + out.generateId();

    _decoratorBeanVar = (String) map.get("decorator_beans");
    if (_decoratorBeanVar == null) {
      _decoratorBeanVar = _uniqueName + "_bean";
      map.put("decorator_beans", _decoratorBeanVar);

      out.println();

      out.println("private static java.util.List<javax.webbeans.manager.Decorator> " + _decoratorBeanVar + ";");

      out.println("private transient Object [] " + _decoratorBeanVar + "_i;");
    }

    _delegateVar = (String) map.get(_decoratorType.getName() + "_var");
    if (_delegateVar == null) {
      _delegateVar = _uniqueName + "_delegate";
      map.put(_decoratorType.getName() + "_var", _delegateVar);
    
      out.print("private transient ");
      out.printClass(_decoratorType);
      out.print(" " + _delegateVar + ";");
    }

    _decoratorClass = (String) map.get("decorator_class");
    
    if (_decoratorClass == null) {
      _decoratorClass =  _uniqueName + "_class";
      _decoratorLocalVar = _decoratorClass + "_tl";
      
      map.put("decorator_class", _decoratorClass);


      out.println("static ThreadLocal<" + _decoratorClass + "> "
		  + _decoratorLocalVar
		  + " = new ThreadLocal<" + _decoratorClass + ">();");

      generateDecoratorClass(out);
    }

    _decoratorLocalVar = _decoratorClass + "_tl";
  }

  private void generateDecoratorClass(JavaWriter out)
    throws IOException
  {
    String className = _decoratorClass;

    ArrayList<Class> decorators = _view.getBean().getDecoratorTypes();

    out.println();
    out.print("class ");
    out.print(className + " implements ");

    for (int i = 0; i < decorators.size(); i++) {
      if (i != 0)
	out.print(", ");
      
      out.printClass(decorators.get(i));
    }
    
    out.println(" {");
    out.pushDepth();

    out.println("private int _index;");

    out.println();
    out.println(className + "(int index)");
    out.println("{");
    out.println("  _index = index;");
    out.println("}");
    
    ArrayList<Method> methodList = new ArrayList<Method>();
    HashMap<ArrayList<Class>,String> apiMap =
      new HashMap<ArrayList<Class>,String>();

    for (Class decorator : decorators) {
      for (Method method : decorator.getMethods()) {
	if (! containsMethod(methodList, method)) {
	  methodList.add(method);
	
	  generateDecoratorMethod(out, method, apiMap);
	}
      }
    }

    out.popDepth();
    out.println("}");

    for (Map.Entry<ArrayList<Class>,String> entry : apiMap.entrySet()) {
      ArrayList<Class> apis = entry.getKey();
      String name = entry.getValue();

      out.print("static Class []" + name + " = new Class[] {");
      for (int i = 0; i < apis.size(); i++) {
	out.printClass(apis.get(i));
	out.println(".class,");
      }
      out.println("};");
    }
  }

  private boolean containsMethod(ArrayList<Method> methodList, Method method)
  {
    for (Method oldMethod : methodList) {
      if (isMatch(oldMethod, method))
	return true;
    }

    return false;
  }

  private boolean containsMethod(Method []methodList, Method method)
  {
    for (Method oldMethod : methodList) {
      if (isMatch(oldMethod, method))
	return true;
    }

    return false;
  }

  private void generateDecoratorMethod(JavaWriter out,
				       Method method,
				       HashMap<ArrayList<Class>,String> apiMap)
    throws IOException
  {
    ArrayList<Class> apis = getMethodApis(method);

    String apiName = apiMap.get(apis);
    if (apiName == null && apis.size() > 1) {
      apiName = _uniqueName + "_api_" + apiMap.size();

      apiMap.put(apis, apiName);
    }

    Class decoratorType = apis.get(0);
    
    out.println();
    out.print("public ");
    out.printClass(method.getReturnType());
    out.print(" ");
    out.print(method.getName());
    out.print("(");

    Class []paramTypes = method.getParameterTypes();
    for (int i = 0; i < paramTypes.length; i++) {
      if (i != 0)
	out.print(", ");

      out.printClass(paramTypes[i]);
      out.print(" a" + i);
    }
    out.println(")");
    Class []exnTypes = method.getExceptionTypes();
    
    if (exnTypes.length > 0) {
      out.print("  throws ");
      for (int i = 0; i < exnTypes.length; i++) {
	if (i != 0)
	  out.print(", ");
	out.printClass(exnTypes[i]);
      }
    }

    out.println("{");
    out.pushDepth();

    out.println(_decoratorClass + " var = " + _decoratorLocalVar + ".get();");

    out.print("var._index = com.caucho.ejb.util.EjbUtil.nextDelegate(");
    out.print(_decoratorBeanVar + "_i, ");
    if (apis.size() > 1) {
      out.print(apiName);
    }
    else {
      out.printClass(decoratorType);
      out.print(".class");
    }
    out.println(", var._index);");

    out.println();
    out.println("if (var._index >= 0) {");
    out.pushDepth();
    
    out.println("Object bean = " + _decoratorBeanVar + "_i[var._index];");

    for (int j = 0; j < apis.size(); j++) {
      if (j > 0)
	out.print("else ");
      
      if (j + 1 < apis.size()) {
	out.print("if (bean instanceof ");
	out.printClass(apis.get(j));
	out.print(")");
      }

      if (apis.size() > 1) {
	out.println();
	out.print("  ");
      }
      
      if (! void.class.equals(method.getReturnType()))
	out.print("return ");

      out.print("((");
      out.printClass(apis.get(j));
      out.print(") bean)." + method.getName() + "(");
      for (int i = 0; i < paramTypes.length; i++) {
	if (i != 0)
	  out.print(", ");

	out.print("a" + i);
      }
      out.println(");");
    }
    
    out.popDepth();
    out.println("}");

    out.println("else");
    out.pushDepth();

    if (! void.class.equals(method.getReturnType()))
      out.print("return ");

    out.print("__caucho_" + method.getName());
    out.print("(");
    for (int i = 0; i < paramTypes.length; i++) {
      if (i != 0)
	out.print(", ");

      out.print("a" + i);
    }
    out.println(");");

    out.popDepth();
    out.popDepth();
    out.println("}");
  }

  private ArrayList<Class> getMethodApis(Method method)
  {
    ArrayList<Class> apis = new ArrayList<Class>();

    for (Class decorator : _view.getBean().getDecoratorTypes()) {
      if (containsMethod(decorator.getMethods(), method)
	  && ! apis.contains(decorator))
	apis.add(decorator);
    }

    return apis;
  }

  @Override
  public void generateConstructor(JavaWriter out, HashMap map)
    throws IOException
  {
    for (Class iClass : _ownInterceptors) {
      String var = _interceptorVarMap.get(iClass);

      out.println("if (" + var + "_f == null)");
      out.println("  " + var + "_f = __caucho_manager.createTransient(" + iClass.getName() + ".class);");

      out.print(var + " = (");
      out.printClass(iClass);
      out.println(") __caucho_manager.getInstance(" + var + "_f);");
    }

    if (_decoratorType != null) {
      out.println("if (" + _delegateVar + " == null) {");
      out.pushDepth();

      out.println("if (" + _decoratorBeanVar + " == null) {");
      out.pushDepth();

      out.print(_decoratorBeanVar);
      out.print(" = __caucho_manager.resolveDecorators(");
      out.printClass(_view.getBean().getEjbClass().getJavaClass());
      out.println(".class);");
      
      out.popDepth();
      out.println("}");

      String className = _decoratorClass;

      /*
      generateThis(out);
      out.println("." + _delegateVar + " = ");

      out.print("(");
      out.printClass(_decoratorType);
      out.print(")");
      
      out.print("com.caucho.ejb.util.EjbUtil.generateDelegate(");
      out.print(_decoratorBeanVar + ", ");
      out.println(" new " + className + "());");
      */

      out.println("if (" + _delegateVar + " == null)");
      out.println("  " + _delegateVar + " = new " + className + "(0);");

      out.println("if (" + _decoratorBeanVar + "_i == null) {");
      out.pushDepth();
      out.print(_decoratorBeanVar + "_i = ");
      out.print("com.caucho.ejb.util.EjbUtil.generateProxyDelegate(");
      out.print("__caucho_manager, ");
      out.println(_decoratorBeanVar + ", " + _delegateVar + ");");
      out.popDepth();
      out.println("}");
      
      out.popDepth();
      out.println("}");
    }
    
    _next.generateConstructor(out, map);
  }

  @Override
  public void generateCall(JavaWriter out)
    throws IOException
  {
    boolean hasInterceptor = 
      _interceptors.size() > 0 || getAroundInvokeMethod() != null;
    
    if (! hasInterceptor && _decoratorType == null) {
      _next.generateCall(out);
      return;
    }

    if (hasInterceptor) {
      out.println("try {");
      out.pushDepth();

      out.print("if (");
      generateThis(out);
      out.println("." + _uniqueName + "_objectChain == null) {");
      out.pushDepth();
      generateObjectChain(out);
      out.popDepth();
      out.println("}");

      if (! void.class.equals(_implMethod.getReturnType())) {
	out.printClass(_implMethod.getReturnType());
	out.println(" result;");
      }
      
      if (! void.class.equals(_implMethod.getReturnType())) {
	out.print("result = (");
	printCastClass(out, _implMethod.getReturnType());
	out.print(") ");
      }
    
      out.print("new com.caucho.ejb3.gen.InvocationContextImpl(");
      generateThis(out);
      out.print(", ");
      generateThis(out);
      out.print("." + _uniqueName + "_method, ");
      generateThis(out);
      out.print("." + _uniqueName + "_implMethod, ");
      generateThis(out);
      out.print("." + _uniqueName + "_methodChain, ");
      generateThis(out);
      out.print("." + _uniqueName + "_objectChain, ");
      out.print("new Object[] { ");
      for (int i = 0; i < _implMethod.getParameterTypes().length; i++) {
	out.print("a" + i + ", ");
      }
      out.println("}).proceed();");

      _next.generatePreReturn(out);
    
      if (! void.class.equals(_implMethod.getReturnType())) {
	out.println("return result;");
      }
    
      out.popDepth();
      out.println("} catch (RuntimeException e) {");
      out.println("  throw e;");

      boolean isException = false;
      Class []exnList = _implMethod.getExceptionTypes();
      for (Class cl : exnList) {
	if (RuntimeException.class.isAssignableFrom(cl))
	  continue;

	if (! isMostGeneralException(exnList, cl))
	  continue;
      
	if (cl.isAssignableFrom(Exception.class))
	  isException = true;
      
	out.println("} catch (" + cl.getName() + " e) {");
	out.println("  throw e;");
      }

      if (! isException) {
	out.println("} catch (Exception e) {");
	out.println("  throw new RuntimeException(e);");
      }
    
      out.println("}");
    }
    else {
      generateDelegator(out);
    }
  }

  protected void generateDelegator(JavaWriter out)
    throws IOException
  {
    out.print(_decoratorLocalVar);
    out.print(".set(new " + _decoratorClass + "(");
    out.println(_decoratorBeanVar + "_i.length));");
    
    if (! void.class.equals(_implMethod.getReturnType()))
      out.print(" return ");

    out.print(_delegateVar + ".");
    out.print(_implMethod.getName());
    out.print("(");
      
    out.println(");");
  }

  protected void generateThis(JavaWriter out)
    throws IOException
  {
    _next.generateThis(out);
  }

  private boolean isMostGeneralException(Class []exnList, Class cl)
  {
    for (Class exn : exnList) {
      if (exn != cl && exn.isAssignableFrom(cl))
	return false;
    }

    return true;
  }

  protected Method findInterceptorMethod(Class cl)
  {
    if (cl == null)
      return null;

    for (Method method : cl.getDeclaredMethods()) {
      if (method.isAnnotationPresent(AroundInvoke.class))
	return method;
    }

    return findInterceptorMethod(cl.getSuperclass());
  }

  protected void generateMethodChain(JavaWriter out)
    throws IOException
  {
    out.println(_uniqueName + "_methodChain = new java.lang.reflect.Method[] {");
    out.pushDepth();

    for (Class iClass : _interceptors) {
      Method method = findInterceptorMethod(iClass);

      if (method == null)
	throw new IllegalStateException(L.l("Can't find @AroundInvoke in '{0}'",
					    iClass.getName()));
      
      generateGetMethod(out, method);
      out.println(", ");
    }

    if (getAroundInvokeMethod() != null) {
      out.println("__caucho_aroundInvokeMethod, ");
    }
    
    out.popDepth();
    out.println("};");
  }

  protected void generateObjectChain(JavaWriter out)
    throws IOException
  {
    generateThis(out);
    out.print("." + _uniqueName + "_objectChain = new Object[] { ");

    for (Class iClass : _interceptors) {
      generateThis(out);
      out.print("." + _interceptorVarMap.get(iClass) + ", ");
    }

    if (getAroundInvokeMethod() != null) {
      generateThis(out);
      out.print(", ");
    }
    
    out.println("};");
  }
  
  protected void generateGetMethod(JavaWriter out, Method method)
    throws IOException
  {
    generateGetMethod(out,
		      method.getDeclaringClass().getName(),
		      method.getName(),
		      method.getParameterTypes());
  }
  
  protected void generateGetMethod(JavaWriter out,
				   String className,
				   String methodName,
				   Class []paramTypes)
    throws IOException
  {
    out.print("com.caucho.ejb.util.EjbUtil.getMethod(");
    out.print(className + ".class");
    out.print(", \"" + methodName + "\", new Class[] { ");
    
    for (Class type : paramTypes) {
      out.printClass(type);
      out.print(".class, ");
    }
    out.print("})");
  }

  protected void printCastClass(JavaWriter out, Class type)
    throws IOException
  {
    if (! type.isPrimitive())
      out.printClass(type);
    else if (boolean.class.equals(type))
      out.print("Boolean");
    else if (char.class.equals(type))
      out.print("Character");
    else if (byte.class.equals(type))
      out.print("Byte");
    else if (short.class.equals(type))
      out.print("Short");
    else if (int.class.equals(type))
      out.print("Integer");
    else if (long.class.equals(type))
      out.print("Long");
    else if (float.class.equals(type))
      out.print("Float");
    else if (double.class.equals(type))
      out.print("Double");
    else
      throw new IllegalStateException(type.getName());
  }
}
