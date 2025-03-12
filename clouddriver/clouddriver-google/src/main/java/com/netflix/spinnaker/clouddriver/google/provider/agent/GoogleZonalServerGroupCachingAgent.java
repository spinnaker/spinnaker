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

package com.netflix.spinnaker.clouddriver.google.provider.agent;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.SERVER_GROUPS;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.Compute.InstanceGroupManagers;
import com.google.api.services.compute.Compute.InstanceGroupManagers.Get;
import com.google.api.services.compute.model.Autoscaler;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.InstanceGroupManager;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.google.cache.Keys;
import com.netflix.spinnaker.clouddriver.google.compute.BatchPaginatedComputeRequest;
import com.netflix.spinnaker.clouddriver.google.compute.GetFirstBatchComputeRequest;
import com.netflix.spinnaker.clouddriver.google.compute.GoogleComputeApiFactory;
import com.netflix.spinnaker.clouddriver.google.compute.Instances;
import com.netflix.spinnaker.clouddriver.google.compute.ZoneAutoscalers;
import com.netflix.spinnaker.clouddriver.google.compute.ZoneInstanceGroupManagers;
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import com.netflix.spinnaker.kork.client.ServiceClientProvider;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import javax.annotation.ParametersAreNonnullByDefault;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ParametersAreNonnullByDefault
public final class GoogleZonalServerGroupCachingAgent
    extends AbstractGoogleServerGroupCachingAgent {

  public GoogleZonalServerGroupCachingAgent(
      GoogleNamedAccountCredentials credentials,
      GoogleComputeApiFactory computeApiFactory,
      Registry registry,
      String region,
      ObjectMapper objectMapper,
      ServiceClientProvider serviceClientProvider) {
    super(credentials, computeApiFactory, registry, region, objectMapper, serviceClientProvider);
  }

  @Override
  Collection<String> getOnDemandKeysToEvictForMissingServerGroup(
      ProviderCache providerCache, String serverGroupName) {
    // If we didn't find this  server group, look for any existing ON_DEMAND entries for it (in
    // any zone) and evict them.
    // TODO(plumpy): I think this is a bug and SERVER_GROUPS should be ON_DEMAND.
    String serverGroupKey =
        Keys.getServerGroupKey(
            serverGroupName, /* cluster= */ null, getAccountName(), getRegion(), /* zone= */ "*");
    return providerCache.filterIdentifiers(SERVER_GROUPS.getNs(), serverGroupKey);
  }

  @Override
  boolean keyOwnedByThisAgent(Map<String, String> parsedKey) {
    return getAccountName().equals(parsedKey.get("account"))
        && getRegion().equals(parsedKey.get("region"))
        && parsedKey.get("zone") != null;
  }

  @Override
  Collection<InstanceGroupManager> retrieveInstanceGroupManagers() throws IOException {

    ZoneInstanceGroupManagers managersApi =
        getComputeApiFactory().createZoneInstanceGroupManagers(getCredentials());
    BatchPaginatedComputeRequest<InstanceGroupManagers.List, InstanceGroupManager> request =
        getComputeApiFactory().createPaginatedBatchRequest(getCredentials());

    getZonesForRegion().forEach(zone -> request.queue(managersApi.list(zone)));

    return request.execute(getBatchContext("igm"));
  }

  @Override
  Collection<Autoscaler> retrieveAutoscalers() throws IOException {

    ZoneAutoscalers autoscalersApi = getComputeApiFactory().createZoneAutoscalers(getCredentials());
    BatchPaginatedComputeRequest<Compute.Autoscalers.List, Autoscaler> request =
        getComputeApiFactory().createPaginatedBatchRequest(getCredentials());

    getZonesForRegion().forEach(zone -> request.queue(autoscalersApi.list(zone)));

    return request.execute(getBatchContext("autoscaler"));
  }

  @Override
  Optional<InstanceGroupManager> retrieveInstanceGroupManager(String name) throws IOException {

    ZoneInstanceGroupManagers managersApi =
        getComputeApiFactory().createZoneInstanceGroupManagers(getCredentials());
    GetFirstBatchComputeRequest<Get, InstanceGroupManager> request =
        GetFirstBatchComputeRequest.create(
            getComputeApiFactory().createBatchRequest(getCredentials()));
    for (String zone : getZonesForRegion()) {
      request.queue(managersApi.get(zone, name));
    }
    return request.execute(getBatchContext("igm"));
  }

  @Override
  Optional<Autoscaler> retrieveAutoscaler(InstanceGroupManager manager) throws IOException {

    ZoneAutoscalers autoscalersApi = getComputeApiFactory().createZoneAutoscalers(getCredentials());
    return autoscalersApi.get(getZone(manager), manager.getName()).executeGet();
  }

  @Override
  Collection<Instance> retrieveRelevantInstances(InstanceGroupManager manager) throws IOException {

    Instances instancesApi = getComputeApiFactory().createInstances(getCredentials());
    return instancesApi.list(getZone(manager)).execute().stream().collect(toImmutableList());
  }

  private String getZone(InstanceGroupManager manager) {

    checkState(
        !isNullOrEmpty(manager.getZone()),
        "Managed instance group %s did not have a zone.",
        manager.getName());
    return Utils.getLocalName(manager.getZone());
  }

  @Override
  String getBatchContextPrefix() {
    return "ZonalServerGroupCaching";
  }
}
