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

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.handlers.RequestHandler2;
import com.amazonaws.retry.PredefinedRetryPolicies;
import com.amazonaws.retry.RetryPolicy;
import com.amazonaws.services.applicationautoscaling.AWSApplicationAutoScaling;
import com.amazonaws.services.applicationautoscaling.AWSApplicationAutoScalingClientBuilder;
import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClientBuilder;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ecr.AmazonECR;
import com.amazonaws.services.ecr.AmazonECRClientBuilder;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.AmazonECSClientBuilder;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClientBuilder;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClientBuilder;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaAsync;
import com.amazonaws.services.lambda.AWSLambdaAsyncClientBuilder;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;
import com.amazonaws.services.route53.AmazonRoute53;
import com.amazonaws.services.route53.AmazonRoute53ClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder;
import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClientBuilder;
import com.amazonaws.services.servicediscovery.AWSServiceDiscovery;
import com.amazonaws.services.servicediscovery.AWSServiceDiscoveryClientBuilder;
import com.amazonaws.services.shield.AWSShield;
import com.amazonaws.services.shield.AWSShieldClientBuilder;
import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflow;
import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflowClientBuilder;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.awsobjectmapper.AmazonObjectMapperConfigurer;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.aws.security.sdkclient.*;
import com.netflix.spinnaker.clouddriver.core.limits.ServiceLimitConfiguration;
import com.netflix.spinnaker.clouddriver.core.limits.ServiceLimitConfigurationBuilder;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Provider of Amazon SDK Clients that can read through Edda.
 */
public class AmazonClientProvider {

  /**
   * This constant (as null) indicates that whatever the current region from the
   * AWS SDKs perspective should be used.
   *
   * The region to use will be resolved dynamically by {@link SpinnakerAwsRegionProvider}
   * which supports all the standard SDK means of explicitly specifying the current region,
   * (environment variable, instance profile, instance metadata).
   */
  public static final String DEFAULT_REGION = null;

  private final AwsSdkClientSupplier awsSdkClientSupplier;
  private final ProxyHandlerBuilder proxyHandlerBuilder;

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
    private boolean addSpinnakerUserToUserAgent = false;
    private ServiceLimitConfiguration serviceLimitConfiguration = new ServiceLimitConfigurationBuilder().build();
    private Registry registry = new NoopRegistry();

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

    public Builder serviceLimitConfiguration(ServiceLimitConfiguration serviceLimitConfiguration) {
      this.serviceLimitConfiguration = serviceLimitConfiguration;
      return this;
    }

    public Builder registry(Registry registry) {
      this.registry = registry;
      return this;
    }

