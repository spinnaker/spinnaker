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

import java.util.Map;
import java.util.NoSuchElementException;
import com.netflix.spinnaker.echo.model.Event;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.echo.pipelinetriggers.orca.OrcaService;
import com.netflix.spinnaker.echo.services.Front50Service;
import lombok.extern.slf4j.Slf4j;
import static java.lang.String.format;

@Slf4j
public class DryRunNotificationAgent extends AbstractEventNotificationAgent {

  private final Front50Service front50;
  private final OrcaService orca;

  public DryRunNotificationAgent(Front50Service front50, OrcaService orca) {
    this.front50 = front50;
    this.orca = orca;
  }

  @Override public String getNotificationType() {
    return "dryrun";
  }

  @Override
  public void sendNotifications(
    Map preference,
    String application,
    Event event,
    Map config,
    String status) {
    Map<String, ?> execution = (Map<String, ?>) event.getContent().get("execution");
    String pipelineConfigId = (String) execution.get("pipelineConfigId");
    if (pipelineConfigId == null) {
      return;
    }
    log.info("Received dry run notification for {}", pipelineConfigId);
    front50
      .getPipelines(application)
      .flatMapIterable(pipelines -> pipelines)
      .filter(pipeline -> pipeline.getId().equals(pipelineConfigId))
      .first()
      .flatMap(pipeline -> {
        log.warn("Triggering dry run of {} {}", pipeline.getApplication(), pipeline.getName());
        Trigger trigger = Trigger
          .builder()
          .type(Trigger.Type.DRYRUN.toString())
          .lastSuccessfulExecution(execution)
          .build();
        return orca.trigger(
          pipeline
            .withName(format("%s (dry run)", pipeline.getName()))
            .withTrigger(trigger)
        );
      })
      .doOnError(ex -> {
        if (ex instanceof NoSuchElementException) {
          log.error("No pipeline with config id {} found for {}", pipelineConfigId, application);
        } else {
          log.error(format("Error triggering dry run of %s", pipelineConfigId), ex);
        }
      })
      .subscribe(response -> {
        log.info("Pipeline triggered: {}", response);
      });
  }
}
