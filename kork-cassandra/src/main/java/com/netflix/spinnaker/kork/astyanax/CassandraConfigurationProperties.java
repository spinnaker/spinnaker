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

package com.netflix.spinnaker.kork.astyanax;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("cassandra")
public class CassandraConfigurationProperties {
  private String host = "127.0.0.1";
  private int port = 9160;
  private int storagePort = 7000;
  private int asyncExecutorPoolSize = 5;

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public int getStoragePort() {
    return storagePort;
  }

  public void setStoragePort(int storagePort) {
    this.storagePort = storagePort;
  }

  public int getAsyncExecutorPoolSize() {
    return asyncExecutorPoolSize;
  }

  public void setAsyncExecutorPoolSize(int asyncExecutorPoolSize) {
    this.asyncExecutorPoolSize = asyncExecutorPoolSize;
  }
}
