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

import static com.google.common.base.Predicates.or;

import com.google.common.base.Predicate;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.paths.AbstractPathProvider;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@EnableSwagger2
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

  @Bean
  public Docket gateApi() {
    return new Docket(DocumentationType.SWAGGER_2)
        .pathProvider(new BasePathProvider(basePath, documentationPath))
        .select()
        .apis(RequestHandlerSelectors.any())
        .paths(paths())
        .build()
        .apiInfo(apiInfo());
  }

  private Predicate<String> paths() {
    return or(patterns.stream().map(PathSelectors::regex).collect(Collectors.toList()));
  }

  private ApiInfo apiInfo() {
    return new ApiInfo(title, description, null, null, contact, null, null);
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

  public class BasePathProvider extends AbstractPathProvider {
    private String basePath;
    private String documentationPath;

    private BasePathProvider(String basePath, String documentationPath) {
      this.basePath = basePath;
      this.documentationPath = documentationPath;
    }

    @Override
    protected String applicationPath() {
      return basePath;
    }

    @Override
    protected String getDocumentationPath() {
      return documentationPath;
    }
  }
}
