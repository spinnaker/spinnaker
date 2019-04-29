/*
 * Copyright 2019 Armory, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.kork.secrets;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

public class SecretBeanPostProcessor implements BeanPostProcessor, Ordered {

  private ConfigurableApplicationContext applicationContext;
  private SecretManager secretManager;

  SecretBeanPostProcessor(
      ConfigurableApplicationContext applicationContext, SecretManager secretManager) {
    this.applicationContext = applicationContext;
    this.secretManager = secretManager;
    MutablePropertySources propertySources =
        applicationContext.getEnvironment().getPropertySources();
    List<EnumerablePropertySource> enumerableSources = new ArrayList<>();

    for (PropertySource ps : propertySources) {
      if (ps instanceof EnumerablePropertySource) {
        enumerableSources.add((EnumerablePropertySource) ps);
      }
    }

    for (EnumerablePropertySource s : enumerableSources) {
      propertySources.replace(s.getName(), new SecretAwarePropertySource(s, secretManager));
    }
  }

  @Override
  public Object postProcessBeforeInitialization(Object bean, String beanName)
      throws BeansException {
    return bean;
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
    return bean;
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 8;
  }
}
