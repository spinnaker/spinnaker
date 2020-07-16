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

import com.netflix.spinnaker.kork.annotations.Alpha;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.pf4j.PluginWrapper;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/** Allows a plugin to scan packages for beans and load Spring Configurations. */
@Alpha
public abstract class SpringLoaderPlugin extends PrivilegedSpringPlugin {

  protected AnnotationConfigApplicationContext pluginContext =
      new AnnotationConfigApplicationContext();

  /**
   * Constructor to be used by plugin manager for plugin instantiation. Your plugins have to provide
   * constructor with this exact signature to be successfully loaded by manager.
   *
   * @param wrapper
   */
  public SpringLoaderPlugin(PluginWrapper wrapper) {
    super(wrapper);
  }

  @Override
  public void registerBeanDefinitions(BeanDefinitionRegistry registry) {
    final String springLoaderBeanName = wrapper.getPluginId() + "." + SpringLoader.class.getName();

    ClassLoader pluginClassLoader = getClass().getClassLoader();
    BeanDefinition springLoaderBeanDefinition =
        BeanDefinitionBuilder.genericBeanDefinition(SpringLoader.class)
            .setScope(BeanDefinition.SCOPE_SINGLETON)
            .setAutowireMode(AutowireCapableBeanFactory.AUTOWIRE_NO)
            .addConstructorArgValue(pluginContext)
            .addConstructorArgValue(pluginClassLoader)
            .addConstructorArgValue(getPackagesToScan())
            .addConstructorArgValue(getClassesToRegister())
            .getBeanDefinition();
    registry.registerBeanDefinition(springLoaderBeanName, springLoaderBeanDefinition);

    // delay controller mapping until after the plugin has a chance to load its own controllers
    final BeanDefinition requestMappingHandlerMapping =
        registry.getBeanDefinition("requestMappingHandlerMapping");
    final String[] mappingDependsOnArray = requestMappingHandlerMapping.getDependsOn();
    final List<String> mappingDependsOn;
    if (mappingDependsOnArray == null) {
      mappingDependsOn = new ArrayList<>();
    } else {
      mappingDependsOn = new ArrayList<>(Arrays.asList(mappingDependsOnArray));
    }
    mappingDependsOn.add(springLoaderBeanName);
    requestMappingHandlerMapping.setDependsOn(
        mappingDependsOn.toArray(new String[mappingDependsOn.size()]));
  }

  /** Specify plugin packages to scan for beans. */
  public List<String> getPackagesToScan() {
    return Collections.emptyList();
  }

  /** Specify plugin classes to register with the plugin context. */
  public List<Class> getClassesToRegister() {
    return Collections.emptyList();
  }
}
