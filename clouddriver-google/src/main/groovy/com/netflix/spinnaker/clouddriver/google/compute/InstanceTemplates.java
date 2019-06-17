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
import com.google.api.services.compute.model.InstanceTemplate;
import com.google.api.services.compute.model.Operation;
import com.google.common.collect.ImmutableMap;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.google.GoogleExecutor;
import com.netflix.spinnaker.clouddriver.google.compute.GoogleComputeOperationRequestImpl.OperationWaiter;
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil;
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import java.io.IOException;

public class InstanceTemplates {

  public static final ImmutableMap<String, String> TAGS =
      ImmutableMap.of(GoogleExecutor.getTAG_SCOPE(), GoogleExecutor.getSCOPE_GLOBAL());

  private final GoogleNamedAccountCredentials credentials;
  private final GoogleOperationPoller operationPoller;
  private final Registry registry;

  InstanceTemplates(
      GoogleNamedAccountCredentials credentials,
      GoogleOperationPoller operationPoller,
      Registry registry) {
    this.credentials = credentials;
    this.operationPoller = operationPoller;
    this.registry = registry;
  }

  public GoogleComputeOperationRequest delete(String name) throws IOException {

    ComputeRequest<Operation> request =
        credentials.getCompute().instanceTemplates().delete(credentials.getProject(), name);
    return wrapOperationRequest(request, "delete");
  }

  public GoogleComputeRequest<InstanceTemplate> get(String name) throws IOException {
    ComputeRequest<InstanceTemplate> request =
        credentials.getCompute().instanceTemplates().get(credentials.getProject(), name);
    return wrapRequest(request, "get");
  }

  public GoogleComputeOperationRequest insert(InstanceTemplate template) throws IOException {
    ComputeRequest<Operation> request =
        credentials.getCompute().instanceTemplates().insert(credentials.getProject(), template);
    return wrapOperationRequest(request, "insert");
  }

  private <T> GoogleComputeRequest<T> wrapRequest(ComputeRequest<T> request, String api) {
    return new GoogleComputeRequestImpl<>(request, registry, getMetricName(api), TAGS);
  }

  private GoogleComputeOperationRequest wrapOperationRequest(
      ComputeRequest<Operation> request, String api) {
    OperationWaiter waiter =
        (operation, task, phase) ->
            operationPoller.waitForGlobalOperation(
                credentials.getCompute(),
                credentials.getProject(),
                operation.getName(),
                /* timeoutSeconds= */ null,
                task,
                GCEUtil.getLocalName(operation.getTargetLink()),
                phase);
    return new GoogleComputeOperationRequestImpl(
        request, registry, getMetricName(api), TAGS, waiter);
  }

  private String getMetricName(String api) {
    return "compute.instanceTemplates." + api;
  }
}
