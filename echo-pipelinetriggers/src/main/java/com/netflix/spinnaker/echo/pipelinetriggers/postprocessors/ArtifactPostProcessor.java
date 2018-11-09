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

package com.netflix.spinnaker.echo.pipelinetriggers.postprocessors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.echo.artifacts.MessageArtifactTranslator;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.pipelinetriggers.artifacts.JinjaTemplate;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Post-processor extracts artifacts from a pipeline using a supplied Jinja template and adds
 * these artifacts to the pipeline as received artifacts.
 * This post-processor is not implemented yet and is currently a no-op.
 */
@Component
@Slf4j
public class ArtifactPostProcessor implements PipelinePostProcessor {
  private final ObjectMapper objectMapper;

  @Autowired
  public ArtifactPostProcessor(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public Pipeline processPipeline(Pipeline inputPipeline) {
    List<Artifact> newArtifacts = extractArtifacts(inputPipeline.getTrigger());
    List<Artifact> existingArtifacts = inputPipeline.getReceivedArtifacts();

    List<Artifact> receivedArtifacts = new ArrayList<>();
    if (existingArtifacts != null) {
      receivedArtifacts.addAll(existingArtifacts);
    }
    if (newArtifacts != null) {
      receivedArtifacts.addAll(newArtifacts);
    }
    return inputPipeline.withReceivedArtifacts(receivedArtifacts);
  }

  private List<Artifact> extractArtifacts(Trigger inputTrigger) {
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
    MessageArtifactTranslator messageArtifactTranslator = new MessageArtifactTranslator(template.getAsStream());
    return messageArtifactTranslator.parseArtifacts(messageString);
  }

  private List<JinjaTemplate> getArtifactTemplates(Trigger trigger) {
    // TODO: This always returns an empty list; implement this so that it selects a template based on the trigger
    return Collections.emptyList();
  }

  public PostProcessorPriority priority() {
    return PostProcessorPriority.ARTIFACT_EXTRACTION;
  }
}
