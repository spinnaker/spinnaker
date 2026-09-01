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

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSSessionCredentials;
import com.amazonaws.handlers.RequestHandler2;
import com.amazonaws.retry.PredefinedRetryPolicies;
import com.amazonaws.retry.RetryPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.awsobjectmapper.AmazonObjectMapperConfigurer;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.aws.security.sdkclient.*;
import com.netflix.spinnaker.clouddriver.core.limits.ServiceLimitConfiguration;
import com.netflix.spinnaker.clouddriver.core.limits.ServiceLimitConfigurationBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.services.applicationautoscaling.ApplicationAutoScalingClient;
import software.amazon.awssdk.services.autoscaling.AutoScalingClient;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.elasticloadbalancing.ElasticLoadBalancingClient;
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.route53.Route53Client;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.servicediscovery.ServiceDiscoveryClient;
import software.amazon.awssdk.services.shield.ShieldClient;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.support.SupportClient;
import software.amazon.awssdk.services.swf.SwfClient;

/** Provider of Amazon SDK Clients. */
public class AmazonClientProvider {

  /**
   * This constant (as null) indicates that whatever the current region from the AWS SDKs
   * perspective should be used.
   *
   * <p>The region to use will be resolved dynamically by {@link SpinnakerAwsRegionProvider} which
   * supports all the standard SDK means of explicitly specifying the current region, (environment
   * variable, instance profile, instance metadata).
   */
  public static final String DEFAULT_REGION = null;

  private final AwsSdkClientSupplier awsSdkClientSupplier;
  private final AwsSdkV2ClientSupplier awsSdkV2ClientSupplier;

  public static class Builder {
    private ObjectMapper objectMapper;
    private RetryPolicy.RetryCondition retryCondition;
    private RetryPolicy.BackoffStrategy backoffStrategy;
    private Integer maxErrorRetry;
    private List<RequestHandler2> requestHandlers = new ArrayList<>();
    private AWSProxy proxy;
    private boolean uzeGzip = true;
    private boolean addSpinnakerUserToUserAgent = false;
    private boolean logEndpoints = false;
    private ServiceLimitConfiguration serviceLimitConfiguration =
        new ServiceLimitConfigurationBuilder().build();
    private Registry registry = new NoopRegistry();
    private List<ExecutionInterceptor> v2ExecutionInterceptors = new ArrayList<>();

    public Builder proxy(AWSProxy proxy) {
      this.proxy = proxy;
      return this;
    }

