/*
 * Copyright 2019 Google, Inc.
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

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.igor.BuildService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class GetBuildArtifactsTask extends RetryableIgorTask {
  private final BuildService buildService;

  @Override
  protected @Nonnull TaskResult tryExecute(@Nonnull CIJobRequest jobRequest) {
    if (StringUtils.isEmpty(jobRequest.getPropertyFile())) {
      return TaskResult.SUCCEEDED;
    }
    List<Artifact> artifacts = buildService.getArtifacts(
      jobRequest.getBuildNumber(), jobRequest.getPropertyFile(), jobRequest.getMaster(), jobRequest.getJob()
    );
    Map<String, List<Artifact>> outputs = Collections.singletonMap("artifacts", artifacts);
    return new TaskResult(ExecutionStatus.SUCCEEDED, Collections.emptyMap(), outputs);
  }
}
