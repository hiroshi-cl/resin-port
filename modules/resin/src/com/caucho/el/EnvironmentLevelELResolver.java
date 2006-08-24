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

package com.caucho.el;

import java.beans.*;
import java.util.*;

import javax.el.*;

import com.caucho.loader.Environment;
import com.caucho.loader.EnvironmentLocal;
import com.caucho.loader.EnvironmentClassLoader;

import com.caucho.el.EL;
import com.caucho.el.AbstractVariableResolver;

/**
 * Creates a variable resolver based on the classloader.
 */
public class EnvironmentLevelELResolver extends ELResolver {
  private static final EnvironmentLocal<EnvironmentLevelELResolver> _local
    = new EnvironmentLocal<EnvironmentLevelELResolver>();
  
  private final ClassLoader _loader;

  private EnvironmentLevelELResolver(ClassLoader loader)
  {
    _loader = loader;
  }
  
  /**
   * Creates the resolver
   */
  public static EnvironmentLevelELResolver create()
  {
    return create(Thread.currentThread().getContextClassLoader());
  }
  
  /**
   * Creates the resolver
   */
  public static EnvironmentLevelELResolver create(ClassLoader loader)
  {
    EnvironmentLevelELResolver elResolver = _local.getLevel(loader);

    if (elResolver == null) {
      for (; loader != null; loader = loader.getParent()) {
	if (loader instanceof EnvironmentClassLoader) {
	  elResolver = new EnvironmentLevelELResolver(loader);
	  _local.set(elResolver, loader);

	  return elResolver;
	}
      }

      loader = ClassLoader.getSystemClassLoader();
      elResolver = new EnvironmentLevelELResolver(loader);
      _local.set(elResolver, loader);
    }

    return elResolver;
  }

  /**
   * Returns true for read-only.
   */
  @Override
  public boolean isReadOnly(ELContext context, Object base, Object property)
  {
    if (property != null || ! (base instanceof String))
      return true;

    context.setPropertyResolved(true);

    return false;
  }
  
  /**
   * Returns the named variable value.
   */
  @Override
  public Class<?> getType(ELContext context,
			Object base,
			Object property)
  {
    Object value = getValue(context, base, property);

    if (value != null)
      return value.getClass();
    else
      return null;
  }

  public Class<?> getCommonPropertyType(ELContext context,
					Object base)
  {
    return null;
  }

  public Iterator<FeatureDescriptor>
    getFeatureDescriptors(ELContext context, Object base)
  {
    return null;
  }
  
  /**
   * Returns the named variable value.
   */
  @Override
  public Object getValue(ELContext env,
			 Object base,
			 Object property)
  {
    if (property != null)
      return null;
    else if (! (base instanceof String))
      return null;

    String var = (String) base;

    Object value = EL.getLevelVar(var, _loader);

    if (value == null)
      return null;

    env.setPropertyResolved(true);

    if (value == EL.NULL)
      return null;
    else
      return value;
  }
  
  /**
   * Sets the value for the named variable.
   */
  @Override
  public void setValue(ELContext env,
		       Object base,
		       Object property,
		       Object value)
  {
    if (property != null || ! (base instanceof String))
      return;

    env.setPropertyResolved(true);

    String name = (String) base;

    EL.putVar(name, value, _loader);
  }

  public String toString()
  {
    return "EnvironmentLevelELResolver[" + _loader + "]";
  }
}
