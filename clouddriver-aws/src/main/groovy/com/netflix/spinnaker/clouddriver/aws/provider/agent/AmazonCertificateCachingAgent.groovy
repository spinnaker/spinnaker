/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.aws.provider.agent

import com.amazonaws.services.identitymanagement.model.ListServerCertificatesRequest
import com.amazonaws.services.identitymanagement.model.ServerCertificateMetadata
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.agent.*
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.cache.Keys
import com.netflix.spinnaker.clouddriver.aws.provider.AwsInfrastructureProvider
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import groovy.util.logging.Slf4j

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.clouddriver.aws.cache.Keys.Namespace.CERTIFICATES

@Slf4j
class AmazonCertificateCachingAgent implements CachingAgent, AccountAware {

  final AmazonClientProvider amazonClientProvider
  final NetflixAmazonCredentials account
  final String region
  final ObjectMapper objectMapper

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(CERTIFICATES.ns)
  ] as Set)

  AmazonCertificateCachingAgent(AmazonClientProvider amazonClientProvider,
                                NetflixAmazonCredentials account,
                                String region,
                                ObjectMapper objectMapper) {
    this.amazonClientProvider = amazonClientProvider
    this.account = account
    this.region = region
    this.objectMapper = objectMapper
  }

  @Override
  String getProviderName() {
    AwsInfrastructureProvider.PROVIDER_NAME
  }

  @Override
  String getAgentType() {
    "${account.name}/${region}/${AmazonCertificateCachingAgent.simpleName}"
  }

  @Override
  String getAccountName() {
    account.name
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    return types
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    log.info("Describing items in ${agentType}")
    def iam = amazonClientProvider.getAmazonIdentityManagement(account, region)

    // Get all the target groups
    List<ServerCertificateMetadata> iamCertificates = []
    ListServerCertificatesRequest listServerCertificatesRequest = new ListServerCertificatesRequest()
    while (true) {
      def resp = iam.listServerCertificates(listServerCertificatesRequest)
      iamCertificates.addAll(resp.serverCertificateMetadataList)
      if (resp.marker) {
        listServerCertificatesRequest.withMarker(resp.marker)
      } else {
        break
      }
    }

    List<CacheData> data = iamCertificates.collect { ServerCertificateMetadata cert ->
      Map<String, Object> attributes = objectMapper.convertValue(cert, AwsInfrastructureProvider.ATTRIBUTES)
      new DefaultCacheData(Keys.getCertificateKey(cert.serverCertificateId, region, account.name, "iam"),
        attributes,
        [:])
    }
    log.info("Caching ${data.size()} items in ${agentType}")
    new DefaultCacheResult([(CERTIFICATES.ns): data])
  }
}
