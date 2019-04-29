/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.echo.artifacts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.echo.config.WebhookProperties;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TemplateBasedArtifactExtractor implements WebhookArtifactExtractor {
  private final WebhookProperties webhookProperties;
  private final ObjectMapper objectMapper;
  private final MessageArtifactTranslator.Factory messageArtifactTranslatorFactory;

  @Autowired
  public TemplateBasedArtifactExtractor(
      Optional<WebhookProperties> webhookProperties,
      ObjectMapper objectMapper,
      MessageArtifactTranslator.Factory messageArtifactTranslatorFactory) {
    this.webhookProperties = webhookProperties.orElse(null);
    this.objectMapper = objectMapper;
    this.messageArtifactTranslatorFactory = messageArtifactTranslatorFactory;
  }

  @Override
  public List<Artifact> getArtifacts(String source, Map payload) {
    if (webhookProperties == null) {
      return new ArrayList<>();
    }

    String templatePath = webhookProperties.getTemplatePathForSource(source);
    if (StringUtils.isEmpty(templatePath)) {
      return (List<Artifact>) payload.getOrDefault("artifacts", new ArrayList<>());
    } else {
      MessageArtifactTranslator translator;
      try {
        translator =
            messageArtifactTranslatorFactory.createJinja(new FileInputStream(templatePath));
      } catch (FileNotFoundException e) {
        throw new RuntimeException(
            "Failed to read template path " + templatePath + ": " + e.getMessage(), e);
      }

      List<Artifact> result = new ArrayList<>();
      try {
        result = translator.parseArtifacts(objectMapper.writeValueAsString(payload));
        log.info("Webhook artifacts were processed: {}", result);
      } catch (Exception e) {
        log.error("Unable to translate artifacts: {}", payload, e);
      }

      return result;
    }
  }

  @Override
  public boolean handles(String type, String source) {
    return webhookProperties != null;
  }
}
