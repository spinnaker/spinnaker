/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.orca.kayenta.tasks

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.kayenta.KayentaService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.annotation.Nonnull

@Component
@Slf4j
class RunKayentaCanaryTask implements Task {

  @Autowired
  KayentaService kayentaService

  @Override
  TaskResult execute(@Nonnull Stage stage) {
    Map<String, Object> context = stage.getContext()
    String metricsAccountName = (String)context.get("metricsAccountName")
    String storageAccountName = (String)context.get("storageAccountName")
    String canaryConfigId = (String)context.get("canaryConfigId")
    Map<String, Map> scopes = (Map<String, Map>)context.get("scopes")
    Map<String, String> scoreThresholds = (Map<String, String>)context.get("scoreThresholds")
    Map<String, String> canaryExecutionRequest = [
      scopes: scopes,
      thresholds: [
        pass: scoreThresholds?.pass,
        marginal: scoreThresholds?.marginal
      ]
    ]
    String canaryPipelineExecutionId = kayentaService.create(canaryConfigId,
                                                             metricsAccountName,
                                                             storageAccountName /* configurationAccountName */, // TODO(duftler): Propagate configurationAccountName properly.
                                                             storageAccountName,
                                                             canaryExecutionRequest).canaryExecutionId

    return new TaskResult(ExecutionStatus.SUCCEEDED, [canaryPipelineExecutionId: canaryPipelineExecutionId])
  }
}
