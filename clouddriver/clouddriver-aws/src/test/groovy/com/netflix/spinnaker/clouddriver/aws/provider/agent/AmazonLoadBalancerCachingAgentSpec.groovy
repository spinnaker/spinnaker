package com.netflix.spinnaker.clouddriver.aws.provider.agent

import software.amazon.awssdk.services.elasticloadbalancing.ElasticLoadBalancingClient
import software.amazon.awssdk.services.elasticloadbalancing.model.DescribeLoadBalancerAttributesResponse
import software.amazon.awssdk.services.elasticloadbalancing.model.DescribeLoadBalancersResponse
import software.amazon.awssdk.services.elasticloadbalancing.model.DescribeTagsResponse
import software.amazon.awssdk.services.elasticloadbalancing.model.LoadBalancerAttributes
import software.amazon.awssdk.services.elasticloadbalancing.model.LoadBalancerDescription
import software.amazon.awssdk.services.elasticloadbalancing.model.Tag
import software.amazon.awssdk.services.elasticloadbalancing.model.TagDescription
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

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.*

class AmazonLoadBalancerCachingAgentSpec extends Specification {
  static String region = 'region'
  static String accountName = 'accountName'
  static String accountId = 'accountId'

  @Shared
  ElasticLoadBalancingClient elasticLoadBalancing = Mock(ElasticLoadBalancingClient)

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
      getAmazonElasticLoadBalancingClassicV2(_, _) >> Stub(ElasticLoadBalancingClient) {
        describeLoadBalancers(_) >> DescribeLoadBalancersResponse.builder()
          .loadBalancerDescriptions(filterableLBs().keySet() as List)
          .build()

        describeTags(_) >> DescribeTagsResponse.builder()
          .tagDescriptions(filterableLBs().values().flatten() as List<TagDescription>)
          .build()

        describeLoadBalancerAttributes(_) >> DescribeLoadBalancerAttributesResponse.builder()
          .loadBalancerAttributes(LoadBalancerAttributes.builder().build())
          .build()
      }
    }

    new AmazonLoadBalancerCachingAgent(cloud, client, creds, region, eddaApi, AmazonObjectMapperConfigurer.createConfigured().registerModule(new AwsSdkV2Module()), Spectator.globalRegistry(), filter)
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
      loadBalancers: buildCacheKey("*:vpc-????????")
    ]
  }

  private static final Map<LoadBalancerDescription, List<TagDescription>> filterableLBs() {
    return [
      (LoadBalancerDescription.builder().loadBalancerName("test-hello-tag-value").build()):
        [TagDescription.builder().loadBalancerName("test-hello-tag-value").tags(Tag.builder().key("hello").value("goodbye").build()).build()],
      (LoadBalancerDescription.builder().loadBalancerName("test-hello-tag-value-different").build()):
        [TagDescription.builder().loadBalancerName("test-hello-tag-value-different").tags(Tag.builder().key("hello").value("ciao").build()).build()],
      (LoadBalancerDescription.builder().loadBalancerName("test-hello-tag-no-value").build()):
        [TagDescription.builder().loadBalancerName("test-hello-tag-no-value").tags(Tag.builder().key("hello").build()).build()],
      (LoadBalancerDescription.builder().loadBalancerName("test-no-hello-tag").build()):
        [TagDescription.builder().loadBalancerName("test-no-hello-tag").tags(Tag.builder().key("Name").build()).build()],
      (LoadBalancerDescription.builder().loadBalancerName("test-no-tags").build()):[]
    ] as Map
  }

  private static String buildCacheKey(String name) {
    return "aws:loadBalancers:accountName:region:${name}"
  }

  private static List<String> buildCacheKeys(List<String> names) {
    return names.collect {"aws:loadBalancers:accountName:region:${it}" } as List<String>
  }

  private static def taggify(String name = null, String value = null) {
    return new AmazonCachingAgentFilter.TagFilterOption(name, value)
  }
}
