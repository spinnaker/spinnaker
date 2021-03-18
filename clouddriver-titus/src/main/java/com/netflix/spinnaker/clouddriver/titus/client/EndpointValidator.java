/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.titus.client;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class EndpointValidator {

  private static final Set<String> ALLOWED_PROTOCOLS =
      Collections.unmodifiableSet(new HashSet<>(Arrays.asList("http", "https")));

  public static String validateEndpoint(String endpoint) {
    URL url;
    try {
      url = new URL(endpoint);
    } catch (NullPointerException e) {
      throw new IllegalArgumentException(String.format("Invalid endpoint provided (%s)", endpoint));
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException(
          String.format("Invalid endpoint provided (%s): %s", endpoint, e.getMessage()));
    }

    if (url.getHost() == null || "".equals(url.getHost())) {
      throw new IllegalArgumentException(
          String.format("Invalid endpoint provided (%s): No host specified", endpoint));
    }

    String protocol = url.getProtocol();
    if (!ALLOWED_PROTOCOLS.contains(protocol)) {
      throw new IllegalArgumentException(
          String.format(
              "Invalid endpoint provided (%s): Invalid protocol specified (%s)",
              endpoint, protocol));
    }
    return endpoint;
  }
}
