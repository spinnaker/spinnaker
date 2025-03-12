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

import java.util.List;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ConfigurationClassPostProcessor;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.DefaultResourceLoader;

/**
 * Configures a plugin context. Also allows for package scanning and bean promotion from a plugin to
 * the application's context.
 */
public class SpringLoader implements ApplicationContextAware {

  private final ClassLoader pluginClassLoader;
  private final List<String> packagesToScan;
  private final List<Class> classesToRegister;
  private final AnnotationConfigApplicationContext pluginContext;

  public SpringLoader(
      AnnotationConfigApplicationContext pluginContext,
      ClassLoader pluginClassLoader,
      List<String> packagesToScan,
      List<Class> classesToRegister) {
    this.pluginClassLoader = pluginClassLoader;
    this.packagesToScan = packagesToScan;
    this.classesToRegister = classesToRegister;
    this.pluginContext = pluginContext;
  }

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
    final GenericApplicationContext appContext = (GenericApplicationContext) applicationContext;
    final BeanPromoter beanPromoter =
        new BeanPromoter() {
          @Override
          public void promote(String beanName, Object bean, Class beanClass, boolean isPrimary) {
            appContext.registerBean(
                beanName,
                beanClass,
                () -> bean,
                b -> {
                  if (isPrimary) {
                    b.setPrimary(true);
                  }
                });
          }
        };

    pluginContext.setParent(appContext);
    pluginContext.setClassLoader(pluginClassLoader);

    // Process configuration classes
    final ConfigurationClassPostProcessor configPostProcessor =
        new ConfigurationClassPostProcessor();
    configPostProcessor.setBeanClassLoader(pluginClassLoader);
    configPostProcessor.setEnvironment(appContext.getEnvironment());
    final DefaultResourceLoader resourceLoader = new DefaultResourceLoader();
    resourceLoader.setClassLoader(pluginClassLoader);
    configPostProcessor.setResourceLoader(resourceLoader);
    pluginContext.addBeanFactoryPostProcessor(configPostProcessor);

    pluginContext
        .getBeanFactory()
        .addBeanPostProcessor(new SpringLoaderBeanPostProcessor(pluginContext, beanPromoter));

    // Scan our configuration classes
    pluginContext.scan(packagesToScan.toArray(new String[packagesToScan.size()]));
    classesToRegister.forEach(klass -> pluginContext.register(klass));
    pluginContext.refresh();
  }
}
