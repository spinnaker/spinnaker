/*
 * Copyright 2020 Armory, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.kork.plugins.api.spring;

import com.netflix.spinnaker.kork.annotations.Beta;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ApplicationContext;

/**
 * Allows a plugin to register BeanDefinitions to be loaded in the application Spring {@link
 * ApplicationContext}.
 *
 * <p>This can be used in plugins that want to wire themselves into the application's Spring
 * Context.
 */
@Beta
public abstract class PrivilegedSpringPlugin extends Plugin {

  /**
   * Constructor to be used by plugin manager for plugin instantiation. Your plugins have to provide
   * constructor with this exact signature to be successfully loaded by manager.
   *
   * @param wrapper
   */
  public PrivilegedSpringPlugin(PluginWrapper wrapper) {
    super(wrapper);
  }

  /**
   * Provides the opportunity to register bean definitions from the plugin into the application's
   * registry.
   *
   * @param registry
   */
  public abstract void registerBeanDefinitions(BeanDefinitionRegistry registry);

  protected BeanDefinition beanDefinitionFor(Class beanClass) {
    return BeanDefinitionBuilder.genericBeanDefinition(beanClass)
        .setScope(BeanDefinition.SCOPE_SINGLETON)
        .setAutowireMode(AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR)
        .getBeanDefinition();
  }

  protected BeanDefinition primaryBeanDefinitionFor(Class beanClass) {
    final BeanDefinition beanDefinition = beanDefinitionFor(beanClass);
    beanDefinition.setPrimary(true);
    return beanDefinition;
  }

  protected void registerBean(BeanDefinition beanDefinition, BeanDefinitionRegistry registry)
      throws ClassNotFoundException {
    final Class loadedBeanClass =
        this.getClass().getClassLoader().loadClass(beanDefinition.getBeanClassName());
    registry.registerBeanDefinition(loadedBeanClass.getName(), beanDefinition);
  }
}
