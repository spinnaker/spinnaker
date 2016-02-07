/*
 * Copyright 2014 Pivotal Inc.
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

package com.netflix.spinnaker.clouddriver.cf.model

import com.netflix.spinnaker.clouddriver.cache.OnDemandCacheUpdater
import com.netflix.spinnaker.clouddriver.cf.CloudFoundryCloudProvider
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.apache.log4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class CloudFoundryOnDemandCacheUpdater implements OnDemandCacheUpdater {
  protected final Logger log = Logger.getLogger(CloudFoundryOnDemandCacheUpdater.class)

  @Deprecated
  private static final String LEGACY_TYPE = "CloudFoundryServerGroup"

  private static final String TYPE = "ServerGroup"

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  CloudFoundryResourceRetriever cloudFoundryResourceRetriever

  @Autowired
  CloudFoundryCloudProvider cloudFoundryCloudProvider

  @Override
  boolean handles(String type) {
    type == LEGACY_TYPE
  }

  @Override
  OnDemandCacheUpdater.OnDemandCacheStatus handle(String type, Map<String, ? extends Object> data) {
    cloudFoundryResourceRetriever.handleCacheUpdate(data)
    return OnDemandCacheUpdater.OnDemandCacheStatus.SUCCESSFUL
  }

  @Override
  boolean handles(String type, String cloudProvider) {
    type == TYPE && cloudProvider == cloudFoundryCloudProvider.id
  }

  @Override
  OnDemandCacheUpdater.OnDemandCacheStatus handle(String type, String cloudProvider, Map<String, ? extends Object> data) {
    handle(type, data)
    return OnDemandCacheUpdater.OnDemandCacheStatus.SUCCESSFUL
  }

  @Override
  Collection<Map> pendingOnDemandRequests(String type, String cloudProvider) {
    return []
  }
}
