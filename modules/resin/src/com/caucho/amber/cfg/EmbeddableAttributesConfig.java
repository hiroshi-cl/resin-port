/*
 * Copyright (c) 1998-2011 Caucho Technology -- all rights reserved
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
 * @author Rodrigo Westrupp
 */

package com.caucho.amber.cfg;

import java.util.HashMap;


/**
 * The <embeddable-attributes> tag in orm.xml
 */
public class EmbeddableAttributesConfig {

  // elements
  private HashMap<String, BasicConfig> _basicMap
    = new HashMap<String, BasicConfig>();

  private HashMap<String, TransientConfig> _transientMap
    = new HashMap<String, TransientConfig>();

  public BasicConfig getBasic(String name)
  {
    return _basicMap.get(name);
  }

  public void addBasic(BasicConfig basic)
  {
    _basicMap.put(basic.getName(), basic);
  }

  public HashMap<String, BasicConfig> getBasicMap()
  {
    return _basicMap;
  }

  public TransientConfig getTransient(String name)
  {
    return _transientMap.get(name);
  }

  public void addTransient(TransientConfig transientConfig)
  {
    _transientMap.put(transientConfig.getName(), transientConfig);
  }

  public HashMap<String, TransientConfig> getTransientMap()
  {
    return _transientMap;
  }
}
