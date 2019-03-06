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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.echo.artifacts.MessageArtifactTranslator;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Extracts artifacts from a trigger using a supplied Jinja template
 */
@Component
@Slf4j
public class JinjaArtifactExtractor {
  private final ObjectMapper objectMapper;
  private final JinjaTemplateService jinjaTemplateService;
  private final ApplicationEventPublisher applicationEventPublisher;

  @Autowired
  public JinjaArtifactExtractor(
    ObjectMapper objectMapper,
    JinjaTemplateService jinjaTemplateService,
    ApplicationEventPublisher applicationEventPublisher
  ) {
    this.objectMapper = objectMapper;
    this.jinjaTemplateService = jinjaTemplateService;
    this.applicationEventPublisher = applicationEventPublisher;
  }

  public List<Artifact> extractArtifacts(Trigger inputTrigger) {
    final String messageString;
    try {
      messageString = objectMapper.writeValueAsString(inputTrigger);
    } catch (JsonProcessingException e) {
      log.error("Error processing JSON: {}", e);
      return Collections.emptyList();
    }

    return getArtifactTemplates(inputTrigger)
      .stream()
      .flatMap(template -> processTemplate(template, messageString).stream())
      .collect(Collectors.toList());
  }

  private List<Artifact> processTemplate(JinjaTemplate template, String messageString) {
    MessageArtifactTranslator messageArtifactTranslator = new MessageArtifactTranslator(
      template.getAsStream(),
      applicationEventPublisher
    );
    return messageArtifactTranslator.parseArtifacts(messageString);
  }

  private List<JinjaTemplate> getArtifactTemplates(Trigger trigger) {
    List<JinjaTemplate> templates = new ArrayList<>();

    JinjaTemplate templateFromProperty = getTemplateFromProperty(trigger);
    if (templateFromProperty != null) {
      templates.add(templateFromProperty);
    }
    return templates;
  }

  private JinjaTemplate getTemplateFromProperty(Trigger trigger) {
    if (trigger == null) {
      return null;
    }

    Map<String, Object> properties = trigger.getProperties();
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
