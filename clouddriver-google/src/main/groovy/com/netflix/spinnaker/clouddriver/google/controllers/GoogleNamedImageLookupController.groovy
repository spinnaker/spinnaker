/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.controllers

import com.google.api.services.compute.model.Image
import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.mem.InMemoryCache
import com.netflix.spinnaker.clouddriver.google.GoogleCloudProvider
import com.netflix.spinnaker.clouddriver.google.cache.Keys
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

import javax.servlet.http.HttpServletRequest

import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.IMAGES

@Slf4j
@RestController
@RequestMapping("/gce/images")
class GoogleNamedImageLookupController {

  private final Cache cacheView

  @Autowired
  GoogleNamedImageLookupController(Cache cacheView) {
    this.cacheView = cacheView
  }

  @RequestMapping(value = '/find', method = RequestMethod.GET)
  List<NamedImage> list(LookupOptions lookupOptions, HttpServletRequest request) {
    def imageMap = listImagesByAccount()
    def results = []

    if (lookupOptions.account) {
      def imageList = imageMap?.get(lookupOptions.account) ?: []

      results = imageList.collect {
        new NamedImage(imageName: it.name, attributes: [creationDate: it.creationTimestamp], tags: buildTagsMap(it))
      }
    } else {
      imageMap?.entrySet()?.each { Map.Entry<String, List<Image>> accountNameToImagesEntry ->
        accountNameToImagesEntry.value.each {
          results << new NamedImage(
            account: accountNameToImagesEntry.key,
            imageName: it.name,
            attributes: [creationDate: it.creationTimestamp],
            tags: buildTagsMap(it)
          )
        }
      }
      results.sort { a, b -> a.imageName <=> b.imageName }
    }

    def glob = lookupOptions.q?.trim() ?: "*"

    // Wrap in '*' if there are no glob-style characters in the query string.
    if (!glob.contains('*') && !glob.contains('?') && !glob.contains('[') && !glob.contains('\\')) {
      glob = "*${glob}*"
    }

    def pattern = new InMemoryCache.Glob(glob).toPattern()

    // Filter by query pattern.
    def matchingResults = results.findAll { pattern.matcher(it.imageName).matches() }

    // Further filter by tags.
    matchingResults = filter(matchingResults, extractTagFilters(request))

    return matchingResults
  }

  Map<String, List<Image>> listImagesByAccount() {
    def filter = cacheView.filterIdentifiers(IMAGES.ns, "$GoogleCloudProvider.ID:*")
    def result = [:].withDefault { _ -> []}

    cacheView.getAll(IMAGES.ns, filter).each { CacheData cacheData ->
      def account = Keys.parse(cacheData.id).account
      result[account] << (cacheData.attributes.image as Image)
    }

    return result
  }

  @VisibleForTesting
  static Map<String, String> buildTagsMap(Image image) {
    Map<String, String> tags = [:]
    List<String> descriptionTokens = image.description?.tokenize(",")

    descriptionTokens.each { String descriptionToken ->
      if (descriptionToken.contains(": ")) {
        def key = descriptionToken.split(": ")[0].trim()
        def value = descriptionToken.substring(key.length() + 2).trim()

        tags[key] = value
      }
    }

    if (image.labels) {
      tags += image.labels
    }

    return tags
  }

  /**
   * Apply tag-based filtering to the list of named images.
   *
   * For example: /gce/images/find?q=PackageName&tag:stage=released&tag:somekey=someval
   */
  private static List<NamedImage> filter(List<NamedImage> namedImages, Map<String, String> tagFilters) {
    if (!tagFilters) {
      return namedImages
    }

    return namedImages.findResults { NamedImage namedImage ->
      def matches = tagFilters.every {
        namedImage.tags[it.key.toLowerCase()]?.equalsIgnoreCase(it.value)
      }

      return matches ? namedImage : null
    }
  }

  private static Map<String, String> extractTagFilters(HttpServletRequest httpServletRequest) {
    return httpServletRequest.getParameterNames().findAll {
      it.toLowerCase().startsWith("tag:")
    }.collectEntries { String tagParameter ->
      [tagParameter.replaceAll("tag:", "").toLowerCase(), httpServletRequest.getParameter(tagParameter)]
    } as Map<String, String>
  }

  private static class NamedImage {
    String account
    String imageName
    Map<String, Object> attributes = [:]
    Map<String, String> tags = [:]
  }

  private static class LookupOptions {
    String q
    String account
    String region
  }

}
