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

package com.netflix.spinnaker.orca.front50.pipeline;

import static java.lang.String.format;

import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.pipeline.PipelineValidator;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Trigger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EnabledPipelineValidator implements PipelineValidator {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private final Front50Service front50Service;

  @Autowired
  public EnabledPipelineValidator(Optional<Front50Service> front50Service) {
    this.front50Service = front50Service.orElse(null);
  }

  @Override
  public void checkRunnable(Execution pipeline) {
    if (front50Service == null) {
      throw new UnsupportedOperationException(
          "Front50 not enabled, no way to validate pipeline. Fix this by setting front50.enabled: true");
    }

    if (!isStrategy(pipeline)) {
      // attempt an optimized lookup via pipeline history vs fetching all pipelines for the
      // application and filtering
      List<Map<String, Object>> pipelineConfigHistory =
          front50Service.getPipelineHistory(pipeline.getPipelineConfigId(), 1);

      if (!pipelineConfigHistory.isEmpty()) {
        Map<String, Object> pipelineConfig = pipelineConfigHistory.get(0);
        if ((boolean) pipelineConfig.getOrDefault("disabled", false)) {
          throw new PipelineIsDisabled(
              pipelineConfig.get("id").toString(),
              pipelineConfig.get("application").toString(),
              pipelineConfig.get("name").toString());
        }

        return;
      }
    }

    List<Map<String, Object>> pipelines =
        isStrategy(pipeline)
            ? front50Service.getStrategies(pipeline.getApplication())
            : front50Service.getPipelines(pipeline.getApplication(), false);
    pipelines.stream()
        .filter(it -> it.get("id").equals(pipeline.getPipelineConfigId()))
        .findFirst()
        .ifPresent(
            it -> {
              if ((boolean) it.getOrDefault("disabled", false)) {
                throw new PipelineIsDisabled(
                    it.get("id").toString(),
                    it.get("application").toString(),
                    it.get("name").toString());
              }
            });
  }

  private boolean isStrategy(Execution pipeline) {
    Trigger trigger = pipeline.getTrigger();
    return "pipeline".equals(trigger.getType()) && trigger.isStrategy();
  }

  static class PipelineIsDisabled extends PipelineValidationFailed {
    PipelineIsDisabled(String id, String application, String name) {
      super(
          format("The pipeline config %s (%s) belonging to %s is disabled", name, id, application));
    }
  }
}
