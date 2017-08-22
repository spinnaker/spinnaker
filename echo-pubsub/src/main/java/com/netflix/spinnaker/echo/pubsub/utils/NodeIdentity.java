/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.echo.pubsub.utils;

import lombok.Getter;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.*;
import java.util.Collections;
import java.util.Enumeration;

public class NodeIdentity {
  public static final String UNKNOWN_HOST = "UnknownHost";

  private String runtimeName;
  private String hostName;
  @Getter
  private String identity;

  public NodeIdentity() {
    this("www.google.com", 80);
  }

  public NodeIdentity(String validationAddress, Integer validationPort) {
    this.runtimeName = ManagementFactory.getRuntimeMXBean().getName();
    this.hostName = resolveHostname(validationAddress, validationPort);
    if (!hostName.equals(UNKNOWN_HOST)) {
      this.identity = String.format("%s:%s", this.hostName, this.runtimeName);
    }
  }

  @SuppressWarnings("PMD.EmptyCatchBlock")
  private static String resolveHostname(String validationHost, int validationPort) {
    final Enumeration<NetworkInterface> interfaces;
    try {
      interfaces = NetworkInterface.getNetworkInterfaces();
    } catch (SocketException ignored) {
      return UNKNOWN_HOST;
    }
    if (interfaces == null) {
      return UNKNOWN_HOST;
    }

    for (NetworkInterface networkInterface : Collections.list(interfaces)) {
      try {
        if (networkInterface.isLoopback()) {
          continue;
        }

        if (!networkInterface.isUp()) {
          continue;
        }
      } catch (SocketException ignored) {
        continue;
      }

      for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
        Socket socket = null;
        try {
          socket = new Socket();
          socket.bind(new InetSocketAddress(address, 0));
          socket.connect(new InetSocketAddress(validationHost, validationPort), 125);
          return address.getHostName();
        } catch (IOException ignored) {
          //ignored
        } finally {
          if (socket != null) {
            try {
              socket.close();
            } catch (IOException ignored) {
              //ignored
            }
          }
        }
      }
    }

    return UNKNOWN_HOST;
  }
}
