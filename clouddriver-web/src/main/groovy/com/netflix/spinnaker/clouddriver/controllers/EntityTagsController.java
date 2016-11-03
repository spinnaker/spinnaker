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

package com.netflix.spinnaker.clouddriver.controllers;

import com.netflix.spinnaker.clouddriver.model.EntityTags;
import com.netflix.spinnaker.clouddriver.model.EntityTagsProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/tags")
public class EntityTagsController {
  private final MessageSource messageSource;
  private final EntityTagsProvider tagProvider;

  @Autowired(required = false)
  public EntityTagsController(MessageSource messageSource,
                              Optional<EntityTagsProvider> tagProvider) {
    this.messageSource = messageSource;
    this.tagProvider = tagProvider.orElse(null);
  }

  @RequestMapping(method = RequestMethod.GET)
  public Collection<EntityTags> list(@RequestParam(value = "cloudProvider", required = false) String cloudProvider,
                                     @RequestParam(value = "entityType", required = false) String entityType,
                                     @RequestParam(value = "idPrefix", required = false) String idPrefix,
                                     @RequestParam(value = "maxResults", required = false, defaultValue = "100") int maxResults,
                                     @RequestParam Map<String, Object> allParameters) {

    Map<String, Object> tags = allParameters.entrySet().stream()
      .filter(m -> m.getKey().toLowerCase().startsWith("tag"))
      .collect(Collectors.toMap(p -> p.getKey().toLowerCase().replaceAll("tag:", ""), Map.Entry::getValue));

    return tagProvider.getAll(cloudProvider, entityType, idPrefix, tags, maxResults);
  }

  @RequestMapping(value = "/**", method = RequestMethod.GET)
  public EntityTags get(HttpServletRequest request) {
    String pattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    String id = new AntPathMatcher().extractPathWithinPattern(pattern, request.getServletPath());
    return tagProvider.get(id).orElseThrow(() -> new EntityTagsNotFoundException(id));
  }

  @ExceptionHandler
  @ResponseStatus(HttpStatus.NOT_FOUND)
  Map entityTagsNotFoundExceptionHandler(EntityTagsNotFoundException ex) {
    String message = messageSource.getMessage(
      "entityTags.not.found",
      new String[]{ex.name},
      "No EntityTags found w/ id = '" + ex.name + "'",
      LocaleContextHolder.getLocale()
    );

    return new HashMap<String, Object>() {{
      put("error", "entityTags.not.found");
      put("message", message);
      put("status", HttpStatus.NOT_FOUND);
    }};
  }

  static class EntityTagsNotFoundException extends RuntimeException {
    final String name;

    EntityTagsNotFoundException(String name) {
      this.name = name;
    }
  }
}