    public Builder objectMapper(ObjectMapper objectMapper) {
      this.objectMapper = objectMapper;
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

    public Builder logEndpoints(boolean logEndpoints) {
      this.logEndpoints = logEndpoints;
      return this;
    }

    /**
     * Adds an AWS SDK v2 {@link ExecutionInterceptor} that will be attached to every v2 client
     * built by the provider. This is the v2 equivalent of {@link #requestHandler(RequestHandler2)}.
     */
    public Builder v2ExecutionInterceptor(ExecutionInterceptor interceptor) {
      this.v2ExecutionInterceptors.add(interceptor);
      return this;
    }

    public AmazonClientProvider build() {
      ObjectMapper mapper =
          this.objectMapper == null
              ? AmazonObjectMapperConfigurer.createConfigured()
              : this.objectMapper;
      RetryPolicy policy = buildPolicy();
      AWSProxy proxy = this.proxy;

      List<RequestHandler2> handlersToAdd = new ArrayList<>();

      if (addSpinnakerUserToUserAgent) {
        handlersToAdd.add(new AddSpinnakerUserToUserAgentRequestHandler());
      }

      if (logEndpoints) {
        handlersToAdd.add(new LogEndpointRequestHandler());
      }

      final List<RequestHandler2> requestHandlers;
      if (!handlersToAdd.isEmpty()) {
        requestHandlers = new ArrayList<>(this.requestHandlers.size() + handlersToAdd.size());
        requestHandlers.addAll(this.requestHandlers);
        requestHandlers.addAll(handlersToAdd);
      } else {
        requestHandlers = this.requestHandlers;
      }

      return new AmazonClientProvider(
          mapper,
          policy,
          requestHandlers,
          proxy,
          uzeGzip,
          serviceLimitConfiguration,
          registry,
          v2ExecutionInterceptors);
    }

    private RetryPolicy buildPolicy() {
      if (retryCondition == null && backoffStrategy == null) {
        if (maxErrorRetry == null) {
          return PredefinedRetryPolicies.getDefaultRetryPolicy();
        }
        return new RetryPolicy(
            PredefinedRetryPolicies.DEFAULT_RETRY_CONDITION,
            PredefinedRetryPolicies.DEFAULT_BACKOFF_STRATEGY,
            maxErrorRetry,
            true);
      }
      RetryPolicy.RetryCondition condition =
          this.retryCondition == null
              ? PredefinedRetryPolicies.DEFAULT_RETRY_CONDITION
              : this.retryCondition;
      RetryPolicy.BackoffStrategy strategy =
          this.backoffStrategy == null
              ? PredefinedRetryPolicies.DEFAULT_BACKOFF_STRATEGY
              : this.backoffStrategy;
      int retry =
          this.maxErrorRetry == null
              ? PredefinedRetryPolicies.DEFAULT_MAX_ERROR_RETRY
              : this.maxErrorRetry;

      return new RetryPolicy(condition, strategy, retry, true);
    }
  }

  /** So it's possible for tests to create mocks */
  public AmazonClientProvider() {
    this(AmazonObjectMapperConfigurer.createConfigured());
  }

  /** Also for testing */
  public AmazonClientProvider(ObjectMapper objectMapper) {
    this(
        objectMapper == null ? AmazonObjectMapperConfigurer.createConfigured() : objectMapper,
        PredefinedRetryPolicies.getDefaultRetryPolicy(),
        Collections.emptyList(),
        null,
        true,
        new ServiceLimitConfigurationBuilder().build(),
        new NoopRegistry());
  }

  public AmazonClientProvider(
      ObjectMapper objectMapper,
      RetryPolicy retryPolicy,
      List<RequestHandler2> requestHandlers,
      AWSProxy proxy,
      boolean useGzip,
      ServiceLimitConfiguration serviceLimitConfiguration,
      Registry registry) {
    this(
        objectMapper,
        retryPolicy,
        requestHandlers,
        proxy,
        useGzip,
        serviceLimitConfiguration,
        registry,
        Collections.emptyList());
  }

  public AmazonClientProvider(
      ObjectMapper objectMapper,
      RetryPolicy retryPolicy,
      List<RequestHandler2> requestHandlers,
      AWSProxy proxy,
      boolean useGzip,
      ServiceLimitConfiguration serviceLimitConfiguration,
      Registry registry,
      List<ExecutionInterceptor> v2ExecutionInterceptors) {
    RateLimiterSupplier rateLimiterSupplier =
        new RateLimiterSupplier(serviceLimitConfiguration, registry);
    this.awsSdkClientSupplier =
        new AwsSdkClientSupplier(
            rateLimiterSupplier, registry, retryPolicy, requestHandlers, proxy, useGzip);
    software.amazon.awssdk.core.retry.RetryPolicy v2RetryPolicy = buildV2RetryPolicy(retryPolicy);
    boolean v2AddUserAgent =
        requestHandlers.stream()
            .anyMatch(h -> h instanceof AddSpinnakerUserToUserAgentRequestHandler);
    this.awsSdkV2ClientSupplier =
        new AwsSdkV2ClientSupplier(
            rateLimiterSupplier,
            registry,
            v2RetryPolicy,
            proxy,
            v2AddUserAgent,
            v2ExecutionInterceptors);
  }

