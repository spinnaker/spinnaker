/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.ops;

import static java.util.Collections.emptyList;

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.*;
import com.netflix.spinnaker.clouddriver.data.task.DefaultTask;
import com.netflix.spinnaker.clouddriver.data.task.Status;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.Collections;
import java.util.Optional;
import org.assertj.core.api.Condition;

class AbstractCloudFoundryAtomicOperationTest {
  final CloudFoundryClient client;

  AbstractCloudFoundryAtomicOperationTest() {
    client = new MockCloudFoundryClient();
  }

  Task runOperation(AtomicOperation<?> op) {
    Task task = new DefaultTask("test");
    TaskRepository.threadLocalTask.set(task);
    try {
      Optional.ofNullable(op.operate(emptyList()))
          .ifPresent(o -> task.addResultObjects(Collections.singletonList(o)));
    } catch (CloudFoundryApiException e) {
      task.addResultObjects(Collections.singletonList(Collections.singletonMap("EXCEPTION", e)));
    }
    return task;
  }

  static Condition<? super Status> status(String desc) {
    return new Condition<>(
        status -> status.getStatus().equals(desc), "description = '" + desc + "'");
  }

  static Condition<? super Status> statusStartsWith(String desc) {
    return new Condition<>(
        status -> status.getStatus().startsWith(desc), "description = '" + desc + "'");
  }
}
