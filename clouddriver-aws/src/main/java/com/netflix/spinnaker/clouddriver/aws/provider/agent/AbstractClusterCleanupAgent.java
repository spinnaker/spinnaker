package com.netflix.spinnaker.clouddriver.aws.provider.agent;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.*;

import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * A caching agent that handles eviction of clusters that no longer have server groups.
 *
 * <p>Clusters exist due to the existence of server groups, but the caching agents that supply
 * server groups (can can cause clusters to exist) do not have a global view of the data so they can
 * not definitively say a cluster should be removed once there are no server groups in that region.
 *
 * <p>This agent just indexes the server groups that exist to find clusters that should be removed
 * and causes them to be evicted.
 *
 * <p>This class is abstract to allow for an AWS and Titus subclass to handle the differentiation in
 * cache key parsing, globbing, and construction but otherwise the logic is the same across both
 * providers.
 */
@Slf4j
public abstract class AbstractClusterCleanupAgent implements CachingAgent {

  @Override
  public String getAgentType() {
    return getCloudProviderId() + "/" + getClass().getSimpleName();
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return Collections.singleton(INFORMATIVE.forType(CLUSTERS.ns));
  }

  protected abstract String getCloudProviderId();

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    final Collection<String> serverGroups =
        providerCache.filterIdentifiers(
            SERVER_GROUPS.ns, buildMatchAllGlob(getCloudProviderId(), SERVER_GROUPS.ns));
    final Collection<String> clusters =
        new HashSet<>(
            providerCache.filterIdentifiers(
                CLUSTERS.ns, buildMatchAllGlob(getCloudProviderId(), CLUSTERS.ns)));

    for (String sgId : serverGroups) {
      final Map<String, String> parts = parseServerGroupId(sgId);
      if (parts != null
          && parts.containsKey("cluster")
          && parts.containsKey("application")
          && parts.containsKey("account")) {
        final String clusterId =
            buildClusterId(parts.get("cluster"), parts.get("application"), parts.get("account"));
        clusters.remove(clusterId);
      }
    }

    if (clusters.isEmpty()) {
      return new DefaultCacheResult(Collections.emptyMap());
    }

    if (log.isDebugEnabled()) {
      log.debug(
          "Evicting {} clusters. Count: {}, keys: {}",
          getCloudProviderId(),
          clusters.size(),
          clusters);
    } else {
      log.info("Evicting {} clusters. Count: {}", getCloudProviderId(), clusters.size());
    }

    return new DefaultCacheResult(Collections.emptyMap(), Map.of(CLUSTERS.ns, clusters));
  }

  protected abstract Map<String, String> parseServerGroupId(String serverGroupId);

  protected abstract String buildClusterId(String cluster, String application, String account);

  protected static String buildMatchAllGlob(String cloudProviderId, String type) {
    return cloudProviderId + ":" + type + ":*";
  }
}
