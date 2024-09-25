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

package com.netflix.spinnaker.clouddriver.ecs.provider.view

import com.netflix.spinnaker.cats.cache.Cache
import com.amazonaws.services.ecs.model.Cluster
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.DescribeClustersResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys
import com.netflix.spinnaker.clouddriver.ecs.cache.model.EcsCluster
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.EcsClusterCachingAgent
import com.netflix.spinnaker.clouddriver.ecs.security.NetflixECSCredentials
import com.netflix.spinnaker.credentials.CredentialsRepository
import spock.lang.Specification
import spock.lang.Subject

class EcsClusterProviderSpec extends Specification {
  private static String ACCOUNT = 'test-account'
  private static String REGION = 'us-west-1'

  private Cache cacheView = Mock(Cache)
  private CredentialsRepository<NetflixECSCredentials> credentialsRepository;
  private AmazonClientProvider mockAwsProvider
  @Subject
  private EcsClusterProvider ecsClusterProvider = new EcsClusterProvider(cacheView)

  def 'should get multiple cluster descriptions'() {
    given:
    int numberOfClusters = 3
    Set<String> clusterNames = new HashSet<>()
    Collection<CacheData> cacheData = new HashSet<>()
    Collection<Cluster> clustersResponse = new ArrayList<>()
    Collection<String> ecsClustersIdentifiers = new ArrayList<>()
    clusterNames.add("example-app-test-Cluster-NSnYsTXmCfV2")
    clusterNames.add("TestCluster")
    clusterNames.add("spinnaker-deployment-cluster")

    for (int x = 0; x < numberOfClusters; x++) {
      String clusterKey = Keys.getClusterKey(ACCOUNT, REGION, clusterNames[x])
      Map<String, Object> attributes = new HashMap<>()
      ecsClustersIdentifiers.add(Keys.getClusterKey(ACCOUNT, REGION, clusterNames[x]))
      attributes.put("account", ACCOUNT)
      attributes.put("region", REGION)
      attributes.put("clusterArn", "arn:aws:ecs:::cluster/" + clusterNames[x])
      attributes.put("clusterName", clusterNames[x])

      cacheData.add(new DefaultCacheData(clusterKey, attributes, Collections.emptyMap()))
    }
    cacheView.filterIdentifiers(_, _) >> ecsClustersIdentifiers
    cacheView.getAll(_, ecsClustersIdentifiers) >> cacheData

    for (int x = 0; x < numberOfClusters; x++) {
      Cluster cluster = new Cluster()
        .withCapacityProviders("FARGATE", "FARGATE_SPOT").withStatus("ACTIVE")
        .withDefaultCapacityProviderStrategy().withPendingTasksCount(0)
        .withActiveServicesCount(0).withClusterArn("arn:aws:ecs:::cluster/" + clusterNames[x]).withClusterName(clusterNames[x])

      clustersResponse.add(cluster)
    }
    def credentials = Mock(NetflixECSCredentials)
    def amazonEcs = Mock(AmazonECS)
    mockAwsProvider = Mock(AmazonClientProvider)
    credentialsRepository = Mock(CredentialsRepository)

    credentialsRepository.getOne(_) >> credentials
    and:
    ecsClusterProvider.credentialsRepository = credentialsRepository

    mockAwsProvider.getAmazonEcs(_, _, _) >> amazonEcs
    and:
    ecsClusterProvider.amazonClientProvider = mockAwsProvider;

    amazonEcs.describeClusters(_) >> new DescribeClustersResult().withClusters(clustersResponse)

    when:
    def ecsClusters = ecsClusterProvider.getEcsClusterDescriptions(ACCOUNT, REGION)

    then:
    ecsClusters.size() == numberOfClusters
    ecsClusters*.getClusterName().containsAll(clusterNames)
    ecsClusters*.getCapacityProviders()*.get(0).contains("FARGATE")
    ecsClusters*.getCapacityProviders()*.get(1).contains("FARGATE_SPOT")
  }

