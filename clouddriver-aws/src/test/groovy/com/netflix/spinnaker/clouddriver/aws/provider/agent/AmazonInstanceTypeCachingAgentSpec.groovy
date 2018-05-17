package com.netflix.spinnaker.clouddriver.aws.provider.agent

import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import org.apache.http.HttpHost
import org.apache.http.ProtocolVersion
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpHead
import org.apache.http.entity.BasicHttpEntity
import org.apache.http.message.BasicHttpResponse
import spock.lang.Specification

class AmazonInstanceTypeCachingAgentSpec extends Specification {

  static final Set<String> TEST_DATA_SET_INSTANCE_TYPES =
    ['d2.8xlarge', 'c5.xlarge', 'h1.2xlarge', 'c4.8xlarge', 'c3.large', 'i3.metal']

  static final US_WEST_2_ACCT = TestCredential.named("test",
    [regions: [
      [name: 'us-west-2',
       availabilityZones: ['us-west-2a', 'us-west-2b', 'us-west-2c']
      ]
    ]])

  AccountCredentialsRepository repo = Stub(AccountCredentialsRepository) {
    getAll() >> [US_WEST_2_ACCT]
  }

  def httpClient = Mock(HttpClient)
  def providerCache = Mock(ProviderCache)

  def "can deserialize response payload"() {
    when:
    def instanceTypes = getTestSubject().fromStream(cannedDataSet())

    then:
    instanceTypes == TEST_DATA_SET_INSTANCE_TYPES
  }

  def "noop if no matching accounts"() {
    given:
    def agent = getTestSubject('us-east-1')

    when:
    def instanceTypes = agent.loadData(providerCache)

    then:
    instanceTypes.cacheResults.isEmpty()
    instanceTypes.evictions.isEmpty()
    0 * _
  }

  def "skip data load if etags match"() {
    given:
    def agent = getTestSubject()

    when:
    def instanceTypes = agent.loadData(providerCache)

    then:
    1 * providerCache.get(agent.getAgentType(), 'metadata', _ as RelationshipCacheFilter) >>
      metadata('bacon', expectedTypes)
    1 * httpClient.execute(_ as HttpHost, _ as HttpHead) >> basicResponse('bacon')
    instanceTypes.evictions.isEmpty()
    metadataMatches(agent.agentType, instanceTypes, 'bacon', expectedTypes)
    instanceTypesMatch(instanceTypes, expectedTypes)
    0 * _

    where:
    expectedTypes = ['m1.megabig', 't2.arnold']
  }

  def "load data if no metadata"() {
    given:
    def agent = getTestSubject()

    when:
    def instanceTypes = agent.loadData(providerCache)

    then:
    1 * providerCache.get(agent.getAgentType(), 'metadata', _ as RelationshipCacheFilter) >> null
    1 * httpClient.execute(_ as HttpHost, _ as HttpGet) >> getResponse('baloney')

    instanceTypes.evictions.isEmpty()
    metadataMatches(agent.agentType, instanceTypes, 'baloney', TEST_DATA_SET_INSTANCE_TYPES)
    instanceTypesMatch(instanceTypes, TEST_DATA_SET_INSTANCE_TYPES)
    0 * _
  }

  def "load data if metadata mismatch"() {
    given:
    def agent = getTestSubject()

    when:
    def instanceTypes = agent.loadData(providerCache)

    then:
    1 * providerCache.get(agent.getAgentType(), 'metadata', _ as RelationshipCacheFilter) >>
      metadata('mustard', ['t7.shouldntmatter'])
    1 * httpClient.execute(_ as HttpHost, _ as HttpHead) >> basicResponse('baloney')
    1 * httpClient.execute(_ as HttpHost, _ as HttpGet) >> getResponse('baloney')

    instanceTypes.evictions.isEmpty()
    metadataMatches(agent.agentType, instanceTypes, 'baloney', TEST_DATA_SET_INSTANCE_TYPES)
    instanceTypesMatch(instanceTypes, TEST_DATA_SET_INSTANCE_TYPES)
    0 * _
  }

  def "evict metadata if no etag"() {
    given:
    def agent = getTestSubject()

    when:
    def instanceTypes = agent.loadData(providerCache)

    then:
    1 * providerCache.get(agent.getAgentType(), 'metadata', _ as RelationshipCacheFilter) >> null
    1 * httpClient.execute(_ as HttpHost, _ as HttpGet) >> getResponse(null)

    instanceTypes.evictions.get(agent.agentType).head() == 'metadata'
    !instanceTypes.cacheResults.get(agent.agentType)
    instanceTypesMatch(instanceTypes, TEST_DATA_SET_INSTANCE_TYPES)
    0 * _

  }

  CacheData metadata(String etag, Collection<String> instanceTypes) {
    new DefaultCacheData('metadata', [etag: etag, cachedInstanceTypes: instanceTypes], [:])

  }

  boolean metadataMatches(String agentType,
                          CacheResult result,
                          String expectedEtag,
                          Collection<String> expectedTypes) {
    def meta = result?.cacheResults?.get(agentType)?.head()
    if (!meta) {
      return false
    }
    meta.id == 'metadata' &&
    meta.attributes.etag == expectedEtag &&
    meta.attributes.cachedInstanceTypes as Set == expectedTypes as Set
  }

  boolean instanceTypesMatch(CacheResult result, Collection<String> expectedTypes) {
    result?.cacheResults?.instanceTypes?.collect { it.id } as Set ==
      expectedTypes.collect { "aws:instanceTypes:$it:test:us-west-2".toString() } as Set
  }

  BasicHttpResponse basicResponse(String etag, int statusCode = 200) {
    def r = new BasicHttpResponse(
      new ProtocolVersion('HTTP', 1, 1),
      statusCode,
      'because reasons')
    if (etag) {
      r.setHeader("ETag", etag)
    }
    return r
  }

  BasicHttpResponse getResponse(String etag) {
    def r = basicResponse(etag)
    def e = new BasicHttpEntity()
    e.setContent(cannedDataSet())
    r.setEntity(e)
    return r
  }

  InputStream cannedDataSet() {
    getClass().getResourceAsStream("us-west-2.json")
  }

  AmazonInstanceTypeCachingAgent getTestSubject(String region = 'us-west-2') {
    return new AmazonInstanceTypeCachingAgent(region, repo, httpClient)
  }

}
