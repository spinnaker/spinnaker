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

import java.util.List;
import java.util.Map;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.pipeline.PipelineValidator;
import com.netflix.spinnaker.orca.pipeline.model.Pipeline;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import static java.lang.String.format;
import static java.util.Collections.emptyMap;

@Component
@Slf4j
public class EnabledPipelineValidator implements PipelineValidator {

  private final Front50Service front50Service;

  @Autowired
  public EnabledPipelineValidator(Front50Service front50Service) {this.front50Service = front50Service;}

  @Override public void checkRunnable(Pipeline pipeline) {
    List<Map<String, Object>> pipelines = isStrategy(pipeline) ? front50Service.getStrategies(pipeline.getApplication()) : front50Service.getPipelines(pipeline.getApplication());
    pipelines
      .stream()
      .filter(it -> it.get("id").equals(pipeline.getPipelineConfigId()))
      .findFirst()
      .ifPresent(it -> {
        if ((boolean) it.getOrDefault("disabled", false)) {
          throw new PipelineIsDisabled(it.get("id").toString(), it.get("application").toString(), it.get("name").toString());
        }
      });
  }

  private boolean isStrategy(Pipeline pipeline) {
    return (boolean)((Map<String, Object>)pipeline.getTrigger().getOrDefault("parameters", emptyMap())).getOrDefault("strategy", false);
  }

  static class PipelineIsDisabled extends PipelineValidationFailed {
    PipelineIsDisabled(String id, String application, String name) {
      super(format("The pipeline config %s (%s) belonging to %s is disabled", name, id, application));
    }
  }
}
