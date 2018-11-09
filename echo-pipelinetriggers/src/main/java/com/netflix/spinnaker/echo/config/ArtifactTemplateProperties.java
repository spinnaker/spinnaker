/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.echo.config;

import com.netflix.spinnaker.echo.pipelinetriggers.artifacts.CustomJinjaTemplate;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
@Configuration
@ConfigurationProperties(prefix = "artifacts")
@Validated
public class ArtifactTemplateProperties {
  @Valid
  private List<CustomTemplateConfig> templates = new ArrayList<>();

  @Data
  @NoArgsConstructor
  public static class CustomTemplateConfig {
    @NotEmpty
    private String name;

    @NotEmpty
    private String templatePath;

    CustomJinjaTemplate toCustomTemplate() {
      return new CustomJinjaTemplate(templatePath);
    }
  }

  public Map<String, CustomJinjaTemplate> getCustomArtifactTemplates() {
    return templates
      .stream()
      .collect(
        Collectors.toMap(CustomTemplateConfig::getName, CustomTemplateConfig::toCustomTemplate)
      );
  }
}
