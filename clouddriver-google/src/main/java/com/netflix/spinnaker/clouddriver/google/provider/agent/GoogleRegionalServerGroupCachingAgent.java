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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.compute.model.Autoscaler;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.InstanceGroupManager;
import com.google.common.collect.ImmutableSet;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.google.cache.Keys;
import com.netflix.spinnaker.clouddriver.google.compute.GoogleComputeApiFactory;
import com.netflix.spinnaker.clouddriver.google.compute.RegionAutoscalers;
import com.netflix.spinnaker.clouddriver.google.compute.RegionInstanceGroupManagers;
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
public final class GoogleRegionalServerGroupCachingAgent
    extends AbstractGoogleServerGroupCachingAgent {

  public GoogleRegionalServerGroupCachingAgent(
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
    String clusterName = null; // getServerGroupKey will calculate this from serverGroupName
    return ImmutableSet.of(
        Keys.getServerGroupKey(serverGroupName, clusterName, getAccountName(), getRegion()));
  }

  @Override
  boolean keyOwnedByThisAgent(Map<String, String> parsedKey) {
    return getAccountName().equals(parsedKey.get("account"))
        && getRegion().equals(parsedKey.get("region"))
        && parsedKey.get("zone") == null;
  }

  @Override
  Collection<InstanceGroupManager> retrieveInstanceGroupManagers() throws IOException {

    RegionInstanceGroupManagers managersApi =
        getComputeApiFactory().createRegionInstanceGroupManagers(getCredentials());
    return managersApi.list(getRegion()).execute();
  }

  @Override
  Collection<Autoscaler> retrieveAutoscalers() throws IOException {

    RegionAutoscalers autoscalersApi =
        getComputeApiFactory().createRegionAutoscalers(getCredentials());
    return autoscalersApi.list(getRegion()).execute();
  }

  @Override
  Optional<InstanceGroupManager> retrieveInstanceGroupManager(String name) throws IOException {
    RegionInstanceGroupManagers managersApi =
        getComputeApiFactory().createRegionInstanceGroupManagers(getCredentials());
    return managersApi.get(getRegion(), name).executeGet();
  }

  @Override
  Optional<Autoscaler> retrieveAutoscaler(InstanceGroupManager manager) throws IOException {

    RegionAutoscalers autoscalersApi =
        getComputeApiFactory().createRegionAutoscalers(getCredentials());
    return autoscalersApi.get(getRegion(), manager.getName()).executeGet();
  }

  @Override
  Collection<Instance> retrieveRelevantInstances(InstanceGroupManager instanceGroupManager)
      throws IOException {
    return retrieveAllInstancesInRegion();
  }

  @Override
  String getBatchContextPrefix() {
    return "RegionalServerGroupCaching";
  }
}
