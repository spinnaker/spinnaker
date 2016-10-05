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

import com.amazonaws.AmazonServiceException;
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
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaAsync;
import com.amazonaws.services.lambda.AWSLambdaAsyncClient;
import com.amazonaws.services.lambda.AWSLambdaClient;
import com.amazonaws.services.route53.AmazonRoute53;
import com.amazonaws.services.route53.AmazonRoute53Client;
import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflow;
import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflowClient;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.awsobjectmapper.AmazonObjectMapperConfigurer;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;

import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Provides of Amazon SDK Clients that can read through Edda.
 */
public class AmazonClientProvider {

  public static final String DEFAULT_REGION = null;

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final EddaTemplater eddaTemplater;
  private final RetryPolicy retryPolicy;
  private final List<RequestHandler2> requestHandlers;
  private final AWSProxy proxy;
  private final EddaTimeoutConfig eddaTimeoutConfig;
  private final boolean useGzip;

  public static class Builder {
    private HttpClient httpClient;
    private ObjectMapper objectMapper;
    private EddaTemplater eddaTemplater;
    private RetryPolicy.RetryCondition retryCondition;
    private RetryPolicy.BackoffStrategy backoffStrategy;
    private Integer maxErrorRetry;
    private List<RequestHandler2> requestHandlers = new ArrayList<>();
    private AWSProxy proxy;
    private EddaTimeoutConfig eddaTimeoutConfig;
    private int maxConnections = 200;
    private int maxConnectionsPerRoute = 20;
    private boolean uzeGzip = true;

    public Builder httpClient(HttpClient httpClient) {
      this.httpClient = httpClient;
      return this;
    }

    public Builder proxy(AWSProxy proxy) {
      this.proxy = proxy;
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

    public Builder eddaTimeoutConfig(EddaTimeoutConfig eddaTimeoutConfig) {
      this.eddaTimeoutConfig = eddaTimeoutConfig;
      return this;
    }

    public Builder maxConnections(int maxConnections) {
      this.maxConnections = maxConnections;
      return this;
    }

    public Builder maxConnectionsPerRoute(int maxConnectionsPerRoute) {
      this.maxConnectionsPerRoute = maxConnectionsPerRoute;
      return this;
    }

    public Builder useGzip(boolean useGzip) {
      this.uzeGzip = useGzip;
      return this;
    }

    public AmazonClientProvider build() {
      HttpClient client = this.httpClient;
      if (client == null) {
        HttpClientBuilder builder = HttpClientBuilder.create();
        builder.setMaxConnTotal(this.maxConnections);
        builder.setMaxConnPerRoute(this.maxConnectionsPerRoute);
        client = builder.build();
      }

      ObjectMapper mapper = this.objectMapper == null ? AmazonObjectMapperConfigurer.createConfigured() : this.objectMapper;
      EddaTemplater templater = this.eddaTemplater == null ? EddaTemplater.defaultTemplater() : this.eddaTemplater;
      RetryPolicy policy = buildPolicy();
      AWSProxy proxy = this.proxy;
      EddaTimeoutConfig eddaTimeoutConfig = this.eddaTimeoutConfig == null ? EddaTimeoutConfig.DEFAULT : this.eddaTimeoutConfig;

      return new AmazonClientProvider(client, mapper, templater, policy, requestHandlers, proxy, eddaTimeoutConfig, uzeGzip);
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
    this(httpClient, null);
  }

  public AmazonClientProvider(ObjectMapper objectMapper) {
    this(null, objectMapper);
  }

  public AmazonClientProvider(HttpClient httpClient, ObjectMapper objectMapper) {
    this(httpClient == null ? HttpClients.createDefault() : httpClient,
      objectMapper == null ? AmazonObjectMapperConfigurer.createConfigured() : objectMapper,
      EddaTemplater.defaultTemplater(),
      PredefinedRetryPolicies.getDefaultRetryPolicy(),
      Collections.emptyList(),
      null,
      EddaTimeoutConfig.DEFAULT,
      true);
  }

  public AmazonClientProvider(HttpClient httpClient,
                              ObjectMapper objectMapper,
                              EddaTemplater eddaTemplater,
                              RetryPolicy retryPolicy,
                              List<RequestHandler2> requestHandlers,
                              AWSProxy proxy,
                              EddaTimeoutConfig eddaTimeoutConfig,
                              boolean useGzip) {
    this.httpClient = requireNonNull(httpClient, "httpClient");
    this.objectMapper = requireNonNull(objectMapper, "objectMapper");
    this.eddaTemplater = requireNonNull(eddaTemplater, "eddaTemplater");
    this.retryPolicy = requireNonNull(retryPolicy, "retryPolicy");
    this.requestHandlers = requestHandlers == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(requestHandlers));
    this.proxy = proxy;
    this.eddaTimeoutConfig = eddaTimeoutConfig;
    this.useGzip = useGzip;
  }

