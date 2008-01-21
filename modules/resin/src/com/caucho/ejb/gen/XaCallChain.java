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

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import javax.annotation.security.*;
import javax.ejb.*;
import javax.interceptor.*;

/**
 * Represents the xa interception
 */
public class XaCallChain implements EjbCallChain {
  private static final L10N L = new L10N(XaCallChain.class);

  private BusinessMethodGenerator _bizMethod;
  private EjbCallChain _next;

  private TransactionAttributeType _xa;

  public XaCallChain(BusinessMethodGenerator bizMethod, EjbCallChain next)
  {
    _bizMethod = bizMethod;
    _next = next;
  }
  
  /**
   * Returns true if the business method has any active XA annotation.
   */
  public boolean isEnhanced()
  {
    return (_xa != null && ! _xa.equals(TransactionAttributeType.SUPPORTS));
  }

  /**
   * Sets the transaction type
   */
  public void setTransactionType(TransactionAttributeType xa)
  {
    _xa = xa;
  }

  /**
   * Introspects the method for the default values
   */
  public void introspect(Method apiMethod, Method implMethod)
  {
    Class apiClass = apiMethod.getDeclaringClass();
    Class implClass = implMethod.getDeclaringClass();
    
    TransactionAttribute xaAttr;
    
    xaAttr = apiMethod.getAnnotation(TransactionAttribute.class);

    if (xaAttr == null) {
      xaAttr = (TransactionAttribute)
	apiClass.getAnnotation(TransactionAttribute.class);
    }

    if (xaAttr == null) {
      xaAttr = implMethod.getAnnotation(TransactionAttribute.class);
    }

    if (xaAttr == null) {
      xaAttr = (TransactionAttribute)
	implClass.getAnnotation(TransactionAttribute.class);
    }

    if (xaAttr != null)
      _xa = xaAttr.value();
  }

  /**
   * Generates the static class prologue
   */
  public void generatePrologue(JavaWriter out, HashMap map)
    throws IOException
  {
    if (map.get("caucho.ejb.xa") != null)
      return;

    map.put("caucho.ejb.xa", "done");

    out.println();
    out.println("private static final com.caucho.ejb3.xa.XAManager _xa");
    out.println("  = new com.caucho.ejb3.xa.XAManager();");
    
    _next.generatePrologue(out, map);
  }

  /**
   * Generates the method interceptor code
   */
  public void generateCall(JavaWriter out)
    throws IOException
  {
    if (_xa != null) {
      switch (_xa) {
      case MANDATORY:
	{
	  out.println("_xa.beginMandatory();");
	}
	break;
	
      case NEVER:
	{
	  out.println("_xa.beginNever();");
	}
	break;
	
      case NOT_SUPPORTED:
	{
	  out.println("Transaction xa = _xa.beginNotSupported();");
	  out.println();
	  out.println("try {");
	  out.pushDepth();
	}
	break;
	
      case REQUIRED:
	{
	  out.println("Transaction xa = _xa.beginRequired();");
	  out.println();
	  out.println("try {");
	  out.pushDepth();
	}
	break;
	
      case REQUIRES_NEW:
	{
	  out.println("Transaction xa = _xa.beginRequiresNew();");
	  out.println();
	  out.println("try {");
	  out.pushDepth();
	}
	break;
      }
    }
    
    _next.generateCall(out);

    if (_xa != null) {
      for (Class exn : _bizMethod.getApiMethod().getExceptionTypes()) {
	ApplicationException appExn
	  = (ApplicationException) exn.getAnnotation(ApplicationException.class);

	if (appExn == null)
	  continue;
	
	if (! RuntimeException.class.isAssignableFrom(exn)
	    && appExn.rollback()) {
	  out.println("} catch (" + exn.getName() + " e) {");
	  out.println("  _xa.markRollback(e);");
	  out.println("  throw e;");
	}
	else if (RuntimeException.class.isAssignableFrom(exn)
		 && ! appExn.rollback()) {
	  out.println("} catch (" + exn.getName() + " e) {");
	  out.println("  throw e;");
	}
      }

      switch (_xa) {
      case REQUIRED:
      case REQUIRES_NEW:
	{
	  out.println("} catch (RuntimeException e) {");
	  out.println("  _xa.markRollback(e);");
	  out.println("  throw e;");
	}
      }

      switch (_xa) {
      case NOT_SUPPORTED:
	{
	  out.popDepth();
	  out.println("} finally {");
	  out.println("  if (xa != null)");
	  out.println("    _xa.resume(xa);");
	  out.println("}");
	}
	break;
      
      case REQUIRED:
	{
	  out.popDepth();
	  out.println("} finally {");
	  out.println("  if (xa == null)");
	  out.println("    _xa.commit();");
	  out.println("}");
	}
	break;
      
      case REQUIRES_NEW:
	{
	  out.popDepth();
	  out.println("} finally {");
	  out.println("  _xa.endRequiresNew(xa);");
	  out.println("}");
	}
	break;
      }
    }
  }
}
