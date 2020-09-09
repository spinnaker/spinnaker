/*
 * Copyright 2020 Armory, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.provider.agent;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.cache.Keys.Namespace.SPACES;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toSet;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.cache.OnDemandType;
import com.netflix.spinnaker.clouddriver.cloudfoundry.cache.Keys;
import com.netflix.spinnaker.clouddriver.cloudfoundry.cache.ResourceCacheData;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import com.netflix.spinnaker.clouddriver.cloudfoundry.security.CloudFoundryCredentials;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Slf4j
public class CloudFoundrySpaceCachingAgent extends AbstractCloudFoundryCachingAgent {

  private static final ObjectMapper cacheViewMapper =
      new ObjectMapper().disable(MapperFeature.DEFAULT_VIEW_INCLUSION);

  private final Collection<AgentDataType> providedDataTypes =
      Arrays.asList(AUTHORITATIVE.forType(SPACES.getNs()));

  public CloudFoundrySpaceCachingAgent(CloudFoundryCredentials credentials, Registry registry) {
    super(credentials, registry);
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    long loadDataStart = this.getInternalClock().millis();
    String accountName = getAccountName();
    log.info("Caching all spaces in Cloud Foundry account " + accountName);

    List<CloudFoundrySpace> spaces = getCredentials().getSpacesLive();

    Map<String, Collection<CacheData>> results =
        ImmutableMap.of(
            SPACES.getNs(),
            spaces.stream()
                .map(
                    s ->
                        new ResourceCacheData(
                            Keys.getSpaceKey(accountName, s.getRegion()), cacheView(s), emptyMap()))
                .collect(toSet()));

    log.debug(
        "Space cache loaded for Cloud Foundry account {}, ({} sec)",
        accountName,
        (getInternalClock().millis() - loadDataStart) / 1000);
    return new DefaultCacheResult(results, emptyMap());
  }

  @Override
  public boolean handles(OnDemandType type, String cloudProvider) {
    return false;
  }

  @Nullable
  @Override
  public OnDemandResult handle(ProviderCache providerCache, Map<String, ?> data) {
    return null;
  }

  @Override
  public Collection<Map<String, Object>> pendingOnDemandRequests(ProviderCache providerCache) {
    return null;
  }
}
