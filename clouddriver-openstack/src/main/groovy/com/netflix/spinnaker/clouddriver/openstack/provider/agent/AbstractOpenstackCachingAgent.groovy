/*
 * Copyright 2016 Target Inc.
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

package com.netflix.spinnaker.clouddriver.openstack.provider.agent

import com.netflix.spinnaker.cats.agent.AccountAware
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientProvider
import com.netflix.spinnaker.clouddriver.openstack.provider.OpenstackInfastructureProvider
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials

/**
 * Base agent that implements common logic for all agents.
 */
abstract class AbstractOpenstackCachingAgent implements CachingAgent, AccountAware {
  final OpenstackNamedAccountCredentials account
  final String region

  AbstractOpenstackCachingAgent(OpenstackNamedAccountCredentials account, String region) {
    this.account = account
    this.region = region
  }

  @Override
  String getAccountName() {
    account.name
  }

  @Override
  String getProviderName() {
    OpenstackInfastructureProvider.PROVIDER_NAME
  }

  OpenstackClientProvider getClientProvider() {
    account?.credentials?.provider
  }
}
