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

package com.netflix.spinnaker.clouddriver.appengine.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.agent.AccountAware
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.clouddriver.appengine.AppEngineCloudProvider
import com.netflix.spinnaker.clouddriver.appengine.provider.AppEngineProvider
import com.netflix.spinnaker.clouddriver.appengine.security.AppEngineNamedAccountCredentials

abstract class AbstractAppEngineCachingAgent implements CachingAgent, AccountAware {
  final String accountName
  final String providerName = AppEngineProvider.PROVIDER_NAME
  final AppEngineCloudProvider appEngineCloudProvider = new AppEngineCloudProvider()
  final ObjectMapper objectMapper
  final AppEngineNamedAccountCredentials credentials

  AbstractAppEngineCachingAgent(String accountName,
                                ObjectMapper objectMapper,
                                AppEngineNamedAccountCredentials credentials) {
    this.accountName = accountName
    this.objectMapper = objectMapper
    this.credentials = credentials
  }

  abstract String getSimpleName()
}
