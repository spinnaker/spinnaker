/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.orca.deprecation;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import static org.apache.commons.lang3.StringUtils.isEmpty;

@Component public class DeprecationRegistry {

  private static final String METRIC_NAME = "orca.deprecation";
  private static final String APPLICATION_TAG_KEY = "application";
  private static final String DEPRECATION_TAG_KEY = "deprecationName";
  private final Registry registry;

  private final Logger log = LoggerFactory.getLogger(getClass());

  @Autowired public DeprecationRegistry(Registry registry) {
    this.registry = registry;
  }

  public void logDeprecatedUsage(final String tagName, final String application) {
    if (isEmpty(tagName) || isEmpty(application)) {
      log.warn("No deprecation tag name ({}) or application ({}) provided - ignoring publish of deprecated usage", tagName, application);
      return;
    }

    Id id = registry
      .createId(METRIC_NAME)
      .withTag(DEPRECATION_TAG_KEY, tagName)
      .withTag(APPLICATION_TAG_KEY, application);
    registry.counter(id).increment();
  }
}
