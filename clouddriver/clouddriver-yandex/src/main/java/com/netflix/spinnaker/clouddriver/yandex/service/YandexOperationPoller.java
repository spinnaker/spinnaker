/*
 * Copyright 2020 YANDEX LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.clouddriver.yandex.service;

import static yandex.cloud.api.operation.OperationOuterClass.Operation;
import static yandex.cloud.api.operation.OperationServiceOuterClass.GetOperationRequest;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.helpers.OperationPoller;
import com.netflix.spinnaker.clouddriver.yandex.security.YandexCloudCredentials;
import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import yandex.cloud.api.operation.OperationServiceGrpc;

@Component
public class YandexOperationPoller {
  private OperationPoller operationPoller;

  public YandexOperationPoller() {
    operationPoller =
        new OperationPoller(
            (int) Duration.ofMinutes(10).getSeconds(), (int) Duration.ofMinutes(1).getSeconds());
  }

  public Operation waitDone(YandexCloudCredentials credentials, Operation operation, String phase) {
    Task task = TaskRepository.threadLocalTask.get();
    String resourceString = operation.getDescription() + " [" + operation.getId() + "]";
    task.updateStatus(phase, "Waiting on operation '" + resourceString + "'...");
    OperationServiceGrpc.OperationServiceBlockingStub operationService =
        credentials.operationService();
    return operationPoller.waitForOperation(
        () ->
            operationService.get(
                GetOperationRequest.newBuilder().setOperationId(operation.getId()).build()),
        Operation::getDone,
        null,
        task,
        resourceString,
        phase);
  }

  public void doSync(
      Supplier<Operation> request, YandexCloudCredentials credentials, String phase) {
    waitDone(credentials, request.get(), phase);
  }
}
