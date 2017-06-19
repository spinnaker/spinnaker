package com.netflix.spinnaker.clouddriver.dcos.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.security.DcosAccountCredentials
import com.netflix.spinnaker.clouddriver.dcos.cache.Keys
import com.netflix.spinnaker.clouddriver.dcos.deploy.BaseSpecification
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import mesosphere.dcos.client.DCOS
import mesosphere.dcos.client.DCOSException
import mesosphere.dcos.client.model.ListSecretsResponse

class DcosSecretsCachingAgentSpec extends BaseSpecification {
  DcosAccountCredentials credentials
  AccountCredentialsRepository accountCredentialsRepository

  DcosSecretsCachingAgent subject
  private DcosClientProvider clientProvider
  private DCOS dcosClient
  ProviderCache providerCache
  private ObjectMapper objectMapper

  def setup() {
    accountCredentialsRepository = Mock(AccountCredentialsRepository)
    credentials = defaultCredentialsBuilder().build()
    dcosClient = Mock(DCOS)
    providerCache = Mock(ProviderCache)
    objectMapper = new ObjectMapper()

    clientProvider = Mock(DcosClientProvider) {
      getDcosClient(credentials, DEFAULT_REGION) >> dcosClient
    }

    subject = new DcosSecretsCachingAgent(DEFAULT_ACCOUNT, DEFAULT_REGION, credentials, clientProvider, objectMapper)
  }

  void "Should cache secrets when the account has permission to list secrets"() {
    setup:

    ListSecretsResponse secretsResponse = Mock(ListSecretsResponse) {
      getSecrets() >> ["secret1", "secret2"]
    }

    dcosClient.listSecrets(DEFAULT_SECRET_STORE,"") >> secretsResponse

    def providerCacheMock = Mock(ProviderCache)

    when:
    final result = subject.loadData(providerCacheMock)

    then:
    result.cacheResults.secrets.size() == 2

    // Don't think the cacheResult instances are necessarily ordered, so gotta do a find
    def cacheData1 = result.cacheResults.secrets.find { it.id == Keys.getSecretKey(DEFAULT_REGION, "secret1") }
    cacheData1 != null
    cacheData1.attributes.secretPath == "secret1"

    def cacheData2= result.cacheResults.secrets.find { it.id == Keys.getSecretKey(DEFAULT_REGION, "secret2") }
    cacheData2 != null
    cacheData2.attributes.secretPath == "secret2"
  }

  void "Won't cache any secrets when the account does not have the privileges to load them"() {
    setup:

    dcosClient.listSecrets(DEFAULT_SECRET_STORE,"") >> { throw new DCOSException(403, "error", "GET", "no privs") }

    def providerCacheMock = Mock(ProviderCache)

    when:
    final result = subject.loadData(providerCacheMock)

    then:
    result.cacheResults.secrets.isEmpty()
  }
}
