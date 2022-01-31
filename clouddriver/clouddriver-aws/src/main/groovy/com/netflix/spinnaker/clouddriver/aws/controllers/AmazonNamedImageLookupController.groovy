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

import javax.servlet.http.HttpServletRequest

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

    render(null, [cd], null, null, region)
  }

  @RequestMapping(value = '/find', method = RequestMethod.GET)
  List<NamedImage> list(LookupOptions lookupOptions, HttpServletRequest request) {
    validateLookupOptions(lookupOptions)
    String glob = lookupOptions.q?.trim()
    boolean isAmi = glob ==~ AMI_GLOB_PATTERN

    // Wrap in '*' if there are no glob-style characters in the query string
    if (!isAmi && !glob.contains('*') && !glob.contains('?') && !glob.contains('[') && !glob.contains('\\')) {
      glob = "*${glob}*"
    }

    String namedImageSearch = Keys.getNamedImageKey(lookupOptions.account ?: '*', glob)
    String imageSearch = Keys.getImageKey(glob, lookupOptions.account ?: '*', lookupOptions.region ?: '*')

    Collection<String> namedImageIdentifiers = !isAmi ? cacheView.filterIdentifiers(NAMED_IMAGES.ns, namedImageSearch) : []
    Collection<String> imageIdentifiers = namedImageIdentifiers.isEmpty() ? cacheView.filterIdentifiers(IMAGES.ns, imageSearch) : []

    Collection<CacheData> matchesByName = cacheView.getAll(NAMED_IMAGES.ns, namedImageIdentifiers, RelationshipCacheFilter.include(IMAGES.ns))
    Collection<CacheData> matchesByImageId = cacheView.getAll(IMAGES.ns, imageIdentifiers)

    Map<String, String> tagFilters = extractTagFilters(request)

    List<NamedImage> allFilteredImages = filter(
      render(matchesByName, matchesByImageId, tagFilters, lookupOptions.q, lookupOptions.region),
      tagFilters
    )

    return allFilteredImages.subList(0, Math.min(MAX_SEARCH_RESULTS, allFilteredImages.size()))
  }

  /**
   * For the passed in set of tags verifies that all searched for tags are present.
   *
   * @param existingTagsForImageId Set of tags to check.
   * @param tagFilters Set of tags by which to filter.
   * @return true if all tags in tagFilters are present or tagFilters is empty. Otherwise false
   */
  private static boolean checkTagsForImageId(Map<String, String> existingTagsForImageId, final Map<String, String> tagFilters) {
    if (!tagFilters) {
      return true
    }

    return tagFilters.every {
      existingTagsForImageId[it.key.toLowerCase()]?.equalsIgnoreCase(it.value)
    }
  }

  private List<NamedImage> render(
    Collection<CacheData> namedImages,
    Collection<CacheData> images,
    Map<String, String> tagFilters,
    String requestedName,
    String requiredRegion
  ) {
    // Map of AMI Image Name to AMI metadata.
    final Map<String, NamedImage> byImageName = [:].withDefault { String it -> new NamedImage(imageName: it) }

    // Generate list of AMI IDs based on the passed in collection of AMIs found by name.
    final List<String> identifiers = namedImages.collect {
      (it.relationships[IMAGES.ns] ?: []).collect { it }
    }.flatten() as List<String>

    // Populate the tags for each NamedImage. Metadata from the IDs search is the source of truth for tags metadata.
    cacheView.getAll(IMAGES.ns, identifiers).each { CacheData cacheData ->
      final Map<String, Object> newTagsForImageId =
        (cacheData.attributes.tags as List<Map.Entry<String, Object>>)?.collectEntries {
          [it.key.toLowerCase(), it.value]
        }

      final String amiId = cacheData.attributes.imageId;
      final String amiName = cacheData.attributes.name;
      final NamedImage myNamedImage = byImageName[amiName]
      final Map<String, String> existingTagsForImageId = myNamedImage.tagsByImageId[amiId]

      if(!existingTagsForImageId) {
        // No tags set yet for this AMI ID so use the new tags.
        // NOTE: Check against tagFilters is done only if more than one set of tags present.
        myNamedImage.tagsByImageId[amiId] = newTagsForImageId
      } else {
        // Existing tags. If tag filtering is enabled, handle collisions to preserve relevant tags.
        // If more than one set of matching tags, then first to set wins.
        // If tag filtering is not enabled, then leave existing tags.
        if (tagFilters) {
          // If the existing tags are not a match but the new tags are, then use them.
          // Else, either existing tags are a match or new tags are not a match. Keep existing tags.
          if(!checkTagsForImageId(existingTagsForImageId, tagFilters) && checkTagsForImageId(newTagsForImageId, tagFilters)) {
            myNamedImage.tagsByImageId[amiId] = newTagsForImageId
          }
        }
      }
    }

    // Populate metadata for AMI NAME drive searches.
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

    // Populate metadata for AMI ID driven searches.
    for (CacheData data : images) {
      Map<String, String> amiKeyParts = Keys.parse(data.id)
      Map<String, String> namedImageKeyParts = Keys.parse(data.relationships[NAMED_IMAGES.ns][0])
      NamedImage thisImage = byImageName[namedImageKeyParts.imageName]
      thisImage.attributes.virtualizationType = data.attributes.virtualizationType
      thisImage.attributes.architecture = data.attributes.architecture
      thisImage.attributes.creationDate = data.attributes.creationDate
      thisImage.accounts.add(namedImageKeyParts.account)
      thisImage.amis[amiKeyParts.region].add(amiKeyParts.imageId)
      // Deprecated tags field is only set if the query is for an AMI ID.
      // For this case, tags and tagsByImageId should be one to one.
      thisImage.tags.putAll((data.attributes.tags as List<Map.Entry<String, Object>>)?.collectEntries { [it.key.toLowerCase(), it.value] })
      thisImage.tagsByImageId[data.attributes.imageId as String] = thisImage.tags
    }

    // NOTE: Ensure a list is explicitly used to enable in place sorting below.
    final List<NamedImage> results
    if(requiredRegion != null && !requiredRegion.isEmpty()) {
      results = new ArrayList<>(byImageName.values().findAll { it.amis.containsKey(requiredRegion) })
    } else {
      results = new ArrayList<>(byImageName.values())
    }

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

    return results
  }

  /**
   * Apply tag-based filtering to the list of named images.
   * <p>
   * ie. /aws/images/find?q=PackageName&tag:engine=spinnaker&tag:stage=released
   */
  private static List<NamedImage> filter(List<NamedImage> namedImages, Map<String, String> tagFilters) {
    if (!tagFilters) {
      return namedImages
    }

    return namedImages.findResults { NamedImage namedImage ->
      List<String> invalidImageIds = []

      // Iterate over the tags for each AMI and ensure that all tag filters are present.
      namedImage.tagsByImageId.each { String imageId, Map<String, String> tags ->
        boolean matches = checkTagsForImageId(tags, tagFilters)

        if (!matches) {
          invalidImageIds << imageId
        }
      }

      // If not all tag filters are present for the AMI then purge it from the response.
      invalidImageIds.each { String imageId ->
        // Remove all traces of any images that did not pass the filter criteria.
        namedImage.amis.each { String region, Collection<String> imageIds ->
          imageIds.removeAll(imageId)
        }
        namedImage.amis = namedImage.amis.findAll { !it.value.isEmpty() }
        namedImage.tagsByImageId.remove(imageId)
      }

      // Generate null if the outer 'namedImages.findResults{}' should filter this namedImage.
      return (!namedImage.tagsByImageId || namedImage.amis.values().flatten().isEmpty()) ? null : namedImage
    }
  }

  void validateLookupOptions(LookupOptions lookupOptions) {
    if (lookupOptions.q == null || lookupOptions.q.length() < MIN_NAME_FILTER) {
      throw new InvalidRequestException(EXCEPTION_REASON)
    }

    String glob = lookupOptions.q?.trim()
    boolean isAmi = glob ==~ AMI_GLOB_PATTERN
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
