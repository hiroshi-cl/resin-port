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

package com.caucho.config;

import com.caucho.util.L10N;
import com.caucho.xml.QName;

import org.w3c.dom.Node;

public class EnvironmentAttributeStrategy extends AttributeStrategy {
  static final L10N L = new L10N(EnvironmentAttributeStrategy.class);

  private final TypeStrategy _typeStrategy;

  public EnvironmentAttributeStrategy(TypeStrategy typeStrategy)
  {
    _typeStrategy = typeStrategy;
  }

  @Override
  public Object create(NodeBuilder builder, Object parent)
    throws Exception
  {
    return _typeStrategy.create();
  }

  public void configure(NodeBuilder builder, Object bean,
			QName name, Node node)
          throws Exception
  {
    if (builder.isIgnoreEnvironment())
      return;

    Object value = _typeStrategy.configure(builder, node, bean);

    /*
    // builder.configureChildImpl(_typeStrategy, node, bean);
    Object child = builder.createResinType(node);

    if (child == null)
      child = _typeStrategy.create();

    if (child != null) {
      _typeStrategy.setParent(child, bean);
      
      builder.configureImpl(_typeStrategy, child, node);
    }
    else
      builder.configureChildImpl(_typeStrategy, node, bean);
    */
  }

  public String toString()
  {
    return "EnvironmentAttributeStrategy[" + _typeStrategy + "]";
  }
}
