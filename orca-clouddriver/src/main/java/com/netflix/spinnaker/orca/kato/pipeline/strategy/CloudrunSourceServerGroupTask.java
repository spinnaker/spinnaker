/*
 * Copyright 2022 OpsMx, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.orca.kato.pipeline.strategy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.kato.pipeline.support.SourceResolver;
import com.netflix.spinnaker.orca.kato.pipeline.support.StageData;
import groovy.util.logging.Slf4j;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CloudrunSourceServerGroupTask extends DetermineSourceServerGroupTask
    implements RetryableTask {

  public CloudrunSourceServerGroupTask() {}

  public CloudrunSourceServerGroupTask(SourceResolver sourceResolver) {
    super.setSourceResolver(sourceResolver);
  }

  @Override
  public TaskResult execute(StageExecution stage) {
    setRegionInContextFromPayload(stage);
    return super.execute(stage);
  }

  public StageData parseRegionAndSetInContext(StageExecution stage) {
    setRegionInContextFromPayload(stage);
    StageData stageData = stage.mapTo(StageData.class);
    return stageData;
  }

  private void setRegionInContextFromPayload(StageExecution stage) {

    ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
    if (stage.getContext() != null
        && stage.getContext().get("configFiles") != null
        && (!((List) stage.getContext().get("configFiles")).isEmpty())) {
      Map<String, Object> contextMap = stage.getContext();
      String configStr = (String) ((List) contextMap.get("configFiles")).get(0);
      Map<String, Object> yamlMap = null;
      try {
        yamlMap = yamlReader.readValue(configStr, Map.class);
        if (yamlMap.get("metadata") != null
            && ((Map<String, Object>) yamlMap.get("metadata")).get("labels") != null
            && ((Map<String, Object>) ((Map<String, Object>) yamlMap.get("metadata")).get("labels"))
                    .get("cloud.googleapis.com/location")
                != null) {
          stage
              .getContext()
              .put(
                  "region",
                  ((Map<String, Object>)
                          ((Map<String, Object>) yamlMap.get("metadata")).get("labels"))
                      .get("cloud.googleapis.com/location"));
        }
      } catch (JsonProcessingException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
