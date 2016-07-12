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

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.collect.Sets
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.clouddriver.openstack.OpenstackCloudProvider
import com.netflix.spinnaker.clouddriver.openstack.cache.Keys
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackImage
import com.netflix.spinnaker.clouddriver.openstack.provider.ImageProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.IMAGES

@Component
class OpenstackImageProvider implements ImageProvider {

  final Cache cacheView
  final ObjectMapper objectMapper

  @Autowired
  OpenstackImageProvider(final Cache cacheView, final ObjectMapper objectMapper) {
    this.cacheView = cacheView
    this.objectMapper = objectMapper
  }

  @Override
  Map<String, Set<OpenstackImage>> listImagesByAccount() {
    Map<String, Set<OpenstackImage>> result = [:].withDefault { _ -> Sets.newHashSet() }
    Collection<String> filter = cacheView.filterIdentifiers(IMAGES.ns, "$OpenstackCloudProvider.ID:*")

    cacheView.getAll(IMAGES.ns, filter).each { CacheData cacheData ->
      String account = Keys.parse(cacheData.id).account
      result[account] << objectMapper.convertValue(cacheData.attributes, OpenstackImage)
    }

    result
  }
}
