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

package com.caucho.server.cluster;

import com.caucho.config.BuilderProgram;
import com.caucho.config.BuilderProgramContainer;
import com.caucho.config.ConfigException;
import com.caucho.config.types.Period;
import com.caucho.log.Log;
import com.caucho.server.http.HttpProtocol;
import com.caucho.server.port.Port;
import com.caucho.util.L10N;
import com.caucho.util.RandomUtil;
import com.caucho.vfs.QServerSocket;

import javax.annotation.PostConstruct;
import javax.management.ObjectName;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.logging.Logger;

/**
 * Defines a member of the cluster.
 *
 * A {@link ServerConnector} obtained with {@link #getServerConnector} is used to actually
 * communicate with this ClusterServer when it is active in another instance of
 * Resin .
 */
public class ClusterServer {
  private static final Logger log = Log.open(ClusterServer.class);
  private static final L10N L = new L10N(ClusterServer.class);

  private static final long DEFAULT = 0xcafebabe;
  
  private ObjectName _objectName;

  private Cluster _cluster;
  private Machine _machine;
  private String _id = "";

  private int _index;

  private ClusterPort _clusterPort;
  private ServerConnector _serverConnector;

  private long _socketTimeout = 65000L;
  private long _keepaliveTimeout = 15000L;
  
  private long _loadBalanceIdleTime = DEFAULT;
  private long _loadBalanceRecoverTime = 15000L;
  private long _loadBalanceWarmupTime = 60000L;
  
  private long _loadBalanceConnectTimeout = 5000L;
  
  private int _loadBalanceWeight = 100;

  private BuilderProgramContainer _serverProgram
    = new BuilderProgramContainer();

  private ArrayList<Port> _ports = new ArrayList<Port>();

  public ClusterServer(Cluster cluster)
  {
    this(new Machine(cluster));
  }

  public ClusterServer(Machine machine)
  {
    _machine = machine;
    
    _cluster = machine.getCluster();

    _clusterPort = new ClusterPort(this);
    _ports.add(_clusterPort);
    
    _serverConnector = new ServerConnector(this);
  }

  public ClusterServer(Cluster cluster, boolean isTest)
  {
    _cluster = cluster;

    _clusterPort = new ClusterPort(this);
    
    _serverConnector = new ServerConnector(this);
  }

  /**
   * Gets the server identifier.
   */
  public String getId()
  {
    return _id;
  }

  /**
   * Sets the server identifier.
   */
  public void setId(String id)
  {
    _id = id;
  }

  /**
   * Returns the cluster.
   */
  public Cluster getCluster()
  {
    return _cluster;
  }

  /**
   * Returns the machine.
   */
  public Machine getMachine()
  {
    return _machine;
  }

  /**
   * Returns the server index.
   */
  void setIndex(int index)
  {
    _index = index;
  }

  /**
   * Returns the server index.
   */
  public int getIndex()
  {
    return _index;
  }

  /**
   * Sets the keepalive timeout.
   */
  public void setKeepaliveTimeout(Period timeout)
  {
    _keepaliveTimeout = timeout.getPeriod();
  }

  /**
   * Gets the keepalive timeout.
   */
  public long getKeepaliveTimeout()
  {
    return _keepaliveTimeout;
  }

  /**
   * Sets the address
   */
  public void setAddress(String address)
    throws UnknownHostException
  {
    _clusterPort.setAddress(address);
  }

  /**
   * Sets the loadBalance connection time.
   */
  public void setLoadBalanceConnectTimeout(Period period)
  {
    _loadBalanceConnectTimeout = period.getPeriod();
  }

  /**
   * Gets the loadBalance connection time.
   */
  public long getLoadBalanceConnectTimeout()
  {
    return _loadBalanceConnectTimeout;
  }

  /**
   * Sets the loadBalance max-idle-time.
   */
  public void setLoadBalanceIdleTime(Period period)
  {
    _loadBalanceIdleTime = period.getPeriod();
  }

  /**
   * Sets the loadBalance idle-time.
   */
  public long getLoadBalanceIdleTime()
  {
    if (_loadBalanceIdleTime != DEFAULT)
      return _loadBalanceIdleTime;
    else
      return _keepaliveTimeout - 2000L;
  }

  /**
   * Sets the loadBalance fail-recover-time.
   */
  public void setLoadBalanceRecoverTime(Period period)
  {
    _loadBalanceRecoverTime = period.getPeriod();
  }

  /**
   * Gets the loadBalance fail-recover-time.
   */
  public long getLoadBalanceRecoverTime()
  {
    return _loadBalanceRecoverTime;
  }

  /**
   * Sets the loadBalance read/write timeout
   */
  public void setSocketTimeout(Period period)
  {
    _socketTimeout = period.getPeriod();
  }

  /**
   * Gets the loadBalance read/write timeout
   */
  public long getSocketTimeout()
  {
    return _socketTimeout;
  }

  /**
   * Sets the loadBalance warmup time
   */
  public void setLoadBalanceWarmupTime(Period period)
  {
    _loadBalanceWarmupTime = period.getPeriod();
  }

  /**
   * Gets the loadBalance warmup time
   */
  public long getLoadBalanceWarmupTime()
  {
    return _loadBalanceWarmupTime;
  }

  /**
   * Sets the loadBalance weight
   */
  public void setLoadBalanceWeight(int weight)
  {
    _loadBalanceWeight = weight;
  }

