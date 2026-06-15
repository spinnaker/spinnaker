/*
 * Copyright 2025 Netflix, Inc.
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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.core.SdkClient;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
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

  public AwsSdkV2ClientSupplier(
      RateLimiterSupplier rateLimiterSupplier,
      Registry registry,
      RetryPolicy retryPolicy,
      AWSProxy proxy) {
    this.rateLimiterSupplier = requireNonNull(rateLimiterSupplier, "rateLimiterSupplier");
    this.registry = requireNonNull(registry, "registry");
    this.retryPolicy = requireNonNull(retryPolicy, "retryPolicy");
    this.proxy = proxy;
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
  @SuppressWarnings("unchecked")
  public <C extends SdkClient> C getClient(
      Supplier<? extends AwsClientBuilder<?, C>> builderSupplier,
      Class<C> clientType,
      AwsCredentialsProvider credentialsProvider,
      String region,
      String account) {
    requireNonNull(builderSupplier, "builderSupplier");
    requireNonNull(credentialsProvider, "credentialsProvider");
    requireNonNull(region, "region");
    requireNonNull(account, "account");

    V2ClientKey key = new V2ClientKey(builderSupplier, credentialsProvider, region, account);

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
          createRateLimitInterceptor(key.builderClass, key.account, key.region);

      ClientOverrideConfiguration.Builder overrideConfig =
          ClientOverrideConfiguration.builder()
              .addExecutionInterceptor(rateLimitInterceptor)
              .retryPolicy(retryPolicy)
              .addMetricPublisher(new SpectatorMetricPublisher(registry));

      builder.overrideConfiguration(overrideConfig.build());

      // Proxy configuration
      if (proxy != null && proxy.isProxyConfigMode()) {
        ProxyConfiguration proxyConfiguration = buildProxyConfiguration(proxy);
        if (builder
            instanceof
            software.amazon.awssdk.core.client.builder.SdkSyncClientBuilder<?, ?>
            syncBuilder) {
          syncBuilder.httpClient(
              ApacheHttpClient.builder().proxyConfiguration(proxyConfiguration).build());
        }
      }

      key.getRegion()
          .ifPresent(
              r -> {
                log.debug("V2ClientCacheLoader.load: key '{}', region '{}'", key, r);
                builder.region(Region.of(r));
              });

      return (SdkClient) builder.build();
    }
  }

  private RateLimitingExecutionInterceptor createRateLimitInterceptor(
      Class<?> builderClass, String account, String region) {
    RateLimiter limiter = rateLimiterSupplier.getRateLimiter(builderClass, account, region);
    Counter rateLimitCounter =
        registry.counter(
            "amazonClientProvider.v2.rateLimitDelayMillis",
            "clientType",
            builderClass.getSimpleName(),
            "account",
            account,
            "region",
            region == null ? "UNSPECIFIED" : region);
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
   * <p>Identity is based on: - The builder supplier instance (same reference == same service type)
   * - The credentials provider identity (same reference == same account/role) - The region string -
   * The account name (affects rate limiter selection)
   */
  static final class V2ClientKey {
    /**
     * We use reference identity of the supplier to distinguish service types. Callers are expected
     * to pass a stable method-reference (e.g. {@code EcsClient::builder}) — those are recreated per
     * call so we capture the class-level method handle target via its class.
     *
     * <p>In practice two callers passing {@code EcsClient::builder} will produce supplier instances
     * whose functional-interface implementations share the same {@code getClass()} because the JVM
     * generates one lambda class per call site. To make caching correct we capture {@code
     * builderSupplier.get().getClass()} (the builder's concrete class) as the service discriminator
     * rather than the supplier itself.
     */
    private final Class<?> builderClass;

    private final Supplier<? extends AwsClientBuilder<?, ?>> builderSupplier;
    private final AwsCredentialsProvider credentialsProvider;
    private final String region;
    private final String account;

    V2ClientKey(
        Supplier<? extends AwsClientBuilder<?, ?>> builderSupplier,
        AwsCredentialsProvider credentialsProvider,
        String region,
        String account) {
      this.builderSupplier = requireNonNull(builderSupplier);
      this.credentialsProvider = requireNonNull(credentialsProvider);
      this.region = region;
      this.account = requireNonNull(account);
      // Resolve builder class eagerly so equals/hashCode don't need to call get() repeatedly.
      this.builderClass = builderSupplier.get().getClass();
    }

    Optional<String> getRegion() {
      return Optional.ofNullable(region);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof V2ClientKey)) return false;
      V2ClientKey that = (V2ClientKey) o;
      return builderClass.equals(that.builderClass)
          && credentialsProvider == that.credentialsProvider
          && Objects.equals(region, that.region)
          && Objects.equals(account, that.account);
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          builderClass, System.identityHashCode(credentialsProvider), region, account);
    }

    @Override
    public String toString() {
      return "V2ClientKey{"
          + "builderClass="
          + builderClass.getSimpleName()
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
