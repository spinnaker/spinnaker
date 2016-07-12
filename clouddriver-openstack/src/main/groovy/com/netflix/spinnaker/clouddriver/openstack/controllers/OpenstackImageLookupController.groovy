/*
 * Copyright 2016 Target, Inc.
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

package com.netflix.spinnaker.clouddriver.openstack.controllers

import com.netflix.spinnaker.cats.mem.InMemoryCache
import com.netflix.spinnaker.clouddriver.openstack.model.Image
import com.netflix.spinnaker.clouddriver.openstack.provider.ImageProvider
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

import java.util.regex.Pattern
import java.util.stream.Collectors

@Slf4j
@RestController
@RequestMapping("/openstack/images")
class OpenstackImageLookupController {

  final ImageProvider imageProvider

  @Autowired
  OpenstackImageLookupController(final ImageProvider imageProvider) {
    this.imageProvider = imageProvider
  }

  @RequestMapping(value = '/find', method = RequestMethod.GET)
  Set<Image> find(@RequestParam(required = false) String account, @RequestParam(required = false) String q) {
    Set<Image> result
    Map<String, Set<Image>> imageMap = this.imageProvider.listImagesByAccount()
    if (!imageMap) {
      result = Collections.emptySet()
    } else {
      if (account) {
        result = imageMap.get(account)
      } else {
        result = imageMap.entrySet().stream()
          .map{it.value}
          .flatMap{it.stream()}
          .collect(Collectors.toSet())
          .sort { Image a, Image b -> a.name <=> b.name }
      }

      Pattern pattern = resolveQueryToPattern(q)
      log.info('filtering images using pattern {}', pattern)
      result = result.findAll { pattern.matcher(it.name).matches() }
    }

    result
  }

  Pattern resolveQueryToPattern(String query) {
    String glob = query?.trim() ?: '*'
    // Wrap in '*' if there are no glob-style characters in the query string.
    if (!glob.contains('*') && !glob.contains('?') && !glob.contains('[') && !glob.contains('\\')) {
      glob = "*${glob}*"
    }
    new InMemoryCache.Glob(glob).toPattern()
  }
}
