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

package com.netflix.spinnaker.cats.cluster;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultNodeIdentity implements NodeIdentity {

  public static final String UNKNOWN_HOST = "UnknownHost";
  private static final long REFRESH_INTERVAL = TimeUnit.SECONDS.toMillis(30);

  private static String getHostName(String validationHost, int validationPort) {
    final Enumeration<NetworkInterface> interfaces;
    try {
      interfaces = NetworkInterface.getNetworkInterfaces();
    } catch (SocketException ignored) {
      return UNKNOWN_HOST;
    }
    if (interfaces == null || validationHost == null) {
      return UNKNOWN_HOST;
    }

    for (NetworkInterface networkInterface : Collections.list(interfaces)) {
      try {
        if (networkInterface.isLoopback()
            && !validationHost.equals("localhost")
            && !validationHost.startsWith("127.")) {
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
          // ignored
        } finally {
          if (socket != null) {
            try {
              socket.close();
            } catch (IOException ignored) {
              // ignored
            }
          }
        }
      }
    }

    return UNKNOWN_HOST;
  }

  private final String validationAddress;
  private final int validationPort;
  private final String runtimeName;
  private final AtomicReference<String> identity = new AtomicReference<>(null);
  private final AtomicBoolean validIdentity = new AtomicBoolean(false);
  private final AtomicLong refreshTime = new AtomicLong(0);
  private final Lock refreshLock = new ReentrantLock();
  private final long refreshInterval;

  public DefaultNodeIdentity() {
    this("www.google.com", 80);
  }

  public DefaultNodeIdentity(String validationAddress, int validationPort) {
    this(validationAddress, validationPort, REFRESH_INTERVAL);
  }

  public DefaultNodeIdentity(String validationAddress, int validationPort, long refreshInterval) {
    this.validationAddress = validationAddress;
    this.validationPort = validationPort;
    this.runtimeName = ManagementFactory.getRuntimeMXBean().getName();
    this.refreshInterval = refreshInterval;
    loadIdentity();
  }

  @Override
  public String getNodeIdentity() {
    if (!validIdentity.get() && shouldRefresh()) {
      refreshLock.lock();
      try {
        if (!validIdentity.get() && shouldRefresh()) {
          loadIdentity();
        }
      } finally {
        refreshLock.unlock();
      }
    }
    return identity.get();
  }

  private boolean shouldRefresh() {
    return System.currentTimeMillis() - refreshTime.get() > refreshInterval;
  }

  private void loadIdentity() {
    identity.set(
        String.format("%s:%s", getHostName(validationAddress, validationPort), runtimeName));
    validIdentity.set(!identity.get().contains(UNKNOWN_HOST));
    refreshTime.set(System.currentTimeMillis());
  }
}
