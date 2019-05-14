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

package com.netflix.spinnaker.clouddriver.aws.security.sdkclient;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.RateLimiter;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider;
import com.netflix.spinnaker.clouddriver.core.limits.ServiceLimitConfiguration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

/** Factory for shared RateLimiters by SDK client interface/account/region. */
public class RateLimiterSupplier {

  private final LoadingCache<RateLimitKey, RateLimiter> rateLimiters;

  public RateLimiterSupplier(
      ServiceLimitConfiguration serviceLimitConfiguration, Registry registry) {
    rateLimiters =
        CacheBuilder.newBuilder()
            .recordStats()
            .build(new RateLimitCacheLoader(serviceLimitConfiguration));
    LoadingCacheMetrics.instrument("rateLimiterSupplier", registry, rateLimiters);
  }

  public RateLimiter getRateLimiter(Class<?> implementation, String account, String region) {
    try {
      return rateLimiters.get(new RateLimitKey(implementation, account, region));
    } catch (ExecutionException executionException) {
      if (executionException.getCause() instanceof RuntimeException) {
        throw (RuntimeException) executionException.getCause();
      }
      throw new RuntimeException("Failed creating rate limiter", executionException.getCause());
    }
  }

  private static class RateLimitCacheLoader extends CacheLoader<RateLimitKey, RateLimiter> {
    private static final double DEFAULT_LIMIT = 10.0d;

    private final ServiceLimitConfiguration serviceLimitConfiguration;
    private final double defaultLimit;

    public RateLimitCacheLoader(ServiceLimitConfiguration serviceLimitConfiguration) {
      this(serviceLimitConfiguration, DEFAULT_LIMIT);
    }

    public RateLimitCacheLoader(
        ServiceLimitConfiguration serviceLimitConfiguration, double defaultLimit) {
      this.serviceLimitConfiguration = Objects.requireNonNull(serviceLimitConfiguration);
      this.defaultLimit = defaultLimit;
    }

    @Override
    public RateLimiter load(RateLimitKey key) throws Exception {
      double rateLimit =
          serviceLimitConfiguration.getLimit(
              ServiceLimitConfiguration.API_RATE_LIMIT,
              key.implementationClass.getSimpleName(),
              key.account,
              AmazonCloudProvider.ID,
              defaultLimit);

      return RateLimiter.create(rateLimit);
    }
  }

  private static class RateLimitKey {
    private final Class<?> implementationClass;
    private final String account;
    private final String region;

    public RateLimitKey(Class<?> implementationClass, String account, String region) {
      this.implementationClass = Objects.requireNonNull(implementationClass);
      this.account = Objects.requireNonNull(account);
      this.region = region;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      RateLimitKey that = (RateLimitKey) o;

      if (!implementationClass.equals(that.implementationClass)) return false;
      if (!account.equals(that.account)) return false;
      return region != null ? region.equals(that.region) : that.region == null;
    }

    @Override
    public int hashCode() {
      int result = implementationClass.hashCode();
      result = 31 * result + account.hashCode();
      result = 31 * result + (region != null ? region.hashCode() : 0);
      return result;
    }
  }
}
