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

package com.netflix.spinnaker.clouddriver.docker.registry.controllers

import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.clouddriver.docker.registry.DockerRegistryCloudProvider
import com.netflix.spinnaker.clouddriver.docker.registry.cache.Keys
import com.netflix.spinnaker.clouddriver.docker.registry.provider.DockerRegistryProviderUtils
import com.netflix.spinnaker.clouddriver.docker.registry.security.DockerRegistryNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/dockerRegistry/images")
class DockerRegistryImageLookupController {
  @Autowired
  private final Cache cacheView

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @RequestMapping(value = "/tags", method = RequestMethod.GET)
  List<String> getTags(@RequestParam('account') String account, @RequestParam('repository') String repository) {
    def credentials = (DockerRegistryNamedAccountCredentials) accountCredentialsProvider.getCredentials(account)
    credentials?.getTags(repository)
  }

  @RequestMapping(value = '/find', method = RequestMethod.GET)
  List<Map> find(LookupOptions lookupOptions) {
    def account = lookupOptions.account ?: ""

    Set<CacheData> images
    if (lookupOptions.q) {
      def key = Keys.getImageIdKey("*${lookupOptions.q}*")
      def keyData = DockerRegistryProviderUtils.getAllMatchingKeyPattern(cacheView, Keys.Namespace.IMAGE_ID.ns, key)

      if (account) {
        keyData = keyData.findAll { CacheData data ->
          data.attributes.account == account
        }
      }

      def keys = keyData.collect { CacheData data ->
        data.attributes.tagKey
      } as Collection<String>

      images = DockerRegistryProviderUtils.loadResults(cacheView, Keys.Namespace.TAGGED_IMAGE.ns, keys)
    } else {
      def image = '*'
      def tag = '*'
      account = account ?: '*'

      def key = Keys.getTaggedImageKey(account, image, tag)

      // without trackDigests, all information is available in the image keys, so don't bother fetching attributes
      if (trackDigestsDisabled) {
        return listAllImagesWithoutDigests(key, lookupOptions)
      }

      images = DockerRegistryProviderUtils.getAllMatchingKeyPattern(cacheView, Keys.Namespace.TAGGED_IMAGE.ns, key)
    }

    if (lookupOptions.count) {
      images = images.take(lookupOptions.count)
    }

    return images.findResults {
      def credentials = (DockerRegistryNamedAccountCredentials) accountCredentialsProvider.getCredentials((String) it.attributes.account)
      if (!credentials) {
        return null
      } else {
        def parse = Keys.parse(it.id)
        return [
          repository: (String) parse.repository,
          tag       : (String) parse.tag,
          account   : it.attributes.account,
          registry  : credentials.registry,
          digest    : it.attributes.digest,
        ]
      }
    }
  }

  private List<Map> listAllImagesWithoutDigests(String key, LookupOptions lookupOptions) {
    def images = cacheView.filterIdentifiers(Keys.Namespace.TAGGED_IMAGE.ns, key)
    if (lookupOptions.count) {
      images = images.take(lookupOptions.count)
    }
    return images.findResults {
      def parse = Keys.parse(it)
      if (!parse) {
        return null
      }
      def credentials = (DockerRegistryNamedAccountCredentials) accountCredentialsProvider.getCredentials((String) parse.account)
      if (!credentials) {
        return null
      } else {
        return [
          repository: (String) parse.repository,
          tag       : (String) parse.tag,
          account   : (String) parse.account,
          registry  : credentials.registry,
        ]
      }
    }
  }

  private boolean isTrackDigestsDisabled() {
    return accountCredentialsProvider.all
      .findAll { it.cloudProvider == DockerRegistryCloudProvider.DOCKER_REGISTRY }
      .every { !((DockerRegistryNamedAccountCredentials) it).trackDigests }
  }

  private static class LookupOptions {
    String q
    String account
    String region
    Integer count
  }
}
