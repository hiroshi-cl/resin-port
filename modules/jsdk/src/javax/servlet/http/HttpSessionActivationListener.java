/*
 * Copyright (c) 1998-2001 Caucho Technology -- all rights reserved
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
 *   Free SoftwareFoundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 *
 * $Id: HttpSessionActivationListener.java,v 1.2 2004/09/29 00:12:47 cvs Exp $
 */

package javax.servlet.http;

import java.util.EventListener;

/**
 * Interface for a listener receiving events when a session is
 * created or displayed.
 *
 * @since Servlet 2.3
 */
public interface HttpSessionActivationListener extends EventListener {
  /**
   * Callback after the session activates.
   *
   * @param event the event for the session activation
   */
  public void sessionDidActivate(HttpSessionEvent event);
  /**
   * Callback before the session passivates.
   *
   * @param event the event for the session passivation.
   */
  public void sessionWillPassivate(HttpSessionEvent event);
}
