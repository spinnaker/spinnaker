/*
 * Copyright 2019 Netflix, Inc.
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DualTaskRepository implements TaskRepository {

  private static final Logger log = LoggerFactory.getLogger(DualTaskRepository.class);

  private final TaskRepository primary;
  private final TaskRepository previous;
  private final ExecutorService executorService;
  private final long asyncTimeoutSeconds;

  public DualTaskRepository(
      TaskRepository primary,
      TaskRepository previous,
      int threadPoolSize,
      long asyncTimeoutSeconds) {
    this(primary, previous, Executors.newFixedThreadPool(threadPoolSize), asyncTimeoutSeconds);
  }

  public DualTaskRepository(
      TaskRepository primary,
      TaskRepository previous,
      ExecutorService executorService,
      long asyncTimeoutSeconds) {
    this.primary = primary;
    this.previous = previous;
    this.executorService = executorService;
    this.asyncTimeoutSeconds = asyncTimeoutSeconds;
  }

  @Override
  public Task create(String phase, String status) {
    return primary.create(phase, status);
  }

  @Override
  public Task create(String phase, String status, String clientRequestId) {
    return primary.create(phase, status, clientRequestId);
  }

  @Override
  public Task get(String id) {
    return Optional.ofNullable(primary.get(id)).orElse(previous.get(id));
  }

  @Override
  public Task getByClientRequestId(String clientRequestId) {
    return Optional.ofNullable(primary.getByClientRequestId(clientRequestId))
        .orElse(previous.getByClientRequestId(clientRequestId));
  }

  @Override
  public List<Task> list() {
    Future<List<Task>> primaryList = executorService.submit(primary::list);
    Future<List<Task>> previousList = executorService.submit(previous::list);

    List<Task> tasks = new ArrayList<>();
    try {
      tasks.addAll(primaryList.get(asyncTimeoutSeconds, TimeUnit.SECONDS));
      tasks.addAll(previousList.get(asyncTimeoutSeconds, TimeUnit.SECONDS));
    } catch (TimeoutException | InterruptedException | ExecutionException e) {
      log.error("Could not retrieve list of tasks by timeout", e);
      // Return tasks so we can still get data in partial failures
    }

    return tasks;
  }

  @Override
  public List<Task> listByThisInstance() {
    return primary.listByThisInstance();
  }
}
