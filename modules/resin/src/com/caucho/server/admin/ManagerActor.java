/*
 * Copyright (c) 1998-2012 Caucho Technology -- all rights reserved
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
 * @author Alex Rojkov
 */

package com.caucho.server.admin;

import com.caucho.admin.action.AddLicenseAction;
import com.caucho.admin.action.AddUserAction;
import com.caucho.admin.action.CallJmxAction;
import com.caucho.admin.action.HeapDumpAction;
import com.caucho.admin.action.JmxDumpAction;
import com.caucho.admin.action.ListJmxAction;
import com.caucho.admin.action.ListUsersAction;
import com.caucho.admin.action.PdfReportAction;
import com.caucho.admin.action.ProfileAction;
import com.caucho.admin.action.RemoveUserAction;
import com.caucho.admin.action.SetJmxAction;
import com.caucho.admin.action.SetLogLevelAction;
import com.caucho.admin.action.ThreadDumpAction;
import com.caucho.bam.Query;
import com.caucho.bam.actor.SimpleActor;
import com.caucho.bam.mailbox.MultiworkerMailbox;
import com.caucho.cloud.bam.BamSystem;
import com.caucho.cloud.network.NetworkClusterSystem;
import com.caucho.cloud.topology.CloudServer;
import com.caucho.config.ConfigException;
import com.caucho.env.service.ResinSystem;
import com.caucho.security.AdminAuthenticator;
import com.caucho.security.PasswordUser;
import com.caucho.server.cluster.Server;
import com.caucho.util.Alarm;
import com.caucho.util.L10N;
import com.caucho.vfs.Path;
import com.caucho.vfs.StreamSource;
import com.caucho.vfs.Vfs;

