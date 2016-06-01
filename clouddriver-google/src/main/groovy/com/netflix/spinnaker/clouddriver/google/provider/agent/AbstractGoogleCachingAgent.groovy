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
import com.google.api.client.googleapis.batch.BatchRequest
import com.google.api.client.http.HttpRequest
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.services.compute.Compute
import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.cats.agent.AccountAware
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.clouddriver.google.provider.GoogleInfrastructureProvider
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials

abstract class AbstractGoogleCachingAgent implements CachingAgent, AccountAware {

  final TypeReference<Map<String, Object>> ATTRIBUTES = new TypeReference<Map<String, Object>>() {}

  final String providerName = GoogleInfrastructureProvider.name

  String googleApplicationName // "Spinnaker/${version}" HTTP header string
  GoogleNamedAccountCredentials credentials
  ObjectMapper objectMapper

  @VisibleForTesting
  AbstractGoogleCachingAgent() {}

  AbstractGoogleCachingAgent(String googleApplicationName,
                             GoogleNamedAccountCredentials credentials,
                             ObjectMapper objectMapper) {
    this.googleApplicationName = googleApplicationName
    this.credentials = credentials
    this.objectMapper = objectMapper
  }

  String getProject() {
    credentials?.project
  }

  Compute getCompute() {
    credentials?.compute
  }

  String getAccountName() {
    credentials?.name
  }

  def executeIfRequestsAreQueued(BatchRequest batch) {
    if (batch.size()) {
      batch.execute()
    }
  }

  BatchRequest buildBatchRequest() {
    return compute.batch(
        new HttpRequestInitializer() {
          @Override
          void initialize(HttpRequest request) throws IOException {
            request.headers.setUserAgent(googleApplicationName);
          }
        }
    )
  }
}
