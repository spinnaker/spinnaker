/*
 * Copyright 2016 Target, Inc.
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

package com.netflix.spinnaker.clouddriver.openstack.domain

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Load balancer descriptions are used to store the created_time for a load balancer.
 *
 * It is a key value pair.
 *
 * For example:
 *
 * {@code created_time=12345678}
 *
 * Load balancer listener descriptions are used to store external and internal protocols and ports.
 *
 * For example:
 *
 * {@code HTTP:80:HTTP:8080}
 */
trait LoadBalancerResolver {

  final String createdRegex = ".*created_time=([0-9]+).*"
  final Pattern createdPattern = Pattern.compile(createdRegex)

  /**
   * Generate key=value port string, e.g. internal_port=8100,internal_protocol=HTTP
   * @param port
   * @return
   */
  String getListenerKey(int externalPort, String externalProtocol, int port, String protocol) {
    "${externalProtocol}:${externalPort}:${protocol}:${port}"
  }

  Map<String, String> parseListenerKey(String key) {
    Map<String, String> result = [:]

    String[] parts = key?.split(':')

    if (parts?.length == 4) {
      result << [externalProtocol: parts[0], externalPort: parts[1], internalProtocol: parts[2], internalPort: parts[3]]
    }

    result
  }

  /**
   * Parse the created time from a load balancer description in the following format.
   * <br><br>
   * {@code
   *  ...,created_time=12345678,...
   * }
   * @param description
   * @return the port value
   */
  Long parseCreatedTime(final String description) {
    String s = match(description, createdPattern)
    s ? s.toLong() : null
  }

  /**
   * Generate key=value createdTime string, e.g. created_time=12345678
   * @param time
   * @return
   */
  String generateCreatedTime(long time) {
    "created_time=${time}"
  }

  /**
   * Match a pattern in the comma-separated fields of the description
   * @param description
   * @param pattern
   * @return
   */
  String match(final String description, final Pattern pattern) {
    String result = null
    for (String s : description?.split(',')) {
      Matcher matcher = pattern.matcher(s)
      if (matcher.matches() && matcher.groupCount() == 1) {
        result = matcher.group(1)
        break
      }
    }
    result
  }

}
