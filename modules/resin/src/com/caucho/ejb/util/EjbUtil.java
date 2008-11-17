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

package com.caucho.ejb.util;

import com.caucho.webbeans.manager.WebBeansContainer;

import java.lang.reflect.*;
import java.util.List;

import javax.webbeans.manager.Decorator;

/**
 * Utilities
 */
public class EjbUtil {
  public static final Object []NULL_OBJECT_ARRAY = new Object[0];
  
  private EjbUtil()
  {
  }

  public static Method getMethod(Class cl,
				 String methodName,
				 Class paramTypes[])
    throws Exception
  {
    Method method = null;
    Exception firstException = null;

    do {
      try {
	method = cl.getDeclaredMethod(methodName, paramTypes);
      } catch (Exception e) {
	if (firstException == null)
	  firstException = e;

	cl = cl.getSuperclass();
      }
    } while (method == null && cl != null);

    if (method == null)
      throw firstException;

    method.setAccessible(true);
    
    return method;
  }

  public static Object generateDelegate(List<Decorator> beans,
					Object tail)
  {
    WebBeansContainer webBeans = WebBeansContainer.create();

    for (int i = beans.size() - 1; i >= 0; i--) {
      Decorator bean = beans.get(i);

      Object instance = webBeans.getInstance(bean);

      bean.setDelegate(instance, tail);

      tail = instance;
    }
    
    return tail;
  }

  public static Object []generateProxyDelegate(WebBeansContainer webBeans,
					       List<Decorator> beans,
					       Object proxy)
  {
    Object []instances = new Object[beans.size()];

    for (int i = 0; i < beans.size(); i++) {
      Decorator bean = beans.get(i);

      Object instance = webBeans.getInstance(bean);

      bean.setDelegate(instance, proxy);

      instances[beans.size() - 1 - i] = instance;
    }
    
    return instances;
  }

  public static int nextDelegate(Object []beans,
				 Class api,
				 int index)
  {
    for (index--; index >= 0; index--) {
      if (api.isAssignableFrom(beans[index].getClass()))
	return index;
    }

    return index;
  }

  public static int nextDelegate(Object []beans,
				 Class []apis,
				 int index)
  {
    for (index--; index >= 0; index--) {
      for (Class api : apis) {
	if (api.isAssignableFrom(beans[index].getClass()))
	  return index;
      }
    }

    return index;
  }
}
