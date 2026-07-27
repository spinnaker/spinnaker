/*
 * Copyright 2026 spinnaker.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

import static java.util.Objects.requireNonNull;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.RateLimiter;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.aws.security.AWSProxy;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.core.SdkClient;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.client.config.SdkAdvancedClientOption;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.apache.ProxyConfiguration;
import software.amazon.awssdk.regions.Region;

/**
 * Factory for shared instances of AWS SDK v2 clients. Mirrors the caching semantics of {@link
 * AwsSdkClientSupplier} but targets the {@code software.amazon.awssdk} API.
 *
 * <p>Clients are keyed by (service builder supplier, credentials provider identity, region,
 * account) and evicted after 10 minutes of inactivity. A {@link Supplier} of the service builder is
 * used instead of a builder class reference because v2 builders are not reflectively instantiatable
 * via a single static {@code standard()} convention.
 *
 * <p>Edda note: v2 clients returned by this class do not have Edda read-through support. Edda
 * interception works by wrapping v1 service interfaces (e.g. {@code AmazonECS}) in a JDK dynamic
 * proxy backed by {@link
 * com.netflix.spinnaker.clouddriver.aws.security.sdkclient.AmazonClientInvocationHandler}, which
 * hooks into call dispatch via v1 {@code RequestHandler2}. The v2 client types ({@code EcsClient}
 * etc.) are unrelated classes that the existing proxy cannot implement, and v2 uses a different
 * interceptor API ({@code ExecutionInterceptor}) with no equivalent Edda integration. Consumers
 * migrating to v2 should read directly from the AWS APIs rather than through Edda.
 */
@Slf4j
public class AwsSdkV2ClientSupplier {

  private final LoadingCache<V2ClientKey, SdkClient> clientCache;
  private final RateLimiterSupplier rateLimiterSupplier;
  private final Registry registry;
  private final RetryPolicy retryPolicy;
  private final AWSProxy proxy;
  private final boolean addSpinnakerUserToUserAgent;
  private final List<ExecutionInterceptor> additionalInterceptors;

  public AwsSdkV2ClientSupplier(
      RateLimiterSupplier rateLimiterSupplier,
      Registry registry,
      RetryPolicy retryPolicy,
      AWSProxy proxy,
      boolean addSpinnakerUserToUserAgent) {
    this(
        rateLimiterSupplier,
        registry,
        retryPolicy,
        proxy,
        addSpinnakerUserToUserAgent,
        Collections.emptyList());
  }

  /**
   * @param additionalInterceptors extra {@link ExecutionInterceptor}s to attach to every v2 client.
   *     This is the v2 equivalent of the v1 {@code List<RequestHandler2>} extension point — plugins
   *     or configuration can inject interceptors here.
   */
  public AwsSdkV2ClientSupplier(
      RateLimiterSupplier rateLimiterSupplier,
      Registry registry,
      RetryPolicy retryPolicy,
      AWSProxy proxy,
      boolean addSpinnakerUserToUserAgent,
      List<ExecutionInterceptor> additionalInterceptors) {
    this.rateLimiterSupplier = requireNonNull(rateLimiterSupplier, "rateLimiterSupplier");
    this.registry = requireNonNull(registry, "registry");
    this.retryPolicy = requireNonNull(retryPolicy, "retryPolicy");
    this.proxy = proxy;
    this.addSpinnakerUserToUserAgent = addSpinnakerUserToUserAgent;
    this.additionalInterceptors =
        List.copyOf(requireNonNull(additionalInterceptors, "additionalInterceptors"));
    this.clientCache =
        CacheBuilder.newBuilder()
            .recordStats()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build(new V2ClientCacheLoader());
    LoadingCacheMetrics.instrument("awsSdkV2ClientSupplier", registry, clientCache);
  }

  /**
   * Returns a cached v2 SDK client for the given service.
   *
   * @param <C> the v2 service client type (e.g. {@code EcsClient})
   * @param builderSupplier factory that creates a fresh builder for the service (e.g. {@code
   *     EcsClient::builder}). Must produce a new builder instance on each call.
   * @param clientType the expected v2 client interface — used only for casting; not a cache key
   *     component.
   * @param credentialsProvider v2 credentials for this account
   * @param region AWS region string; must not be {@code null} — AWS SDK v2 resolves region eagerly
   *     at client build time, so a missing region will throw at construction, not request time.
   * @param account the Spinnaker account name, used for rate-limiter resolution
   * @return a shared, cached client instance
   */
  public <C extends SdkClient> C getClient(
      Supplier<? extends AwsClientBuilder<?, C>> builderSupplier,
      Class<C> clientType,
      AwsCredentialsProvider credentialsProvider,
      String region,
      String account) {
    return getClient(builderSupplier, clientType, credentialsProvider, region, account, null);
  }

