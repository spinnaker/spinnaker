/*
 * Copyright 2023 Apple Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.kork.secrets;

import javax.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.stereotype.Component;

/**
 * Handles registration of {@link SecretAwarePropertySource} wrappers during startup. This
 * registration must take place after Spring has finished applying bean factory post processors and
 * bean post processors to allow for {@link SecretEngine} instances to be configured as beans.
 */
@Component
@RequiredArgsConstructor
public class SecretAwarePropertySourceRegistrar
    implements EnvironmentAware, BeanFactoryAware, InitializingBean, Ordered, BeanPostProcessor {
  private ConfigurableEnvironment environment;
  @Setter private BeanFactory beanFactory;

  @Override
  public void setEnvironment(@Nonnull Environment environment) {
    this.environment = (ConfigurableEnvironment) environment;
  }

  @Override
  public void afterPropertiesSet() throws Exception {
    MutablePropertySources propertySources = environment.getPropertySources();
    SecretPropertyProcessor processor = beanFactory.getBean(SecretPropertyProcessor.class);
    propertySources.stream()
        .filter(EnumerablePropertySource.class::isInstance)
        .map(source -> (EnumerablePropertySource<?>) source)
        .forEach(
            source ->
                propertySources.replace(
                    source.getName(), new SecretAwarePropertySource<>(source, processor)));
  }

  @Override
  public int getOrder() {
    return LOWEST_PRECEDENCE;
  }
}
