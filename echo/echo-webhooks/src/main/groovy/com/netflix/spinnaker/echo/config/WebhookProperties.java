/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.echo.config;

import java.util.ArrayList;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "webhooks.artifacts")
public class WebhookProperties {
  @Valid private List<WebhookArtifactTranslator> sources = new ArrayList<>();

  public String getTemplatePathForSource(String source) {
    return sources.stream()
        .filter(s -> s.getSource().equals(source))
        .map(WebhookArtifactTranslator::getTemplatePath)
        .findAny()
        .orElse(null);
  }

  @Data
  @NoArgsConstructor
  public static class WebhookArtifactTranslator {
    @NotEmpty private String source;

    @NotEmpty private String templatePath;
  }
}
