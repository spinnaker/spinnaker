/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.orca.igor.tasks;

import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.igor.BuildService;
import com.netflix.spinnaker.orca.igor.model.CIStageDefinition;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class GetBuildPropertiesTask extends RetryableIgorTask<CIStageDefinition> {
  private final BuildService buildService;

  @Override
  protected @Nonnull TaskResult tryExecute(@Nonnull CIStageDefinition stageDefinition) {
    if (StringUtils.isEmpty(stageDefinition.getPropertyFile())) {
      return TaskResult.SUCCEEDED;
    }

    Map<String, Object> properties = buildService.getPropertyFile(
      stageDefinition.getBuildNumber(),
      stageDefinition.getPropertyFile(),
      stageDefinition.getMaster(),
      stageDefinition.getJob()
    );
    if (properties.size() == 0) {
      throw new IllegalStateException(String.format("Expected properties file %s but it was either missing, empty or contained invalid syntax", stageDefinition.getPropertyFile()));
    }
    HashMap<String, Object> outputs = new HashMap<>(properties);
    outputs.put("propertyFileContents", properties);
    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).outputs(outputs).build();
  }

  @Override
  protected @Nonnull CIStageDefinition mapStage(@Nonnull Stage stage) {
    return stage.mapTo(CIStageDefinition.class);
  }
}
