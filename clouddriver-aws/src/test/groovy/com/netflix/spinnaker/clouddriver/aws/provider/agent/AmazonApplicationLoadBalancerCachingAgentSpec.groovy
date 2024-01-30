package com.netflix.spinnaker.clouddriver.aws.provider.agent

import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeListenersResult
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeLoadBalancersResult
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTagsResult
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetGroupsResult
import com.amazonaws.services.elasticloadbalancingv2.model.Listener
import com.amazonaws.services.elasticloadbalancingv2.model.LoadBalancer
import com.amazonaws.services.elasticloadbalancingv2.model.Tag
import com.amazonaws.services.elasticloadbalancingv2.model.TargetGroup
import com.amazonaws.services.elasticloadbalancingv2.model.TagDescription
import com.netflix.awsobjectmapper.AmazonObjectMapperConfigurer
import com.netflix.spectator.api.Spectator
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.clouddriver.aws.edda.EddaApi
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.EddaTimeoutConfig
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import spock.lang.Shared
import spock.lang.Specification

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.INSTANCES
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.LOAD_BALANCERS
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.TARGET_GROUPS

class AmazonApplicationLoadBalancerCachingAgentSpec extends Specification {
  static String region = 'region'
  static String accountName = 'accountName'
  static String accountId = 'accountId'

  @Shared
  AmazonElasticLoadBalancing elasticLoadBalancing = Mock(AmazonElasticLoadBalancing)

  @Shared
  EddaApi eddaApi = Mock(EddaApi)

  @Shared
  EddaTimeoutConfig eddaTimeoutConfig = Mock(EddaTimeoutConfig)

  @Shared
  AmazonCachingAgentFilter filter = new AmazonCachingAgentFilter()

  def getAgent() {
    def creds = Stub(NetflixAmazonCredentials) {
      getName() >> accountName
      it.getAccountId() >> accountId
    }
    def cloud = Stub(AmazonCloudProvider)
    def client = Stub(AmazonClientProvider) {
      getAmazonElasticLoadBalancingV2(_, _) >> Stub(AmazonElasticLoadBalancing) {
        describeLoadBalancers(_) >> new DescribeLoadBalancersResult() {
          List<LoadBalancer> getLoadBalancers() {
            return filterableLBs().keySet() as List
          }
        }

        describeTags(_) >> new DescribeTagsResult() {
          List<TagDescription> getTagDescriptions() {
            return filterableLBs().values().flatten()
          }
        }

        describeTargetGroups(_) >> new DescribeTargetGroupsResult() {
          List<TargetGroup> getTargetGroups() {
            return filterableTargetGroups()
          }
        }

        describeListeners(_) >> new DescribeListenersResult() {
          List<Listener> getListeners() {
            return []
          }
        }
      }
    }

    new AmazonApplicationLoadBalancerCachingAgent(cloud, client, creds, region, eddaApi, AmazonObjectMapperConfigurer.createConfigured(), Spectator.globalRegistry(), eddaTimeoutConfig, filter)
  }

  void "should filter by tags"() {
    given:
    def agent = getAgent()
    filter.includeTags = includeTags
    filter.excludeTags = excludeTags
    ProviderCache providerCache = Stub(ProviderCache) {
      getAll(_, _) >> {
        return []
      }
    }
    providerCache.addCacheResult(INSTANCES.ns, [], null)

    when:
    def result = agent.loadDataInternal(providerCache)

    then:
    result.cacheResults[LOAD_BALANCERS.ns]*.getId() == expected
    result.cacheResults[TARGET_GROUPS.ns]*.relationships[LOAD_BALANCERS.ns].flatten() == expected

    where:
    includeTags                   | excludeTags                   | expected
    null                          | null                          | filterableLBs()*.getKey().collect { buildCacheKey(it.loadBalancerName) }
    [taggify("hello")]            | null                          | buildCacheKeys(["test-hello-tag-value", "test-hello-tag-value-different", "test-hello-tag-no-value"])
    [taggify("hello", "goodbye")] | null                          | buildCacheKeys(["test-hello-tag-value"])
    [taggify("hello", "goo")]     | null                          | buildCacheKeys([])
    [taggify("hello", ".*bye")]   | null                          | buildCacheKeys(["test-hello-tag-value"])
    [taggify(".*a.*")]            | null                          | buildCacheKeys(["test-no-hello-tag"])
    null                          | [taggify("hello")]            | buildCacheKeys(["test-no-hello-tag", "test-no-tags"])
    null                          | [taggify("hello", "goodbye")] | buildCacheKeys(["test-hello-tag-value-different", "test-hello-tag-no-value", "test-no-hello-tag", "test-no-tags"])
    [taggify("hello", "goodbye")] | [taggify("hello")]            | buildCacheKeys([])
    [taggify(".*", "ciao")]       | [taggify("hello", ".*")]      | buildCacheKeys([])
  }

