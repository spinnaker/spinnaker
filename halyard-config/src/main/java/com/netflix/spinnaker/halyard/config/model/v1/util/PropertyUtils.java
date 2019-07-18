/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.halyard.config.model.v1.util;

public class PropertyUtils {
  private static final String PLACEHOLDER_PATTERN = ".\\{.*}";

  private static final String CONFIG_SERVER_PREFIX = "configserver:";

  public static boolean anyContainPlaceholder(String... fields) {
    for (String field : fields) {
      if (field != null && field.matches(PLACEHOLDER_PATTERN)) {
        return true;
      }
    }
    return false;
  }

  public static boolean isConfigServerResource(String path) {
    return path != null && path.startsWith(CONFIG_SERVER_PREFIX);
  }
}
