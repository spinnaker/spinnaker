/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.elasticsearch.aws

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.cats.agent.AgentProvider
import com.netflix.spinnaker.clouddriver.aws.provider.AwsProvider
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.elasticsearch.ElasticSearchClient
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.kork.core.RetrySupport
import io.searchbox.client.JestClient

open class ElasticSearchAmazonCachingAgentProvider(
  private val objectMapper: ObjectMapper,
  private val jestClient: JestClient,
  private val retrySupport: RetrySupport,
  private val registry: Registry,
  private val amazonClientProvider: AmazonClientProvider,
  private val accountCredentialsProvider: AccountCredentialsProvider
) : AgentProvider {

  override fun supports(providerName: String): Boolean {
    return providerName.equals(AwsProvider.PROVIDER_NAME, ignoreCase = true)
  }

  override fun agents(): Collection<Agent> {
    val credentials = accountCredentialsProvider
      .all
      .filter { NetflixAmazonCredentials::class.java.isInstance(it) }
      .map { c -> c as NetflixAmazonCredentials }

    val elasticSearchClient = ElasticSearchClient(
      objectMapper.copy().enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS),
      jestClient
    )

    return listOf(
      ElasticSearchAmazonServerGroupCachingAgent(
        retrySupport,
        registry,
        amazonClientProvider,
        credentials,
        elasticSearchClient
      ),
      ElasticSearchAmazonInstanceCachingAgent(
        retrySupport,
        registry,
        amazonClientProvider,
        credentials,
        elasticSearchClient
      )
    )
  }
}
