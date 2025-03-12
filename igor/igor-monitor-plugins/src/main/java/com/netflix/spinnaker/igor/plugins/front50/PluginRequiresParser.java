/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.spinnaker.igor.plugins.front50;

import com.google.common.base.Splitter;
import com.netflix.spinnaker.igor.plugins.model.PluginRelease;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class PluginRequiresParser {

  private static final Logger log = LoggerFactory.getLogger(PluginRequiresParser.class);

  private static final Splitter SPLITTER = Splitter.on(",");
  private static final Pattern PATTERN =
      Pattern.compile(
          "^(?<service>[\\w\\-]+)(?<operator>[><=]{1,2})(?<version>[0-9]+\\.[0-9]+\\.[0-9]+)$");

  static List<PluginRelease.ServiceRequirement> parseRequires(String requires) {
    List<String> requirements = SPLITTER.splitToList(requires);

    return requirements.stream()
        .map(
            r -> {
              Matcher m = PATTERN.matcher(r);
              if (!m.matches()) {
                log.error("Failed parsing plugin requires field '{}'", r);
                return null;
              }
              return new PluginRelease.ServiceRequirement(
                  m.group("service"), m.group("operator"), m.group("version"));
            })
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }
}
