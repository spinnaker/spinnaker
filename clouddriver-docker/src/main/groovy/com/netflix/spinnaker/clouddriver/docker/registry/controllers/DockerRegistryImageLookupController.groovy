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
import org.springframework.security.access.prepost.PostFilter
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(["/dockerRegistry/images", "/titus/images"])
class DockerRegistryImageLookupController {
  @Autowired
  private Cache cacheView

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @RequestMapping(value = "/tags", method = RequestMethod.GET)
  @PreAuthorize("hasPermission(#account, 'ACCOUNT', 'READ')")
  List<String> getTags(@RequestParam('account') String account, @RequestParam('repository') String repository) {
    def credentials = (DockerRegistryNamedAccountCredentials) accountCredentialsProvider.getCredentials(account)
    if (!credentials) {
      return []
    }

    return DockerRegistryProviderUtils.getAllMatchingKeyPattern(
      cacheView,
      Keys.Namespace.TAGGED_IMAGE.ns,
      Keys.getTaggedImageKey(account, repository, "*")
    ).sort { a, b ->
      if (credentials.sortTagsByDate) {
        b.attributes.date.epochSecond <=> a.attributes.date.epochSecond
      } else {
        a.id <=> b.id
      }
    }.collect {
      def parse = Keys.parse(it.id)
      return (String) parse.tag
    }
  }

  @RequestMapping(value = '/find', method = RequestMethod.GET)
  @PostFilter("hasPermission(filterObject['account'], 'ACCOUNT', 'READ')")
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
        def parse = Keys.parse(it.getId())
        def repo  = (String) parse.repository
        def tag   = (String) parse.tag

        // if the request asks for specific repositories or tags,
        // do the filtering accordingly
        if (lookupOptions.repository && !lookupOptions.repository.equals(repo) ||
            lookupOptions.repository && lookupOptions.tag && !lookupOptions.tag.equals(tag)) {
          return null
        }

        if (lookupOptions.includeDetails) {
          return [
            repository : repo,
            tag        : tag,
            account    : it.attributes.account,
            registry   : credentials.getRegistry(),
            digest     : it.attributes.digest,
            commitId   : it.attributes.labels?.commitId,
            buildNumber: it.attributes.labels?.buildNumber,
            branch     : it.attributes.labels?.branch,
            artifact   : generateArtifact(credentials.getRegistry(), parse.repository, parse.tag, it.attributes.labels)
          ]
        }

        return [
          repository: repo,
          tag       : tag,
          account   : it.attributes.account,
          registry  : credentials.getRegistry(),
          digest    : it.attributes.digest,
          artifact  : generateArtifact(credentials.getRegistry(), parse.repository, parse.tag)
        ]

      }
    }
  }

  Map generateArtifact( String registry,def repository, def tag) {
    generateArtifact( registry, repository, tag, new HashMap());
  }

  Map generateArtifact( String registry,def repository, def tag, def labels) {
    String reference = "${registry}/${repository}:${tag}"
    [
      name      : repository,
      type      : "docker",
      version   : tag,
      reference : reference,
      metadata  : [ registry: registry, labels: labels ]
    ]
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
          artifact  : generateArtifact(credentials.registry, parse.repository, parse.tag)
        ]
      }
    }
  }

  private boolean isTrackDigestsDisabled() {
    return accountCredentialsProvider.all
      .findAll {
        it.getCloudProvider() == DockerRegistryCloudProvider.DOCKER_REGISTRY
      }
      .every {
        !((DockerRegistryNamedAccountCredentials) it).getTrackDigests()
      }
  }

  private static class LookupOptions {
    String q
    String account
    String region
    String repository
    String tag
    Integer count
    Boolean includeDetails
  }
}
