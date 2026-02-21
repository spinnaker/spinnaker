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

package com.netflix.spinnaker.clouddriver.data.task;

import com.netflix.spinnaker.clouddriver.core.ClouddriverHostname;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class InMemoryTaskRepository implements TaskRepository {
  private final Map<String, Task> repository = new ConcurrentHashMap<>();
  private final Map<String, Task> clientRequestRepository = new ConcurrentHashMap<>();

  @Override
  public Task create(String phase, String status, String clientRequestId) {
    if (clientRequestRepository.containsKey(clientRequestId)) {
      return clientRequestRepository.get(clientRequestId);
    }
    DefaultTask task = new DefaultTask(getNextId(), phase, status);
    repository.put(task.getId(), task);
    clientRequestRepository.put(clientRequestId, task);
    return task;
  }

  @Override
  public Task create(String phase, String status) {
    return create(phase, status, UUID.randomUUID().toString());
  }

  @Override
  public Task getByClientRequestId(String clientRequestId) {
    return clientRequestRepository.get(clientRequestId);
  }

  @Override
  public Task get(String id) {
    return repository.get(id);
  }

  @Override
  public List<Task> list() {
    List<Task> tasks = new ArrayList<>();
    for (Task value : repository.values()) {
      if (!value.getStatus().isCompleted()) {
        tasks.add(value);
      }
    }
    return tasks;
  }

  @Override
  public List<Task> listByThisInstance() {
    return list().stream()
        .filter(t -> ClouddriverHostname.ID.equals(t.getOwnerId()))
        .collect(Collectors.toList());
  }

  private String getNextId() {
    while (true) {
      String maybeNext = BigInteger.valueOf(new Random().nextInt(Integer.MAX_VALUE)).toString(36);
      if (!repository.containsKey(maybeNext)) {
        return maybeNext;
      }
    }
  }
}
