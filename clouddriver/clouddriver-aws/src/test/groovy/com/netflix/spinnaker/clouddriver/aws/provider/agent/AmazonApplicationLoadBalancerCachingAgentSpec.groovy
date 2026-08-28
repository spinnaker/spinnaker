package com.netflix.spinnaker.clouddriver.aws.provider.agent

import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeListenersResponse
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeLoadBalancersResponse
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeTagsResponse
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeTargetGroupsResponse
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Listener
import software.amazon.awssdk.services.elasticloadbalancingv2.model.LoadBalancer
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Tag
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetGroup
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TagDescription
import com.netflix.awsobjectmapper.AmazonObjectMapperConfigurer
import com.netflix.spectator.api.Spectator
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.clouddriver.aws.edda.EddaApi
import com.netflix.spinnaker.clouddriver.aws.jackson.AwsSdkV2Module
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
  ElasticLoadBalancingV2Client elasticLoadBalancing = Mock(ElasticLoadBalancingV2Client)

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
      getElasticLoadBalancingV2Client(_, _) >> Stub(ElasticLoadBalancingV2Client) {
        describeLoadBalancers(_) >> DescribeLoadBalancersResponse.builder()
          .loadBalancers(filterableLBs().keySet() as List)
          .build()

        describeTags(_) >> DescribeTagsResponse.builder()
          .tagDescriptions(filterableLBs().values().flatten() as List<TagDescription>)
          .build()

        describeTargetGroups(_) >> DescribeTargetGroupsResponse.builder()
          .targetGroups(filterableTargetGroups())
          .build()

        describeListeners(_) >> DescribeListenersResponse.builder()
          .listeners([] as List<Listener>)
          .build()
      }
    }

    new AmazonApplicationLoadBalancerCachingAgent(cloud, client, creds, region, eddaApi, AmazonObjectMapperConfigurer.createConfigured().registerModule(new AwsSdkV2Module()), Spectator.globalRegistry(), eddaTimeoutConfig, filter)
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
    null                          | null                          | filterableLBs()*.getKey().collect { buildCacheKey(it.loadBalancerName()) }
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
      (LoadBalancer.builder().loadBalancerName("test-hello-tag-value").loadBalancerArn(buildELBArn("test-hello-tag-value")).build())                    :
        [TagDescription.builder().resourceArn(buildELBArn("test-hello-tag-value")).tags(Tag.builder().key("hello").value("goodbye").build()).build()],
      (LoadBalancer.builder().loadBalancerName("test-hello-tag-value-different").loadBalancerArn(buildELBArn("test-hello-tag-value-different")).build()):
        [TagDescription.builder().resourceArn(buildELBArn("test-hello-tag-value-different")).tags(Tag.builder().key("hello").value("ciao").build()).build()],
      (LoadBalancer.builder().loadBalancerName("test-hello-tag-no-value").loadBalancerArn(buildELBArn("test-hello-tag-no-value")).build())              :
        [TagDescription.builder().resourceArn(buildELBArn("test-hello-tag-no-value")).tags(Tag.builder().key("hello").build()).build()],
      (LoadBalancer.builder().loadBalancerName("test-no-hello-tag").loadBalancerArn(buildELBArn("test-no-hello-tag")).build())                          :
        [TagDescription.builder().resourceArn(buildELBArn("test-no-hello-tag")).tags(Tag.builder().key("Name").build()).build()],
      (LoadBalancer.builder().loadBalancerName("test-no-tags").loadBalancerArn(buildELBArn("test-no-tags")).build())                                    : []
    ] as Map
  }

  private static final List<TargetGroup> filterableTargetGroups() {
    return [
      TargetGroup.builder().targetGroupName("tg-test-hello-tag-value").loadBalancerArns(buildELBArn("test-hello-tag-value")).build(),
      TargetGroup.builder().targetGroupName("tg-test-hello-tag-value-different").loadBalancerArns(buildELBArn("test-hello-tag-value-different")).build(),
      TargetGroup.builder().targetGroupName("tg-test-hello-tag-no-value").loadBalancerArns(buildELBArn("test-hello-tag-no-value")).build(),
      TargetGroup.builder().targetGroupName("tg-test-no-hello-tag").loadBalancerArns(buildELBArn("test-no-hello-tag")).build(),
      TargetGroup.builder().targetGroupName("tg-test-no-tags").loadBalancerArns(buildELBArn("test-no-tags")).build(),
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
