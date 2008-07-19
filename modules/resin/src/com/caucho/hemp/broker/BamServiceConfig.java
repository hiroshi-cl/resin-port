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

package com.caucho.hemp.broker;

import java.util.*;
import java.util.logging.*;

import javax.annotation.*;
import javax.jms.*;
import javax.resource.spi.*;

import com.caucho.bam.*;
import com.caucho.config.*;
import com.caucho.config.annotation.Start;
import com.caucho.config.types.*;
import com.caucho.webbeans.cfg.AbstractBeanConfig;
import com.caucho.webbeans.component.ComponentImpl;
import com.caucho.webbeans.manager.WebBeansContainer;

import com.caucho.util.*;
import javax.webbeans.In;

/**
 * bam-service configuration
 */
public class BamServiceConfig extends BeanConfig
{
  private static final L10N L = new L10N(BamServiceConfig.class);
  private static final Logger log
    = Logger.getLogger(BamServiceConfig.class.getName());

  @In private BamBroker _broker;

  private int _threadMax = 1;
  
  public BamServiceConfig()
  {
    setScope("singleton");
  }

  @Override
  public Class getBeanConfigClass()
  {
    return BamService.class;
  }

  public void setThreadMax(int threadMax)
  {
    _threadMax = threadMax;
  }

  @PostConstruct
  public void init()
  {
    super.init();
    start();
  }

  @Start
  public void start()
  {
    BamService service = (BamService) getObject();

    String name = getName();
    if (name == null)
      name = service.getClass().getSimpleName();

    String jid = name;
    if (jid.indexOf('@') < 0)
      jid = name + "@" + _broker.getJid();
    

    service.setJid(jid);
    
    _broker.addService(service);
  }
}

