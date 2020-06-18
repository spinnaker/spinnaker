/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.hash.Hashing
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.orca.ExecutionContext
import com.netflix.spinnaker.orca.clouddriver.model.OperationContext
import com.netflix.spinnaker.orca.clouddriver.model.SubmitOperationResult
import com.netflix.spinnaker.orca.clouddriver.model.Task
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError
import retrofit.client.Response

import javax.annotation.Nonnull
import java.time.Duration

@Component
class KatoService {

  private final KatoRestService katoRestService
  private final CloudDriverTaskStatusService cloudDriverTaskStatusService
  private final RetrySupport retrySupport
  private final ObjectMapper objectMapper

  @Autowired
  KatoService(KatoRestService katoRestService, CloudDriverTaskStatusService cloudDriverTaskStatusService, RetrySupport retrySupport) {
    this.katoRestService = katoRestService
    this.cloudDriverTaskStatusService = cloudDriverTaskStatusService
    this.retrySupport = retrySupport
  }

  TaskId requestOperations(Collection<? extends Map<String, Map>> operations) {
    return retrySupport.retry({
      katoRestService.requestOperations(requestId(operations), operations)
    }, 3, Duration.ofSeconds(1), false)
  }

  TaskId requestOperations(String cloudProvider, Collection<? extends Map<String, Map>> operations) {
    return retrySupport.retry({
      katoRestService.requestOperations(requestId(operations), cloudProvider, operations)
    }, 3, Duration.ofSeconds(1), false)
  }

  SubmitOperationResult submitOperation(@Nonnull String cloudProvider, OperationContext operation) {
    Response response = katoRestService.submitOperation(
      requestId(operation),
      cloudProvider,
      operation.operationType,
      operation
    )

    InputStream body = null
    try {
      body = response.body.in()
    } finally {
      body?.close()
    }

    TaskId taskId = objectMapper.readValue(body, TaskId.class)

    SubmitOperationResult result = new SubmitOperationResult()
    result.id = taskId.id
    result.status = response.status

    return result
  }

  Task lookupTask(String id, boolean skipReplica = false) {
    if (skipReplica) {
      return katoRestService.lookupTask(id)
    }

    return cloudDriverTaskStatusService.lookupTask(id)
  }

  @Nonnull
  TaskId resumeTask(@Nonnull String id) {
    katoRestService.resumeTask(id)
  }

  private static String requestId(Object payload) {
    final ExecutionContext context = ExecutionContext.get()
    final byte[] payloadBytes = OrcaObjectMapper.getInstance().writeValueAsBytes(payload)
    return Hashing.sha256().hashBytes(
      "${context.getStageId()}-${context.getStageStartTime()}-${payloadBytes}".toString().bytes)
  }
}
