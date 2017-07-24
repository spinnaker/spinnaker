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

package com.netflix.spinnaker.fiat;

import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;

import java.io.IOException;

// source: https://stackoverflow.com/a/37349492/5569046
public class YamlFileApplicationContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    try {
      Resource resource = applicationContext.getResource("classpath:application.yml");
      YamlPropertySourceLoader sourceLoader = new YamlPropertySourceLoader();
      PropertySource<?> yamlTestProperties = sourceLoader.load("yamlTestProperties", resource, null);
      applicationContext.getEnvironment().getPropertySources().addLast(yamlTestProperties);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
