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

package com.netflix.spinnaker.echo.pipelinetriggers.artifacts;

import com.netflix.spinnaker.echo.config.ArtifactTemplateProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Map;

@Component
public final class JinjaTemplateService {
  private final ArtifactTemplateProperties artifactTemplateProperties;
  private Map<String, CustomJinjaTemplate> customTemplates;

  @Autowired
  public JinjaTemplateService(ArtifactTemplateProperties artifactTemplateProperties) {
    this.artifactTemplateProperties = artifactTemplateProperties;
  }

  @PostConstruct
  private void initializeCustomTemplates() {
    this.customTemplates = artifactTemplateProperties.getCustomArtifactTemplates();
  }

  public JinjaTemplate getTemplate(String name, JinjaTemplate.TemplateType type) {
    if (type == JinjaTemplate.TemplateType.CUSTOM) {
      return getCustomJinjaTemplate(name);
    } else {
      return getStandardJinjaTemplate(name);
    }
  }

  private StandardJinjaTemplate getStandardJinjaTemplate(String name) {
    return StandardJinjaTemplate.valueOf(name);
  }

  private CustomJinjaTemplate getCustomJinjaTemplate(String name) {
    return customTemplates.get(name);
  }
}
