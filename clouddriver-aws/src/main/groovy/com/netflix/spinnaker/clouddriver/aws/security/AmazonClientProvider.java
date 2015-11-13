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

package com.netflix.spinnaker.clouddriver.aws.security;

import com.amazonaws.AmazonWebServiceClient;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.handlers.RequestHandler2;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.retry.PredefinedRetryPolicies;
import com.amazonaws.retry.RetryPolicy;
import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient;
import com.amazonaws.services.route53.AmazonRoute53;
import com.amazonaws.services.route53.AmazonRoute53Client;
import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflow;
import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflowClient;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.awsobjectmapper.AmazonObjectMapper;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClients;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Provides of Amazon SDK Clients that can read through Edda.
 */
public class AmazonClientProvider {

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final EddaTemplater eddaTemplater;
  private final RetryPolicy retryPolicy;
  private final List<RequestHandler2> requestHandlers;

  public static class Builder {
    private HttpClient httpClient;
    private ObjectMapper objectMapper;
    private EddaTemplater eddaTemplater;
    private RetryPolicy.RetryCondition retryCondition;
    private RetryPolicy.BackoffStrategy backoffStrategy;
    private Integer maxErrorRetry;
    private List<RequestHandler2> requestHandlers = new ArrayList<>();

    public Builder httpClient(HttpClient httpClient) {
      this.httpClient = httpClient;
      return this;
    }

    public Builder objectMapper(ObjectMapper objectMapper) {
      this.objectMapper = objectMapper;
      return this;
    }

    public Builder eddaTemplater(EddaTemplater eddaTemplater) {
      this.eddaTemplater = eddaTemplater;
      return this;
    }

    public Builder retryCondition(RetryPolicy.RetryCondition retryCondition) {
      this.retryCondition = retryCondition;
      return this;
    }

    public Builder backoffStrategy(RetryPolicy.BackoffStrategy backoffStrategy) {
      this.backoffStrategy = backoffStrategy;
      return this;
    }

    public Builder maxErrorRetry(Integer maxErrorRetry) {
      this.maxErrorRetry = maxErrorRetry;
      return this;
    }

    public Builder requestHandler(RequestHandler2 requestHandler) {
      this.requestHandlers.add(requestHandler);
      return this;
    }

    public AmazonClientProvider build() {
      HttpClient client = this.httpClient == null ? HttpClients.createDefault() : this.httpClient;
      ObjectMapper mapper = this.objectMapper == null ? new AmazonObjectMapper() : this.objectMapper;
      EddaTemplater templater = this.eddaTemplater == null ? EddaTemplater.defaultTemplater() : this.eddaTemplater;
      RetryPolicy policy = buildPolicy();

      return new AmazonClientProvider(client, mapper, templater, policy, requestHandlers);
    }

    private RetryPolicy buildPolicy() {
      if (retryCondition == null && backoffStrategy == null) {
        if (maxErrorRetry == null) {
          return PredefinedRetryPolicies.getDefaultRetryPolicy();
        }
        return PredefinedRetryPolicies.getDefaultRetryPolicyWithCustomMaxRetries(maxErrorRetry);
      }
      RetryPolicy.RetryCondition condition = this.retryCondition == null ? PredefinedRetryPolicies.DEFAULT_RETRY_CONDITION : this.retryCondition;
      RetryPolicy.BackoffStrategy strategy = this.backoffStrategy == null ? PredefinedRetryPolicies.DEFAULT_BACKOFF_STRATEGY : this.backoffStrategy;
      int retry = this.maxErrorRetry == null ? PredefinedRetryPolicies.DEFAULT_MAX_ERROR_RETRY : this.maxErrorRetry;

      return new RetryPolicy(condition, strategy, retry, true);
    }
  }

  public AmazonClientProvider() {
    this((HttpClient) null);
  }

  public AmazonClientProvider(HttpClient httpClient) {
    this(httpClient, new AmazonObjectMapper());
  }

  public AmazonClientProvider(ObjectMapper objectMapper) {
    this(null, objectMapper);
  }

  public AmazonClientProvider(HttpClient httpClient, ObjectMapper objectMapper) {
    this(httpClient == null ? HttpClients.createDefault() : httpClient, objectMapper == null ? new AmazonObjectMapper() : objectMapper, EddaTemplater.defaultTemplater(), PredefinedRetryPolicies.getDefaultRetryPolicy(), Collections.emptyList());
  }