  def 'should get multiple cluster descriptions filtered based on the account and region'() {
    given:
    int numberOfClusters = 3
    Set<String> clusterNames = new HashSet<>()
    Collection<CacheData> cacheData = new HashSet<>()
    Collection<Cluster> clustersResponse = new ArrayList<>()
    Collection<String> ecsClustersIdentifiers = new ArrayList<>()
    clusterNames.add("example-app-test-Cluster-NSnYsTXmCfV2")
    clusterNames.add("TestCluster")
    clusterNames.add("spinnaker-deployment-cluster")

    for (int x = 0; x < 2; x++) {
      String clusterKey = Keys.getClusterKey(ACCOUNT, REGION, clusterNames[x])
      ecsClustersIdentifiers.add(Keys.getClusterKey(ACCOUNT, REGION, clusterNames[x]))
      Map<String, Object> attributes = new HashMap<>()
      attributes.put("account", ACCOUNT)
      attributes.put("region", REGION)
      attributes.put("clusterArn", "arn:aws:ecs:::cluster/" + clusterNames[x])
      attributes.put("clusterName", clusterNames[x])

      cacheData.add(new DefaultCacheData(clusterKey, attributes, Collections.emptyMap()))
    }
    //Purposely adding cluster with the different region to the cache data.
    String clusterKey = Keys.getClusterKey(ACCOUNT, "us-east-1", clusterNames[2])
    Map<String, Object> attributes = new HashMap<>()
    attributes.put("account", ACCOUNT)
    attributes.put("region", "us-east-1")
    attributes.put("clusterArn", "arn:aws:ecs:::cluster/" + clusterNames[2])
    attributes.put("clusterName", clusterNames[2])

    cacheData.add(new DefaultCacheData(clusterKey, attributes, Collections.emptyMap()))

    cacheView.filterIdentifiers(_, _) >> ecsClustersIdentifiers
    cacheView.getAll(_, ecsClustersIdentifiers) >> cacheData

    //Adding only two clusters in the response which belongs to the expected region.
    for (int x = 0; x < 2; x++) {
      Cluster cluster = new Cluster()
        .withCapacityProviders("FARGATE", "FARGATE_SPOT").withStatus("ACTIVE")
        .withDefaultCapacityProviderStrategy().withPendingTasksCount(0)
        .withActiveServicesCount(0).withClusterArn("arn:aws:ecs:::cluster/" + clusterNames[x]).withClusterName(clusterNames[x])

      clustersResponse.add(cluster)
    }
    def credentials = Mock(NetflixECSCredentials)
    def amazonEcs = Mock(AmazonECS)
    mockAwsProvider = Mock(AmazonClientProvider)
    credentialsRepository = Mock(CredentialsRepository)

    credentialsRepository.getOne(_) >> credentials
    and:
    ecsClusterProvider.credentialsRepository = credentialsRepository

    mockAwsProvider.getAmazonEcs(_, _, _) >> amazonEcs
    and:
    ecsClusterProvider.amazonClientProvider = mockAwsProvider;

    amazonEcs.describeClusters(_) >> new DescribeClustersResult().withClusters(clustersResponse)

    when:
    def ecsClusters = ecsClusterProvider.getEcsClusterDescriptions(ACCOUNT, REGION)

    then:
    // numberOfClusters - 1 justifies that we added 3 clusters to the cache, two with the us-west-2 which is the expected region
    // and one with the us-east-1 which will be filtered out from the filtering logic in  ecsClusterProvider.getEcsClusterDescriptions
    ecsClusters.size() == numberOfClusters - 1
    ecsClusters*.getClusterName().contains(clusterNames[0])
    ecsClusters*.getClusterName().contains(clusterNames[1])
    ecsClusters*.getCapacityProviders()*.get(0).contains("FARGATE")
    ecsClusters*.getCapacityProviders()*.get(1).contains("FARGATE_SPOT")
  }

  def 'should get no clusters'() {
    given:
    cacheView.getAll(_) >> Collections.emptySet()

    when:
    def ecsClusters = ecsClusterProvider.getAllEcsClusters()

    then:
    ecsClusters.size() == 0
  }

  def 'should get a cluster'() {
    given:
    def clusterName = "test-cluster"
    def clusterArn = "arn:aws:ecs:" + REGION + ":012345678910:cluster/" + clusterName
    def key = Keys.getClusterKey(ACCOUNT, REGION, clusterName)

    def keys = new HashSet()
    keys.add(key)

    def attributes = EcsClusterCachingAgent.convertClusterArnToAttributes(ACCOUNT, REGION, clusterArn)
    def cacheData = new HashSet()
    cacheData.add(new DefaultCacheData(key, attributes, Collections.emptyMap()))

    cacheView.getAll(_) >> cacheData

    when:
    Collection<EcsCluster> ecsClusters = ecsClusterProvider.getAllEcsClusters()

    then:
    ecsClusters.size() == 1
    ecsClusters[0].getName() == clusterName
  }

  def 'should get multiple clusters'() {
    given:
    int numberOfClusters = 5
    Set<String> clusterNames = new HashSet<>()
    Collection<CacheData> cacheData = new HashSet<>()
    Set<String> keys = new HashSet<>()

    for (int x = 0; x < numberOfClusters; x++) {
      String clusterName = "test-cluster-" + x
      String clusterArn = "arn:aws:ecs:" + REGION + ":012345678910:cluster/" + clusterName
      String key = Keys.getClusterKey(ACCOUNT, REGION, clusterName)

      keys.add(key)
      clusterNames.add(clusterName)

      Map<String, Object> attributes = EcsClusterCachingAgent.convertClusterArnToAttributes(ACCOUNT, REGION, clusterArn)
      cacheData.add(new DefaultCacheData(key, attributes, Collections.emptyMap()))
    }

    cacheView.getAll(_) >> cacheData

    when:
    Collection<EcsCluster> ecsClusters = ecsClusterProvider.getAllEcsClusters()

    then:
    ecsClusters.size() == numberOfClusters
    clusterNames.containsAll(ecsClusters*.getName())
    ecsClusters*.getName().containsAll(clusterNames)
  }
}
