/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.clouddriver.oraclebmcs.controller

import com.netflix.spinnaker.cats.mem.InMemoryCache
import com.netflix.spinnaker.clouddriver.oraclebmcs.model.OracleBMCSImage
import com.netflix.spinnaker.clouddriver.oraclebmcs.provider.view.OracleBMCSImageProvider
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*

import java.util.regex.Pattern
import java.util.stream.Collectors

@Slf4j
@RestController
@RequestMapping("/oraclebmcs/images")
class OracleBMCSImageLookupController {

  final OracleBMCSImageProvider imageProvider

  @Autowired
  OracleBMCSImageLookupController(final OracleBMCSImageProvider imageProvider) {
    this.imageProvider = imageProvider
  }

  @RequestMapping(value = '/{account}/{region}/{imageId:.+}', method = RequestMethod.GET)
  List<OracleBMCSImage> getById(
    @PathVariable String account, @PathVariable String region, @PathVariable String imageId) {
    def images = imageProvider.getByAccountAndRegion(account, region)
    if (images) {
      return [images.find { it.id == imageId }] as List
    }
    return []
  }

  @RequestMapping(value = '/find', method = RequestMethod.GET)
  Set<OracleBMCSImage> find(@RequestParam(required = false) String account, @RequestParam(required = false) String q) {
    Set<OracleBMCSImage> results
    Set<OracleBMCSImage> images = imageProvider.getAll()
    if (!images) {
      results = Collections.emptySet()
    } else {
      results = images.stream()
        .collect(Collectors.toSet())
        .sort { OracleBMCSImage a, OracleBMCSImage b -> a.name <=> b.name }


      Pattern pattern = resolveQueryToPattern(q)
      log.info('filtering images using pattern {}', pattern)
      results = results.findAll { pattern.matcher(it.name).matches() }
    }

    return results
  }

  Pattern resolveQueryToPattern(String query) {
    String glob = query?.trim() ?: '*'
    // Wrap in '*' if there are no glob-style characters in the query string.
    if (!glob.contains('*') && !glob.contains('?') && !glob.contains('[') && !glob.contains('\\')) {
      glob = "*${glob}*"
    }
    return new InMemoryCache.Glob(glob).toPattern()
  }
}
