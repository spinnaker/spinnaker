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
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.util.AntPathMatcher
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.HandlerMapping

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@Slf4j
@RestController
@RequestMapping(value = "/v2/tags", produces = MediaType.APPLICATION_JSON_VALUE)
class EntityTagsController {
  @Autowired(required = false)
  EntityTagsDAO taggedEntityDAO

  @RequestMapping(method = RequestMethod.GET)
  Set<EntityTags> tags(@RequestParam(value = "prefix", required = false) String prefix,
                       @RequestParam(value = "ids", required = false) Collection<String> ids,
                       @RequestParam(value = "refresh", required = false) Boolean refresh) {
    if (prefix == null && !ids) {
      throw new BadRequestException("Either 'prefix' or 'ids' parameter is required")
    }

    if (ids) {
      return findAllByIds(ids)
    }

    refresh = (refresh == null) ? true : refresh
    return taggedEntityDAO?.all(refresh)?.findAll { prefix ? it.id.startsWith(prefix) : true }
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

  @RequestMapping(value = "/batchUpdate", method = RequestMethod.POST)
  Collection<EntityTags> batchUpdate(@RequestBody final Collection<EntityTags> tags) {
    if (!taggedEntityDAO) {
      throw new BadRequestException("Tagging is not supported")
    }

    taggedEntityDAO.bulkImport(tags)
    return findAllByIds(tags.findResults { it.id })
  }

  @RequestMapping(value = "/batchDelete", method = RequestMethod.POST)
  void batchDelete(@RequestBody final Collection<String> ids) {
    if (!taggedEntityDAO) {
      throw new BadRequestException("Tagging is not supported")
    }

    taggedEntityDAO.bulkDelete(ids)
  }

  @RequestMapping(method = RequestMethod.DELETE, value = "/**")
  void delete(HttpServletRequest request, HttpServletResponse response) {
    String pattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    String tagId = new AntPathMatcher().extractPathWithinPattern(pattern, request.getServletPath());

    taggedEntityDAO.delete(tagId)
    response.setStatus(HttpStatus.NO_CONTENT.value())
  }

  private Set<EntityTags> findAllByIds(Collection<String> ids) {
    return ids.findResults {
      try {
        taggedEntityDAO.findById(it)
      } catch (ignored) {}
    }
  }
}
