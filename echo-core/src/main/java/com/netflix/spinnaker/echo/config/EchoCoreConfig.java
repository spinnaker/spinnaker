/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.echo.config;

import com.netflix.spinnaker.config.PluginsAutoConfiguration;
import com.netflix.spinnaker.echo.api.events.EventListener;
import com.netflix.spinnaker.echo.events.EventPropagator;
import com.netflix.spinnaker.kork.artifacts.parsing.DefaultJinjavaFactory;
import com.netflix.spinnaker.kork.artifacts.parsing.JinjaArtifactExtractor;
import com.netflix.spinnaker.kork.artifacts.parsing.JinjavaFactory;
import com.netflix.spinnaker.kork.core.RetrySupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Configuration for Event Propagator */
@Configuration
@ComponentScan({
  "com.netflix.spinnaker.echo.build",
  "com.netflix.spinnaker.echo.events",
})
@Import({PluginsAutoConfiguration.class})
public class EchoCoreConfig {
  private ApplicationContext context;

  @Autowired
  public EchoCoreConfig(ApplicationContext context) {
    this.context = context;
  }

  @Bean
  public EventPropagator propagator() {
    EventPropagator instance = new EventPropagator();
    for (EventListener e : context.getBeansOfType(EventListener.class).values()) {
      instance.addListener(e);
    }
    return instance;
  }

  @Bean
  @ConditionalOnMissingBean
  public JinjavaFactory jinjavaFactory() {
    return new DefaultJinjavaFactory();
  }

  @Bean
  @ConditionalOnMissingBean
  public JinjaArtifactExtractor.Factory jinjaArtifactExtractorFactory(
      JinjavaFactory jinjavaFactory) {
    return new JinjaArtifactExtractor.Factory(jinjavaFactory);
  }

  @Bean
  public RetrySupport retrySupport() {
    return new RetrySupport();
  }
}
