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
import com.google.api.services.compute.model.Operation;
import com.google.api.services.compute.model.RegionInstanceGroupManagersAbandonInstancesRequest;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import java.io.IOException;
import java.util.List;

final class RegionGoogleServerGroupManagers implements GoogleServerGroupManagers {

  private final GoogleNamedAccountCredentials credentials;
  private final RegionalGoogleComputeRequestFactory requestFactory;
  private final Compute.RegionInstanceGroupManagers managers;
  private final String instanceGroupName;
  private final String region;

  RegionGoogleServerGroupManagers(
      GoogleNamedAccountCredentials credentials,
      GoogleOperationPoller operationPoller,
      Registry registry,
      String instanceGroupName,
      String region) {
    this.credentials = credentials;
    this.requestFactory =
        new RegionalGoogleComputeRequestFactory(
            "regionInstanceGroupManagers", credentials, operationPoller, registry);
    this.managers = credentials.getCompute().regionInstanceGroupManagers();
    this.instanceGroupName = instanceGroupName;
    this.region = region;
  }

  @Override
  public GoogleComputeOperationRequest<ComputeRequest<Operation>> abandonInstances(
      List<String> instances) throws IOException {
    RegionInstanceGroupManagersAbandonInstancesRequest request =
        new RegionInstanceGroupManagersAbandonInstancesRequest();
    request.setInstances(instances);
    return requestFactory.wrapOperationRequest(
        managers.abandonInstances(credentials.getProject(), region, instanceGroupName, request),
        "abandonInstances",
        region);
  }

  @Override
  public GoogleComputeOperationRequest<ComputeRequest<Operation>> delete() throws IOException {
    return requestFactory.wrapOperationRequest(
        managers.delete(credentials.getProject(), region, instanceGroupName), "delete", region);
  }

  @Override
  public GoogleComputeGetRequest<ComputeRequest<InstanceGroupManager>, InstanceGroupManager> get()
      throws IOException {
    return requestFactory.wrapGetRequest(
        managers.get(credentials.getProject(), region, instanceGroupName), "get", region);
  }

  @Override
  public GoogleComputeOperationRequest patch(InstanceGroupManager content) throws IOException {
    return requestFactory.wrapOperationRequest(
        managers.patch(credentials.getProject(), region, instanceGroupName, content),
        "patch",
        region);
  }

  @Override
  public GoogleComputeOperationRequest<ComputeRequest<Operation>> update(
      InstanceGroupManager content) throws IOException {
    return requestFactory.wrapOperationRequest(
        managers.update(credentials.getProject(), region, instanceGroupName, content),
        "update",
        region);
  }
}
