/*
 * Copyright (c) 1998-2009 Caucho Technology -- all rights reserved
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

package javax.cache;

import java.util.Collection;
import java.util.Map;

public interface CacheLoader<K,V>
{
  /**
   * Obtains the value associated with the key, which will be loaded into the Cache
   * @param key associated with the value.
   * @return the value returned from the CacheLoader
   * @throws CacheException
   */
  public V load(K key)
    throws CacheException;

  /**
   * Creates a set of entries that will be loaded into the cache.
   * @param keys the collection of keys
   * @return a map of key-value pairs that will be loaded into the cache.
   * @throws CacheException
   */
  public Map<K,V> loadAll(Collection<K> keys)
    throws CacheException;
}
