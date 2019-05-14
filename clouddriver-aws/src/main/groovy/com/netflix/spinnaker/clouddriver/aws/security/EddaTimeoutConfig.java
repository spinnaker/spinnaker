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

package com.netflix.spinnaker.clouddriver.aws.security;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class EddaTimeoutConfig {
  public static final EddaTimeoutConfig DEFAULT = new Builder().build();
  private static final long EDDA_RETRY_BASE_MILLIS = 50L;
  private static final int EDDA_RETRY_BACKOFF_MILLIS = 10;
  private static final int EDDA_RETRY_MAX_ATTEMPTS = 3;
  private static final int EDDA_CONNECT_TIMEOUT_MILLIS = 1000;
  private static final int EDDA_CONNECT_REQUEST_TIMEOUT_MILLIS = 10000;
  private static final int EDDA_SOCKET_TIMEOUT_MILLIS = 5000;

  private final long retryBase;
  private final int backoffMillis;
  private final int maxAttempts;
  private final int connectTimeout;
  private final int connectionRequestTimeout;
  private final int socketTimeout;
  private final Set<String> disabledRegions;
  private boolean albEnabled;

  public EddaTimeoutConfig(
      long retryBase,
      int backoffMillis,
      int maxAttempts,
      int connectTimeout,
      int connectionRequestTimeout,
      int socketTimeout,
      Collection<String> disabledRegions,
      boolean albEnabled) {
    this.retryBase = retryBase;
    this.backoffMillis = backoffMillis;
    this.maxAttempts = maxAttempts;
    this.connectTimeout = connectTimeout;
    this.connectionRequestTimeout = connectionRequestTimeout;
    this.socketTimeout = socketTimeout;
    this.disabledRegions =
        disabledRegions == null || disabledRegions.isEmpty()
            ? Collections.emptySet()
            : Collections.unmodifiableSet(new LinkedHashSet<>(disabledRegions));
    this.albEnabled = albEnabled;
  }

  public long getRetryBase() {
    return retryBase;
  }

  public int getBackoffMillis() {
    return backoffMillis;
  }

  public int getMaxAttempts() {
    return maxAttempts;
  }

  public int getConnectTimeout() {
    return connectTimeout;
  }

  public int getConnectionRequestTimeout() {
    return connectionRequestTimeout;
  }

  public int getSocketTimeout() {
    return socketTimeout;
  }

  public Set<String> getDisabledRegions() {
    return disabledRegions;
  }

  public boolean getAlbEnabled() {
    return albEnabled;
  }

  public static class Builder {
    private long retryBase;
    private int backoffMillis;
    private int maxAttempts;
    private int connectTimeout;
    private int connectionRequestTimeout;
    private int socketTimeout;
    private List<String> disabledRegions;
    private boolean albEnabled;

    public Builder() {
      this.retryBase = EDDA_RETRY_BASE_MILLIS;
      this.backoffMillis = EDDA_RETRY_BACKOFF_MILLIS;
      this.maxAttempts = EDDA_RETRY_MAX_ATTEMPTS;
      this.connectTimeout = EDDA_CONNECT_TIMEOUT_MILLIS;
      this.connectionRequestTimeout = EDDA_CONNECT_REQUEST_TIMEOUT_MILLIS;
      this.socketTimeout = EDDA_SOCKET_TIMEOUT_MILLIS;
      this.disabledRegions = null;
      this.albEnabled = false;
    }

    public EddaTimeoutConfig build() {
      return new EddaTimeoutConfig(
          retryBase,
          backoffMillis,
          maxAttempts,
          connectTimeout,
          connectionRequestTimeout,
          socketTimeout,
          disabledRegions,
          albEnabled);
    }

    public long getRetryBase() {
      return retryBase;
    }

    public void setRetryBase(long retryBase) {
      this.retryBase = retryBase;
    }

    public int getBackoffMillis() {
      return backoffMillis;
    }

    public void setBackoffMillis(int backoffMillis) {
      this.backoffMillis = backoffMillis;
    }

    public int getMaxAttempts() {
      return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
      this.maxAttempts = maxAttempts;
    }

    public int getConnectTimeout() {
      return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
      this.connectTimeout = connectTimeout;
    }

    public int getConnectionRequestTimeout() {
      return connectionRequestTimeout;
    }

    public void setConnectionRequestTimeout(int connectionRequestTimeout) {
      this.connectionRequestTimeout = connectionRequestTimeout;
    }

    public int getSocketTimeout() {
      return socketTimeout;
    }

    public void setSocketTimeout(int socketTimeout) {
      this.socketTimeout = socketTimeout;
    }

    public List<String> getDisabledRegions() {
      return disabledRegions;
    }

    public void setDisabledRegions(List<String> disabledRegions) {
      this.disabledRegions = disabledRegions;
    }

    public boolean getAlbEnabled() {
      return albEnabled;
    }

    public void setAlbEnabled(boolean albEnabled) {
      this.albEnabled = albEnabled;
    }
  }
}
