/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.controllers

import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.aws.data.Keys
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

import jakarta.servlet.http.HttpServletRequest

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.IMAGES
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.NAMED_IMAGES

@Slf4j
@RestController
@RequestMapping("/aws/images")
class AmazonNamedImageLookupController {
  // TODO-AJ This will be replaced with an appropriate v2 API allowing a user-supplied limit and indication of totalNumberOfResults vs. returnedResults
  private static final int MAX_SEARCH_RESULTS = 1000

  private static final int MIN_NAME_FILTER = 3
  private static final String EXCEPTION_REASON = 'Minimum of ' + MIN_NAME_FILTER + ' characters required to filter namedImages'

  private static final AMI_GLOB_PATTERN = /^ami-([a-f0-9]{8}|[a-f0-9]{17})$/

  private final Cache cacheView

  @Autowired
  AmazonNamedImageLookupController(Cache cacheView) {
    this.cacheView = cacheView
  }

  @RequestMapping(value = '/{account}/{region}/{imageId:.+}', method = RequestMethod.GET)
  List<NamedImage> getByAmiId(@PathVariable String account, @PathVariable String region, @PathVariable String imageId) {
    CacheData cd = cacheView.get(IMAGES.ns, Keys.getImageKey(imageId, account, region))
    if (cd == null) {
      throw new NotFoundException("${imageId} not found in ${account}/${region}")
    }
    Collection<String> namedImageKeys = cd.relationships[NAMED_IMAGES.ns]
    if (!namedImageKeys) {
      throw new NotFoundException("Name not found on image ${imageId} in ${account}/${region}")
    }

    render(null, [cd], null, region)
  }

  @RequestMapping(value = '/find', method = RequestMethod.GET)
  List<NamedImage> list(LookupOptions lookupOptions, HttpServletRequest request) {
    validateLookupOptions(lookupOptions)
    String glob = lookupOptions.q?.trim()
    def isAmi = glob ==~ AMI_GLOB_PATTERN

    // Wrap in '*' if there are no glob-style characters in the query string
    if (!isAmi && !glob.contains('*') && !glob.contains('?') && !glob.contains('[') && !glob.contains('\\')) {
      glob = "*${glob}*"
    }

    def namedImageSearch = Keys.getNamedImageKey(lookupOptions.account ?: '*', glob)
    def imageSearch = Keys.getImageKey(glob, lookupOptions.account ?: '*', lookupOptions.region ?: '*')

    Collection<String> namedImageIdentifiers = !isAmi ? cacheView.filterIdentifiers(NAMED_IMAGES.ns, namedImageSearch) : []
    Collection<String> imageIdentifiers = namedImageIdentifiers.isEmpty() ? cacheView.filterIdentifiers(IMAGES.ns, imageSearch) : []

    Collection<CacheData> matchesByName = cacheView.getAll(NAMED_IMAGES.ns, namedImageIdentifiers, RelationshipCacheFilter.include(IMAGES.ns))

    Collection<CacheData> matchesByImageId = cacheView.getAll(IMAGES.ns, imageIdentifiers)

    List<NamedImage> allFilteredImages = filter(
      render(matchesByName, matchesByImageId, lookupOptions.q, lookupOptions.region),
      extractTagFilters(request)
    )

    return allFilteredImages.subList(0, Math.min(MAX_SEARCH_RESULTS, allFilteredImages.size()))
  }

