package com.netflix.spinnaker.clouddriver.aws.provider.agent;

import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider;
import com.netflix.spinnaker.clouddriver.aws.data.Keys;
import com.netflix.spinnaker.clouddriver.aws.provider.AwsProvider;
import java.util.Map;

public class ClusterCleanupAgent extends AbstractClusterCleanupAgent {

  @Override
  public String getProviderName() {
    return AwsProvider.PROVIDER_NAME;
  }

  @Override
  protected String getCloudProviderId() {
    return AmazonCloudProvider.ID;
  }

  @Override
  protected Map<String, String> parseServerGroupId(String serverGroupId) {
    return Keys.parse(serverGroupId);
  }

  @Override
  protected String buildClusterId(String cluster, String application, String account) {
    return Keys.getClusterKey(cluster, application, account);
  }
}
