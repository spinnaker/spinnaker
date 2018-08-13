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

import com.amazonaws.AmazonServiceException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.handlers.RequestHandler2;
import com.amazonaws.regions.Regions;
import com.amazonaws.retry.RetryPolicy;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.RateLimiter;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.aws.security.AWSProxy;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixSTSAssumeRoleSessionCredentialsProvider;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

/**
 * Factory for shared instances of AWS SDK clients.
 */
public class AwsSdkClientSupplier {

  private final Registry registry;
  private final LoadingCache<AmazonClientKey<?>, ?> awsSdkClients;
  private final RateLimiterSupplier rateLimiterSupplier;

  public AwsSdkClientSupplier(RateLimiterSupplier rateLimiterSupplier, Registry registry, RetryPolicy retryPolicy, List<RequestHandler2> requestHandlers, AWSProxy proxy, boolean useGzip) {
    this.rateLimiterSupplier = Objects.requireNonNull(rateLimiterSupplier);
    this.registry = Objects.requireNonNull(registry);
    awsSdkClients = CacheBuilder
      .newBuilder()
      .recordStats()
      .expireAfterAccess(10, TimeUnit.MINUTES)
      .build(
        new SdkClientCacheLoader(retryPolicy, requestHandlers, proxy, useGzip)
      );
    LoadingCacheMetrics.instrument("awsSdkClientSupplier", registry, awsSdkClients);
  }

  public <T> T getClient(Class<? extends AwsClientBuilder<?, T>> impl, Class<T> iface, String account, AWSCredentialsProvider awsCredentialsProvider, String region) {
    final RequestHandler2 handler = getRateLimiterHandler(iface, account, region);
    final AmazonClientKey<T> key = new AmazonClientKey<>(impl, awsCredentialsProvider, region, handler);

    try {
      return iface.cast(awsSdkClients.get(key));
    } catch (ExecutionException executionException) {
      if (executionException.getCause() instanceof RuntimeException) {
        throw (RuntimeException) executionException.getCause();
      }
      throw new RuntimeException("Failed creating amazon client", executionException.getCause());
    }
  }

  private RequestHandler2 getRateLimiterHandler(Class<?> sdkInterface, String account, String region) {
    final RateLimiter limiter = rateLimiterSupplier.getRateLimiter(sdkInterface, account, region);
    final Counter rateLimitCounter = registry.counter("amazonClientProvider.rateLimitDelayMillis",
      "clientType", sdkInterface.getSimpleName(),
      "account", account,
      "region", region == null ? "UNSPECIFIED" : region);
    return new RateLimitingRequestHandler(rateLimitCounter, limiter);
  }

  private static class SdkClientCacheLoader extends CacheLoader<AmazonClientKey<?>, Object> {
    private final RetryPolicy retryPolicy;
    private final List<RequestHandler2> requestHandlers;
    private final AWSProxy proxy;
    private final boolean useGzip;

    public SdkClientCacheLoader(RetryPolicy retryPolicy, List<RequestHandler2> requestHandlers, AWSProxy proxy, boolean useGzip) {
      this.retryPolicy = Objects.requireNonNull(retryPolicy);
      this.requestHandlers = requestHandlers == null ? Collections.emptyList() : ImmutableList.copyOf(requestHandlers);
      this.proxy = proxy;
      this.useGzip = useGzip;
    }

    @Override
    public Object load(AmazonClientKey<?> key) throws Exception {
      Method m = key.implClass.getDeclaredMethod("standard");
      AwsClientBuilder<?, ?> builder = key.implClass.cast(m.invoke(null));

      ClientConfiguration clientConfiguration = new ClientConfiguration();
      clientConfiguration.setRetryPolicy(getRetryPolicy(key));
      clientConfiguration.setUseGzip(useGzip);
      clientConfiguration.setUserAgentSuffix("spinnaker");

      if (proxy != null && proxy.isProxyConfigMode()) {
        proxy.apply(clientConfiguration);
      }

      builder.withCredentials(key.awsCredentialsProvider)
        .withClientConfiguration(clientConfiguration);
      getRequestHandlers(key).ifPresent(builder::withRequestHandlers);
      builder.withRegion(key.getRegion().orElseGet(() -> new SpinnakerAwsRegionProvider().getRegion()));

      return builder.build();
    }

    private Optional<RequestHandler2[]> getRequestHandlers(AmazonClientKey<?> key) {
      List<RequestHandler2> handlers = new ArrayList<>(requestHandlers.size() + 1);
      key.getRequestHandler().ifPresent(handlers::add);
      handlers.addAll(requestHandlers);
      if (handlers.isEmpty()) {
        return Optional.empty();
      }
      return Optional.of(handlers.toArray(new RequestHandler2[handlers.size()]));
    }

    private RetryPolicy getRetryPolicy(AmazonClientKey<?> key) {

      if (!(key.getAwsCredentialsProvider() instanceof NetflixSTSAssumeRoleSessionCredentialsProvider)) {
        return retryPolicy;
      }

      final RetryPolicy.RetryCondition delegatingRetryCondition = (originalRequest, exception, retriesAttempted) -> {
        NetflixSTSAssumeRoleSessionCredentialsProvider stsCredentialsProvider = (NetflixSTSAssumeRoleSessionCredentialsProvider) key.getAwsCredentialsProvider();
        if (exception instanceof AmazonServiceException) {
          ((AmazonServiceException) exception).getHttpHeaders().put("targetAccountId", stsCredentialsProvider.getAccountId());
        }
        return retryPolicy.getRetryCondition().shouldRetry(originalRequest, exception, retriesAttempted);
      };

      return new RetryPolicy(
        delegatingRetryCondition,
        retryPolicy.getBackoffStrategy(),
        retryPolicy.getMaxErrorRetry(),
        retryPolicy.isMaxErrorRetryInClientConfigHonored()
      );

    }
  }

  private static class AmazonClientKey<T> {
    private final Class<? extends AwsClientBuilder<?, T>> implClass;
    private final AWSCredentialsProvider awsCredentialsProvider;
    private final Regions region;
    private final RequestHandler2 requestHandler;

    public AmazonClientKey(Class<? extends AwsClientBuilder<?, T>> implClass, AWSCredentialsProvider awsCredentialsProvider, String region, RequestHandler2 requestHandler) {
      this.implClass = requireNonNull(implClass);
      this.awsCredentialsProvider = requireNonNull(awsCredentialsProvider);
      this.region = region == null ? null : Regions.fromName(region);
      this.requestHandler = requestHandler;
    }

    public Class<? extends AwsClientBuilder<?, T>> getImplClass() {
      return implClass;
    }

    public AWSCredentialsProvider getAwsCredentialsProvider() {
      return awsCredentialsProvider;
    }

    public Optional<String> getRegion() {
      return Optional.ofNullable(region).map(Regions::getName);
    }

    public Optional<RequestHandler2> getRequestHandler() {
      return Optional.ofNullable(requestHandler);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      AmazonClientKey<?> that = (AmazonClientKey<?>) o;

      if (!implClass.equals(that.implClass)) return false;
      if (!awsCredentialsProvider.equals(that.awsCredentialsProvider)) return false;
      if (region != that.region) return false;
      return requestHandler != null ? requestHandler.equals(that.requestHandler) : that.requestHandler == null;
    }

    @Override
    public int hashCode() {
      int result = implClass.hashCode();
      result = 31 * result + awsCredentialsProvider.hashCode();
      result = 31 * result + (region != null ? region.hashCode() : 0);
      result = 31 * result + (requestHandler != null ? requestHandler.hashCode() : 0);
      return result;
    }
  }
}
