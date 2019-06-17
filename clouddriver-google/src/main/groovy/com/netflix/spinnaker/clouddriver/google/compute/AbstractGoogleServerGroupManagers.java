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
import com.google.api.services.compute.model.InstanceGroupManager;
import com.google.api.services.compute.model.Operation;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.google.compute.GoogleComputeOperationRequestImpl.OperationWaiter;
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import java.io.IOException;
import java.util.List;
import java.util.Map;

abstract class AbstractGoogleServerGroupManagers implements GoogleServerGroupManagers {

  private final GoogleNamedAccountCredentials credentials;
  private final GoogleOperationPoller poller;
  private final Registry registry;
  private final String instanceGroupName;

  AbstractGoogleServerGroupManagers(
      GoogleNamedAccountCredentials credentials,
      GoogleOperationPoller poller,
      Registry registry,
      String instanceGroupName) {
    this.credentials = credentials;
    this.poller = poller;
    this.registry = registry;
    this.instanceGroupName = instanceGroupName;
  }

  @Override
  public GoogleComputeOperationRequest abandonInstances(List<String> instances) throws IOException {
    return wrapOperationRequest(performAbandonInstances(instances), "abandonInstances");
  }

  abstract ComputeRequest<Operation> performAbandonInstances(List<String> instances)
      throws IOException;

  @Override
  public GoogleComputeOperationRequest delete() throws IOException {
    return wrapOperationRequest(performDelete(), "delete");
  }

  abstract ComputeRequest<Operation> performDelete() throws IOException;

  @Override
  public GoogleComputeRequest<InstanceGroupManager> get() throws IOException {
    return wrapRequest(performGet(), "get");
  }

  abstract ComputeRequest<InstanceGroupManager> performGet() throws IOException;

  @Override
  public GoogleComputeOperationRequest update(InstanceGroupManager content) throws IOException {
    return wrapOperationRequest(performUpdate(content), "update");
  }

  abstract ComputeRequest<Operation> performUpdate(InstanceGroupManager content) throws IOException;

  private <T> GoogleComputeRequest<T> wrapRequest(ComputeRequest<T> request, String api) {
    return new GoogleComputeRequestImpl<>(
        request, registry, getMetricName(api), getRegionOrZoneTags());
  }

  private GoogleComputeOperationRequest wrapOperationRequest(
      ComputeRequest<Operation> request, String api) {

    OperationWaiter waiter = getOperationWaiter(credentials, poller);
    return new GoogleComputeOperationRequestImpl(
        request, registry, getMetricName(api), getRegionOrZoneTags(), waiter);
  }

  private String getMetricName(String api) {
    return String.format("compute.%s.%s", getManagersType(), api);
  }

  abstract OperationWaiter getOperationWaiter(
      GoogleNamedAccountCredentials credentials, GoogleOperationPoller poller);

  String getProject() {
    return credentials.getProject();
  }

  String getInstanceGroupName() {
    return instanceGroupName;
  }

  abstract String getManagersType();

  abstract Map<String, String> getRegionOrZoneTags();
}
