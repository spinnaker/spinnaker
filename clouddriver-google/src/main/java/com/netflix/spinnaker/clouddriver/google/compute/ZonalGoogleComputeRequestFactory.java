/*
 * Copyright 2019 Google, LLC
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

package com.netflix.spinnaker.clouddriver.google.compute;

import com.google.api.services.compute.ComputeRequest;
import com.google.api.services.compute.model.Operation;
import com.google.common.collect.ImmutableMap;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.google.GoogleExecutor;
import com.netflix.spinnaker.clouddriver.google.compute.GoogleComputeOperationRequestImpl.OperationWaiter;
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil;
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;

final class ZonalGoogleComputeRequestFactory {

  private final String serviceName;
  private final GoogleNamedAccountCredentials credentials;
  private final Registry registry;
  private final GoogleOperationPoller poller;

  ZonalGoogleComputeRequestFactory(
      String serviceName,
      GoogleNamedAccountCredentials credentials,
      GoogleOperationPoller poller,
      Registry registry) {
    this.serviceName = serviceName;
    this.credentials = credentials;
    this.registry = registry;
    this.poller = poller;
  }

  <RequestT extends ComputeRequest<ResponseT>, ResponseT>
      GoogleComputeRequest<RequestT, ResponseT> wrapRequest(
          RequestT request, String api, String zone) {
    return new GoogleComputeRequestImpl<>(request, registry, getMetricName(api), tags(zone));
  }

  <RequestT extends ComputeRequest<ResponseT>, ResponseT>
      GoogleComputeGetRequest<RequestT, ResponseT> wrapGetRequest(
          RequestT request, String api, String zone) {
    return new GoogleComputeGetRequestImpl<>(request, registry, getMetricName(api), tags(zone));
  }

  <RequestT extends ComputeRequest<Operation>>
      GoogleComputeOperationRequest<RequestT> wrapOperationRequest(
          RequestT request, String api, String zone) {
    return new GoogleComputeOperationRequestImpl<>(
        request, registry, getMetricName(api), tags(zone), new ZonalOperationWaiter());
  }

  private String getMetricName(String api) {
    return String.join(".", "compute", serviceName, api);
  }

  private static ImmutableMap<String, String> tags(String zone) {
    return ImmutableMap.<String, String>builder()
        .put(GoogleExecutor.getTAG_SCOPE(), GoogleExecutor.getSCOPE_ZONAL())
        .put(GoogleExecutor.getTAG_ZONE(), zone)
        .build();
  }

  private final class ZonalOperationWaiter implements OperationWaiter {

    @Override
    public Operation wait(Operation operation, Task task, String phase) {
      return poller.waitForZonalOperation(
          credentials.getCompute(),
          credentials.getProject(),
          GCEUtil.getLocalName(operation.getZone()),
          operation.getName(),
          /* timeoutSeconds= */ null,
          task,
          GCEUtil.getLocalName(operation.getTargetLink()),
          phase);
    }
  }
}
