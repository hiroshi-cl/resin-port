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

package com.caucho.hemp.manager;

import java.util.*;
import java.lang.ref.*;
import java.io.Serializable;

import com.caucho.hemp.*;
import com.caucho.server.resin.*;
import com.caucho.util.*;

/**
 * Entity
 */
class RosterItem {
  private static final L10N L = new L10N(RosterItem.class);

  private final String _ownerJid;
  private final String _targetJid;

  private final SubscriptionState _state;

  private RosterItem()
  {
    _ownerJid = null;
    _targetJid = null;
    _state = null;
  }

  RosterItem(String ownerJid, String targetJid, SubscriptionState state)
  {
    _ownerJid = ownerJid;
    _targetJid = targetJid;

    _state = state;
  }

  public String getOwner()
  {
    return _ownerJid;
  }

  public String getTarget()
  {
    return _targetJid;
  }

  public boolean isSubscribedTo()
  {
    return (_state == SubscriptionState.TO
	    || _state == SubscriptionState.BOTH);
  }

  public boolean isSubscriptionFrom()
  {
    return (_state == SubscriptionState.FROM
	    || _state == SubscriptionState.BOTH);
  }
  
  @Override
  public String toString()
  {
    return (getClass().getSimpleName()
	    + "[target=" + _targetJid
	    + ",owner=" + _ownerJid
	    + "," + _state + "]");
  }
}
