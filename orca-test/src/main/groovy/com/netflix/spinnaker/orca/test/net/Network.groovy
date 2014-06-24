/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.test.net

import groovy.transform.CompileStatic
import groovy.transform.Memoized
import groovy.util.logging.Slf4j
import static java.util.concurrent.TimeUnit.SECONDS

@CompileStatic
@Slf4j
class Network {

  @Memoized
  static boolean isReachable(String url, int timeoutMillis = SECONDS.toMillis(1)) {
    try {
      def connection = url.toURL().openConnection()
      connection.connectTimeout = timeoutMillis
      connection.connect()
      true
    } catch (IOException ex) {
      log.warn "${ex.getClass().simpleName}: $ex.message"
      false
    }
  }
}
