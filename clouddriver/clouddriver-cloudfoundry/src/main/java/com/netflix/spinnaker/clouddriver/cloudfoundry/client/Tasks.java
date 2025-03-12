/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.client;

import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClientUtils.safelyCall;

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.TaskService;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.CreateTask;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Task;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class Tasks {
  private final TaskService api;

  public Task createTask(String applicationGuid, String command, String name) {
    CreateTask createTask = CreateTask.builder().command(command).name(name).build();

    return safelyCall(() -> api.createTask(applicationGuid, createTask))
        .orElseThrow(ResourceNotFoundException::new);
  }

  public Task getTask(String id) {
    return safelyCall(() -> api.getTask(id)).orElseThrow(ResourceNotFoundException::new);
  }

  public Task cancelTask(String id) {
    return safelyCall(() -> api.cancelTask(id, "")).orElseThrow(ResourceNotFoundException::new);
  }
}
