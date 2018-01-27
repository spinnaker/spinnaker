/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.telemetry;

import com.netflix.spectator.api.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import static java.lang.String.format;

public abstract class AbstractMetricsPostProcessor<T> implements BeanPostProcessor {

  private final Class<T> beanType;
  protected final Registry registry;
  protected final Logger log = LoggerFactory.getLogger(getClass());

  protected AbstractMetricsPostProcessor(Class<T> beanType, Registry registry) {
    this.beanType = beanType;
    this.registry = registry;
  }

  protected abstract void applyMetrics(T bean, String beanName) throws Exception;

  @SuppressWarnings("unchecked") @Override
  public final Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
    if (beanType.isAssignableFrom(bean.getClass())) {
      try {
        log.info("Applying metrics to {} {}", bean.getClass(), beanName);
        applyMetrics((T) bean, beanName);
      } catch (Exception e) {
        throw new BeanInitializationException(format("Error applying metrics to %s", beanName), e);
      }
    }
    return bean;
  }

  @Override
  public final Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
    return bean;
  }
}
