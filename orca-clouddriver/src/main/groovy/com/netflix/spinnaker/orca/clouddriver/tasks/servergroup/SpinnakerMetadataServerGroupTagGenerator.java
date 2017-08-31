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

package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup;

import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Orchestration;
import com.netflix.spinnaker.orca.pipeline.model.Pipeline;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class SpinnakerMetadataServerGroupTagGenerator implements ServerGroupEntityTagGenerator {

  @Override
  public Map<String, Object> generateTag(Stage stage) {
    Execution execution = stage.getExecution();
    Map context = stage.getContext();

    Map<String, String> value = new HashMap<>();
    value.put("stageId", stage.getId());
    value.put("executionId", execution.getId());
    value.put("executionType", execution.getClass().getSimpleName().toLowerCase());
    value.put("application", execution.getApplication());

    if (execution.getAuthentication() != null) {
      value.put("user", execution.getAuthentication().getUser());
    }

    if (execution instanceof Orchestration) {
      value.put("description", ((Orchestration) execution).getDescription());
    } else if (execution instanceof Pipeline) {
      value.put("description", execution.getName());
      value.put("pipelineConfigId", ((Pipeline) execution).getPipelineConfigId());
    }

    if (context.containsKey("reason") && context.get("reason") != null) {
      value.put("comments", (String) context.get("reason"));
    }
    if (context.containsKey("comments") && context.get("comments") != null) {
      value.put("comments", (String) context.get("comments"));
    }

    Map<String, Object> tag = new HashMap<>();
    tag.put("name", "spinnaker:metadata");
    tag.put("value", value);

    return tag;
  }

}
