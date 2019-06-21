/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.compute;

import com.google.api.services.compute.ComputeRequest;
import com.google.api.services.compute.model.Operation;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import java.io.IOException;
import java.util.Map;

final class GoogleComputeOperationRequestImpl<RequestT extends ComputeRequest<Operation>>
    extends GoogleComputeRequestImpl<RequestT, Operation>
    implements GoogleComputeOperationRequest<RequestT> {

  @FunctionalInterface
  interface OperationWaiter {
    Operation wait(Operation operation, Task task, String phase);
  }

  private final OperationWaiter operationWaiter;

  GoogleComputeOperationRequestImpl(
      RequestT request,
      Registry registry,
      String metricName,
      Map<String, String> tags,
      OperationWaiter operationWaiter) {
    super(request, registry, metricName, tags);
    this.operationWaiter = operationWaiter;
  }

  @Override
  public Operation executeAndWait(Task task, String phase) throws IOException {
    return operationWaiter.wait(execute(), task, phase);
  }
}
