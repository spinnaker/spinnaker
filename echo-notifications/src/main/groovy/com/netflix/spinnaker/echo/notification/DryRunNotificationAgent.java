/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.echo.notification;

import static java.lang.String.format;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.echo.config.DryRunConfig;
import com.netflix.spinnaker.echo.model.Event;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.pipelinetriggers.orca.OrcaService;
import com.netflix.spinnaker.echo.services.Front50Service;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DryRunNotificationAgent extends AbstractEventNotificationAgent {

  private final Front50Service front50;
  private final OrcaService orca;
  private final DryRunConfig.DryRunProperties properties;

  public DryRunNotificationAgent(
      Front50Service front50, OrcaService orca, DryRunConfig.DryRunProperties properties) {
    this.front50 = front50;
    this.orca = orca;
    this.properties = properties;
  }

  @Override
  public String getNotificationType() {
    return "dryrun";
  }

  @Override
  public void sendNotifications(
      Map preference, String application, Event event, Map config, String status) {
    Map<String, ?> execution = (Map<String, ?>) event.getContent().get("execution");
    String pipelineConfigId = (String) execution.get("pipelineConfigId");
    if (pipelineConfigId == null) {
      return;
    }
    log.info("Received dry run notification for {}", pipelineConfigId);
    Optional<Pipeline> match =
        front50.getPipelines(application).stream()
            .filter(pipeline -> pipeline.getId().equals(pipelineConfigId))
            .findFirst();

    if (!match.isPresent()) {
      log.error("No pipeline with config id {} found for {}", pipelineConfigId, application);
      return;
    }

    try {
      Pipeline pipeline = match.get();
      log.warn("Triggering dry run of {} {}", pipeline.getApplication(), pipeline.getName());
      Trigger trigger =
          Trigger.builder()
              .type(Trigger.Type.DRYRUN.toString())
              .lastSuccessfulExecution(execution)
              .build();
      OrcaService.TriggerResponse response =
          orca.trigger(
              pipeline
                  .withName(format("%s (dry run)", pipeline.getName()))
                  .withId(null)
                  .withTrigger(trigger)
                  .withNotifications(
                      mapper.convertValue(properties.getNotifications(), List.class)));
      log.info("Pipeline triggered: {}", response);
    } catch (Exception ex) {
      log.error("Error triggering dry run of {}", pipelineConfigId, ex);
    }
  }

  private final ObjectMapper mapper = new ObjectMapper();
}
