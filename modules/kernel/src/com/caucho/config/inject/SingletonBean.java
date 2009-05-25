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

package com.caucho.config.inject;

import com.caucho.config.ConfigContext;
import com.caucho.config.inject.HandleAware;
import com.caucho.config.scope.ScopeContext;
import com.caucho.config.scope.ApplicationScope;

import java.io.Closeable;
import java.lang.annotation.*;
import java.lang.reflect.Type;
import java.util.Set;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.ManagedBean;

/**
 * SingletonBean represents a singleton instance exported as a web beans.
 *
 * <code><pre>
 * @Current Manager manager;
 *
 * manager.addBean(new SingletonBean(myValue));
 * </pre></code>
 */
public class SingletonBean extends AbstractSingletonBean
  implements Closeable
{
  private Object _value;

  SingletonBean(ManagedBean managedBean,
		Set<Type> types,
		Class<? extends Annotation> deploymentType,
		Set<Annotation> bindings,
		Class<? extends Annotation> scopeType,
		String name,
		Object value)
  {
    super(managedBean, types, deploymentType, bindings, scopeType, name);

    _value = value;
  }

  @Override
  public Object create(CreationalContext env)
  {
    return _value;
  }

  /**
   * Frees the singleton on environment shutdown
   */
  public void close()
  {
    if (_value != null)
      destroy(_value);
  }
}
