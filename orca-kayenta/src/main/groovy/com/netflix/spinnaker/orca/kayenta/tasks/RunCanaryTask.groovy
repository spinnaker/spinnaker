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
import retrofit.client.Response
import retrofit.mime.TypedByteArray

import javax.annotation.Nonnull

@Component
@Slf4j
class RunCanaryTask implements Task {

  @Autowired
  KayentaService kayentaService

  @Override
  TaskResult execute(@Nonnull Stage stage) {
    Map<String, Object> context = stage.getContext()
    String metricsAccountName = (String)context.get("metricsAccountName")
    String storageAccountName = (String)context.get("storageAccountName")
    String canaryConfigId = (String)context.get("canaryConfigId")
    String controlScope = (String)context.get("controlScope")
    String experimentScope = (String)context.get("experimentScope")
    String startTimeIso = (String)context.get("startTimeIso")
    String endTimeIso = (String)context.get("endTimeIso")
    String step = (String)context.get("step")
    Map<String, String> extendedScopeParams = (Map<String, String>)context.get("extendedScopeParams")
    Map<String, String> scoreThresholds = (Map<String, String>)context.get("scoreThresholds")
    Response response = kayentaService.create(metricsAccountName,
                                              storageAccountName,
                                              canaryConfigId,
                                              controlScope,
                                              experimentScope,
                                              startTimeIso,
                                              endTimeIso,
                                              step,
                                              extendedScopeParams,
                                              scoreThresholds?.pass,
                                              scoreThresholds?.marginal)
    String canaryPipelineExecutionId = new String(((TypedByteArray)response.getBody()).getBytes())

    return new TaskResult(ExecutionStatus.SUCCEEDED, [canaryPipelineExecutionId: canaryPipelineExecutionId])
  }
}
