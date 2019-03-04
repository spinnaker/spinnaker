/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.provider.agent

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.services.compute.Compute
import com.google.common.annotations.VisibleForTesting
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.AccountAware
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.clouddriver.google.GoogleCloudProvider
import com.netflix.spinnaker.clouddriver.google.GoogleExecutorTraits
import com.netflix.spinnaker.clouddriver.google.model.GoogleLabeledResource
import com.netflix.spinnaker.clouddriver.google.names.GoogleLabeledResourceNamer
import com.netflix.spinnaker.clouddriver.google.provider.GoogleInfrastructureProvider
import com.netflix.spinnaker.clouddriver.google.batch.GoogleBatchRequest
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.names.NamerRegistry
import com.netflix.spinnaker.moniker.Namer

abstract class AbstractGoogleCachingAgent implements CachingAgent, AccountAware, GoogleExecutorTraits {

  final TypeReference<Map<String, Object>> ATTRIBUTES = new TypeReference<Map<String, Object>>() {}

  final String providerName = GoogleInfrastructureProvider.name

  final Namer<GoogleLabeledResource> naming

  String clouddriverUserAgentApplicationName // "Spinnaker/${version}" HTTP header string
  GoogleNamedAccountCredentials credentials
  ObjectMapper objectMapper
  Registry registry

  @VisibleForTesting
  AbstractGoogleCachingAgent() {
    this.naming = new GoogleLabeledResourceNamer()
  }

  AbstractGoogleCachingAgent(String clouddriverUserAgentApplicationName,
                             GoogleNamedAccountCredentials credentials,
                             ObjectMapper objectMapper,
                             Registry registry) {
    this.clouddriverUserAgentApplicationName = clouddriverUserAgentApplicationName
    this.credentials = credentials
    this.objectMapper = objectMapper
    this.registry = registry
    this.naming = NamerRegistry.lookup()
      .withProvider(GoogleCloudProvider.ID)
      .withAccount(credentials.name)
      .withResource(GoogleLabeledResource)
  }

  String getProject() {
    credentials?.project
  }

  String getXpnHostProject() {
    credentials?.xpnHostProject
  }

  Compute getCompute() {
    credentials?.compute
  }

  String getAccountName() {
    credentials?.name
  }

  GoogleBatchRequest buildGoogleBatchRequest() {
    return new GoogleBatchRequest(compute, clouddriverUserAgentApplicationName)
  }

  def executeIfRequestsAreQueued(GoogleBatchRequest googleBatchRequest, String instrumentationContext) {
    if (googleBatchRequest.size()) {
      timeExecuteBatch(googleBatchRequest, instrumentationContext)
    }
  }
}
