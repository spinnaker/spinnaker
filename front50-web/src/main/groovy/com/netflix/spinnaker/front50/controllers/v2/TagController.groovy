/*
 * Copyright 2016 Netflix, Inc.
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


package com.netflix.spinnaker.front50.controllers.v2

import com.netflix.spinnaker.front50.exception.BadRequestException
import com.netflix.spinnaker.front50.exception.NotFoundException
import com.netflix.spinnaker.front50.model.tag.EntityTags
import com.netflix.spinnaker.front50.model.tag.EntityTagsDAO
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.util.AntPathMatcher
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.HandlerMapping

import javax.servlet.http.HttpServletRequest

@Slf4j
@RestController
@RequestMapping(value = "/v2/tags", produces = MediaType.APPLICATION_JSON_VALUE)
class TagController {
  @Autowired(required = false)
  EntityTagsDAO taggedEntityDAO

  @RequestMapping(method = RequestMethod.GET)
  Set<EntityTags> tags(@RequestParam(value = "prefix", required = true) String prefix) {
    return taggedEntityDAO?.all(prefix) ?: []
  }

  @RequestMapping(value = "/**", method = RequestMethod.GET)
  EntityTags tag(HttpServletRequest request) {
    String pattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    String searchTerm = new AntPathMatcher().extractPathWithinPattern(pattern, request.getServletPath());

    if (!taggedEntityDAO) {
      throw new NotFoundException("No tags found for '${searchTerm}'")
    }

    return taggedEntityDAO.findById(searchTerm)
  }

  @RequestMapping(method = RequestMethod.POST)
  EntityTags create(@RequestBody final EntityTags tag) {
    if (!taggedEntityDAO) {
      throw new BadRequestException("Tagging is not supported")
    }

    return taggedEntityDAO.create(tag.id, tag)
  }
}
