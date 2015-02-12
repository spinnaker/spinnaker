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

package com.netflix.spinnaker.config;

import com.mangofactory.swagger.configuration.SpringSwaggerConfig;
import com.mangofactory.swagger.models.dto.ApiInfo;
import com.mangofactory.swagger.plugin.EnableSwagger;
import com.mangofactory.swagger.plugin.SwaggerSpringMvcPlugin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@EnableSwagger
@Configuration
@ConditionalOnProperty("swagger.enabled")
@ConfigurationProperties(prefix = "swagger")
public class SwaggerConfig {
  private String title;
  private String description;
  private String contact;
  private List<String> patterns;

  @Autowired
  SpringSwaggerConfig springSwaggerConfig;

  @Bean
  public SwaggerSpringMvcPlugin swaggerPlugin() {
    SwaggerSpringMvcPlugin plugin = new SwaggerSpringMvcPlugin(this.springSwaggerConfig).apiInfo(apiInfo());
    if (patterns != null && !patterns.isEmpty()) {
      plugin = plugin.includePatterns(patterns.toArray(new String[patterns.size()]));
    }

    return plugin;
  }

  private ApiInfo apiInfo() {
    return new ApiInfo(
      title,
      description,
      null,
      contact,
      null,
      null
    );
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public void setContact(String contact) {
    this.contact = contact;
  }

  public List<String> getPatterns() {
    return patterns;
  }

  public void setPatterns(List<String> patterns) {
    this.patterns = patterns;
  }
}
