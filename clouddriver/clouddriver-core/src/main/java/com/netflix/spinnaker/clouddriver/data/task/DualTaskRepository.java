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

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DualTaskRepository implements TaskRepository {

  private static final Logger log = LoggerFactory.getLogger(DualTaskRepository.class);

  private final TaskRepository primary;
  private final TaskRepository previous;
  private final ExecutorService executorService;
  private final long asyncTimeoutSeconds;
  private final DynamicConfigService dynamicConfigService;

  public DualTaskRepository(
      TaskRepository primary,
      TaskRepository previous,
      int threadPoolSize,
      long asyncTimeoutSeconds,
      DynamicConfigService dynamicConfigService) {
    this(
        primary,
        previous,
        Executors.newFixedThreadPool(
            threadPoolSize,
            new ThreadFactoryBuilder()
                .setNameFormat(DualTaskRepository.class.getSimpleName() + "-%d")
                .build()),
        asyncTimeoutSeconds,
        dynamicConfigService);
  }

  public DualTaskRepository(
      TaskRepository primary,
      TaskRepository previous,
      ExecutorService executorService,
      long asyncTimeoutSeconds,
      DynamicConfigService dynamicConfigService) {
    this.primary = primary;
    this.previous = previous;
    this.executorService = executorService;
    this.asyncTimeoutSeconds = asyncTimeoutSeconds;
    this.dynamicConfigService = dynamicConfigService;
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
    Task task = primary.get(id);

    if (task == null && dynamicConfigService.isEnabled("dualtaskrepo.previous", true)) {
      task = previous.get(id);
    }

    return task;
  }

  @Override
  public Task getByClientRequestId(String clientRequestId) {
    Task task = primary.getByClientRequestId(clientRequestId);

    if (task == null && dynamicConfigService.isEnabled("dualtaskrepo.previous", true)) {
      task = previous.getByClientRequestId(clientRequestId);
    }

    return task;
  }

  @Override
  public List<Task> list() {
    List<Task> tasks = new ArrayList<>();

    try {
      Future<List<Task>> primaryList = executorService.submit(primary::list);
      List<Task> tasksFromPrevious = Collections.emptyList();

      tasks.addAll(primaryList.get(asyncTimeoutSeconds, TimeUnit.SECONDS));
      if (dynamicConfigService.isEnabled("dualtaskrepo.previous", true)) {
        Future<List<Task>> previousList = executorService.submit(previous::list);
        tasksFromPrevious = previousList.get(asyncTimeoutSeconds, TimeUnit.SECONDS);
      }

      Set<String> primaryTasks = tasks.stream().map(Task::getId).collect(Collectors.toSet());
      tasksFromPrevious.stream()
          .filter(task -> !primaryTasks.contains(task.getId()))
          .forEach(tasks::add);
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
