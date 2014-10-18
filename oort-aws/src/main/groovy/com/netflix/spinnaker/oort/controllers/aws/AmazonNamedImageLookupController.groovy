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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

import static com.netflix.spinnaker.oort.data.aws.Keys.Namespace.IMAGES
import static com.netflix.spinnaker.oort.data.aws.Keys.Namespace.NAMED_IMAGES

@RestController
@RequestMapping("/aws/namedImages")
class AmazonNamedImageLookupController {

  private final Cache cacheView

  @Autowired
  AmazonNamedImageLookupController(Cache cacheView) {
    this.cacheView = cacheView
  }

  @RequestMapping(method = RequestMethod.GET)
  List<NamedImage> list(LookupOptions lookupOptions) {

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

    List<NamedImage> results = byImageName.values().findAll { lookupOptions.region ? it.amis.containsKey(lookupOptions.region) : true }
    results.sort { a, b ->
      int a1, b1
      if (lookupOptions.imageName) {
        a1 = a.imageName.indexOf(lookupOptions.imageName)
        b1 = b.imageName.indexOf(lookupOptions.imageName)
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