  /**
   * Gets the loadBalance weight
   */
  public int getLoadBalanceWeight()
  {
    return _loadBalanceWeight;
  }

  /**
   * Arguments on boot
   */
  public void addJvmArg(String args)
  {
  }

  /**
   * Arguments on boot
   */
  public void addWatchdogArg(String args)
  {
  }

  /**
   * Sets a port.
   */
  public void setPort(int port)
  {
    _clusterPort.setPort(port);
  }

  /**
   * Adds a http.
   */
  public Port createHttp()
    throws ConfigException
  {
    Port port = new Port(this);
    
    HttpProtocol protocol = new HttpProtocol();
    protocol.setParent(port);
    port.setProtocol(protocol);

    _ports.add(port);

    return port;
  }

  /**
   * Adds a custom-protocol port.
   */
  public Port createProtocol()
    throws ConfigException
  {
    Port port = new Port(this);

    _ports.add(port);

    return port;
  }

  /**
   * Pre-binding of ports.
   */
  public void bind(String address, int port, QServerSocket ss)
    throws Exception
  {
    for (int i = 0; i < _ports.size(); i++) {
      Port serverPort = _ports.get(i);

      if (port != serverPort.getPort())
	continue;

      if ((address == null) != (serverPort.getAddress() == null))
	continue;
      else if (address == null || address.equals(serverPort.getAddress())) {
	serverPort.bind(ss);

	return;
      }
    }

    throw new IllegalStateException(L.l("No matching port for {0}:{1}",
					address, port));
  }

  /**
   * Sets the user name.
   */
  public void setUserName(String userName)
  {
  }

  /**
   * Sets the group name.
   */
  public void setGroupName(String groupName)
  {
  }

  /**
   * Returns the ports.
   */
  public ArrayList<Port> getPorts()
  {
    return _ports;
  }

  /**
   * Sets the ClusterPort.
   */
  public ClusterPort createClusterPort()
  {
    return _clusterPort;
  }

  /**
   * Sets the ClusterPort.
   */
  public ClusterPort getClusterPort()
  {
    return _clusterPort;
  }

  /**
   * Returns true for secure.
   */
  public boolean isSSL()
  {
    return getClusterPort().isSSL();
  }

  /**
   * Returns the server connector.
   */
  public ServerConnector getServerConnector()
  {
    if (_cluster.getSelfServer() != this)
      return _serverConnector;
    else
      return null;
  }

  /**
   * Adds a program.
   */
  public void addBuilderProgram(BuilderProgram program)
  {
    _serverProgram.addProgram(program);
  }

  /**
   * Adds a program.
   */
  public BuilderProgram getServerProgram()
  {
    return _serverProgram;
  }

  /**
   * Initialize
   */
  public void init()
    throws Exception
  {
    _clusterPort.init();

    if (_cluster != null && ! getId().equals(_cluster.getServerId()))
      _serverConnector.init();
  }

  /**
   * Starts the server.
   */
  public Server startServer()
    throws Throwable
  {
    return _cluster.startServer(this);
  }

  /**
   * Generate the primary, secondary, tertiary, returning the value encoded
   * in a long.
   */
  public long generateBackupCode()
  {
    return generateBackupCode(_index);
  }

  /**
   * Generate the primary, secondary, tertiary, returning the value encoded
   * in a long.
   */
  public long generateBackupCode(int index)
  {
    Cluster cluster = getCluster();
    ClusterServer []srunList = cluster.getServerList();
    int srunLength = srunList.length;
    ArrayList<Machine> machineList = cluster.getMachineList();
    int machineLength = machineList.size();

    long backupCode = index;

    long backupLength = srunLength;
    if (backupLength < 3)
      backupLength = 3;
    int backup;

    if (srunLength <= 1) {
      backup = 0;
      backupCode |= 1L << 16;
    }
    else if (srunLength == 2) {
      backup = 0;
      
      backupCode |= ((index + 1L) % 2) << 16;
    }
    else if (machineLength == 1) {
      int sublen = srunLength - 1;
      if (sublen > 7)
	sublen = 7;
	
      backup = RandomUtil.nextInt(sublen);
      
      backupCode |= ((index + backup + 1L) % backupLength) << 16;
    }
    else {
      int machineIndex = _machine.getIndex();
      int sublen = machineLength - 1;
      if (sublen > 7)
	sublen = 7;
	
      int backupMachine = ((machineIndex + RandomUtil.nextInt(sublen) + 1)
			   % machineLength);

      Machine machine = machineList.get(backupMachine);
      ArrayList<ClusterServer> serverList = machine.getServerList();

      ClusterServer server;

      if (serverList.size() > 1)
	server = serverList.get(RandomUtil.nextInt(serverList.size()));
      else
	server = serverList.get(0);

      backup = (int) (server.getIndex() - index + srunLength) % srunLength - 1;
      
      backupCode |= ((index + backup + 1L) % backupLength) << 16;
    }

    if (srunLength <= 2)
      backupCode |= 2L << 32;
    else {
      int sublen = srunLength - 2;
      if (sublen > 6)
	sublen = 6;

      int third = RandomUtil.nextInt(sublen);

      if (backup <= third)
	third += 1;

      backupCode |= ((index + third + 1) % backupLength) << 32;
    }

    return backupCode;
  }

  /**
   * Close any ports.
   */
  public void close()
  {
    if (_serverConnector != null)
      _serverConnector.close();
  }

  public String toString()
  {
    return ("ClusterServer[id=" + getId() + "]");
  }
}
