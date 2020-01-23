/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under
 * the License.
 */

package com.netflix.spinnaker.clouddriver.ecs.provider.agent;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.AgentExecution;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.cats.provider.ProviderRegistry;
import com.netflix.spinnaker.clouddriver.aws.provider.AwsProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AbstractEcsAwsAwareCachingAgent provides an AWS Provider cache for ECS caching agents that need
 * to access resources cached by the AWS Provider.
 */
abstract class AbstractEcsAwsAwareCachingAgent<T> extends AbstractEcsCachingAgent<T> {
  protected ProviderCache awsProviderCache;

  AbstractEcsAwsAwareCachingAgent(
      NetflixAmazonCredentials account,
      String region,
      AmazonClientProvider amazonClientProvider,
      AWSCredentialsProvider awsCredentialsProvider) {
    super(account, region, amazonClientProvider, awsCredentialsProvider);
  }

  public void setAwsCache(ProviderCache awsCache) {
    this.awsProviderCache = awsCache;
  }

  @Override
  public AgentExecution getAgentExecution(ProviderRegistry providerRegistry) {
    return new EcsAwsAwareCacheExecution(providerRegistry);
  }

  class EcsAwsAwareCacheExecution extends CacheExecution {
    private final Logger log = LoggerFactory.getLogger(EcsAwsAwareCacheExecution.class);
    private final ProviderRegistry providerRegistry;

    public EcsAwsAwareCacheExecution(ProviderRegistry providerRegistry) {
      super(providerRegistry);
      this.providerRegistry = providerRegistry;
    }

    /**
     * Retrieves the awsProviderCache from the provider registry and sets it on the agent before
     * loading its data.
     */
    @Override
    public CacheResult executeAgentWithoutStore(Agent agent) {
      AbstractEcsAwsAwareCachingAgent cachingAgent = (AbstractEcsAwsAwareCachingAgent) agent;
      ProviderCache ecsCache = providerRegistry.getProviderCache(cachingAgent.getProviderName());

      String awsProviderName = AwsProvider.PROVIDER_NAME;
      log.info("Setting AWS Provider: " + awsProviderName);
      ProviderCache awsCache = providerRegistry.getProviderCache(awsProviderName);
      cachingAgent.setAwsCache(awsCache);

      return cachingAgent.loadData(ecsCache);
    }
  }
}
