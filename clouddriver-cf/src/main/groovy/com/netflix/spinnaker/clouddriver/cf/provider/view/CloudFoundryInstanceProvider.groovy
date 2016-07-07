/*
 * Copyright 2016 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cf.provider.view
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.clouddriver.cf.cache.CacheUtils
import com.netflix.spinnaker.clouddriver.cf.cache.Keys
import com.netflix.spinnaker.clouddriver.cf.model.CloudFoundryApplicationInstance
import com.netflix.spinnaker.clouddriver.cf.provider.ProviderUtils
import com.netflix.spinnaker.clouddriver.cf.security.CloudFoundryAccountCredentials
import com.netflix.spinnaker.clouddriver.model.InstanceProvider
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.cf.cache.Keys.Namespace.INSTANCES
import static com.netflix.spinnaker.clouddriver.cf.cache.Keys.Namespace.SERVER_GROUPS

@Component
@CompileStatic
class CloudFoundryInstanceProvider implements InstanceProvider<CloudFoundryApplicationInstance> {

  String platform = 'cf'

  private final Cache cacheView

  private final AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  CloudFoundryInstanceProvider(Cache cacheView, AccountCredentialsProvider accountCredentialsProvider) {
    this.cacheView = cacheView
    this.accountCredentialsProvider = accountCredentialsProvider
  }

  @Override
  CloudFoundryApplicationInstance getInstance(String account, String region, String id) {
    CacheData instanceEntry = cacheView.get(INSTANCES.ns, Keys.getInstanceKey(id, account, region))
    if (!instanceEntry) {
      return null
    }

    CacheData serverGroup = ProviderUtils.resolveRelationshipData(cacheView, instanceEntry, SERVER_GROUPS.ns)[0]
    CacheUtils.translateInstance(instanceEntry, serverGroup)
  }

  @Override
  String getConsoleOutput(String account, String region, String id) {
    def accountCredentials = accountCredentialsProvider.getCredentials(account)

    if (!(accountCredentials instanceof CloudFoundryAccountCredentials)) {
      throw new IllegalArgumentException("Invalid credentials: ${account}:${region}")
    }

    // TODO: Figure out how to talk to loggregator?

    return null
  }
}