  /**
   * When edda serves the request, the last-modified time is captured from the response metadata.
   * @return the last-modified timestamp, if available.
   */
  public Long getLastModified() {
    return AmazonClientInvocationHandler.lastModified.get();
  }

  public AmazonEC2 getAmazonEC2(NetflixAmazonCredentials amazonCredentials, String region) {
    return getAmazonEC2(amazonCredentials, region, false);
  }

  public AmazonEC2 getAmazonEC2(NetflixAmazonCredentials amazonCredentials, String region, boolean skipEdda) {
    return getProxyHandler(AmazonEC2.class, AmazonEC2Client.class, amazonCredentials, region, skipEdda);
  }

  public AmazonEC2 getAmazonEC2(AWSCredentialsProvider awsCredentialsProvider, String region) {
    return getClient(AmazonEC2Client.class, awsCredentialsProvider, region);
  }

  public AWSLambda getAmazonLambda(NetflixAmazonCredentials amazonCredentials, String region) {
    return getProxyHandler(AWSLambda.class, AWSLambdaClient.class, amazonCredentials, region);
  }

  public AWSLambda getAmazonLambda(AWSCredentialsProvider awsCredentialsProvider, String region) {
    return getClient(AWSLambdaClient.class, awsCredentialsProvider, region);
  }

  public AWSLambdaAsync getAmazonLambdaAsync(NetflixAmazonCredentials amazonCredentials, String region) {
    return getProxyHandler(AWSLambdaAsync.class, AWSLambdaAsyncClient.class, amazonCredentials, region);
  }

  public AWSLambdaAsync getAmazonLambdaAsync(AWSCredentialsProvider awsCredentialsProvider, String region) {
    return getClient(AWSLambdaAsyncClient.class, awsCredentialsProvider, region);
  }

  public AmazonAutoScaling getAutoScaling(NetflixAmazonCredentials amazonCredentials, String region) {
    return getAutoScaling(amazonCredentials, region, false);
  }

  public AmazonAutoScaling getAutoScaling(NetflixAmazonCredentials amazonCredentials, String region, boolean skipEdda) {
    return getProxyHandler(AmazonAutoScaling.class, AmazonAutoScalingClient.class, amazonCredentials, region, skipEdda);
  }

  public AmazonAutoScaling getAutoScaling(AWSCredentialsProvider awsCredentialsProvider, String region) {
    return getClient(AmazonAutoScalingClient.class, awsCredentialsProvider, region);
  }

  public AmazonRoute53 getAmazonRoute53(NetflixAmazonCredentials amazonCredentials, String region) {
    return getAmazonRoute53(amazonCredentials, region, false);
  }

  public AmazonRoute53 getAmazonRoute53(NetflixAmazonCredentials amazonCredentials, String region, boolean skipEdda) {
    return getProxyHandler(AmazonRoute53.class, AmazonRoute53Client.class, amazonCredentials, region, skipEdda);
  }

  public AmazonRoute53 getAmazonRoute53(AWSCredentialsProvider awsCredentialsProvider, String region) {
    return getClient(AmazonRoute53Client.class, awsCredentialsProvider, region);
  }

  public AmazonElasticLoadBalancing getAmazonElasticLoadBalancing(NetflixAmazonCredentials amazonCredentials, String region) {
    return getAmazonElasticLoadBalancing(amazonCredentials, region, false);
  }

  public AmazonElasticLoadBalancing getAmazonElasticLoadBalancing(NetflixAmazonCredentials amazonCredentials, String region, boolean skipEdda) {
    return getProxyHandler(AmazonElasticLoadBalancing.class, AmazonElasticLoadBalancingClient.class, amazonCredentials, region, skipEdda);
  }

