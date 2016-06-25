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

package com.netflix.spinnaker.clouddriver.openstack.provider.view

import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.clouddriver.model.InstanceProvider
import com.netflix.spinnaker.clouddriver.openstack.OpenstackCloudProvider
import com.netflix.spinnaker.clouddriver.openstack.cache.Keys
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackInstance
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.INSTANCES

@Component
@Slf4j
class OpenstackInstanceProvider implements InstanceProvider<OpenstackInstance> {

  private final Cache cacheView
  private final AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  OpenstackInstanceProvider(Cache cacheView, AccountCredentialsProvider accountCredentialsProvider) {
    this.cacheView = cacheView
    this.accountCredentialsProvider = accountCredentialsProvider
  }

  @Override
  String getPlatform() {
    OpenstackCloudProvider.ID
  }

  @Override
  OpenstackInstance getInstance(String account, String region, String id) {
    OpenstackInstance result

    CacheData instanceEntry = cacheView.get(INSTANCES.ns, Keys.getInstanceKey(id, account, region))
    if (instanceEntry) {
      result = new OpenstackInstance(name: instanceEntry.attributes.name
        , region: instanceEntry.attributes.region
        , zone: instanceEntry.attributes.zone
        , instanceId: instanceEntry.attributes.instanceId
        , launchTime: instanceEntry.attributes.launchedTime
        , metadata: instanceEntry.attributes.metadata
        , status: instanceEntry.attributes.status
        , keyName: instanceEntry.attributes.keyName)
    }
    result
  }

  @Override
  String getConsoleOutput(String account, String region, String id) {
    String result
    OpenstackNamedAccountCredentials namedAccountCredentials = this.accountCredentialsProvider.getCredentials(account)
    if (!namedAccountCredentials) {
      throw new IllegalArgumentException("Invalid credentials: ${account}:${region}")
    } else {
      result = namedAccountCredentials.credentials.provider.getConsoleOutput(region, id)
    }
    result
  }
}
