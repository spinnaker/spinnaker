/*
 * Copyright 2025 Netflix, Inc.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.applicationautoscaling.ApplicationAutoScalingClient;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.servicediscovery.ServiceDiscoveryClient;

/**
 * Unit tests verifying the seven v2 getter methods on {@link AmazonClientProvider} produce the
 * correct v2 client types and delegate to the v2 supplier correctly.
 */
class AmazonClientProviderV2Test {

  private AmazonClientProvider provider;
  private NetflixAmazonCredentials creds;
  private static final String REGION = "us-east-1";

  private static AwsCredentialsProvider dummyCreds() {
    return StaticCredentialsProvider.create(AwsBasicCredentials.create("key", "secret"));
  }

  @BeforeEach
  void setUp() {
    // Use the no-arg constructor (designed for tests) — it wires a real AwsSdkV2ClientSupplier.
    provider = new AmazonClientProvider();

    creds = mock(NetflixAmazonCredentials.class);
    when(creds.getV2CredentialsProvider()).thenReturn(dummyCreds());
    when(creds.getName()).thenReturn("test-account");
  }

  @Test
  void getAmazonEcsV2ReturnsEcsClient() {
    EcsClient client = provider.getAmazonEcsV2(creds, REGION);
    assertThat(client).isNotNull().isInstanceOf(EcsClient.class);
  }

  @Test
  void getAmazonEcrV2ReturnsEcrClient() {
    EcrClient client = provider.getAmazonEcrV2(creds, REGION);
    assertThat(client).isNotNull().isInstanceOf(EcrClient.class);
  }

  @Test
  void getIamV2ReturnsIamClient() {
    IamClient client = provider.getIamV2(creds, REGION);
    assertThat(client).isNotNull().isInstanceOf(IamClient.class);
  }

  @Test
  void getAmazonCloudWatchV2ReturnsCloudWatchClient() {
    CloudWatchClient client = provider.getAmazonCloudWatchV2(creds, REGION);
    assertThat(client).isNotNull().isInstanceOf(CloudWatchClient.class);
  }

  @Test
  void getAmazonSecretsManagerV2ReturnsSecretsManagerClient() {
    SecretsManagerClient client = provider.getAmazonSecretsManagerV2(creds, REGION);
    assertThat(client).isNotNull().isInstanceOf(SecretsManagerClient.class);
  }

  @Test
  void getAmazonServiceDiscoveryV2ReturnsServiceDiscoveryClient() {
    ServiceDiscoveryClient client = provider.getAmazonServiceDiscoveryV2(creds, REGION);
    assertThat(client).isNotNull().isInstanceOf(ServiceDiscoveryClient.class);
  }

  @Test
  void getAmazonApplicationAutoScalingV2ReturnsApplicationAutoScalingClient() {
    ApplicationAutoScalingClient client = provider.getAmazonApplicationAutoScalingV2(creds, REGION);
    assertThat(client).isNotNull().isInstanceOf(ApplicationAutoScalingClient.class);
  }

  @Test
  void sameCredsAndRegionReturnCachedInstance() {
    EcsClient first = provider.getAmazonEcsV2(creds, REGION);
    EcsClient second = provider.getAmazonEcsV2(creds, REGION);
    assertThat(first).isSameAs(second);
  }

  @Test
  void differentRegionReturnsDifferentInstance() {
    EcsClient east = provider.getAmazonEcsV2(creds, "us-east-1");
    EcsClient west = provider.getAmazonEcsV2(creds, "us-west-2");
    assertThat(east).isNotSameAs(west);
  }
}