    public Builder addSpinnakerUserToUserAgent(boolean addSpinnakerUserToUserAgent) {
      this.addSpinnakerUserToUserAgent = addSpinnakerUserToUserAgent;
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

      final List<RequestHandler2> requestHandlers;
      if (addSpinnakerUserToUserAgent) {
        requestHandlers = new ArrayList<>(this.requestHandlers.size() + 1);
        requestHandlers.addAll(this.requestHandlers);
        requestHandlers.add(new AddSpinnakerUserToUserAgentRequestHandler());
      } else {
        requestHandlers = this.requestHandlers;
      }

      return new AmazonClientProvider(client, mapper, templater, policy, requestHandlers, proxy, eddaTimeoutConfig, uzeGzip, serviceLimitConfiguration, registry);
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
    this(httpClient, AmazonObjectMapperConfigurer.createConfigured());
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
      true,
      new ServiceLimitConfigurationBuilder().build(),
      new NoopRegistry());
  }

  public AmazonClientProvider(HttpClient httpClient,
                              ObjectMapper objectMapper,
                              EddaTemplater eddaTemplater,
                              RetryPolicy retryPolicy,
                              List<RequestHandler2> requestHandlers,
                              AWSProxy proxy,
                              EddaTimeoutConfig eddaTimeoutConfig,
                              boolean useGzip,
                              ServiceLimitConfiguration serviceLimitConfiguration,
                              Registry registry) {
    RateLimiterSupplier rateLimiterSupplier = new RateLimiterSupplier(serviceLimitConfiguration, registry);
    this.awsSdkClientSupplier = new AwsSdkClientSupplier(rateLimiterSupplier, registry, retryPolicy, requestHandlers, proxy, useGzip);
    this.proxyHandlerBuilder = new ProxyHandlerBuilder(awsSdkClientSupplier, httpClient, objectMapper, eddaTemplater, eddaTimeoutConfig, registry);
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
    return proxyHandlerBuilder.getProxyHandler(AmazonEC2.class, AmazonEC2ClientBuilder.class, amazonCredentials, region, skipEdda);
  }

  public AmazonEC2 getAmazonEC2(AWSCredentialsProvider awsCredentialsProvider, String region) {
    return awsSdkClientSupplier.getClient(AmazonEC2ClientBuilder.class, AmazonEC2.class, "UNSPECIFIED_ACCOUNT", awsCredentialsProvider, region);
  }

  public AmazonEC2 getAmazonEC2(String accountName, AWSCredentialsProvider awsCredentialsProvider, String region) {
    return awsSdkClientSupplier.getClient(AmazonEC2ClientBuilder.class, AmazonEC2.class, accountName, awsCredentialsProvider, region);
  }

  public AmazonECS getAmazonEcs(NetflixAmazonCredentials amazonCredentials, String region, boolean skipEdda) {
    return proxyHandlerBuilder.getProxyHandler(AmazonECS.class, AmazonECSClientBuilder.class, amazonCredentials, region, skipEdda);
  }

  public AmazonIdentityManagement getIam(NetflixAmazonCredentials amazonCredentials, String region, boolean skipEdda) {
    return proxyHandlerBuilder.getProxyHandler(AmazonIdentityManagement.class, AmazonIdentityManagementClientBuilder.class, amazonCredentials, region, skipEdda);
    // return awsSdkClientSupplier.getClient(AmazonIdentityManagementClientBuilder.class, AmazonIdentityManagement.class, accountName, awsCredentialsProvider, region);
  }

  public AWSLambda getAmazonLambda(NetflixAmazonCredentials amazonCredentials, String region) {
    return proxyHandlerBuilder.getProxyHandler(AWSLambda.class, AWSLambdaClientBuilder.class, amazonCredentials, region);
  }

  public AWSLambda getAmazonLambda(String accountName, AWSCredentialsProvider awsCredentialsProvider, String region) {
    return awsSdkClientSupplier.getClient(AWSLambdaClientBuilder.class, AWSLambda.class, accountName, awsCredentialsProvider, region);
  }

  public AWSLambdaAsync getAmazonLambdaAsync(NetflixAmazonCredentials amazonCredentials, String region) {
    return proxyHandlerBuilder.getProxyHandler(AWSLambdaAsync.class, AWSLambdaAsyncClientBuilder.class, amazonCredentials, region);
  }

  public AWSLambdaAsync getAmazonLambdaAsync(String accountName, AWSCredentialsProvider awsCredentialsProvider, String region) {
    return awsSdkClientSupplier.getClient(AWSLambdaAsyncClientBuilder.class, AWSLambdaAsync.class, accountName, awsCredentialsProvider, region);
  }

  public AmazonS3 getAmazonS3(NetflixAmazonCredentials amazonCredentials, String region) {
    return proxyHandlerBuilder.getProxyHandler(AmazonS3.class, AmazonS3ClientBuilder.class, amazonCredentials, region, true);
  }

  public AmazonCloudFormation getAmazonCloudFormation(NetflixAmazonCredentials amazonCredentials, String region) {
    return proxyHandlerBuilder.getProxyHandler(AmazonCloudFormation.class, AmazonCloudFormationClientBuilder.class, amazonCredentials, region, true);
  }

  public AmazonAutoScaling getAutoScaling(NetflixAmazonCredentials amazonCredentials, String region) {
    return getAutoScaling(amazonCredentials, region, false);
  }

  public AmazonAutoScaling getAutoScaling(NetflixAmazonCredentials amazonCredentials, String region, boolean skipEdda) {
    return proxyHandlerBuilder.getProxyHandler(AmazonAutoScaling.class, AmazonAutoScalingClientBuilder.class, amazonCredentials, region, skipEdda);
  }

  public AmazonAutoScaling getAutoScaling(String accountName, AWSCredentialsProvider awsCredentialsProvider, String region) {
    return awsSdkClientSupplier.getClient(AmazonAutoScalingClientBuilder.class, AmazonAutoScaling.class, accountName, awsCredentialsProvider, region);
  }

  public AmazonRoute53 getAmazonRoute53(NetflixAmazonCredentials amazonCredentials, String region) {
    return getAmazonRoute53(amazonCredentials, region, false);
  }

  public AmazonRoute53 getAmazonRoute53(NetflixAmazonCredentials amazonCredentials, String region, boolean skipEdda) {
    return proxyHandlerBuilder.getProxyHandler(AmazonRoute53.class, AmazonRoute53ClientBuilder.class, amazonCredentials, region, skipEdda);
  }

  public AmazonRoute53 getAmazonRoute53(String accountName, AWSCredentialsProvider awsCredentialsProvider, String region) {
    return awsSdkClientSupplier.getClient(AmazonRoute53ClientBuilder.class, AmazonRoute53.class, accountName, awsCredentialsProvider, region);
  }

  public AmazonElasticLoadBalancing getAmazonElasticLoadBalancing(NetflixAmazonCredentials amazonCredentials, String region) {
    return getAmazonElasticLoadBalancing(amazonCredentials, region, false);
  }

  public AmazonElasticLoadBalancing getAmazonElasticLoadBalancing(NetflixAmazonCredentials amazonCredentials, String region, boolean skipEdda) {
    return proxyHandlerBuilder.getProxyHandler(AmazonElasticLoadBalancing.class, AmazonElasticLoadBalancingClientBuilder.class, amazonCredentials, region, skipEdda);
  }

  public AmazonElasticLoadBalancing getAmazonElasticLoadBalancing(String accountName, AWSCredentialsProvider awsCredentialsProvider, String region) {
    return awsSdkClientSupplier.getClient(AmazonElasticLoadBalancingClientBuilder.class, AmazonElasticLoadBalancing.class, accountName, awsCredentialsProvider, region);
  }

  public com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing getAmazonElasticLoadBalancingV2(NetflixAmazonCredentials amazonCredentials, String region) {
    return getAmazonElasticLoadBalancingV2(amazonCredentials, region, false);
  }

  public com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing getAmazonElasticLoadBalancingV2(NetflixAmazonCredentials amazonCredentials, String region, boolean skipEdda) {
    return proxyHandlerBuilder.getProxyHandler(com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing.class, com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancingClientBuilder.class, amazonCredentials, region, skipEdda);
  }

  public com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing getAmazonElasticLoadBalancingV2(String accountName, AWSCredentialsProvider awsCredentialsProvider, String region) {
    return awsSdkClientSupplier.getClient(
      com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancingClientBuilder.class,
      com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing.class,
      accountName,
      awsCredentialsProvider,
      region);
  }

  public AmazonSimpleWorkflow getAmazonSimpleWorkflow(NetflixAmazonCredentials amazonCredentials, String region) {
    return getAmazonSimpleWorkflow(amazonCredentials, region, false);
  }

  public AmazonSimpleWorkflow getAmazonSimpleWorkflow(NetflixAmazonCredentials amazonCredentials, String region, boolean skipEdda) {
    return proxyHandlerBuilder.getProxyHandler(AmazonSimpleWorkflow.class, AmazonSimpleWorkflowClientBuilder.class, amazonCredentials, region, skipEdda);
  }

  public AmazonSimpleWorkflow getAmazonSimpleWorkflow(String accountName, AWSCredentialsProvider awsCredentialsProvider, String region) {
    return awsSdkClientSupplier.getClient(AmazonSimpleWorkflowClientBuilder.class, AmazonSimpleWorkflow.class, accountName, awsCredentialsProvider, region);
  }

  public AmazonCloudWatch getAmazonCloudWatch(NetflixAmazonCredentials amazonCredentials, String region) {
    return getAmazonCloudWatch(amazonCredentials, region, false);
  }

  public AmazonCloudWatch getAmazonCloudWatch(NetflixAmazonCredentials amazonCredentials, String region, boolean skipEdda) {
    return proxyHandlerBuilder.getProxyHandler(AmazonCloudWatch.class, AmazonCloudWatchClientBuilder.class, amazonCredentials, region, skipEdda);
  }

  public AmazonCloudWatch getAmazonCloudWatch(String accountName, AWSCredentialsProvider awsCredentialsProvider, String region) {
    return awsSdkClientSupplier.getClient(AmazonCloudWatchClientBuilder.class, AmazonCloudWatch.class, accountName, awsCredentialsProvider, region);
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
    return proxyHandlerBuilder.getProxyHandler(AmazonSNS.class, AmazonSNSClientBuilder.class, amazonCredentials, region, skipEdda);
  }

  public AmazonSNS getAmazonSNS(String accountName, AWSCredentialsProvider awsCredentialsProvider, String region) {
    return awsSdkClientSupplier.getClient(AmazonSNSClientBuilder.class, AmazonSNS.class, accountName, awsCredentialsProvider, region);
  }

  public AmazonSQS getAmazonSQS(NetflixAmazonCredentials amazonCredentials, String region) {
    return proxyHandlerBuilder.getProxyHandler(AmazonSQS.class, AmazonSQSClientBuilder.class, amazonCredentials, region, false);
  }

  public AmazonIdentityManagement getAmazonIdentityManagement(NetflixAmazonCredentials amazonCredentials, String region) {
    return getAmazonIdentityManagement(amazonCredentials, region, false);
  }

  public AmazonIdentityManagement getAmazonIdentityManagement(NetflixAmazonCredentials amazonCredentials, String region, boolean skipEdda) {
    return proxyHandlerBuilder.getProxyHandler(AmazonIdentityManagement.class, AmazonIdentityManagementClientBuilder.class, amazonCredentials, region, skipEdda);
  }

  public AmazonIdentityManagement getAmazonIdentityManagement(String accountName, AWSCredentialsProvider awsCredentialsProvider, String region) {
    return awsSdkClientSupplier.getClient(AmazonIdentityManagementClientBuilder.class, AmazonIdentityManagement.class, accountName, awsCredentialsProvider, region);
  }

  public AWSShield getAmazonShield(NetflixAmazonCredentials amazonCredentials, String region) {
    return proxyHandlerBuilder.getProxyHandler(AWSShield.class, AWSShieldClientBuilder.class, amazonCredentials, region, true);
  }

  public AWSShield getAmazonShield(String accountName, AWSCredentialsProvider awsCredentialsProvider, String region) {
    return awsSdkClientSupplier.getClient(AWSShieldClientBuilder.class, AWSShield.class, accountName, awsCredentialsProvider, region);
  }

  public AWSApplicationAutoScaling getAmazonApplicationAutoScaling(NetflixAmazonCredentials amazonCredentials, String region, boolean skipEdda) {
    return proxyHandlerBuilder.getProxyHandler(AWSApplicationAutoScaling.class, AWSApplicationAutoScalingClientBuilder.class, amazonCredentials, region, skipEdda);
  }

  public AmazonECR getAmazonEcr(NetflixAmazonCredentials amazonCredentials, String region, boolean skipEdda) {
    return proxyHandlerBuilder.getProxyHandler(AmazonECR.class, AmazonECRClientBuilder.class, amazonCredentials, region, skipEdda);
  }

  public AWSSecretsManager getAmazonSecretsManager(NetflixAmazonCredentials amazonCredentials, String region, boolean skipEdda) {
    return proxyHandlerBuilder.getProxyHandler(AWSSecretsManager.class, AWSSecretsManagerClientBuilder.class, amazonCredentials, region, skipEdda);
  }

  public AWSServiceDiscovery getAmazonServiceDiscovery(NetflixAmazonCredentials amazonCredentials, String region, boolean skipEdda) {
    return proxyHandlerBuilder.getProxyHandler(AWSServiceDiscovery.class, AWSServiceDiscoveryClientBuilder.class, amazonCredentials, region, skipEdda);
  }
}
