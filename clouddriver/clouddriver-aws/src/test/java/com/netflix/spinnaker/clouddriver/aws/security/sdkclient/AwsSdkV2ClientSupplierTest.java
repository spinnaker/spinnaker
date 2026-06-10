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

package com.netflix.spinnaker.clouddriver.aws.security.sdkclient;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecs.EcsClient;

/**
 * Unit tests for {@link AwsSdkV2ClientSupplier}.
 *
 * <p>Tests verify caching semantics: same key returns the same client instance; different key
 * components produce distinct instances.
 */
class AwsSdkV2ClientSupplierTest {

  private AwsSdkV2ClientSupplier supplier;

  private static AwsCredentialsProvider dummyCreds() {
    return StaticCredentialsProvider.create(AwsBasicCredentials.create("key", "secret"));
  }

  @BeforeEach
  void setUp() {
    supplier = new AwsSdkV2ClientSupplier();
  }

  @Test
  void sameKeyReturnsSameInstance() {
    AwsCredentialsProvider creds = dummyCreds();

    EcsClient first = supplier.getClient(EcsClient::builder, EcsClient.class, creds, "us-east-1");
    EcsClient second = supplier.getClient(EcsClient::builder, EcsClient.class, creds, "us-east-1");

    assertThat(first).isSameAs(second);
  }

  @Test
  void differentRegionReturnsDifferentInstance() {
    AwsCredentialsProvider creds = dummyCreds();

    EcsClient east = supplier.getClient(EcsClient::builder, EcsClient.class, creds, "us-east-1");
    EcsClient west = supplier.getClient(EcsClient::builder, EcsClient.class, creds, "us-west-2");

    assertThat(east).isNotSameAs(west);
  }

  @Test
  void differentCredentialsReturnDifferentInstance() {
    AwsCredentialsProvider creds1 = dummyCreds();
    AwsCredentialsProvider creds2 = dummyCreds();

    EcsClient c1 = supplier.getClient(EcsClient::builder, EcsClient.class, creds1, "us-east-1");
    EcsClient c2 = supplier.getClient(EcsClient::builder, EcsClient.class, creds2, "us-east-1");

    // Different provider instances → different cache entries.
    assertThat(c1).isNotSameAs(c2);
  }

  @Test
  void differentServiceTypesReturnDifferentInstances() {
    AwsCredentialsProvider creds = dummyCreds();

    EcsClient ecs = supplier.getClient(EcsClient::builder, EcsClient.class, creds, "us-east-1");
    EcrClient ecr = supplier.getClient(EcrClient::builder, EcrClient.class, creds, "us-east-1");

    // Different types, so asserting they are not the same object is always trivially true,
    // but this test guards that no ClassCastException is thrown.
    assertThat(ecs).isNotNull();
    assertThat(ecr).isNotNull();
  }
}