  public AmazonElasticLoadBalancing getAmazonElasticLoadBalancing(AWSCredentialsProvider awsCredentialsProvider, String region) {
    return getClient(AmazonElasticLoadBalancingClient.class, awsCredentialsProvider, region);
  }

  public com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing getAmazonElasticLoadBalancingV2(NetflixAmazonCredentials amazonCredentials, String region) {
    return getAmazonElasticLoadBalancingV2(amazonCredentials, region, false);
  }

  public com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing getAmazonElasticLoadBalancingV2(NetflixAmazonCredentials amazonCredentials, String region, boolean skipEdda) {
    return getProxyHandler(com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing.class, com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancingClient.class, amazonCredentials, region, skipEdda);
  }

  public com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing getAmazonElasticLoadBalancingV2(AWSCredentialsProvider awsCredentialsProvider, String region) {
    return getClient(com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancingClient.class, awsCredentialsProvider, region);
  }

  public AmazonSimpleWorkflow getAmazonSimpleWorkflow(NetflixAmazonCredentials amazonCredentials, String region) {
    return getAmazonSimpleWorkflow(amazonCredentials, region, false);
  }

  public AmazonSimpleWorkflow getAmazonSimpleWorkflow(NetflixAmazonCredentials amazonCredentials, String region, boolean skipEdda) {
    return getProxyHandler(AmazonSimpleWorkflow.class, AmazonSimpleWorkflowClient.class, amazonCredentials, region, skipEdda);
  }

  public AmazonSimpleWorkflow getAmazonSimpleWorkflow(AWSCredentialsProvider awsCredentialsProvider, String region) {
    return getClient(AmazonSimpleWorkflowClient.class, awsCredentialsProvider, region);
  }

  public AmazonCloudWatch getAmazonCloudWatch(NetflixAmazonCredentials amazonCredentials, String region) {
    return getAmazonCloudWatch(amazonCredentials, region, false);
  }

  public AmazonCloudWatch getAmazonCloudWatch(NetflixAmazonCredentials amazonCredentials, String region, boolean skipEdda) {
    return getProxyHandler(AmazonCloudWatch.class, AmazonCloudWatchClient.class, amazonCredentials, region, skipEdda);
  }

  public AmazonCloudWatch getAmazonCloudWatch(AWSCredentialsProvider awsCredentialsProvider, String region) {
    return getClient(AmazonCloudWatchClient.class, awsCredentialsProvider, region);
  }

  public AmazonCloudWatch getCloudWatch(NetflixAmazonCredentials amazonCredentials, String region) {
    return getAmazonCloudWatch(amazonCredentials, region);
  }

  public AmazonCloudWatch getCloudWatch(NetflixAmazonCredentials amazonCredentials, String region, boolean skipEdda) {
    return getAmazonCloudWatch(amazonCredentials, region, skipEdda);
  }

  public AmazonSNS getAmazonSNS(NetflixAmazonCredentials amazonCredentials, String region) {
    return getAmazonSNS(amazonCredentials, region, false);
  }

  public AmazonSNS getAmazonSNS(NetflixAmazonCredentials amazonCredentials, String region, boolean skipEdda) {
    return getProxyHandler(AmazonSNS.class, AmazonSNSClient.class, amazonCredentials, region, skipEdda);
  }

  public AmazonSNS getAmazonSNS(AWSCredentialsProvider awsCredentialsProvider, String region) {
    return getClient(AmazonSNSClient.class, awsCredentialsProvider, region);
  }

  public AmazonIdentityManagement getAmazonIdentityManagement(NetflixAmazonCredentials amazonCredentials, String region) {
    return getAmazonIdentityManagement(amazonCredentials, region, false);
  }

  public AmazonIdentityManagement getAmazonIdentityManagement(NetflixAmazonCredentials amazonCredentials, String region, boolean skipEdda) {
    return getProxyHandler(AmazonIdentityManagement.class, AmazonIdentityManagementClient.class, amazonCredentials, region, skipEdda);
  }

