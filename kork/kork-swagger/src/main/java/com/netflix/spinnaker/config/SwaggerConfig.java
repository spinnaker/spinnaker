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
import io.swagger.v3.oas.models.media.Schema;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
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
    return new OpenAPI()
        .info(new Info().description(description).title(title).contact(new Contact().name(contact)))
        .externalDocs(
            new ExternalDocumentation()
                .url("https://spinnaker.io")
                .description("Spinnaker Documentation"));
  }

  /**
   * OpenAPI customizer to remove Groovy's MetaClass from generated schemas.
   *
   * <p>In Groovy applications, the dynamic {@code groovy.lang.MetaClass} property can be picked up
   * by OpenAPI/Swagger and included as a schema, which is unnecessary and can cause issues in API
   * documentation.
   *
   * <p>This bean iterates over all schemas in the OpenAPI components and removes any entry named
   * {@code "groovy.lang.MetaClass"}. This achieves the same effect as the previous approach used in
   * older OpenAPI versions, where similar logic or workarounds were applied to ignore Groovy's
   * MetaClass in API documentation.
   *
   * <p>By using this customizer, we ensure that the generated OpenAPI spec reflects only the
   * relevant application classes without any Groovy-specific dynamic properties.
   *
   * @return an {@link OpenApiCustomizer} that removes Groovy MetaClass schemas
   */
  @Bean
  public OpenApiCustomizer ignoreGroovyMetaClassCustomizer() {
    return openApi -> {
      if (openApi.getComponents() != null) {
        Map<String, Schema> schemas = openApi.getComponents().getSchemas();
        if (schemas != null) {
          Iterator<Map.Entry<String, Schema>> iterator = schemas.entrySet().iterator();
          while (iterator.hasNext()) {
            Map.Entry<String, Schema> entry = iterator.next();
            if ("groovy.lang.MetaClass".equals(entry.getKey())) {
              iterator.remove(); // remove schema for groovy.lang.MetaClass
            }
          }
        }
      }
    };
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
