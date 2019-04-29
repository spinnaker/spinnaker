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

package com.netflix.spinnaker.igor.artifacts;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.igor.build.model.GenericBuild;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.artifacts.parsing.JinjaArtifactExtractor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ArtifactExtractor {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final JinjaTemplateService jinjaTemplateService;
  private final JinjaArtifactExtractor.Factory jinjaArtifactExtractorFactory;

  public List<Artifact> extractArtifacts(GenericBuild build) {
    final String messageString;
    try {
      messageString = objectMapper.writeValueAsString(build);
    } catch (JsonProcessingException e) {
      log.error("Error processing JSON: {}", e);
      return Collections.emptyList();
    }

    return getArtifactTemplates(build).stream()
        .flatMap(template -> processTemplate(template, messageString).stream())
        .collect(Collectors.toList());
  }

  private List<Artifact> processTemplate(JinjaTemplate template, String messageString) {
    JinjaArtifactExtractor artifactExtractor =
        jinjaArtifactExtractorFactory.create(template.getAsStream());
    return artifactExtractor.getArtifacts(messageString);
  }

  private List<JinjaTemplate> getArtifactTemplates(GenericBuild build) {
    List<JinjaTemplate> templates = new ArrayList<>();

    JinjaTemplate templateFromProperty = getTemplateFromProperty(build);
    if (templateFromProperty != null) {
      templates.add(templateFromProperty);
    }
    return templates;
  }

  private JinjaTemplate getTemplateFromProperty(GenericBuild build) {
    Map<String, ?> properties = build.getProperties();
    if (properties == null) {
      return null;
    }

    String messageFormat = (String) properties.get("messageFormat");
    if (StringUtils.isEmpty(messageFormat)) {
      return null;
    }

    JinjaTemplate.TemplateType templateType = JinjaTemplate.TemplateType.STANDARD;

    Object customFormat = properties.get("customFormat");
    if (parseCustomFormat(customFormat)) {
      templateType = JinjaTemplate.TemplateType.CUSTOM;
    }

    return jinjaTemplateService.getTemplate(messageFormat, templateType);
  }

  private boolean parseCustomFormat(Object customFormat) {
    if (customFormat == null) {
      return false;
    }

    if (customFormat instanceof Boolean) {
      return (Boolean) customFormat;
    }

    if (customFormat instanceof String) {
      return Boolean.parseBoolean((String) customFormat);
    }

    throw new RuntimeException("Unexpected customFormat in property file: " + customFormat);
  }
}
