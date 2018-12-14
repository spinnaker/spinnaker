/*
 * Copyright 2015 Google, Inc.
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
package com.netflix.spinnaker.orca;

import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Utility class that aids in debugging Maps in the logs.
 * <p>
 * Created by ttomsu on 8/20/15.
 */
public class DebugSupport {
  /**
   * @return a prettier, loggable string version of a Map.
   */
  public static String prettyPrint(final Map m) {
    try {
      return new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(m);
    } catch (Exception ignored) {}

    return "Could not pretty print map: " + String.valueOf(m);
  }

}
