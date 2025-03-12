/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.cats.redis.test

class NetworkUnavailableCheck {
  public static boolean networkUnavailable() {
    final Enumeration<NetworkInterface> interfaces
    try {
      interfaces = NetworkInterface.getNetworkInterfaces()
    } catch (SocketException ignored) {
      return true
    }
    if (interfaces == null) {
      return true
    }

    for (NetworkInterface networkInterface : Collections.list(interfaces)) {
      try {
        if (!networkInterface.isLoopback() && networkInterface.isUp()) {
          return false
        }
      } catch (SocketException ignored) {
      }
    }

    return true
  }
}
