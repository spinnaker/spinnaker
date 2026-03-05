/*
 * Copyright 2025 OpsMx,Inc.
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
package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.distributed.google;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.Random;
import javax.net.ServerSocketFactory;
import org.springframework.util.Assert;

/**
 * Simple utility for finding available TCP ports on {@code localhost}.
 *
 * <p>This is a limited form of {@code org.springframework.util.SocketUtils}, which has been
 * deprecated since Spring Framework 5.3.16 and removed in Spring Framework 6.0.
 */
public class SocketUtils {

  /** The minimum value for port ranges used when finding an available TCP port. */
  static final int PORT_RANGE_MIN = 1024;

  /** The maximum value for port ranges used when finding an available TCP port. */
  static final int PORT_RANGE_MAX = 65535;

  private static final int PORT_RANGE_PLUS_ONE = PORT_RANGE_MAX - PORT_RANGE_MIN + 1;

  private static final int MAX_ATTEMPTS = 1_000;

  private static final Random random = new Random(System.nanoTime());

  private SocketUtils() {}

  /**
   * Find an available TCP port randomly selected from the range [1024, 65535].
   *
   * @return an available TCP port number
   * @throws IllegalStateException if no available port could be found
   */
  public static int findAvailableTcpPort() {
    int candidatePort;
    int searchCounter = 0;
    do {
      Assert.state(
          ++searchCounter <= MAX_ATTEMPTS,
          () ->
              String.format(
                  "Could not find an available TCP port in the range [%d, %d] after %d attempts",
                  PORT_RANGE_MIN, PORT_RANGE_MAX, MAX_ATTEMPTS));
      candidatePort = PORT_RANGE_MIN + random.nextInt(PORT_RANGE_PLUS_ONE);
    } while (!isPortAvailable(candidatePort));

    return candidatePort;
  }

  /**
   * Determine if the specified TCP port is currently available on {@code localhost}.
   *
   * <p>Package-private solely for testing purposes.
   */
  private static boolean isPortAvailable(int port) {
    try {
      ServerSocket serverSocket =
          ServerSocketFactory.getDefault()
              .createServerSocket(port, 1, InetAddress.getByName("localhost"));
      serverSocket.close();
      return true;
    } catch (Exception ex) {
      return false;
    }
  }
}
