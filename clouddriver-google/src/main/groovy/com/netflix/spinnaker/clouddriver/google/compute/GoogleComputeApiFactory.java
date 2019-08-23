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
import com.google.common.util.concurrent.ListeningExecutorService;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller;
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class GoogleComputeApiFactory {

  private final GoogleOperationPoller operationPoller;
  private final Registry registry;
  private String clouddriverUserAgentApplicationName;
  private ListeningExecutorService batchExecutor;

  @Autowired
  public GoogleComputeApiFactory(
      GoogleOperationPoller operationPoller,
      Registry registry,
      String clouddriverUserAgentApplicationName,
      @Qualifier(ComputeConfiguration.BATCH_REQUEST_EXECUTOR)
          ListeningExecutorService batchExecutor) {
    this.operationPoller = operationPoller;
    this.registry = registry;
    this.clouddriverUserAgentApplicationName = clouddriverUserAgentApplicationName;
    this.batchExecutor = batchExecutor;
  }

  public Images createImages(GoogleNamedAccountCredentials credentials) {
    return new Images(credentials, operationPoller, registry);
  }

  public Instances createInstances(GoogleNamedAccountCredentials credentials) {
    return new Instances(credentials, operationPoller, registry);
  }

  public InstanceTemplates createInstanceTemplates(GoogleNamedAccountCredentials credentials) {
    return new InstanceTemplates(credentials, operationPoller, registry);
  }

  public ZoneAutoscalers createZoneAutoscalers(GoogleNamedAccountCredentials credentials) {
    return new ZoneAutoscalers(credentials, operationPoller, registry);
  }

  public ZoneInstanceGroupManagers createZoneInstanceGroupManagers(
      GoogleNamedAccountCredentials credentials) {
    return new ZoneInstanceGroupManagers(credentials, operationPoller, registry);
  }

  public GoogleServerGroupManagers createServerGroupManagers(
      GoogleNamedAccountCredentials credentials, GoogleServerGroup.View serverGroup) {
    return serverGroup.getRegional()
        ? new RegionGoogleServerGroupManagers(
            credentials, operationPoller, registry, serverGroup.getName(), serverGroup.getRegion())
        : new ZoneGoogleServerGroupManagers(
            credentials, operationPoller, registry, serverGroup.getName(), serverGroup.getZone());
  }

  public <RequestT extends ComputeRequest<ResponseT>, ResponseT>
      BatchComputeRequest<RequestT, ResponseT> createBatchRequest(
          GoogleNamedAccountCredentials credentials) {
    return new BatchComputeRequestImpl<>(
        credentials.getCompute(), registry, clouddriverUserAgentApplicationName, batchExecutor);
  }

  public <ComputeRequestT extends ComputeRequest<ResponseT>, ResponseT, ItemT>
      BatchPaginatedComputeRequest<ComputeRequestT, ItemT> createPaginatedBatchRequest(
          GoogleNamedAccountCredentials credentials) {
    return new BatchPaginatedComputeRequestImpl<ComputeRequestT, ResponseT, ItemT>(
        () -> createBatchRequest(credentials));
  }
}
