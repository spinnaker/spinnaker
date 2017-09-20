/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.gate.security.ldap

import org.springframework.security.ldap.server.ApacheDSContainer

class ApacheDSServer {

  private static ApacheDSContainer server
  private static Integer serverPort

  static void startServer(String ldif) throws Exception {
    server = new ApacheDSContainer("dc=unit,dc=test", ldif)
    serverPort = getAvailablePort()
    server.port = serverPort
    server.afterPropertiesSet()
  }

  static void stopServer() throws Exception {
    serverPort = null
    server?.stop()
  }

  static int getServerPort() {
    if (!serverPort) {
      throw new IllegalStateException("The ApacheDSContainer is not currently running")
    }
    return serverPort
  }

  private static int getAvailablePort() throws IOException {
    ServerSocket serverSocket = null
    try {
      serverSocket = new ServerSocket(0)
      return serverSocket.getLocalPort()
    }
    finally {
      serverSocket?.close()
    }
  }
}
