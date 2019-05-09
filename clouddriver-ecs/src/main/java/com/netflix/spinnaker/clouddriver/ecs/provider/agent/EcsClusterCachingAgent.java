/*
 * Copyright 2017 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.provider.agent;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.ListClustersRequest;
import com.amazonaws.services.ecs.model.ListClustersResult;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.ECS_CLUSTERS;

public class EcsClusterCachingAgent extends AbstractEcsCachingAgent<String> {
  private static final Collection<AgentDataType> types = Collections.unmodifiableCollection(Arrays.asList(
    AUTHORITATIVE.forType(ECS_CLUSTERS.toString())
  ));
  private final Logger log = LoggerFactory.getLogger(getClass());

  public EcsClusterCachingAgent(NetflixAmazonCredentials account, String region, AmazonClientProvider amazonClientProvider, AWSCredentialsProvider awsCredentialsProvider) {
    super(account, region, amazonClientProvider, awsCredentialsProvider);
  }

  @Override
  public String getAgentType() {
    return accountName + "/" + region + "/" + getClass().getSimpleName();
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return types;
  }

  @Override
  protected List<String> getItems(AmazonECS ecs, ProviderCache providerCache) {
    List<String> allClusterArns = new LinkedList<>();
    String nextToken = null;
    do {
      ListClustersRequest listClustersRequest = new ListClustersRequest();
      if (nextToken != null) {
        listClustersRequest.setNextToken(nextToken);
      }

      ListClustersResult listClustersResult = ecs.listClusters(listClustersRequest);
      allClusterArns.addAll(listClustersResult.getClusterArns());

      nextToken = listClustersResult.getNextToken();
    } while (nextToken != null && nextToken.length() != 0);

    return allClusterArns;
  }

  @Override
  protected Map<String, Collection<CacheData>> generateFreshData(Collection<String> clusterArns) {
    Collection<CacheData> dataPoints = new LinkedList<>();
    for (String clusterArn : clusterArns) {
      String clusterName = StringUtils.substringAfterLast(clusterArn, "/");

      Map<String, Object> attributes = convertClusterArnToAttributes(accountName, region, clusterArn);

      String key = Keys.getClusterKey(accountName, region, clusterName);
      dataPoints.add(new DefaultCacheData(key, attributes, Collections.emptyMap()));
    }

    log.info("Caching " + dataPoints.size() + " ECS clusters in " + getAgentType());
    Map<String, Collection<CacheData>> dataMap = new HashMap<>();
    dataMap.put(ECS_CLUSTERS.toString(), dataPoints);

    return dataMap;
  }
  public static  Map<String, Object> convertClusterArnToAttributes(String accountName, String region, String clusterArn){
    String clusterName = StringUtils.substringAfterLast(clusterArn, "/");

    Map<String, Object> attributes = new HashMap<>();
    attributes.put("account", accountName);
    attributes.put("region", region);
    attributes.put("clusterName", clusterName);
    attributes.put("clusterArn", clusterArn);

    return attributes;
  }
}