  private List<NamedImage> render(Collection<CacheData> namedImages, Collection<CacheData> images, String requestedName = null, String requiredRegion = null) {
    Map<String, NamedImage> byImageName = [:].withDefault { new NamedImage(imageName: it) }

    cacheView.getAll(IMAGES.ns, namedImages.collect {
      (it.relationships[IMAGES.ns] ?: []).collect {
        it
      }
    }.flatten() as Collection<String>).each {
      // associate tags with their AMI's image id
      byImageName[it.attributes.name as String].tagsByImageId[it.attributes.imageId as String] =
        ((it.attributes.tags as List)?.collectEntries { [it.key.toLowerCase(), it.value] })
    }

    for (CacheData data : namedImages) {
      Map<String, String> keyParts = Keys.parse(data.id)
      NamedImage thisImage = byImageName[keyParts.imageName]
      thisImage.attributes.putAll(data.attributes - [name: keyParts.imageName])
      thisImage.accounts.add(keyParts.account)

      for (String imageKey : data.relationships[IMAGES.ns] ?: []) {
        Map<String, String> imageParts = Keys.parse(imageKey)
        thisImage.amis[imageParts.region].add(imageParts.imageId)
      }
    }

    for (CacheData data : images) {
      Map<String, String> amiKeyParts = Keys.parse(data.id)
      Map<String, String> namedImageKeyParts = Keys.parse(data.relationships[NAMED_IMAGES.ns][0])
      NamedImage thisImage = byImageName[namedImageKeyParts.imageName]
      thisImage.attributes.virtualizationType = data.attributes.virtualizationType
      thisImage.attributes.architecture = data.attributes.architecture
      thisImage.attributes.creationDate = data.attributes.creationDate
      thisImage.accounts.add(namedImageKeyParts.account)
      thisImage.amis[amiKeyParts.region].add(amiKeyParts.imageId)
      thisImage.tags.putAll((data.attributes.tags as List)?.collectEntries { [it.key.toLowerCase(), it.value] })
      thisImage.tagsByImageId[data.attributes.imageId as String] = thisImage.tags
    }

    List<NamedImage> results = byImageName.values().findAll { requiredRegion ? it.amis.containsKey(requiredRegion) : true }
    results.sort { a, b ->
      int a1, b1
      if (requestedName) {
        a1 = a.imageName.indexOf(requestedName)
        b1 = b.imageName.indexOf(requestedName)
      } else {
        a1 = 0
        b1 = 0
      }

      if (a1 == b1) {
        a.imageName <=> b.imageName
      } else {
        a1 <=> b1
      }
    }
  }

  /**
   * Apply tag-based filtering to the list of named images.
   *
   * ie. /aws/images/find?q=PackageName&tag:engine=spinnaker&tag:stage=released
   */
  private static List<NamedImage> filter(List<NamedImage> namedImages, Map<String, String> tagFilters) {
    if (!tagFilters) {
      return namedImages
    }

    return namedImages.findResults { NamedImage namedImage ->
      def invalidImageIds = []
      namedImage.tagsByImageId.each { String imageId, Map<String, String> tags ->
        def matches = tagFilters.every {
          tags[it.key.toLowerCase()]?.equalsIgnoreCase(it.value)
        }
        if (!matches) {
          invalidImageIds << imageId
        }
      }

      invalidImageIds.each { String imageId ->
        // remove all traces of any images that did not pass the filter criteria
        namedImage.amis.each { String region, Collection<String> imageIds ->
          imageIds.removeAll(imageId)
        }
        namedImage.amis = namedImage.amis.findAll { !it.value.isEmpty() }
        namedImage.tagsByImageId.remove(imageId)
      }

      return (!namedImage.tagsByImageId || namedImage.amis.values().flatten().isEmpty()) ? null : namedImage
    }
  }

  void validateLookupOptions(LookupOptions lookupOptions) {
    if (lookupOptions.q == null || lookupOptions.q.length() < MIN_NAME_FILTER) {
      throw new InvalidRequestException(EXCEPTION_REASON)
    }

    String glob = lookupOptions.q?.trim()
    def isAmi = glob ==~ AMI_GLOB_PATTERN
    if (glob == "ami" || (!isAmi && glob.startsWith("ami-"))) {
      throw new InvalidRequestException("Searches by AMI id must be an exact match (ami-xxxxxxxx)")
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
    String imageName
    Map<String, Object> attributes = [:]
    Map<String, Map<String, String>> tagsByImageId = [:]
    Set<String> accounts = []
    Map<String, Collection<String>> amis = [:].withDefault { new HashSet<String>() }

    @Deprecated
    Map<String, String> tags = [:]
  }

  static class LookupOptions {
    String q
    String account
    String region
  }
}
