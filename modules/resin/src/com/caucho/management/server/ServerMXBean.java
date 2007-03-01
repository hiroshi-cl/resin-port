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

package com.caucho.management.server;

import com.caucho.jmx.Description;
import com.caucho.jmx.Units;

import java.util.Date;

/**
 * Management interface for the server.  Each server corresponds to a
 * JVM instance running Resin.
 *
 * <p>Since exactly on ServerMBean is running at a time it has a unique
 * mbean name,
 *
 * <pre>
 * resin:type=Server
 * </pre>
 */
@Description("The Resin Server running on this JVM instance")
public interface ServerMXBean extends ManagedObjectMXBean {
  //
  // ID attributes
  //
  
  /**
   * Returns the -server id.
   */
  @Description("The server id used when starting this instance"
               + " of Resin, the value of `-server'")
  public String getId();

  //
  // Hierarchy
  //
  
  /**
   * Returns the cluster owning this server
   */
  @Description("The cluster contains the peer servers")
  public ClusterMXBean getCluster();

  /**
   * Returns the array of ports.
   */
  @Description("Ports accept socket connections")
  public PortMXBean []getPorts();

  /**
   * Returns the server's thread pool administration
   */
  @Description("The thread pool for the server")
  public ThreadPoolMXBean getThreadPool();

  /**
   * Returns the cluster port
   */
  @Description("The cluster port handles management and cluster messages")
  public PortMXBean getClusterPort();

  //
  // Configuration attributes
  //

  /**
   * Returns true if a {@link com.caucho.server.port.AbstractSelectManager} is enabled and active
   */
  @Description("A SelectManager handles keepalive without requiring a thread")
  public boolean isSelectManagerEnabled();

  /**
   * Returns true if detailed statistics are being kept.
   */
  @Description("Detailed statistics causes various parts of Resin to keep"
               + " more detailed statistics at the possible expense of"
               +" some performance")
  public boolean isDetailedStatistics();

  //
  // state
  //

  /**
   * The current lifecycle state.
   */
  @Description("The current lifecycle state")
  public String getState();

  /**
   * Returns the last start time.
   */
  @Description("The time that this instance was last started or restarted")
  public Date getStartTime();

  //
  // statistics
  //

  /**
   * Returns the current number of threads that are servicing requests.
   */
  @Description("The current number of threads that are servicing requests")
  public int getThreadActiveCount();

  /**
   * Returns the current number of connections that are in the keepalive
   * state and are using a thread to maintain the connection.
   */
  @Description("The current number of connections that are" +
               " in the keepalive state and are using" +
               " a thread to maintain the connection")
  public int getThreadKeepaliveCount();

  /**
   * Returns the current number of connections that are in the keepalive
   * state and are using select to maintain the connection.
   */
  @Description("The current number of connections that are" +
               " in the keepalive state and are using" +
               " select to maintain the connection")
  public int getSelectKeepaliveCount();

  /**
   * Returns the total number of requests serviced by the server
   * since it started.
   */
  @Description("The total number of requests serviced by the"
               + " server since it started")
  public long getRequestCountTotal();

  /**
   * Returns the number of requests that have ended up in the keepalive state
   * for this server in it's lifetime.
   */
  @Description("The total number of requests that have ended"
               + " up in the keepalive state")
  public long getKeepaliveCountTotal();

  /**
   * The total number of connections that have terminated with
   * {@link com.caucho.vfs.ClientDisconnectException}.
   */
  @Description("The total number of connections that have" +
               " terminated with a client disconnect")
  long getClientDisconnectCountTotal();

  /**
   * Returns the total duration in milliseconds that requests serviced by
   * this server have taken.
   */
  @Description("The total duration in milliseconds that"
               + " requests serviced by this service have taken")
  @Units("milliseconds")
  long getRequestTimeTotal();

  /**
   * Returns the total number of bytes that requests serviced by this
   * server have read.
   */
  @Description("The total number of bytes that requests"
               + " serviced by this server have read")
  @Units("bytes")
  long getRequestReadBytesTotal();

  /**
   * Returns the total number of bytes that requests serviced by this
   * server have written.
   */
  @Description("The total number of bytes that requests"
               + " serviced by this server have written")
  @Units("bytes")
  long getRequestWriteBytesTotal();

  /**
   * Returns the invocation cache hit count.
   */
  @Description("The invocation cache is an internal cache used"
               + " by Resin to optimize the handling of urls")
  public long getInvocationCacheHitCountTotal();

  /**
   * Returns the invocation cache miss count.
   */
  @Description("The invocation cache is an internal cache used"
               + " by Resin to optimize the handling of urls")
  public long getInvocationCacheMissCountTotal();

  /**
   * Returns the current total amount of memory available for the JVM, in bytes.
   */
  @Description("The current total amount of memory available for the JVM, in bytes")
  @Units("bytes")
  public long getRuntimeMemory();

  /**
   * Returns the current free amount of memory available for the JVM, in bytes.
   */
  @Description("The current free amount of memory available for the JVM, in bytes")
  @Units("bytes")
  public long getRuntimeMemoryFree();

  /**
   * Returns the current CPU load average.
   */
  @Description("The current CPU load average")
  public double getCpuLoadAvg();

  //
  // Operations
  //

  /**
   * Restart this Resin server.
   */
  @Description("Exit this instance cleanly and allow the wrapper script to"
               + " start a new JVM")
  public void restart();

}
