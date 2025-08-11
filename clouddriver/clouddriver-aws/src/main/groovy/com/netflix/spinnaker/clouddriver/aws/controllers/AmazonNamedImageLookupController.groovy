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

import com.google.common.collect.ImmutableMap
import com.google.common.collect.Iterables
import com.google.common.collect.Sets
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.aws.data.Keys
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import groovy.transform.Immutable
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

    List<NamedImage> allFilteredImages = render(matchesByName, matchesByImageId, tagFilters, lookupOptions.q, lookupOptions.region)

    return allFilteredImages.subList(0, Math.min(MAX_SEARCH_RESULTS, allFilteredImages.size()))
  }

  /**
   * Generates the existing tag mapping for a CacheData.
   * NOTE: This method only works with CacheData of type Namespace.IMAGES.ns as that is the source of tags metadata.
   *
   * @param cacheData Source of tags metadata.
   * @return Map of tag name to tag value.
   */
  private static Map<String, String> generateTagsFromCacheData(final CacheData cacheData) {
    final Map<String, String> tagsForImageId =
      (cacheData.attributes.tags as List<Map.Entry<String, String>>)?.collectEntries {
        [it.key.toLowerCase(), it.value]
      }
    return tagsForImageId
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

  /**
   * Render an http response -- a list of NamedImages from information retrieved
   * from the cache.
   *
   * @param namedImages info from the NAMED_IMAGES cache table
   * @param images info from the IMAGES cache table
   * @param tagFilters tags that resulting images must contain
   * @param requestedName the name provided in the query
   * @param requiredRegion images must be from this region (or null to include images from all regions)
   * @return the images to respond with
   */
  private List<NamedImage> render(
    Collection<CacheData> namedImages,
    Collection<CacheData> images,
    Map<String, String> tagFilters,
    String requestedName,
    String requiredRegion
  ) {
    // Assemble NamedImage objects for each image that satisfies tagFilters.
    // Note that it's possible to have multiple images with the same name, but
    // also multiple images with the same ami id in different accounts, so
    // neither image name nor image id is sufficient to track them.  It's
    // potentially simpler to use the same cache key that IMAGES.ns uses.
    // Currently (23-aug-22), that contains account, region, and image id.  It's
    // not clear yet whether it's possible to have two images with the same id
    // in different regions of the same account.  As well, an explicit key
    // class, is, well, nicely explicit.  If we need to add region to it later,
    // it seems straightforward to do so -- by adding a region member to AmiKey.
    // Initial testing shows that it may not be so straightforward.
    final Map<AmiKey, NamedImage> byAmiKey = new HashMap<>()

    // Generate list of AMI IDs based on the passed in collection of AMIs found by name.
    final List<String> identifiers = namedImages.collect {
      (it.relationships[IMAGES.ns] ?: []).collect { it }
    }.flatten() as List<String>

    // Populate the tags for each NamedImage. Metadata from the AMI IDs search
    // is the source of truth for tags metadata.
    for(final CacheData cacheData : cacheView.getAll(IMAGES.ns, identifiers)) {
      final Map<String, String> tagsForImageId = generateTagsFromCacheData(cacheData)

      // Skip images that don't satisfy the tag filter
      if(tagFilters && !checkTagsForImageId(tagsForImageId, tagFilters)) {
        continue
      }

      final Map<String, String> keyParts = Keys.parse(cacheData.id)
      final String amiId = keyParts.imageId
      final AmiKey amiKey = new AmiKey(account: keyParts.account,
                                       amiId: amiId)
      NamedImage namedImage = byAmiKey.get(amiKey)
      if (namedImage == null) {
        final String amiName = cacheData.attributes.name
        namedImage = new NamedImage(imageName: amiName)
        byAmiKey.put(amiKey, namedImage)
      }

      namedImage.tagsByImageId[amiId] = tagsForImageId
    }

    // Populate metadata for AMI NAME driven searches.
    for (final CacheData cacheData : namedImages) {
      final Map<String, String> keyParts = Keys.parse(cacheData.id)
      final String account = keyParts.account

      for (final String imageKey : cacheData.relationships[IMAGES.ns] ?: []) {
        final Map<String, String> imageParts = Keys.parse(imageKey)
        String amiId = imageParts.imageId
        final AmiKey amiKey = new AmiKey(account: account, amiId: amiId)
        final NamedImage thisImage = byAmiKey[amiKey]
        if (thisImage != null) {
          thisImage.attributes.putAll(cacheData.attributes - [name: keyParts.imageName])
          thisImage.accounts.add(keyParts.account)
          thisImage.amis[imageParts.region].add(imageParts.imageId)
        }
      }
    }

    // Populate metadata for AMI ID driven searches.
    for (final CacheData cacheData : images) {
      final Map<String, String> tagsForImageId = generateTagsFromCacheData(cacheData)

      // Avoid generating any metadata for a non-relevant image.
      if(tagFilters && !checkTagsForImageId(tagsForImageId, tagFilters)) {
        continue
      }

      final Map<String, String> amiKeyParts = Keys.parse(cacheData.id)
      final String amiId = amiKeyParts.imageId
      final AmiKey amiKey = new AmiKey(account: amiKeyParts.account, amiId: amiId)
      NamedImage thisImage = byAmiKey[amiKey]
      if (thisImage == null) {
        final String amiName = cacheData.attributes.name
        thisImage = new NamedImage(imageName: amiName)
        byAmiKey.put(amiKey, thisImage)
      }
      thisImage.attributes.virtualizationType = cacheData.attributes.virtualizationType
      thisImage.attributes.architecture = cacheData.attributes.architecture
      thisImage.attributes.creationDate = cacheData.attributes.creationDate
      thisImage.accounts.add(amiKeyParts.account)
      thisImage.amis[amiKeyParts.region].add(amiId)

      thisImage.tagsByImageId[amiId] = tagsForImageId

      // Deprecated tags field is only set if the query is for an AMI ID.
      thisImage.tags = thisImage.tagsByImageId[amiId]
    }

    // NOTE: Ensure a list is explicitly used to enable in place sorting below.
    final List<NamedImage> results = new ArrayList<>()

    // Previous behavior was that each element in the response has a unique
    // image name, so merge elements of byAmiKey that have the same name.
    Map<String, NamedImage> byImageName = new HashMap<>()

    // Iterate through byAmiKey and merge entries that have the same imageName,
    // including only those in requiredRegion if specified.
    byAmiKey.values().findAll { requiredRegion ? it.amis.containsKey(requiredRegion) : true }.each { NamedImage namedImage ->
      NamedImage result = byImageName.putIfAbsent(namedImage.imageName, namedImage)
      if (result == null) {
        // This is the first time we've seen this image name
        results.add(namedImage)
      } else {
        // We've already got an element in the results list with this image
        // name.  Add the info from namedImage into it.

        // The amiKey for namedImage is different than the one for result, but
        // the image ids could be the same (e.g. if they're from different
        // accounts).  So, use merge instead of putAll, and choose (arbitrarily)
        // to keep the tags in result, as opposed to replacing them with the
        // ones from namedImage, or combining them all.  Unless of course result
        // has no tags at all.  In that case use the tags from namedImage.
        def keepTags = { Map<String, String> tags1, Map<String, String> tags2 -> ImmutableMap.copyOf(tags1.isEmpty() ? tags2 : tags1) }
        namedImage.tagsByImageId.each { String imageId, Map<String, String> tags ->
          result.tagsByImageId.merge(imageId, tags, keepTags)
        }

        result.accounts.addAll(namedImage.accounts)

        // The key of the amis map is a region. The value of the amis map is a
        // collection of ami ids.  When both result and namedImage have
        // information in the same region, combine the two collections together.
        def combineImageIds = { Collection<String> imageIds1, Collection<String> imageIds2 -> Sets.newHashSet(Iterables.concat(imageIds1, imageIds2)) }
        namedImage.amis.each { String region, Collection<String> imageIds ->
          result.amis.merge(region, imageIds, combineImageIds)
        }

        // Similar to the tagsByImageId handling, keep the tags in result if
        // there are any, otherwise replace them with the ones from namedImage.
        if (result.tags.isEmpty()) {
          result.tags = namedImage.tags
        }
      }
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

  /**
   * Information to uniquely identify an image.  It's possible to have two images
   * with the same ami id in different accounts, so ami id isn't sufficient.
   */
  @Immutable
  private static class AmiKey {
    String account;
    String amiId;
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
