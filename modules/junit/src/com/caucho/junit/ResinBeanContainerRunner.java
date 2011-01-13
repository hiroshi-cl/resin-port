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
 *   Free SoftwareFoundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.junit;

import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;

import com.caucho.config.inject.InjectManager;
import com.caucho.resin.BeanContainerRequest;
import com.caucho.resin.ResinBeanContainer;

/**
 * Resin bean container runner runs a JUnit 4 test backed by a Resin context.
 * 
 * TODO Add more Javadoc since this is a public API.
 */
// TODO The container is not being shutdown properly, so some pre-destroy call-backs may not
// happen. Add a JVM shutdown hook or register to listen when JUnit finishes running all the tests?
public class ResinBeanContainerRunner extends BlockJUnit4ClassRunner {
  private Class<?> _testClass;

  private ResinBeanContainer _beanContainer;
  private ResinBeanConfiguration _beanConfiguration;

  public ResinBeanContainerRunner(Class<?> testClass)
    throws Throwable
  {
    super(testClass);

    _testClass = testClass;

    _beanConfiguration = testClass.getAnnotation(ResinBeanConfiguration.class);
  }

  @Override
  protected Object createTest()
    throws Exception
  {
    InjectManager manager = getResinContext().getInstance(InjectManager.class);

    // Make the test class a CDI bean, but do not actually register it with CDI.
    return manager.createTransientObject(_testClass);
  }  
  
  @Override
  protected void runChild(FrameworkMethod method, RunNotifier notifier)
  {
    ResinBeanContainer beanContainer = getResinContext();
    
    // Each method is treated as a separate HTTP request.
    BeanContainerRequest request = beanContainer.beginRequest();

    try {
      super.runChild(method, notifier);
    } finally {
      request.close();
    }
  }

  protected ResinBeanContainer getResinContext()
  {
    if (_beanContainer == null) {
      _beanContainer = new ResinBeanContainer();

      // TODO Make sure this is system-independent. Use java.io.tmpdir, path.separator, 
      // user.dir, user.home, instead?      
      String userName = System.getProperty("user.name");
      String workDir = "file:/tmp/" + userName;

      _beanContainer.setWorkDirectory(workDir);

      if (_beanConfiguration != null) {
        for (String module : _beanConfiguration.modules()) {
          _beanContainer.addModule(module);
        }

        for (String conf : _beanConfiguration.beansXml()) {
          _beanContainer.addBeansXml(conf);
        }
      }

      _beanContainer.start();
    }

    return _beanContainer;
  }
}
