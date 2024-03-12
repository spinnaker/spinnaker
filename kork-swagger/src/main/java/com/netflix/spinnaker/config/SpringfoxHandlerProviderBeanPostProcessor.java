/*
 * Copyright 2024 OpsMx, Inc.
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

package com.netflix.spinnaker.config;

import java.lang.reflect.Field;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping;
import springfox.documentation.spring.web.plugins.WebMvcRequestHandlerProvider;

/**
 * With spring boot 2.6.x, default strategy for matching request paths against registered Spring MVC
 * handler mappings has changed from AntPathMatcher to PathPatternParser. The actuator endpoints
 * also use PathPattern based URL matching and path matching strategy cannot be configured for
 * actuator endpoints via a configuration property. Using Actuator and Springfox may result in
 * application failing to start. {@see https://github.com/springfox/springfox/issues/3462}
 */
@Component
public class SpringfoxHandlerProviderBeanPostProcessor implements BeanPostProcessor {

  /**
   * Overrides BeanPostProcessor method postProcessAfterInitialization() to filter the
   * springfox.documentation.spring.web.plugins.WebMvcRequestHandlerProvider beans for request
   * mapping.
   *
   * @param bean – the new bean instance
   * @param beanName – the name of the bean
   * @return the bean instance to use, either the original or a wrapped one; if null, no subsequent
   *     BeanPostProcessors will be invoked
   */
  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
    if (bean instanceof WebMvcRequestHandlerProvider) {
      customizeSpringfoxHandlerMappings(getHandlerMappings(bean));
    }
    return bean;
  }

  private <T extends RequestMappingInfoHandlerMapping> void customizeSpringfoxHandlerMappings(
      List<T> mappings) {
    List<T> copy =
        mappings.stream()
            .filter(mapping -> mapping.getPatternParser() == null)
            .collect(Collectors.toList());
    mappings.clear();
    mappings.addAll(copy);
  }

  @SuppressWarnings("unchecked")
  private List<RequestMappingInfoHandlerMapping> getHandlerMappings(Object bean) {
    try {
      Field field = ReflectionUtils.findField(bean.getClass(), "handlerMappings");
      field.setAccessible(true);
      return (List<RequestMappingInfoHandlerMapping>) field.get(bean);
    } catch (IllegalArgumentException | IllegalAccessException e) {
      throw new IllegalStateException(e);
    }
  }
}
