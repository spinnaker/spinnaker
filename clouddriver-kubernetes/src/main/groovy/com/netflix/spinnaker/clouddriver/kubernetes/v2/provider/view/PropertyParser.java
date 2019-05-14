/*
 * Copyright 2019 Armory
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v2.provider.view;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PropertyParser {

  private static final String MAGIC_SEARCH_STRING = "SPINNAKER_PROPERTY_";
  private static final Pattern MAGIC_SEARCH_PATTERN = Pattern.compile(MAGIC_SEARCH_STRING);
  private static final String MAGIC_JSON_SEARCH_STRING = "SPINNAKER_CONFIG_JSON=";
  private static final Pattern MAGIC_JSON_SEARCH_PATTERN =
      Pattern.compile("^\\s*" + MAGIC_JSON_SEARCH_STRING);

  public static Map<String, Object> extractPropertiesFromLog(String buildLog) throws IOException {
    final Map<String, Object> map = new HashMap<>();

    for (String line : buildLog.split("\n")) {
      if (MAGIC_SEARCH_PATTERN.matcher(line).find()) {
        log.debug("Identified: " + line);
        String[] splittedLine = line.split("=");
        final String key = splittedLine[0].replaceFirst(MAGIC_SEARCH_STRING, "").toLowerCase();
        final String value = splittedLine[1].trim();
        log.debug(key + ":" + value);
        map.put(key, value);
      }

      if (MAGIC_JSON_SEARCH_PATTERN.matcher(line).find()) {
        log.debug("Identified Spinnaker JSON properties magic string: " + line);
        final String jsonContent = line.replaceFirst(MAGIC_JSON_SEARCH_STRING, "");
        ObjectMapper objectMapper = new ObjectMapper();
        try {
          map.putAll(
              objectMapper.readValue(jsonContent, new TypeReference<Map<String, Object>>() {}));
        } catch (IOException e) {
          log.error(
              "Unable to parse content from {}. Content is: {}",
              MAGIC_JSON_SEARCH_STRING,
              jsonContent);
          throw e;
        }
      }
    }
    return map;
  }
}
