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

package javax.enterprise.inject.spi;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Set;

import javax.enterprise.context.spi.Context;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.event.Observer;
import javax.enterprise.inject.TypeLiteral;

/**
 * API for the Java Injection manager
 *
 * Applications needing a programmatic interface to WebBeans will obtain an
 * instance of the Manager either though webbeans itself or in JNDI at
 * "java:comp/env/Manager"
 */
public interface BeanManager
{
  //
  // deployment types
  //

  /**
   * Returns the enabled deployment types
   */
  public List<Class<?>> getEnabledDeploymentTypes();
  
  //
  // bean registration
  //

  /**
   * Creates a managed bean.
   */
  public <T> ManagedBean<T> createManagedBean(AnnotatedType<T> type);

  /**
   * Creates a managed bean.
   */
  public <T> ManagedBean<T> createManagedBean(Class<T> type);

  /**
   * Creates an injection target
   */
  public <T> InjectionTarget<T> createInjectionTarget(AnnotatedType<T> type);

  /**
   * Creates a managed bean.
   */
  public <T> InjectionTarget<T> createInjectionTarget(Class<T> type);
  
  /**
   * Adds a new bean definition to the manager
   */
  public void addBean(Bean<?> bean);

  /**
   * Internal callback during creation to get a new injection instance.
   */
  public void validate(InjectionPoint injectionPoint);

  //
  // Bean resolution
  //

  /**
   * Returns the beans matching a class and annotation set
   *
   * @param type the bean's class
   * @param bindings required @BindingType annotations
   */
  public <T> Set<Bean<T>> getBeans(Class<T> type,
				   Annotation... bindings);

  /**
   * Returns the beans matching a generic type and annotation set
   *
   * @param type the bean's primary type
   * @param bindings required @BindingType annotations
   */
  public <T> Set<Bean<T>> getBeans(TypeLiteral<T> type,
				   Annotation... bindings);

  /**
   * Returns the beans matching a generic type and annotation set
   *
   * @param type the bean's primary type
   * @param bindings required @BindingType annotations
   */
  public Set<Bean<?>> getBeans(Type type,
			       Annotation... bindings);

  /**
   * Returns the bean definitions matching a given name
   *
   * @param name the name of the bean to match
   */
  public Set<Bean<?>> getBeans(String name);

  /**
   * Returns the most specialized bean
   *
   * @param name the basic bean
   */
  public <X> Bean<X> getMostSpecializedBean(Bean<X> bean);

  //
  // Bean instantiation
  //

  /**
   * Returns an instance for the given bean.  This method will obey
   * the scope of the bean, so a singleton will return the single bean.
   *
   * @param bean the metadata for the bean
   * @param beanType the expected type
   *
   * @return an instance of the bean obeying scope
   */
  public Object getReference(Bean<?> bean, Type beanType);

  /**
   * Returns an instance for the given bean.  This method will obey
   * the scope of the bean, so a singleton will return the single bean.
   *
   * @param bean the metadata for the bean
   * @param beanType the expected type
   *
   * @return an instance of the bean obeying scope
   */
  public <T> T getReference(Bean<? extends T> bean, Class<T> beanType);

  /**
   * Returns an instance for the given bean.  This method will obey
   * the scope of the bean, so a singleton will return the single bean.
   *
   * @param bean the metadata for the bean
   * @param beanType the expected type
   *
   * @return an instance of the bean obeying scope
   */
  public <T> T getReference(Bean<? extends T> bean, TypeLiteral<T> beanType);

  /**
   * Internal callback during creation to get a new injection instance.
   */
  public <T> T getInjectableReference(InjectionPoint ij,
				      CreationalContext<?> ctx);

  /**
   * Returns an instance of bean matching a given name
   *
   * @param name the name of the bean to match
   */
  public Object getInstanceByName(String name);

  //
  // Observer registration
  //

  /**
   * Registers an event observer
   *
   * @param observer the observer object
   * @param eventType the type of event to listen for
   * @param bindings the binding set for the event
   */
  public <T> void addObserver(Observer<T> observer,
			      Class<T> eventType,
			      Annotation... bindings);

  /**
   * Registers an event observer
   *
   * @param observer the observer object
   * @param eventType the type of event to listen for
   * @param bindings the binding set for the event
   */
  public <T> void addObserver(Observer<T> observer,
			      TypeLiteral<T> eventType,
			      Annotation... bindings);

  /**
   * Registers an event observer
   *
   * @param observer the observer object
   * @param eventType the type of event to listen for
   * @param bindings the binding set for the event
   */
  public void addObserver(Observer<?> observer,
			  Type eventType,
			  Annotation... bindings);

  /**
   * Removes an event observer
   *
   * @param observer the observer object
   * @param eventType the type of event to listen for
   * @param bindings the binding set for the event
   */
  public <T> void removeObserver(Observer<T> observer,
				 Class<T> eventType,
				 Annotation... bindings);

  /**
   * Removes an event observer
   *
   * @param observer the observer object
   * @param eventType the type of event to listen for
   * @param bindings the binding set for the event
   */
  public <T> void removeObserver(Observer<T> observer,
				 TypeLiteral<T> eventType,
				 Annotation... bindings);

  /**
   * Removes an event observer
   *
   * @param observer the observer object
   * @param eventType the type of event to listen for
   * @param bindings the binding set for the event
   */
  public void removeObserver(Observer<?> observer,
			     Type eventType,
			     Annotation... bindings);

  //
  // Observer resolution
  //

  /**
   * Returns the observers listening for an event
   *
   * @param eventType event to resolve
   * @param bindings the binding set for the event
   */
  public <T> Set<Observer<T>> resolveObservers(T event,
					       Annotation... bindings);

  //
  // scopes
  //

  /**
   * Adds a new scope context
   */
  public void addContext(Context context);

  /**
   * Returns the scope context for the given type
   */
  public Context getContext(Class<? extends Annotation> scopeType);

  //
  // event management
  //

  /**
   * Fires an event
   *
   * @param event the event to fire
   * @param bindings the event bindings
   */
  public void fireEvent(Object event,
			Annotation... bindings);

  //
  // interceptor support
  //

  /**
   * Adds a new interceptor
   */
  public void addInterceptor(Interceptor interceptor);

  /**
   * Resolves the interceptors for a given interceptor type
   *
   * @param type the main interception type
   * @param bindings qualifying bindings
   *
   * @return the matching interceptors
   */
  public List<Interceptor<?>> resolveInterceptors(InterceptionType type,
						  Annotation... bindings);
  

  //
  // decorator
  //

  /**
   * Adds a new decorator
   */
  public BeanManager addDecorator(Decorator decorator);

  /**
   * Resolves the decorators for a given set of types
   *
   * @param types the types to match for the decorator
   * @param bindings qualifying bindings
   *
   * @return the matching interceptors
   */
  public List<Decorator<?>> resolveDecorators(Set<Type> types,
					      Annotation... bindings);

  //
  // Actitivities
  //

  /**
   * Creates a new activity
   */
  public BeanManager createActivity();

  /**
   * Associate the context with a scope
   */
  public BeanManager setCurrent(Class<? extends Annotation> scopeType);
}