  /**
   * Returns a cached v2 SDK client, applying optional per-service {@link
   * AwsSdkV2ClientConfiguration} (retry count, socket timeout, TCP keep-alive).
   *
   * <p>The client config does not participate in cache identity: like the v1 behavior, tuning is
   * captured on first build and shared for subsequent lookups of the same (clientType, credentials,
   * region, account) key.
   */
  @SuppressWarnings("unchecked")
  public <C extends SdkClient> C getClient(
      Supplier<? extends AwsClientBuilder<?, C>> builderSupplier,
      Class<C> clientType,
      AwsCredentialsProvider credentialsProvider,
      String region,
      String account,
      AwsSdkV2ClientConfiguration clientConfiguration) {
    requireNonNull(builderSupplier, "builderSupplier");
    requireNonNull(clientType, "clientType");
    requireNonNull(credentialsProvider, "credentialsProvider");
    requireNonNull(region, "region");
    requireNonNull(account, "account");

    V2ClientKey key =
        new V2ClientKey(
            clientType, builderSupplier, credentialsProvider, region, account, clientConfiguration);

    try {
      return clientType.cast(clientCache.get(key));
    } catch (ExecutionException e) {
      if (e.getCause() instanceof RuntimeException) {
        throw (RuntimeException) e.getCause();
      }
      throw new RuntimeException("Failed creating AWS SDK v2 client", e.getCause());
    }
  }

  /**
   * Overload without account — delegates with a default "unknown" account. Prefer the overload with
   * explicit account for proper rate-limit resolution.
   */
  @SuppressWarnings("unchecked")
  public <C extends SdkClient> C getClient(
      Supplier<? extends AwsClientBuilder<?, C>> builderSupplier,
      Class<C> clientType,
      AwsCredentialsProvider credentialsProvider,
      String region) {
    return getClient(builderSupplier, clientType, credentialsProvider, region, "unknown");
  }

  // -------------------------------------------------------------------------
  // Cache loader
  // -------------------------------------------------------------------------

  private class V2ClientCacheLoader extends CacheLoader<V2ClientKey, SdkClient> {
    @Override
    public SdkClient load(V2ClientKey key) {
      AwsClientBuilder<?, ?> builder = key.builderSupplier.get();
      builder.credentialsProvider(key.credentialsProvider);

      // Rate limiting interceptor
      RateLimitingExecutionInterceptor rateLimitInterceptor =
          createRateLimitInterceptor(key.clientType, key.account, key.region);

      ClientOverrideConfiguration.Builder overrideConfig =
          ClientOverrideConfiguration.builder()
              .addExecutionInterceptor(rateLimitInterceptor)
              .retryPolicy(resolveRetryPolicy(key.clientConfiguration))
              .addMetricPublisher(new SpectatorMetricPublisher(registry))
              .putAdvancedOption(SdkAdvancedClientOption.USER_AGENT_SUFFIX, "spinnaker");

      if (addSpinnakerUserToUserAgent) {
        overrideConfig.addExecutionInterceptor(new SpinnakerUserAgentExecutionInterceptor());
      }

      for (ExecutionInterceptor interceptor : additionalInterceptors) {
        overrideConfig.addExecutionInterceptor(interceptor);
      }

      builder.overrideConfiguration(overrideConfig.build());

      // HTTP client configuration (proxy + per-service tuning). Only override the builder's default
      // HTTP client when there is something to customize.
      ApacheHttpClient.Builder httpClientBuilder = buildHttpClient(key.clientConfiguration);
      if (httpClientBuilder != null
          && builder
              instanceof
              software.amazon.awssdk.core.client.builder.SdkSyncClientBuilder<?, ?>
              syncBuilder) {
        syncBuilder.httpClient(httpClientBuilder.build());
      }

      log.debug("V2ClientCacheLoader.load: key '{}', region '{}'", key, key.region);
      builder.region(Region.of(key.region));

      return (SdkClient) builder.build();
    }
  }

  /**
   * Returns the retry policy to use, overriding the number of retries from the shared policy when a
   * per-service {@link AwsSdkV2ClientConfiguration#getMaxErrorRetry()} is set. Package-private for
   * testability.
   */
  RetryPolicy resolveRetryPolicy(AwsSdkV2ClientConfiguration clientConfiguration) {
    if (clientConfiguration == null || clientConfiguration.getMaxErrorRetry() == null) {
      return retryPolicy;
    }
    return retryPolicy.toBuilder().numRetries(clientConfiguration.getMaxErrorRetry()).build();
  }

