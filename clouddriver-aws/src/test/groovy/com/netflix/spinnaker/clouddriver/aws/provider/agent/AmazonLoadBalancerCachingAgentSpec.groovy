package com.netflix.spinnaker.clouddriver.aws.provider.agent

import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing
import com.amazonaws.services.elasticloadbalancing.model.*
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

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.*

class AmazonLoadBalancerCachingAgentSpec extends Specification {
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
      getAmazonElasticLoadBalancing(_, _) >> Stub(AmazonElasticLoadBalancing) {
        describeLoadBalancers(_) >> new DescribeLoadBalancersResult() {
          List<LoadBalancerDescription> getLoadBalancerDescriptions() {
            return filterableLBs().keySet() as List
          }
        }

        describeTags(_) >> new DescribeTagsResult() {
          List<TagDescription> getTagDescriptions() {
            return filterableLBs().values().flatten()
          }
        }

        describeLoadBalancerAttributes(_) >> new DescribeLoadBalancerAttributesResult() {
          LoadBalancerAttributes getLoadBalancerAttributes() {
            return new LoadBalancerAttributes()
          }
        }
      }
    }

    new AmazonLoadBalancerCachingAgent(cloud, client, creds, region, eddaApi, AmazonObjectMapperConfigurer.createConfigured(), Spectator.globalRegistry(), filter)
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
      loadBalancers: buildCacheKey("*:vpc-????????")
    ]
  }

  private static final Map<LoadBalancerDescription, List<TagDescription>> filterableLBs() {
    return [
      (new LoadBalancerDescription().withLoadBalancerName("test-hello-tag-value")):
        [new TagDescription().withLoadBalancerName("test-hello-tag-value").withTags(new Tag().withKey("hello").withValue("goodbye"))],
      (new LoadBalancerDescription().withLoadBalancerName("test-hello-tag-value-different")):
        [new TagDescription().withLoadBalancerName("test-hello-tag-value-different").withTags(new Tag().withKey("hello").withValue("ciao"))],
      (new LoadBalancerDescription().withLoadBalancerName("test-hello-tag-no-value")):
        [new TagDescription().withLoadBalancerName("test-hello-tag-no-value").withTags(new Tag().withKey("hello"))],
      (new LoadBalancerDescription().withLoadBalancerName("test-no-hello-tag")):
        [new TagDescription().withLoadBalancerName("test-no-hello-tag").withTags(new Tag().withKey("Name"))],
      (new LoadBalancerDescription().withLoadBalancerName("test-no-tags")):[]
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
