/*
 * Copyright 2017 Cerner Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 *
 */

package com.netflix.spinnaker.clouddriver.dcos.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.clouddriver.dcos.DcosCloudProvider
import com.netflix.spinnaker.clouddriver.dcos.cache.Keys
import com.netflix.spinnaker.clouddriver.dcos.model.DcosInstance
import com.netflix.spinnaker.clouddriver.model.InstanceProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class DcosInstanceProvider implements InstanceProvider<DcosInstance, String> {
  private final Cache cacheView
  private final ObjectMapper objectMapper

  @Autowired
  DcosInstanceProvider(Cache cacheView, ObjectMapper objectMapper) {
    this.cacheView = cacheView
    this.objectMapper = objectMapper
  }

  final String cloudProvider = DcosCloudProvider.ID

  @Override
  DcosInstance getInstance(String account, String region, String name) {
    CacheData instanceData = cacheView.get(Keys.Namespace.INSTANCES.ns, Keys.getInstanceKey(account, region, name))
    if (!instanceData) {
      return null
    }

    return objectMapper.convertValue(instanceData.attributes.instance, DcosInstance)
  }

  @Override
  String getConsoleOutput(String account, String region, String id) {
    return null
  }
}