  /**
   * Translates v1 retry settings into a v2 {@link software.amazon.awssdk.core.retry.RetryPolicy}.
   * The v2 SDK provides sensible defaults for backoff and retry conditions; we only override the
   * max number of retries to match the v1 configuration.
   */
  private static software.amazon.awssdk.core.retry.RetryPolicy buildV2RetryPolicy(
      RetryPolicy v1RetryPolicy) {
    return software.amazon.awssdk.core.retry.RetryPolicy.builder()
        .numRetries(v1RetryPolicy.getMaxErrorRetry())
        .build();
  }

  public com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing
      getAmazonElasticLoadBalancingV2(
          String accountName, AWSCredentialsProvider awsCredentialsProvider, String region) {
    return awsSdkClientSupplier.getClient(
        com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancingClientBuilder.class,
        com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing.class,
        accountName,
        awsCredentialsProvider,
        region);
  }

  // ---------------------------------------------------------------------------
  // AWS SDK v2 client methods
  // ---------------------------------------------------------------------------

  /** Returns an AWS SDK v2 {@link Ec2Client} for the given account and region. */
  public Ec2Client getAmazonEC2V2(NetflixAmazonCredentials amazonCredentials, String region) {
    return awsSdkV2ClientSupplier.getClient(
        Ec2Client::builder,
        Ec2Client.class,
        amazonCredentials.getV2CredentialsProvider(),
        region,
        amazonCredentials.getName());
  }

  /**
   * Returns an AWS SDK v2 {@link Ec2Client} for a raw v1 {@link AWSCredentialsProvider}, for
   * callers (like {@link DefaultAWSAccountInfoLookup}) that don't have a {@link
   * NetflixAmazonCredentials} to build against.
   */
  public Ec2Client getAmazonEC2V2(AWSCredentialsProvider awsCredentialsProvider, String region) {
    return awsSdkV2ClientSupplier.getClient(
        Ec2Client::builder,
        Ec2Client.class,
        bridgeV1CredentialsProvider(awsCredentialsProvider),
        region,
        "UNSPECIFIED_ACCOUNT");
  }

  private static AwsCredentialsProvider bridgeV1CredentialsProvider(
      AWSCredentialsProvider v1Provider) {
    return () -> {
      AWSCredentials v1Creds = v1Provider.getCredentials();
      if (v1Creds instanceof AWSSessionCredentials) {
        AWSSessionCredentials session = (AWSSessionCredentials) v1Creds;
        return AwsSessionCredentials.create(
            session.getAWSAccessKeyId(), session.getAWSSecretKey(), session.getSessionToken());
      }
      return AwsBasicCredentials.create(v1Creds.getAWSAccessKeyId(), v1Creds.getAWSSecretKey());
    };
  }

  /** Returns an AWS SDK v2 {@link AutoScalingClient} for the given account and region. */
  public AutoScalingClient getAutoScalingV2(
      NetflixAmazonCredentials amazonCredentials, String region) {
    return awsSdkV2ClientSupplier.getClient(
        AutoScalingClient::builder,
        AutoScalingClient.class,
        amazonCredentials.getV2CredentialsProvider(),
        region,
        amazonCredentials.getName());
  }

  /** Returns an AWS SDK v2 {@link EcsClient} for the given account and region. */
  public EcsClient getAmazonEcsV2(NetflixAmazonCredentials amazonCredentials, String region) {
    return awsSdkV2ClientSupplier.getClient(
        EcsClient::builder,
        EcsClient.class,
        amazonCredentials.getV2CredentialsProvider(),
        region,
        amazonCredentials.getName());
  }

  /** Returns an AWS SDK v2 {@link EcrClient} for the given account and region. */
  public EcrClient getAmazonEcrV2(NetflixAmazonCredentials amazonCredentials, String region) {
    return awsSdkV2ClientSupplier.getClient(
        EcrClient::builder,
        EcrClient.class,
        amazonCredentials.getV2CredentialsProvider(),
        region,
        amazonCredentials.getName());
  }

