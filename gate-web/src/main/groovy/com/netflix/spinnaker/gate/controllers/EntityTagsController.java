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

package com.netflix.spinnaker.gate.controllers;

import com.netflix.spinnaker.gate.services.EntityTagsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping(value = "/tags")
public class EntityTagsController {
  private EntityTagsService entityTagsService;

  @Autowired
  public EntityTagsController(EntityTagsService entityTagsService) {
    this.entityTagsService = entityTagsService;
  }

  @RequestMapping(method = RequestMethod.GET)
  public Collection<Map> list(@RequestParam Map<String, Object> allParameters) {
    return entityTagsService.list(allParameters);
  }

  @RequestMapping(value = "/**", method = RequestMethod.GET)
  public Map get(HttpServletRequest request) {
    String pattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    String id = new AntPathMatcher().extractPathWithinPattern(pattern, request.getServletPath());

    return entityTagsService.get(id);
  }
}
