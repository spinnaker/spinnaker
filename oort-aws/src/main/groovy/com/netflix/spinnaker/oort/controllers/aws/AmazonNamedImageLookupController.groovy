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

package com.netflix.spinnaker.oort.controllers.aws

import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.oort.data.aws.Keys
import groovy.transform.InheritConstructors
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

import static com.netflix.spinnaker.oort.data.aws.Keys.Namespace.IMAGES
import static com.netflix.spinnaker.oort.data.aws.Keys.Namespace.NAMED_IMAGES

@RestController
@RequestMapping("/aws/images")
class AmazonNamedImageLookupController {
  private static final int MIN_NAME_FILTER = 2;
  private static final String EXCEPTION_REASON = 'Minimum of ' + MIN_NAME_FILTER + ' characters required to filter namedImages'

  private final Cache cacheView

  @Autowired
  AmazonNamedImageLookupController(Cache cacheView) {
    this.cacheView = cacheView
  }

  @RequestMapping(value = '/{account}/{region}/{imageId}', method = RequestMethod.GET)
  List<NamedImage> getByAmiId(@PathVariable String account, @PathVariable String region, @PathVariable String imageId) {
    CacheData cd = cacheView.get(IMAGES.ns, Keys.getImageKey(imageId, account, region))
    if (cd == null) {
      throw new ImageNotFoundException("${imageId} not found in ${account}/${region}")
    }
    Collection<String> namedImageKeys = cd.relationships[NAMED_IMAGES.ns]

    if (!namedImageKeys) {
      throw new ImageNotFoundException("Name not found on image ${imageId} in ${account}/${region}")
    }

    Collection<CacheData> namedImages = cacheView.getAll(NAMED_IMAGES.ns, namedImageKeys)
    render(namedImages, null, region)
  }

  @RequestMapping(value = '/find', method = RequestMethod.GET)
  List<NamedImage> list(LookupOptions lookupOptions) {
    if (lookupOptions.imageName == null || lookupOptions.imageName.length() < MIN_NAME_FILTER) {
      throw new InsufficientLookupOptionsException(EXCEPTION_REASON)
    }

    Collection<String> identifiers = cacheView.getIdentifiers(NAMED_IMAGES.ns)
    Collection<String> filtered = identifiers.findAll {
      def parts = Keys.parse(it)
      if (lookupOptions.imageName) {
        if (parts.imageName.indexOf(lookupOptions.imageName) == -1) {
          return false
        }
      }

      if (lookupOptions.account) {
        if (parts.account != lookupOptions.account) {
          return false
        }
      }

      true
    }

    Collection<CacheData> namedImages = cacheView.getAll(NAMED_IMAGES.ns, filtered)

    render(namedImages, lookupOptions.imageName, lookupOptions.region)
  }

  private List<NamedImage> render(Collection<CacheData> namedImages, String requestedName = null, String requiredRegion = null) {
    if (!namedImages) {
      throw new ImageNotFoundException('Not found')
    }
    Map<String, NamedImage> byImageName = [:].withDefault { new NamedImage(imageName: it) }
    for (CacheData data : namedImages) {
      Map<String, String> keyParts = Keys.parse(data.id)
      NamedImage thisImage = byImageName[keyParts.imageName]
      thisImage.accounts.add(keyParts.account)
      for (String imageKey : data.relationships[IMAGES.ns] ?: []) {
        Map<String, String> imageParts = Keys.parse(imageKey)
        thisImage.amis[imageParts.region].add(imageParts.imageId)
      }
    }

    List<NamedImage> results = byImageName.values().findAll { requiredRegion ? it.amis.containsKey(requiredRegion) : true }
    if (!results) {
      throw new ImageNotFoundException('Not found')
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
  }


  @ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = 'Minimum of 2 characters required to filter namedImages')
  @InheritConstructors
  private static class InsufficientLookupOptionsException extends RuntimeException { }

  @ResponseStatus(value = HttpStatus.NOT_FOUND, reason = 'Image not found')
  @InheritConstructors
  private static class ImageNotFoundException extends RuntimeException { }


  private static class NamedImage {
    String imageName
    Set<String> accounts = []
    Map<String, Collection<String>> amis = [:].withDefault { new HashSet<String>() }
  }

  private static class LookupOptions {
    String imageName
    String account
    String region
  }
}
