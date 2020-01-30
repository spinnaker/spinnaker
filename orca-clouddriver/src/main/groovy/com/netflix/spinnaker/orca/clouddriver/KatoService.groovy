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

import com.google.common.hash.Hashing
import com.netflix.spinnaker.orca.ExecutionContext
import com.netflix.spinnaker.orca.clouddriver.model.Task
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import rx.Observable

import javax.annotation.Nonnull

@Component
class KatoService {

  private final KatoRestService katoRestService
  private final CloudDriverTaskStatusService cloudDriverTaskStatusService

  @Autowired
  KatoService(KatoRestService katoRestService, CloudDriverTaskStatusService cloudDriverTaskStatusService) {
    this.katoRestService = katoRestService
    this.cloudDriverTaskStatusService = cloudDriverTaskStatusService
  }

  Observable<TaskId> requestOperations(Collection<? extends Map<String, Map>> operations) {
    return Observable.from(katoRestService.requestOperations(requestId(operations), operations))
  }

  Observable<TaskId> requestOperations(String cloudProvider, Collection<? extends Map<String, Map>> operations) {
    return Observable.from(katoRestService.requestOperations(requestId(operations), cloudProvider, operations))
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
