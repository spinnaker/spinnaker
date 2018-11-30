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

import com.netflix.spinnaker.orca.clouddriver.model.Task
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import rx.Observable

@Component
class KatoService {

  @Autowired
  private KatoRestService katoRestService

  @Autowired
  private CloudDriverTaskStatusService cloudDriverTaskStatusService

  Observable<TaskId> requestOperations(Collection<? extends Map<String, Map>> operations) {
    String clientRequestId = UUID.randomUUID().toString()
    return Observable.from(katoRestService.requestOperations(clientRequestId, operations))
  }

  Observable<TaskId> requestOperations(String cloudProvider, Collection<? extends Map<String, Map>> operations) {
    String clientRequestId = UUID.randomUUID().toString()
    return Observable.from(katoRestService.requestOperations(clientRequestId, cloudProvider, operations))
  }

  Observable<Task> lookupTask(String id, boolean skipReplica = false) {
    if (skipReplica) {
      return Observable.from(katoRestService.lookupTask(id))
    }

    return Observable.from(cloudDriverTaskStatusService.lookupTask(id))
  }
}
