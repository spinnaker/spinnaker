/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.data.task

import java.util.concurrent.ConcurrentHashMap

class InMemoryTaskRepository implements TaskRepository {
  private final Map<String, Task> repository = new ConcurrentHashMap<>()
  private final Map<String, Task> clientRequestRepository = new ConcurrentHashMap<>()

  @Override
  Task create(String phase, String status, String clientRequestId) {
    if (clientRequestRepository.containsKey(clientRequestId)) {
      return clientRequestRepository.get(clientRequestId)
    }
    def task = new DefaultTask(nextId, phase, status)
    repository[task.id] = task
    clientRequestRepository[clientRequestId] = task
  }

  @Override
  Task create(String phase, String status) {
    create(phase, status, UUID.randomUUID().toString())
  }

  @Override
  Task getByClientRequestId(String clientRequestId) {
    clientRequestRepository[clientRequestId]
  }

  @Override
  Task get(String id) {
    repository?.get(id)
  }

  @Override
  List<Task> list() {
    repository.values() as List
  }

  @Override
  List<Task> listByThisInstance() {
    return list()
  }

  private String getNextId() {
    while (true) {
      def maybeNext = new BigInteger(new Random().nextInt(Integer.MAX_VALUE)).toString(36)
      if (!repository.containsKey(maybeNext)) {
        return maybeNext
      }
    }
  }
}