  /** Returns an AWS SDK v2 {@link IamClient} for the given account and region. */
  public IamClient getIamV2(NetflixAmazonCredentials amazonCredentials, String region) {
    return awsSdkV2ClientSupplier.getClient(
        IamClient::builder,
        IamClient.class,
        amazonCredentials.getV2CredentialsProvider(),
        region,
        amazonCredentials.getName());
  }

  /** Returns an AWS SDK v2 {@link LambdaClient} for the given account and region. */
  public LambdaClient getLambdaV2(NetflixAmazonCredentials amazonCredentials, String region) {
    return getLambdaV2(amazonCredentials, region, null);
  }

  /**
   * Returns an AWS SDK v2 {@link LambdaClient} for the given account and region, applying
   * per-service tuning (retry count, socket timeout, TCP keep-alive).
   */
  public LambdaClient getLambdaV2(
      NetflixAmazonCredentials amazonCredentials,
      String region,
      AwsSdkV2ClientConfiguration clientConfiguration) {
    return awsSdkV2ClientSupplier.getClient(
        LambdaClient::builder,
        LambdaClient.class,
        amazonCredentials.getV2CredentialsProvider(),
        region,
        amazonCredentials.getName(),
        clientConfiguration);
  }

  /** Returns an AWS SDK v2 {@link CloudWatchClient} for the given account and region. */
  public CloudWatchClient getAmazonCloudWatchV2(
      NetflixAmazonCredentials amazonCredentials, String region) {
    return awsSdkV2ClientSupplier.getClient(
        CloudWatchClient::builder,
        CloudWatchClient.class,
        amazonCredentials.getV2CredentialsProvider(),
        region,
        amazonCredentials.getName());
  }

  /** Returns an AWS SDK v2 {@link Route53Client} for the given account and region. */
  public Route53Client getAmazonRoute53V2(
      NetflixAmazonCredentials amazonCredentials, String region) {
    return awsSdkV2ClientSupplier.getClient(
        Route53Client::builder,
        Route53Client.class,
        amazonCredentials.getV2CredentialsProvider(),
        region,
        amazonCredentials.getName());
  }

  /** Returns an AWS SDK v2 {@link S3Client} for the given account and region. */
  public S3Client getAmazonS3V2(NetflixAmazonCredentials amazonCredentials, String region) {
    return awsSdkV2ClientSupplier.getClient(
        S3Client::builder,
        S3Client.class,
        amazonCredentials.getV2CredentialsProvider(),
        region,
        amazonCredentials.getName());
  }

  /** Returns an AWS SDK v2 {@link SecretsManagerClient} for the given account and region. */
  public SecretsManagerClient getAmazonSecretsManagerV2(
      NetflixAmazonCredentials amazonCredentials, String region) {
    return awsSdkV2ClientSupplier.getClient(
        SecretsManagerClient::builder,
        SecretsManagerClient.class,
        amazonCredentials.getV2CredentialsProvider(),
        region,
        amazonCredentials.getName());
  }

  /** Returns an AWS SDK v2 {@link ServiceDiscoveryClient} for the given account and region. */
  public ServiceDiscoveryClient getAmazonServiceDiscoveryV2(
      NetflixAmazonCredentials amazonCredentials, String region) {
    return awsSdkV2ClientSupplier.getClient(
        ServiceDiscoveryClient::builder,
        ServiceDiscoveryClient.class,
        amazonCredentials.getV2CredentialsProvider(),
        region,
        amazonCredentials.getName());
  }

  /**
   * Returns an AWS SDK v2 {@link ApplicationAutoScalingClient} for the given account and region.
   */
  public ApplicationAutoScalingClient getAmazonApplicationAutoScalingV2(
      NetflixAmazonCredentials amazonCredentials, String region) {
    return awsSdkV2ClientSupplier.getClient(
        ApplicationAutoScalingClient::builder,
        ApplicationAutoScalingClient.class,
        amazonCredentials.getV2CredentialsProvider(),
        region,
        amazonCredentials.getName());
  }