  public static <T> T notNull(T obj, String name) {
    if (obj == null) {
      throw new NullPointerException(name);
    }
    return obj;
  }

  public AmazonClientProvider(HttpClient httpClient, ObjectMapper objectMapper, EddaTemplater eddaTemplater, RetryPolicy retryPolicy, List<RequestHandler2> requestHandlers) {
    this.httpClient = notNull(httpClient, "httpClient");
    this.objectMapper = notNull(objectMapper, "objectMapper");
    this.eddaTemplater = notNull(eddaTemplater, "eddaTemplater");
    this.retryPolicy = notNull(retryPolicy, "retryPolicy");
    this.requestHandlers = requestHandlers == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(requestHandlers));
  }

  /**
   * When edda serves the request, the last-modified time is captured from the response metadata.
   * @return the last-modified timestamp, if available.
   */
  public Long getLastModified() {
    return AmazonClientInvocationHandler.lastModified.get();
  }

  public AmazonEC2 getAmazonEC2(NetflixAmazonCredentials amazonCredentials, String region) {
    checkCredentials(amazonCredentials);
    return getProxyHandler(AmazonEC2.class, AmazonEC2Client.class, amazonCredentials, region);

  }

  public AmazonAutoScaling getAutoScaling(NetflixAmazonCredentials amazonCredentials, String region) {
    checkCredentials(amazonCredentials);
    return getProxyHandler(AmazonAutoScaling.class, AmazonAutoScalingClient.class, amazonCredentials, region);
  }

  public AmazonRoute53 getAmazonRoute53(NetflixAmazonCredentials amazonCredentials, String region) {
    checkCredentials(amazonCredentials);
    return getProxyHandler(AmazonRoute53.class, AmazonRoute53Client.class, amazonCredentials, region);
  }

  public AmazonElasticLoadBalancing getAmazonElasticLoadBalancing(NetflixAmazonCredentials amazonCredentials, String region) {
    checkCredentials(amazonCredentials);
    return getProxyHandler(AmazonElasticLoadBalancing.class, AmazonElasticLoadBalancingClient.class, amazonCredentials, region);
  }

  public AmazonSimpleWorkflow getAmazonSimpleWorkflow(NetflixAmazonCredentials amazonCredentials, String region) {
    checkCredentials(amazonCredentials);
    return getProxyHandler(AmazonSimpleWorkflow.class, AmazonSimpleWorkflowClient.class, amazonCredentials, region);
  }

  public AmazonCloudWatch getAmazonCloudWatch(NetflixAmazonCredentials amazonCredentials, String region) {
    checkCredentials(amazonCredentials);
    return getProxyHandler(AmazonCloudWatch.class, AmazonCloudWatchClient.class, amazonCredentials, region);
  }

  public AmazonSNS getAmazonSNS(NetflixAmazonCredentials amazonCredentials, String region) {
    checkCredentials(amazonCredentials);
    return getProxyHandler(AmazonSNS.class, AmazonSNSClient.class, amazonCredentials, region);
  }

  public AmazonCloudWatch getCloudWatch(NetflixAmazonCredentials amazonCredentials, String region) {
    checkCredentials(amazonCredentials);
    return getProxyHandler(AmazonCloudWatch.class, AmazonCloudWatchClient.class, amazonCredentials, region);
  }

  public AmazonEC2 getAmazonEC2(NetflixAmazonCredentials amazonCredentials, String region, boolean skipEdda) {
    checkCredentials(amazonCredentials);
    return getProxyHandler(AmazonEC2.class, AmazonEC2Client.class, amazonCredentials, region, skipEdda);
  }

  public AmazonAutoScaling getAutoScaling(NetflixAmazonCredentials amazonCredentials, String region, boolean skipEdda) {
    checkCredentials(amazonCredentials);
    return getProxyHandler(AmazonAutoScaling.class, AmazonAutoScalingClient.class, amazonCredentials, region, skipEdda);
  }

  public AmazonRoute53 getAmazonRoute53(NetflixAmazonCredentials amazonCredentials, String region, boolean skipEdda) {
    checkCredentials(amazonCredentials);
    return getProxyHandler(AmazonRoute53.class, AmazonRoute53Client.class, amazonCredentials, region, skipEdda);
  }

  public AmazonElasticLoadBalancing getAmazonElasticLoadBalancing(NetflixAmazonCredentials amazonCredentials, String region, boolean skipEdda) {
    checkCredentials(amazonCredentials);
    return getProxyHandler(AmazonElasticLoadBalancing.class, AmazonElasticLoadBalancingClient.class, amazonCredentials, region, skipEdda);
  }

  public AmazonSimpleWorkflow getAmazonSimpleWorkflow(NetflixAmazonCredentials amazonCredentials, String region, boolean skipEdda) {
    checkCredentials(amazonCredentials);
    return getProxyHandler(AmazonSimpleWorkflow.class, AmazonSimpleWorkflowClient.class, amazonCredentials, region, skipEdda);
  }

  public AmazonCloudWatch getAmazonCloudWatch(NetflixAmazonCredentials amazonCredentials, String region, boolean skipEdda) {
    checkCredentials(amazonCredentials);
    return getProxyHandler(AmazonCloudWatch.class, AmazonCloudWatchClient.class, amazonCredentials, region, skipEdda);
  }

  public AmazonSNS getAmazonSNS(NetflixAmazonCredentials amazonCredentials, String region, boolean skipEdda) {
    checkCredentials(amazonCredentials);
    return getProxyHandler(AmazonSNS.class, AmazonSNSClient.class, amazonCredentials, region, skipEdda);
  }

  public AmazonCloudWatch getCloudWatch(NetflixAmazonCredentials amazonCredentials, String region, boolean skipEdda) {
    checkCredentials(amazonCredentials);
    return getProxyHandler(AmazonCloudWatch.class, AmazonCloudWatchClient.class, amazonCredentials, region, skipEdda);
  }

  protected <T extends AmazonWebServiceClient, U> U getProxyHandler(Class<U> interfaceKlazz, Class<T> impl, NetflixAmazonCredentials amazonCredentials, String region) {
    return getProxyHandler(interfaceKlazz, impl, amazonCredentials, region, false);
  }

  protected <T extends AmazonWebServiceClient, U> U getProxyHandler(Class<U> interfaceKlazz, Class<T> impl, NetflixAmazonCredentials amazonCredentials, String region, boolean skipEdda) {
    try {
      T delegate = getClient(impl, amazonCredentials, region);
      if (amazonCredentials.getEddaEnabled() && !skipEdda) {
        return interfaceKlazz.cast(Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{interfaceKlazz},
                getInvocationHandler(delegate, delegate.getServiceName(), region, amazonCredentials)));
      } else {
        return interfaceKlazz.cast(delegate);
      }
    } catch (Exception e) {
      throw new RuntimeException("Instantiation of client implementation failed!", e);
    }
  }

  protected <T extends AmazonWebServiceClient> T getClient(Class<T> impl, NetflixAmazonCredentials amazonCredentials, String region) throws IllegalAccessException, InvocationTargetException,
    InstantiationException, NoSuchMethodException {
    Constructor<T> constructor = impl.getConstructor(AWSCredentialsProvider.class, ClientConfiguration.class);

    ClientConfiguration clientConfiguration = new ClientConfiguration();
    clientConfiguration.setRetryPolicy(retryPolicy);

    T delegate = constructor.newInstance(amazonCredentials.getCredentialsProvider(), clientConfiguration);
    for (RequestHandler2 requestHandler : requestHandlers) {
      delegate.addRequestHandler(requestHandler);
    }
    if (region != null && region.length() > 0) {
      delegate.setRegion(Region.getRegion(Regions.fromName(region)));
    }
    return delegate;
  }

  protected AmazonClientInvocationHandler getInvocationHandler(Object client, String serviceName, String region, NetflixAmazonCredentials amazonCredentials) {
    return new AmazonClientInvocationHandler(client, serviceName, eddaTemplater.getUrl(amazonCredentials.getEdda(), region),
      this.httpClient, objectMapper);
  }

  private static void checkCredentials(NetflixAmazonCredentials amazonCredentials) {
    if (amazonCredentials == null) {
      throw new IllegalArgumentException("Credentials cannot be null");
    }
  }
}

