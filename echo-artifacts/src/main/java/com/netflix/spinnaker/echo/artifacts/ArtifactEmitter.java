/*
 * Copyright 2019 Netflix, Inc.
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
import com.netflix.spinnaker.echo.config.ArtifactEmitterProperties;
import com.netflix.spinnaker.echo.model.ArtifactEvent;
import com.netflix.spinnaker.echo.services.KeelService;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * In an effort to move towards a more artifact centric workflow, this collector will accept
 * artifacts from all over echo, and will publish them to any keel.
 *
 * <p>todo eb: figure out if there are other cases for emitting artifacts, and if so make this
 * solution more general
 */
@Component
@ConditionalOnExpression("${artifact-emitter.enabled:false}")
public class ArtifactEmitter {
  private static final Logger log = LoggerFactory.getLogger(ArtifactEmitter.class);
  private final ObjectMapper objectMapper;
  private final KeelService keelService;
  private final ArtifactEmitterProperties artifactEmitterProperties;

  @Autowired
  public ArtifactEmitter(
      KeelService keelService,
      ArtifactEmitterProperties artifactEmitterProperties,
      ObjectMapper objectMapper) {
    this.keelService = keelService;
    this.artifactEmitterProperties = artifactEmitterProperties;
    this.objectMapper = objectMapper;
    log.info("Preparing to emit artifacts");
  }

  @EventListener
  @SuppressWarnings("unchecked")
  public void processEvent(ArtifactEvent event) {
    try {
      Map sentEvent = new HashMap();
      sentEvent.put("eventName", artifactEmitterProperties.getEventName());
      sentEvent.put(
          artifactEmitterProperties.getFieldName(), objectMapper.convertValue(event, Map.class));

      keelService.sendArtifactEvent(sentEvent);
    } catch (Exception e) {
      log.error("Could not send event {} to Keel", event, e);
    }
  }
}
