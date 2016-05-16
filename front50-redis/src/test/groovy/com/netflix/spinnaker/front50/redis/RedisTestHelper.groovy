/*
 * Copyright 2016 Pivotal, Inc.
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

package com.netflix.spinnaker.front50.redis

class RedisTestHelper {

  static boolean redisUnavailable() {
    Socket s
    try {
      byte[] localhost = [127, 0, 0, 1]
      def resolvedAddress = new InetSocketAddress(InetAddress.getByAddress('localhost', localhost), 6379)
      s = new Socket()
      s.connect(resolvedAddress, 125)
      false
    } catch (Throwable t) {
      true
    } finally {
      try {
        s?.close()
      } catch (IOException ignored) {

      }
    }

  }
}
