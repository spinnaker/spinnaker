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

import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.aws.security.sdkclient.*;
import com.netflix.spinnaker.clouddriver.core.limits.ServiceLimitConfiguration;
import com.netflix.spinnaker.clouddriver.core.limits.ServiceLimitConfigurationBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.core.retry.RetryPolicy;
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
   * This constant (as null) indicates that the region should be resolved dynamically via the AWS
   * SDK's default region provider chain (environment variable, instance profile, instance
   * metadata), falling back to {@code us-east-1} if none of those resolve. See {@link
   * AwsSdkV2ClientSupplier}.
   */
  public static final String DEFAULT_REGION = null;

  private final AwsSdkV2ClientSupplier awsSdkV2ClientSupplier;

  public static class Builder {
    private Integer maxErrorRetry;
    private AWSProxy proxy;
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

    public Builder maxErrorRetry(Integer maxErrorRetry) {
      this.maxErrorRetry = maxErrorRetry;
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
     * built by the provider.
     */
    public Builder v2ExecutionInterceptor(ExecutionInterceptor interceptor) {
      this.v2ExecutionInterceptors.add(interceptor);
      return this;
    }

    public AmazonClientProvider build() {
      RetryPolicy.Builder retryPolicyBuilder = RetryPolicy.builder();
      if (maxErrorRetry != null) {
        retryPolicyBuilder.numRetries(maxErrorRetry);
      }

      List<ExecutionInterceptor> interceptors = this.v2ExecutionInterceptors;
      if (logEndpoints) {
        interceptors = new ArrayList<>(interceptors);
        interceptors.add(new LogEndpointExecutionInterceptor());
      }

      return new AmazonClientProvider(
          retryPolicyBuilder.build(),
          proxy,
          addSpinnakerUserToUserAgent,
          serviceLimitConfiguration,
          registry,
          interceptors);
    }
  }

  /** So it's possible for tests to create mocks */
  public AmazonClientProvider() {
    this(
        RetryPolicy.defaultRetryPolicy(),
        null,
        false,
        new ServiceLimitConfigurationBuilder().build(),
        new NoopRegistry(),
        Collections.emptyList());
  }

  public AmazonClientProvider(
      RetryPolicy retryPolicy,
      AWSProxy proxy,
      boolean addSpinnakerUserToUserAgent,
      ServiceLimitConfiguration serviceLimitConfiguration,
      Registry registry,
      List<ExecutionInterceptor> v2ExecutionInterceptors) {
    RateLimiterSupplier rateLimiterSupplier =
        new RateLimiterSupplier(serviceLimitConfiguration, registry);
    this.awsSdkV2ClientSupplier =
        new AwsSdkV2ClientSupplier(
            rateLimiterSupplier,
            registry,
            retryPolicy,
            proxy,
            addSpinnakerUserToUserAgent,
            v2ExecutionInterceptors);
  }

  // ---------------------------------------------------------------------------
  // AWS SDK v2 client methods
  // ---------------------------------------------------------------------------

  /** Returns an AWS SDK v2 {@link Ec2Client} for the given account and region. */
  public Ec2Client getAmazonEC2V2(NetflixAmazonCredentials amazonCredentials, String region) {
    return awsSdkV2ClientSupplier.getClient(
        Ec2Client::builder,
        Ec2Client.class,
        amazonCredentials.getCredentialsProvider(),
        region,
        amazonCredentials.getName());
  }

  /**
   * Returns an AWS SDK v2 {@link Ec2Client} for a raw {@link AwsCredentialsProvider}, for callers
   * (like {@link DefaultAWSAccountInfoLookup}) that don't have a {@link NetflixAmazonCredentials}
   * to build against.
   */
  public Ec2Client getAmazonEC2V2(AwsCredentialsProvider awsCredentialsProvider, String region) {
    return awsSdkV2ClientSupplier.getClient(
        Ec2Client::builder, Ec2Client.class, awsCredentialsProvider, region, "UNSPECIFIED_ACCOUNT");
  }

  /** Returns an AWS SDK v2 {@link AutoScalingClient} for the given account and region. */
  public AutoScalingClient getAutoScalingV2(
      NetflixAmazonCredentials amazonCredentials, String region) {
    return awsSdkV2ClientSupplier.getClient(
        AutoScalingClient::builder,
        AutoScalingClient.class,
        amazonCredentials.getCredentialsProvider(),
        region,
        amazonCredentials.getName());
  }

  /** Returns an AWS SDK v2 {@link EcsClient} for the given account and region. */
  public EcsClient getAmazonEcsV2(NetflixAmazonCredentials amazonCredentials, String region) {
    return awsSdkV2ClientSupplier.getClient(
        EcsClient::builder,
        EcsClient.class,
        amazonCredentials.getCredentialsProvider(),
        region,
        amazonCredentials.getName());
  }

  /** Returns an AWS SDK v2 {@link EcrClient} for the given account and region. */
  public EcrClient getAmazonEcrV2(NetflixAmazonCredentials amazonCredentials, String region) {
    return awsSdkV2ClientSupplier.getClient(
        EcrClient::builder,
        EcrClient.class,
        amazonCredentials.getCredentialsProvider(),
        region,
        amazonCredentials.getName());
  }

  /** Returns an AWS SDK v2 {@link IamClient} for the given account and region. */
  public IamClient getIamV2(NetflixAmazonCredentials amazonCredentials, String region) {
    return awsSdkV2ClientSupplier.getClient(
        IamClient::builder,
        IamClient.class,
        amazonCredentials.getCredentialsProvider(),
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
        amazonCredentials.getCredentialsProvider(),
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
        amazonCredentials.getCredentialsProvider(),
        region,
        amazonCredentials.getName());
  }

  /** Returns an AWS SDK v2 {@link Route53Client} for the given account and region. */
  public Route53Client getAmazonRoute53V2(
      NetflixAmazonCredentials amazonCredentials, String region) {
    return awsSdkV2ClientSupplier.getClient(
        Route53Client::builder,
        Route53Client.class,
        amazonCredentials.getCredentialsProvider(),
        region,
        amazonCredentials.getName());
  }

  /** Returns an AWS SDK v2 {@link S3Client} for the given account and region. */
  public S3Client getAmazonS3V2(NetflixAmazonCredentials amazonCredentials, String region) {
    return awsSdkV2ClientSupplier.getClient(
        S3Client::builder,
        S3Client.class,
        amazonCredentials.getCredentialsProvider(),
        region,
        amazonCredentials.getName());
  }

  /** Returns an AWS SDK v2 {@link SecretsManagerClient} for the given account and region. */
  public SecretsManagerClient getAmazonSecretsManagerV2(
      NetflixAmazonCredentials amazonCredentials, String region) {
    return awsSdkV2ClientSupplier.getClient(
        SecretsManagerClient::builder,
        SecretsManagerClient.class,
        amazonCredentials.getCredentialsProvider(),
        region,
        amazonCredentials.getName());
  }

  /** Returns an AWS SDK v2 {@link ServiceDiscoveryClient} for the given account and region. */
  public ServiceDiscoveryClient getAmazonServiceDiscoveryV2(
      NetflixAmazonCredentials amazonCredentials, String region) {
    return awsSdkV2ClientSupplier.getClient(
        ServiceDiscoveryClient::builder,
        ServiceDiscoveryClient.class,
        amazonCredentials.getCredentialsProvider(),
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
        amazonCredentials.getCredentialsProvider(),
        region,
        amazonCredentials.getName());
  }

  /** Returns an AWS SDK v2 {@link SupportClient} for the given account and region. */
  public SupportClient getAmazonSupportV2(
      NetflixAmazonCredentials amazonCredentials, String region) {
    return awsSdkV2ClientSupplier.getClient(
        SupportClient::builder,
        SupportClient.class,
        amazonCredentials.getCredentialsProvider(),
        region,
        amazonCredentials.getName());
  }

  /** Returns an AWS SDK v2 {@link SwfClient} for the given account and region. */
  public SwfClient getAmazonSimpleWorkflowV2(
      NetflixAmazonCredentials amazonCredentials, String region) {
    return awsSdkV2ClientSupplier.getClient(
        SwfClient::builder,
        SwfClient.class,
        amazonCredentials.getCredentialsProvider(),
        region,
        amazonCredentials.getName());
  }

  /** Returns an AWS SDK v2 {@link SnsClient} for the given account and region. */
  public SnsClient getAmazonSnsV2(NetflixAmazonCredentials amazonCredentials, String region) {
    return awsSdkV2ClientSupplier.getClient(
        SnsClient::builder,
        SnsClient.class,
        amazonCredentials.getCredentialsProvider(),
        region,
        amazonCredentials.getName());
  }

  /** Returns an AWS SDK v2 {@link SqsClient} for the given account and region. */
  public SqsClient getAmazonSqsV2(NetflixAmazonCredentials amazonCredentials, String region) {
    return awsSdkV2ClientSupplier.getClient(
        SqsClient::builder,
        SqsClient.class,
        amazonCredentials.getCredentialsProvider(),
        region,
        amazonCredentials.getName());
  }

  /** Returns an AWS SDK v2 {@link CloudFormationClient} for the given account and region. */
  public CloudFormationClient getAmazonCloudFormationV2(
      NetflixAmazonCredentials amazonCredentials, String region) {
    return awsSdkV2ClientSupplier.getClient(
        CloudFormationClient::builder,
        CloudFormationClient.class,
        amazonCredentials.getCredentialsProvider(),
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
        amazonCredentials.getCredentialsProvider(),
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
        amazonCredentials.getCredentialsProvider(),
        region,
        amazonCredentials.getName());
  }

  /** Returns an AWS SDK v2 {@link ShieldClient} for the given account and region. */
  public ShieldClient getAmazonShieldV2(NetflixAmazonCredentials amazonCredentials, String region) {
    return awsSdkV2ClientSupplier.getClient(
        ShieldClient::builder,
        ShieldClient.class,
        amazonCredentials.getCredentialsProvider(),
        region,
        amazonCredentials.getName());
  }
}
