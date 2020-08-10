/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.netflix.spinnaker.front50.model.application;

import com.google.common.base.Strings;
import com.netflix.spinnaker.front50.UntypedUtils;
import com.netflix.spinnaker.front50.model.ItemDAO;
import com.netflix.spinnaker.front50.model.SearchUtils;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.util.*;
import java.util.stream.Collectors;

public interface ApplicationDAO extends ItemDAO<Application> {
  Application findByName(String name) throws NotFoundException;

  Collection<Application> search(Map<String, String> attributes);

  class Searcher {
    public static Collection<Application> search(
        Collection<Application> searchableApplications, Map<String, String> attributes) {
      Map<String, String> normalizedAttributes = new HashMap<>();
      attributes.forEach((key, value) -> normalizedAttributes.put(key.toLowerCase(), value));

      // filtering vs. querying to achieve case-insensitivity without using an additional column
      // (small data set)
      return searchableApplications.stream()
          .filter(
              it -> {
                for (Map.Entry<String, String> e : normalizedAttributes.entrySet()) {
                  if (Strings.isNullOrEmpty(e.getValue())) {
                    continue;
                  }
                  if (!UntypedUtils.hasProperty(it, e.getKey())
                      && !it.details().containsKey(e.getKey())) {
                    return false;
                  }

                  Object appVal =
                      UntypedUtils.hasProperty(it, e.getKey())
                          ? UntypedUtils.getProperty(it, e.getKey())
                          : it.details().get(e.getKey());
                  if (appVal == null) {
                    appVal = "";
                  }
                  if (!appVal.toString().toLowerCase().contains(e.getValue().toLowerCase())) {
                    return false;
                  }
                }
                return true;
              })
          .distinct()
          .sorted(
              (a, b) ->
                  SearchUtils.score(b, normalizedAttributes)
                      - SearchUtils.score(a, normalizedAttributes))
          .collect(Collectors.toList());
    }
  }
}
