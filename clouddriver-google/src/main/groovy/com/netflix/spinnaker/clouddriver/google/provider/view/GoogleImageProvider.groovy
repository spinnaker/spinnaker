/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
 */

package com.netflix.spinnaker.clouddriver.google.provider.view

import com.google.api.services.compute.model.Image
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.clouddriver.google.GoogleCloudProvider
import com.netflix.spinnaker.clouddriver.google.cache.Keys
import com.netflix.spinnaker.clouddriver.google.controllers.GoogleNamedImageLookupController.ImageProvider
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.IMAGES

@Slf4j
@Component
class GoogleImageProvider implements ImageProvider {

  @Autowired
  Cache cacheView

  Map<String, List<Image>> listImagesByAccount() {
    def filter = cacheView.filterIdentifiers(IMAGES.ns, "$GoogleCloudProvider.GCE:*")
    def result = [:].withDefault { _ -> []}

    cacheView.getAll(IMAGES.ns, filter).each { CacheData cacheData ->
      def account = Keys.parse(cacheData.id).account
      result[account] << (cacheData.attributes.image as Image)
    }

    result
  }
}
