/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.echo.pipelinetriggers.eventhandlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.echo.model.trigger.DockerEvent;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Implementation of TriggerEventHandler for events of type {@link DockerEvent}, which occur when a
 * new container is pushed to a docker registry.
 */
@Component
public class DockerEventHandler extends AbstractOCIRegistryEventHandler<DockerEvent> {
  private static final String TRIGGER_TYPE = "docker";
  private static final List<String> SUPPORTED_TYPES = Collections.singletonList(TRIGGER_TYPE);

  @Autowired
  public DockerEventHandler(
      Registry registry,
      ObjectMapper objectMapper,
      FiatPermissionEvaluator fiatPermissionEvaluator) {
    super(registry, objectMapper, fiatPermissionEvaluator);
  }

  @Override
  public boolean handleEventType(String eventType) {
    return eventType.equalsIgnoreCase(DockerEvent.TYPE);
  }

  @Override
  public Class<DockerEvent> getEventType() {
    return DockerEvent.class;
  }

  @Override
  protected String getTriggerType() {
    return TRIGGER_TYPE;
  }

  @Override
  protected String getArtifactType() {
    return "docker/image";
  }

  @Override
  public List<String> supportedTriggerTypes() {
    return SUPPORTED_TYPES;
  }
}
