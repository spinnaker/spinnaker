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

import com.google.api.services.compute.Compute;
import com.google.api.services.compute.ComputeRequest;
import com.google.api.services.compute.model.InstanceGroupManager;
import com.google.api.services.compute.model.InstanceGroupManagersAbandonInstancesRequest;
import com.google.api.services.compute.model.Operation;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import java.io.IOException;
import java.util.List;

final class ZoneGoogleServerGroupManagers implements GoogleServerGroupManagers {

  private final GoogleNamedAccountCredentials credentials;
  private final ZonalGoogleComputeRequestFactory requestFactory;
  private final Compute.InstanceGroupManagers managers;
  private final String instanceGroupName;
  private final String zone;

  ZoneGoogleServerGroupManagers(
      GoogleNamedAccountCredentials credentials,
      GoogleOperationPoller operationPoller,
      Registry registry,
      String instanceGroupName,
      String zone) {
    this.credentials = credentials;
    this.requestFactory =
        new ZonalGoogleComputeRequestFactory(
            "instanceGroupManagers", credentials, operationPoller, registry);
    this.managers = credentials.getCompute().instanceGroupManagers();
    this.instanceGroupName = instanceGroupName;
    this.zone = zone;
  }

  @Override
  public GoogleComputeOperationRequest<ComputeRequest<Operation>> abandonInstances(
      List<String> instances) throws IOException {
    InstanceGroupManagersAbandonInstancesRequest request =
        new InstanceGroupManagersAbandonInstancesRequest();
    request.setInstances(instances);
    return requestFactory.wrapOperationRequest(
        managers.abandonInstances(credentials.getProject(), zone, instanceGroupName, request),
        "abandonInstances",
        zone);
  }

  @Override
  public GoogleComputeOperationRequest<ComputeRequest<Operation>> delete() throws IOException {
    return requestFactory.wrapOperationRequest(
        managers.delete(credentials.getProject(), zone, instanceGroupName), "delete", zone);
  }

  @Override
  public GoogleComputeGetRequest<ComputeRequest<InstanceGroupManager>, InstanceGroupManager> get()
      throws IOException {
    return requestFactory.wrapGetRequest(
        managers.get(credentials.getProject(), zone, instanceGroupName), "get", zone);
  }

  @Override
  public GoogleComputeOperationRequest patch(InstanceGroupManager content) throws IOException {
    return requestFactory.wrapOperationRequest(
        managers.patch(credentials.getProject(), zone, instanceGroupName, content), "patch", zone);
  }

  @Override
  public GoogleComputeOperationRequest<ComputeRequest<Operation>> update(
      InstanceGroupManager content) throws IOException {
    return requestFactory.wrapOperationRequest(
        managers.patch(credentials.getProject(), zone, instanceGroupName, content), "update", zone);
  }
}
