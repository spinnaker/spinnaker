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

import static java.util.Objects.requireNonNull;

import com.amazonaws.client.builder.AwsClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.aws.security.EddaTemplater;
import com.netflix.spinnaker.clouddriver.aws.security.EddaTimeoutConfig;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import java.lang.reflect.Proxy;
import java.util.Map;
import org.apache.http.client.HttpClient;

/**
 * Constructs a JDK dynamic proxy for an AWS service interface that (if enabled for an account) will
 * delegate read requests to Edda and otherwise fallback to the underlying SDK client.
 */
public class ProxyHandlerBuilder {
  private final AwsSdkClientSupplier awsSdkClientSupplier;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final EddaTemplater eddaTemplater;
  private final EddaTimeoutConfig eddaTimeoutConfig;
  private final Registry registry;

  public ProxyHandlerBuilder(
      AwsSdkClientSupplier awsSdkClientSupplier,
      HttpClient httpClient,
      ObjectMapper objectMapper,
      EddaTemplater eddaTemplater,
      EddaTimeoutConfig eddaTimeoutConfig,
      Registry registry) {
    this.awsSdkClientSupplier = requireNonNull(awsSdkClientSupplier);
    this.httpClient = requireNonNull(httpClient);
    this.objectMapper = requireNonNull(objectMapper);
    this.eddaTemplater = requireNonNull(eddaTemplater);
    this.eddaTimeoutConfig = eddaTimeoutConfig;
    this.registry = requireNonNull(registry);
  }

  public <T extends AwsClientBuilder<T, U>, U> U getProxyHandler(
      Class<U> interfaceKlazz,
      Class<T> impl,
      NetflixAmazonCredentials amazonCredentials,
      String region) {
    return getProxyHandler(interfaceKlazz, impl, amazonCredentials, region, false);
  }

  public <T extends AwsClientBuilder<T, U>, U> U getProxyHandler(
      Class<U> interfaceKlazz,
      Class<T> impl,
      NetflixAmazonCredentials amazonCredentials,
      String region,
      boolean skipEdda) {
    requireNonNull(amazonCredentials, "Credentials cannot be null");
    try {
      U delegate =
          awsSdkClientSupplier.getClient(
              impl,
              interfaceKlazz,
              amazonCredentials.getName(),
              amazonCredentials.getCredentialsProvider(),
              region);
      if (skipEdda
          || !amazonCredentials.getEddaEnabled()
          || eddaTimeoutConfig.getDisabledRegions().contains(region)) {
        return delegate;
      }
      return interfaceKlazz.cast(
          Proxy.newProxyInstance(
              getClass().getClassLoader(),
              new Class[] {interfaceKlazz},
              getInvocationHandler(
                  delegate, interfaceKlazz.getSimpleName(), region, amazonCredentials)));
    } catch (RuntimeException re) {
      throw re;
    } catch (Exception e) {
      throw new RuntimeException("Instantiation of client implementation failed!", e);
    }
  }

  protected AmazonClientInvocationHandler getInvocationHandler(
      Object client,
      String serviceName,
      String region,
      NetflixAmazonCredentials amazonCredentials) {
    final Map<String, String> baseTags =
        ImmutableMap.of(
            "account", amazonCredentials.getName(),
            "region", region,
            "serviceName", serviceName);
    return new AmazonClientInvocationHandler(
        client,
        serviceName,
        eddaTemplater.getUrl(amazonCredentials.getEdda(), region),
        this.httpClient,
        objectMapper,
        eddaTimeoutConfig,
        registry,
        baseTags);
  }
}