  /**
   * Builds an {@link ApacheHttpClient.Builder} combining the (optional) proxy configuration and the
   * (optional) per-service tuning. Returns {@code null} when neither is present, signaling that the
   * SDK default HTTP client should be used. Package-private for testability.
   */
  ApacheHttpClient.Builder buildHttpClient(AwsSdkV2ClientConfiguration clientConfiguration) {
    boolean hasProxy = proxy != null && proxy.isProxyConfigMode();
    boolean hasClientConfig = clientConfiguration != null;
    if (!hasProxy && !hasClientConfig) {
      return null;
    }

    ApacheHttpClient.Builder httpClientBuilder = ApacheHttpClient.builder();
    if (hasProxy) {
      httpClientBuilder.proxyConfiguration(buildProxyConfiguration(proxy));
    }
    if (hasClientConfig) {
      if (clientConfiguration.getSocketTimeout() != null) {
        httpClientBuilder.socketTimeout(clientConfiguration.getSocketTimeout());
      }
      httpClientBuilder.tcpKeepAlive(clientConfiguration.isTcpKeepAlive());
    }
    return httpClientBuilder;
  }

  private RateLimitingExecutionInterceptor createRateLimitInterceptor(
      Class<?> clientType, String account, String region) {
    RateLimiter limiter = rateLimiterSupplier.getRateLimiter(clientType, account, region);
    Counter rateLimitCounter =
        registry.counter(
            "amazonClientProvider.v2.rateLimitDelayMillis",
            "clientType",
            clientType.getSimpleName(),
            "account",
            account,
            "region",
            region);
    return new RateLimitingExecutionInterceptor(rateLimitCounter, limiter);
  }

  /**
   * Builds a v2 {@link ProxyConfiguration} from the v1 {@link AWSProxy} settings. Package-private
   * for testability.
   */
  static ProxyConfiguration buildProxyConfiguration(AWSProxy proxy) {
    String scheme = "HTTPS".equalsIgnoreCase(proxy.getProtocol()) ? "https" : "http";
    ProxyConfiguration.Builder proxyConfig =
        ProxyConfiguration.builder()
            .endpoint(
                URI.create(scheme + "://" + proxy.getProxyHost() + ":" + proxy.getProxyPort()));
    if (proxy.getProxyUsername() != null) {
      proxyConfig.username(proxy.getProxyUsername());
    }
    if (proxy.getProxyPassword() != null) {
      proxyConfig.password(proxy.getProxyPassword());
    }
    return proxyConfig.build();
  }

  // -------------------------------------------------------------------------
  // Cache key
  // -------------------------------------------------------------------------

  /**
   * Identifies a unique v2 client in the cache.
   *
   * <p>Identity is based on: - The client type class (e.g. {@code EcsClient.class}) - The
   * credentials provider identity (same reference == same account/role) - The region string - The
   * account name (affects rate limiter selection) - The per-service {@link
   * AwsSdkV2ClientConfiguration} (value equality), so callers can obtain distinct clients tuned
   * differently for the same account, region, and service.
   */
  static final class V2ClientKey {
    /** The v2 client interface class, used as the service type discriminator. */
    private final Class<?> clientType;

    private final Supplier<? extends AwsClientBuilder<?, ?>> builderSupplier;
    private final AwsCredentialsProvider credentialsProvider;
    private final String region;
    private final String account;

    /**
     * Per-service tuning applied at build time. Included in {@link #equals}/{@link #hashCode} by
     * value so that different tuning for the same (clientType, credentials, region, account)
     * resolves to distinct cached clients (e.g. a short-timeout client vs a long-timeout invoke
     * client). Being a Lombok {@code @Value}, equal field values reuse the same cached instance.
     */
    private final AwsSdkV2ClientConfiguration clientConfiguration;

    V2ClientKey(
        Class<?> clientType,
        Supplier<? extends AwsClientBuilder<?, ?>> builderSupplier,
        AwsCredentialsProvider credentialsProvider,
        String region,
        String account,
        AwsSdkV2ClientConfiguration clientConfiguration) {
      this.clientType = requireNonNull(clientType);
      this.builderSupplier = requireNonNull(builderSupplier);
      this.credentialsProvider = requireNonNull(credentialsProvider);
      this.region = requireNonNull(region);
      this.account = requireNonNull(account);
      this.clientConfiguration = clientConfiguration;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof V2ClientKey)) return false;
      V2ClientKey that = (V2ClientKey) o;
      return clientType.equals(that.clientType)
          // Identity comparison: same provider reference == same account/role credentials.
          && credentialsProvider == that.credentialsProvider
          && region.equals(that.region)
          && account.equals(that.account)
          // Value comparison: different tuning => different cached client.
          && Objects.equals(clientConfiguration, that.clientConfiguration);
    }

    @Override
    public int hashCode() {
      // identityHashCode matches the identity (==) comparison used in equals() for
      // credentialsProvider.
      return Objects.hash(
          clientType,
          System.identityHashCode(credentialsProvider),
          region,
          account,
          clientConfiguration);
    }

    @Override
    public String toString() {
      return "V2ClientKey{"
          + "clientType="
          + clientType.getSimpleName()
          + ", credentialsProvider="
          + credentialsProvider.getClass().getSimpleName()
          + ", region="
          + region
          + ", account="
          + account
          + '}';
    }
  }
}