  /** Returns an AWS SDK v2 {@link SupportClient} for the given account and region. */
  public SupportClient getAmazonSupportV2(
      NetflixAmazonCredentials amazonCredentials, String region) {
    return awsSdkV2ClientSupplier.getClient(
        SupportClient::builder,
        SupportClient.class,
        amazonCredentials.getV2CredentialsProvider(),
        region,
        amazonCredentials.getName());
  }

  /** Returns an AWS SDK v2 {@link SwfClient} for the given account and region. */
  public SwfClient getAmazonSimpleWorkflowV2(
      NetflixAmazonCredentials amazonCredentials, String region) {
    return awsSdkV2ClientSupplier.getClient(
        SwfClient::builder,
        SwfClient.class,
        amazonCredentials.getV2CredentialsProvider(),
        region,
        amazonCredentials.getName());
  }

  /** Returns an AWS SDK v2 {@link SnsClient} for the given account and region. */
  public SnsClient getAmazonSnsV2(NetflixAmazonCredentials amazonCredentials, String region) {
    return awsSdkV2ClientSupplier.getClient(
        SnsClient::builder,
        SnsClient.class,
        amazonCredentials.getV2CredentialsProvider(),
        region,
        amazonCredentials.getName());
  }

  /** Returns an AWS SDK v2 {@link SqsClient} for the given account and region. */
  public SqsClient getAmazonSqsV2(NetflixAmazonCredentials amazonCredentials, String region) {
    return awsSdkV2ClientSupplier.getClient(
        SqsClient::builder,
        SqsClient.class,
        amazonCredentials.getV2CredentialsProvider(),
        region,
        amazonCredentials.getName());
  }

  /** Returns an AWS SDK v2 {@link CloudFormationClient} for the given account and region. */
  public CloudFormationClient getAmazonCloudFormationV2(
      NetflixAmazonCredentials amazonCredentials, String region) {
    return awsSdkV2ClientSupplier.getClient(
        CloudFormationClient::builder,
        CloudFormationClient.class,
        amazonCredentials.getV2CredentialsProvider(),
        region,
        amazonCredentials.getName());
  }

  /**
   * Returns an AWS SDK v2 {@link ElasticLoadBalancingV2Client} for the given account and region.
   */
  public ElasticLoadBalancingV2Client getElasticLoadBalancingV2Client(
      NetflixAmazonCredentials amazonCredentials, String region) {
    return awsSdkV2ClientSupplier.getClient(
        ElasticLoadBalancingV2Client::builder,
        ElasticLoadBalancingV2Client.class,
        amazonCredentials.getV2CredentialsProvider(),
        region,
        amazonCredentials.getName());
  }

  /**
   * Returns an AWS SDK v2 {@link ElasticLoadBalancingClient} for classic (non-Application/Network)
   * Elastic Load Balancers, for the given account and region.
   */
  public ElasticLoadBalancingClient getAmazonElasticLoadBalancingClassicV2(
      NetflixAmazonCredentials amazonCredentials, String region) {
    return awsSdkV2ClientSupplier.getClient(
        ElasticLoadBalancingClient::builder,
        ElasticLoadBalancingClient.class,
        amazonCredentials.getV2CredentialsProvider(),
        region,
        amazonCredentials.getName());
  }

  /** Returns an AWS SDK v2 {@link ShieldClient} for the given account and region. */
  public ShieldClient getAmazonShieldV2(NetflixAmazonCredentials amazonCredentials, String region) {
    return awsSdkV2ClientSupplier.getClient(
        ShieldClient::builder,
        ShieldClient.class,
        amazonCredentials.getV2CredentialsProvider(),
        region,
        amazonCredentials.getName());
  }
}