  public AmazonIdentityManagement getAmazonIdentityManagement(AWSCredentialsProvider awsCredentialsProvider, String region) {
    return getClient(AmazonIdentityManagementClient.class, awsCredentialsProvider, region);
  }

  protected <T extends AmazonWebServiceClient, U> U getProxyHandler(Class<U> interfaceKlazz, Class<T> impl, NetflixAmazonCredentials amazonCredentials, String region) {
    return getProxyHandler(interfaceKlazz, impl, amazonCredentials, region, false);
  }

  protected <T extends AmazonWebServiceClient, U> U getProxyHandler(Class<U> interfaceKlazz, Class<T> impl, NetflixAmazonCredentials amazonCredentials, String region, boolean skipEdda) {
    checkCredentials(amazonCredentials);
    try {
      T delegate = getClient(impl, amazonCredentials.getCredentialsProvider(), region);
      if (amazonCredentials.getEddaEnabled() && !skipEdda) {
        return interfaceKlazz.cast(Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{interfaceKlazz},
          getInvocationHandler(delegate, delegate.getServiceName(), region, amazonCredentials)));
      } else {
        return interfaceKlazz.cast(delegate);
      }
    } catch (RuntimeException re) {
      throw re;
    } catch (Exception e) {
      throw new RuntimeException("Instantiation of client implementation failed!", e);
    }
  }

  protected <T extends AmazonWebServiceClient> T getClient(Class<T> impl, AWSCredentialsProvider awsCredentialsProvider, String region) {
    checkAWSCredentialsProvider(awsCredentialsProvider);
    try {
      Constructor<T> constructor = impl.getConstructor(AWSCredentialsProvider.class, ClientConfiguration.class);

      ClientConfiguration clientConfiguration = new ClientConfiguration();

      if (awsCredentialsProvider instanceof NetflixSTSAssumeRoleSessionCredentialsProvider) {
        RetryPolicy.RetryCondition delegatingRetryCondition = (originalRequest, exception, retriesAttempted) -> {
          NetflixSTSAssumeRoleSessionCredentialsProvider stsCredentialsProvider = (NetflixSTSAssumeRoleSessionCredentialsProvider) awsCredentialsProvider;
          if (exception instanceof AmazonServiceException) {
            ((AmazonServiceException) exception).getHttpHeaders().put("targetAccountId", stsCredentialsProvider.getAccountId());
          }
          return retryPolicy.getRetryCondition().shouldRetry(originalRequest, exception, retriesAttempted);
        };

        RetryPolicy delegatingRetryPolicy = new RetryPolicy(
          delegatingRetryCondition,
          retryPolicy.getBackoffStrategy(),
          retryPolicy.getMaxErrorRetry(),
          retryPolicy.isMaxErrorRetryInClientConfigHonored()
        );
        clientConfiguration.setRetryPolicy(delegatingRetryPolicy);
      } else {
        clientConfiguration.setRetryPolicy(retryPolicy);
      }

      if (proxy != null && proxy.isProxyConfigMode()) {
        proxy.apply(clientConfiguration);
      }

      clientConfiguration.setUseGzip(useGzip);

      T delegate = constructor.newInstance(awsCredentialsProvider, clientConfiguration);
      for (RequestHandler2 requestHandler : requestHandlers) {
        delegate.addRequestHandler(requestHandler);
      }
      if (region != null && region.length() > 0) {
        delegate.setRegion(Region.getRegion(Regions.fromName(region)));
      }
      return delegate;
    } catch (Exception e) {
      throw new RuntimeException("Instantiation of client implementation failed!", e);
    }
  }

  protected AmazonClientInvocationHandler getInvocationHandler(Object client, String serviceName, String region, NetflixAmazonCredentials amazonCredentials) {
    return new AmazonClientInvocationHandler(client, serviceName, eddaTemplater.getUrl(amazonCredentials.getEdda(), region),
      this.httpClient, objectMapper, eddaTimeoutConfig);
  }

  private static void checkCredentials(NetflixAmazonCredentials amazonCredentials) {
    requireNonNull(amazonCredentials, "Credentials cannot be null");
  }

  private static void checkAWSCredentialsProvider(AWSCredentialsProvider awsCredentialsProvider) {
    requireNonNull(awsCredentialsProvider, "AWSCredentialsProvider cannot be null");
  }
}
