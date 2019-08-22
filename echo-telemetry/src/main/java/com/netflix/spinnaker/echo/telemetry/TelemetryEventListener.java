/*
 * Copyright 2019 Armory, Inc.
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

package com.netflix.spinnaker.echo.telemetry;

import com.google.protobuf.util.JsonFormat;
import com.netflix.spinnaker.echo.events.EchoEventListener;
import com.netflix.spinnaker.echo.model.Event;
import com.netflix.spinnaker.echo.proto.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty("telemetry.enabled")
public class TelemetryEventListener implements EchoEventListener {
  private final TelemetryService telemetryService;

  @Autowired
  public TelemetryEventListener(TelemetryService telemetryService) {
    this.telemetryService = telemetryService;
  }

  @Value("${telemetry.instanceId}")
  String instanceId;

  @Override
  public void processEvent(Event event) {
    try {
      if (event.getDetails() == null
          || !event.getDetails().getType().equals("orca:pipeline:complete")) {
        return;
      }

      Map execution = (Map) event.content.get("execution");
      Execution.Builder executionBuilder = getExecutionBuilder(execution);

      List<Map> stages = (ArrayList<Map>) execution.get("stages");
      for (Map stage : stages) {
        executionBuilder.addStages(getStageBuilder(stage));
      }

      Application.Builder applicationBuilder =
          Application.newBuilder()
              .setId(event.details.getApplication())
              .setExecution(executionBuilder);

      SpinnakerInstance.Builder spinnakerInstance =
          SpinnakerInstance.newBuilder().setId(instanceId).setApplication(applicationBuilder);

      EventProto.Builder eventProto =
          EventProto.newBuilder().setSpinnakerInstance(spinnakerInstance);

      telemetryService.sendMessage(JsonFormat.printer().print(eventProto));

    } catch (Exception e) {
      log.error("Could not send Telemetry event {}", event, e);
    }
  }

  private Execution.Builder getExecutionBuilder(Map execution) {
    Map trigger = (Map) execution.get("trigger");
    return Execution.newBuilder()
        .setId(execution.get("id").toString())
        .setType(Execution.Type.valueOf(execution.get("type").toString().toUpperCase()))
        .setTrigger(
            Execution.Trigger.newBuilder()
                .setType(
                    Execution.Trigger.Type.valueOf(trigger.get("type").toString().toUpperCase())))
        .setStatus(Status.valueOf(execution.get("status").toString().toUpperCase()));
  }

  private Stage.Builder getStageBuilder(Map stage) {
    return Stage.newBuilder()
        .setType(stage.get("type").toString())
        .setStatus(Status.valueOf(stage.get("status").toString().toUpperCase()));
  }
}
