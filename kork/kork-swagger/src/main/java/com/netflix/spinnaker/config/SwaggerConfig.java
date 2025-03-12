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

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import org.springdoc.core.SpringDocUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty("swagger.enabled")
@ConfigurationProperties(prefix = "swagger")
public class SwaggerConfig {
  private String title;
  private String description;
  private String contact;
  private List<String> patterns;
  private String basePath = "";
  private String documentationPath = "/";

  private static final List<String> IGNORED_CLASS_NAMES = List.of("groovy.lang.MetaClass");

  @Bean
  public OpenAPI gateApi() {
    Arrays.stream((ignoredClasses()))
        .forEach(
            each -> {
              SpringDocUtils.getConfig().addJavaTypeToIgnore(each);
            });
    return new OpenAPI()
        .info(new Info().description(description).title(title).contact(new Contact().name(contact)))
        .externalDocs(
            new ExternalDocumentation()
                .url("https://spinnaker.io")
                .description("Spinnaker Documentation"));
  }

  private static Class[] ignoredClasses() {
    return IGNORED_CLASS_NAMES.stream()
        .map(SwaggerConfig::getClassIfPresent)
        .filter(Objects::nonNull)
        .toArray(Class[]::new);
  }

  @Nullable
  private static Class<?> getClassIfPresent(String name) {
    try {
      return Class.forName(name);
    } catch (ClassNotFoundException e) {
      return null;
    }
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

  public void setBasePath(String basePath) {
    this.basePath = basePath;
  }

  public String getBasePath() {
    return basePath;
  }

  public void setDocumentationPath(String documentationPath) {
    this.documentationPath = documentationPath;
  }

  public String getDocumentationPath() {
    return documentationPath;
  }
}