import javax.annotation.PostConstruct;
import javax.management.JMException;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ManagerActor extends SimpleActor
{
  private static final Logger log
    = Logger.getLogger(ManagerActor.class.getName());

  private static final L10N L = new L10N(ManagerActor.class);

  private Server _server;
  private Path _hprofDir;

  private AtomicBoolean _isInit = new AtomicBoolean();

  private AdminAuthenticator _adminAuthenticator;

  public ManagerActor()
  {
    super("manager@resin.caucho", BamSystem.getCurrentBroker());
  }

  @PostConstruct
  public void init()
  {
    if (_isInit.getAndSet(true))
      return;

    _server = Server.getCurrent();
    if (_server == null)
      throw new ConfigException(L.l(
        "resin:ManagerService requires an active Server.\n  {0}",
        Thread.currentThread().getContextClassLoader()));

    _adminAuthenticator = _server.getAdminAuthenticator();

    setBroker(getBroker());
    MultiworkerMailbox mailbox
      = new MultiworkerMailbox(getActor().getAddress(),
                               getActor(), getBroker(), 2);

    getBroker().addMailbox(mailbox);
  }

  public Path getHprofDir()
  {
    return _hprofDir;
  }

  public void setHprofDir(String hprofDir)
  {
    if (hprofDir.isEmpty())
      throw new ConfigException("hprof-dir can not be set to an emtpy string");

    Path path = Vfs.lookup(hprofDir);

    _hprofDir = path;
  }

  @Query
  public AddUserQueryResult addUser(long id,
                                    String to,
                                    String from,
                                    AddUserQuery query)
  {
    PasswordUser user = new AddUserAction(_adminAuthenticator,
                                          query.getUser(),
                                          query.getPassword(),
                                          query.getRoles()).execute();

    AddUserQueryResult result
      = new AddUserQueryResult(new UserQueryResult.User(user.getPrincipal()
                                                            .getName(),
                                                        user.getRoles()));

    getBroker().queryResult(id, from, to, result);

    return result;
  }

  @Query
  public ListUsersQueryResult listUsers(long id,
                                        String to,
                                        String from,
                                        ListUsersQuery query)
  {
    Hashtable<String,com.caucho.security.PasswordUser> userMap
      = new ListUsersAction(_adminAuthenticator).execute();

    List<UserQueryResult.User> userList = new ArrayList<UserQueryResult.User>();

    for (Map.Entry<String,PasswordUser> userEntry : userMap.entrySet()) {
      com.caucho.security.PasswordUser passwordUser = userEntry.getValue();
      UserQueryResult.User user = new UserQueryResult.User(userEntry.getKey(),
                                                           passwordUser.getRoles());
      userList.add(user);
    }

    UserQueryResult.User[] users
      = userList.toArray(new UserQueryResult.User[userList.size()]);

    ListUsersQueryResult result = new ListUsersQueryResult(users);

    getBroker().queryResult(id, from, to, result);

    return result;
  }

  @Query
  public RemoveUserQueryResult removeUser(long id,
                                          String to,
                                          String from,
                                          RemoveUserQuery query)
  {
    PasswordUser user = new RemoveUserAction(_adminAuthenticator,
                                             query.getUser()).execute();

    RemoveUserQueryResult result
      = new RemoveUserQueryResult(new UserQueryResult.User(user.getPrincipal()
                                                               .getName(),
                                                           user.getRoles()));

    getBroker().queryResult(id, from, to, result);

    return result;
  }

  @Query
  public StringQueryResult doThreadDump(long id,
                                        String to,
                                        String from,
                                        ThreadDumpQuery query)
  {
    StringQueryResult result
      = new StringQueryResult(new ThreadDumpAction().execute(false));

    getBroker().queryResult(id, from, to, result);

    return result;
  }

  @Query
  public StringQueryResult doHeapDump(long id,
                                      String to,
                                      String from,
                                      HeapDumpQuery query)
  {
    try {
      String dump = new HeapDumpAction().execute(query.isRaw(),
                                                 _server.getServerId(),
                                                 _hprofDir);

      StringQueryResult result = new StringQueryResult(dump);

      getBroker().queryResult(id, from, to, result);

      return result;
    } catch (JMException e) {
      e.printStackTrace();

      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  
  @Query
  public StringQueryResult listJmx(long id,
                                   String to,
                                   String from,
                                   JmxListQuery query)
  {
    try {
      String list = new ListJmxAction().execute(query.getPattern(),
                                                query.isPrintAttributes(),
                                                query.isPrintValues(),
                                                query.isPrintOperations(),
                                                query.isAllBeans(),
                                                query.isPlatform());

      StringQueryResult result = new StringQueryResult(list);

      getBroker().queryResult(id, from, to, result);

      return result;
    } catch (JMException e) {
      throw new RuntimeException(e);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }
  
  @Query
  public StringQueryResult doJmxDump(long id,
                                     String to,
                                     String from,
                                     JmxDumpQuery query)
  {
    try {
      String jmxDump = new JmxDumpAction().execute();

      StringQueryResult  result = new StringQueryResult(jmxDump);
      getBroker().queryResult(id, from, to, result);

      return result;
    } catch (JMException e) {
      throw new RuntimeException(e);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }  

  @Query
  public StringQueryResult setJmx(long id,
                                  String to,
                                  String from,
                                  JmxSetQuery query)
  {
    try {
      String message = new SetJmxAction().execute(query.getPattern(),
                                                  query.getAttribute(),
                                                  query.getValue());

      StringQueryResult result = new StringQueryResult(message);

      getBroker().queryResult(id, from, to, result);

      return result;
    } catch (JMException e) {
      throw new RuntimeException(e);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  @Query
  public StringQueryResult callJmx(long id,
                                   String to,
                                   String from,
                                   JmxCallQuery query)
  {
    try {
      String message = new CallJmxAction().execute(query.getPattern(),
                                                   query.getOperation(),
                                                   query.getOperationIndex(),
                                                   query.getParams());
      StringQueryResult result = new StringQueryResult(message);

      getBroker().queryResult(id, from, to, result);

      return result;
    } catch (JMException e) {
      throw new RuntimeException(e);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  @Query
  public StringQueryResult setLogLevel(long id,
                                       String to,
                                       String from,
                                       LogLevelQuery query)
  {
    String message = new SetLogLevelAction().execute(query.getLoggers(),
                                                     query.getLevel(),
                                                     query.getPeriod());

    StringQueryResult result = new StringQueryResult(message);

    getBroker().queryResult(id, from, to, result);

    return result;
  }

  @Query
  public PdfReportQueryResult pdfReport(long id,
                                        String to,
                                        String from,
                                        PdfReportQuery query)
  {
    PdfReportAction action = new PdfReportAction();
    
    if (query.getPath() != null)
      action.setPath(query.getPath());
    
    if (query.getPeriod() > 0)
      action.setPeriod(query.getPeriod());

    action.setSnapshot(query.isSnapshot());
    action.setWatchdog(query.isWatchdog());

    if (query.getProfileTime() > 0)
      action.setProfileTime(query.getProfileTime());

    if (query.getSamplePeriod() > 0)
      action.setProfileTick(query.getSamplePeriod());

    if (query.getReport() != null)
      action.setReport(query.getReport());
    
    if (query.getLogDirectory() != null)
      action.setLogDirectory(query.getLogDirectory());

    action.setReturnPdf(query.isReturnPdf());

    try {
      action.init();

      PdfReportAction.PdfReportActionResult actionResult =
        action.execute();

      StreamSource pdfSource = null;

      if (query.isReturnPdf())
        pdfSource = new StreamSource(actionResult.getPdfOutputStream());

      PdfReportQueryResult result
        = new PdfReportQueryResult(actionResult.getMessage(),
                                   actionResult.getFileName(),
                                   pdfSource);

      getBroker().queryResult(id, from, to, result);

      return result;
    } catch (RuntimeException e) {
      log.log(Level.WARNING, e.getMessage(), e);

      throw e;
    } catch (IOException e) {
      log.log(Level.WARNING, e.getMessage(), e);

      throw new RuntimeException(e);
    }
  }

  @Query
  public StringQueryResult profile(long id,
                                   String to,
                                   String from,
                                   ProfileQuery query)
  {
    String profile = new ProfileAction().execute(query.getActiveTime(),
                                                 query.getPeriod(),
                                                 query.getDepth());

    StringQueryResult result = new StringQueryResult(profile);

    getBroker().queryResult(id, from, to, result);

    return result;
  }

  @Query
  public StringQueryResult listRestarts(long id,
                                        String to,
                                        String from,
                                        ListRestartsQuery query)
  {
    final long now = Alarm.getCurrentTime();

    NetworkClusterSystem clusterService = NetworkClusterSystem.getCurrent();

    CloudServer cloudServer = clusterService.getSelfServer();

    int index = cloudServer.getIndex();

    StatSystem statSystem = ResinSystem.getCurrentService(StatSystem.class);

    long []restartTimes
      = statSystem.getStartTimes(index, now - query.getTimeBackSpan(), now);

    Date since = new Date(now - query.getTimeBackSpan());

    StringQueryResult result;
    if (restartTimes.length == 0) {
      result = new StringQueryResult(L.l(
        "Server '{0}' hasn't restarted since '{1}'",
        cloudServer,
        since));
    }
    else if (restartTimes.length == 1) {
      StringBuilder resultBuilder = new StringBuilder(L.l(
        "Server started 1 time since '{0}'", since));

      resultBuilder.append("\n  ");
      resultBuilder.append(new Date(restartTimes[0]));

      result = new StringQueryResult(resultBuilder.toString());

    }
    else {
      StringBuilder resultBuilder = new StringBuilder(L.l(
        "Server restarted {0} times since '{1}'",
        restartTimes.length,
        since));

      for (long restartTime : restartTimes) {
        resultBuilder.append("\n  ");
        resultBuilder.append(new Date(restartTime));
      }

      result = new StringQueryResult(resultBuilder.toString());
    }

    getBroker().queryResult(id, from, to, result);

    return result;
  }
  
  @Query
  public StringQueryResult addLicense(long id,
                                      String to,
                                      String from,
                                      LicenseAddQuery query)
  {
    String message = new AddLicenseAction().execute(query.getLicenseContent(),
                                                    query.getFileName(),
                                                    query.isOverwrite(),
                                                    query.isRestart());

    StringQueryResult result = new StringQueryResult(message);

    getBroker().queryResult(id, from, to, result);

    return result;
  }
}