  void "should get correct cache key pattern"() {
    given:
    def agent = getAgent()

    when:
    def cacheKeyPatterns = agent.getCacheKeyPatterns()

    then:
    cacheKeyPatterns.isPresent()
    cacheKeyPatterns.get() == [
      loadBalancers: buildCacheKey("*:vpc-????????:*")
    ]
  }

  private static final Map<LoadBalancer, List<TagDescription>> filterableLBs() {
    return [
      (new LoadBalancer().withLoadBalancerName("test-hello-tag-value").withLoadBalancerArn(buildELBArn("test-hello-tag-value")))                    :
        [new TagDescription().withResourceArn(buildELBArn("test-hello-tag-value")).withTags(new Tag().withKey("hello").withValue("goodbye"))],
      (new LoadBalancer().withLoadBalancerName("test-hello-tag-value-different").withLoadBalancerArn(buildELBArn("test-hello-tag-value-different"))):
        [new TagDescription().withResourceArn(buildELBArn("test-hello-tag-value-different")).withTags(new Tag().withKey("hello").withValue("ciao"))],
      (new LoadBalancer().withLoadBalancerName("test-hello-tag-no-value").withLoadBalancerArn(buildELBArn("test-hello-tag-no-value")))              :
        [new TagDescription().withResourceArn(buildELBArn("test-hello-tag-no-value")).withTags(new Tag().withKey("hello"))],
      (new LoadBalancer().withLoadBalancerName("test-no-hello-tag").withLoadBalancerArn(buildELBArn("test-no-hello-tag")))                          :
        [new TagDescription().withResourceArn(buildELBArn("test-no-hello-tag")).withTags(new Tag().withKey("Name"))],
      (new LoadBalancer().withLoadBalancerName("test-no-tags").withLoadBalancerArn(buildELBArn("test-no-tags")))                                    : []
    ] as Map
  }

  private static final List<TargetGroup> filterableTargetGroups() {
    return [
      new TargetGroup().withTargetGroupName("tg-test-hello-tag-value").withLoadBalancerArns(buildELBArn("test-hello-tag-value")),
      new TargetGroup().withTargetGroupName("tg-test-hello-tag-value-different").withLoadBalancerArns(buildELBArn("test-hello-tag-value-different")),
      new TargetGroup().withTargetGroupName("tg-test-hello-tag-no-value").withLoadBalancerArns(buildELBArn("test-hello-tag-no-value")),
      new TargetGroup().withTargetGroupName("tg-test-no-hello-tag").withLoadBalancerArns(buildELBArn("test-no-hello-tag")),
      new TargetGroup().withTargetGroupName("tg-test-no-tags").withLoadBalancerArns(buildELBArn("test-no-tags")),
    ]
  }

  private static String buildCacheKey(String name) {
    return "aws:loadBalancers:accountName:region:${name}"
  }

  private static List<String> buildCacheKeys(List<String> names) {
    return names.collect {"aws:loadBalancers:accountName:region:${it}" } as List<String>
  }

  private static String buildTargetGroupCacheKey(String name) {
    return "aws:targetGroups:accountName:region:${name}:null:null"
  }

  private static List<String> buildTargetGroupCacheKeys(List<String> names) {
    return names.collect {"aws:targetGroups:accountName:region:${it}:null:null" } as List<String>
  }

  private static String buildELBArn(String name) {
    return "arn:aws:elasticloadbalancing:${region}:${accountId}:loadbalancer/net/${name}/1234567890"
  }

  private static def taggify(String name = null, String value = null) {
    return new AmazonCachingAgentFilter.TagFilterOption(name, value)
  }
}
