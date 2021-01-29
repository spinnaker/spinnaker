package com.netflix.spinnaker.clouddriver.titus.caching.agents;

import com.netflix.spinnaker.clouddriver.aws.provider.agent.AbstractClusterCleanupAgent;
import com.netflix.spinnaker.clouddriver.titus.TitusCloudProvider;
import com.netflix.spinnaker.clouddriver.titus.caching.Keys;
import com.netflix.spinnaker.clouddriver.titus.caching.TitusCachingProvider;
import java.util.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ClusterCleanupAgent extends AbstractClusterCleanupAgent {

  @Override
  public String getProviderName() {
    return TitusCachingProvider.PROVIDER_NAME;
  }

  @Override
  protected String getCloudProviderId() {
    return TitusCloudProvider.ID;
  }

  @Override
  protected Map<String, String> parseServerGroupId(String serverGroupId) {
    return Keys.parse(serverGroupId);
  }

  @Override
  protected String buildClusterId(String cluster, String application, String account) {
    return Keys.getClusterV2Key(cluster, application, account);
  }
}
