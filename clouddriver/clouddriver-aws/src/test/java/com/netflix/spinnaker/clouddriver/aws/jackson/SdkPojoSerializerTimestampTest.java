/*
 * Copyright 2026 spinnaker.io
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

package com.netflix.spinnaker.clouddriver.aws.jackson;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.autoscaling.model.AutoScalingGroup;
import software.amazon.awssdk.services.ec2.model.Instance;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link SdkPojoSerializer} hands each field to the mapper's own serializer, so AWS SDK v2 shapes
 * inherit its Instant handling. These assert the cached representation, which downstream reads as
 * epoch millis -- {@code AmazonServerGroup.getCreatedTime()} casts the attribute straight to Long.
 */
final class SdkPojoSerializerTimestampTest {

  private static final long EPOCH_MILLIS = 1790816562123L;

  /** Mirrors the shared mapper the caching agents receive; see CloudDriverConfigTest. */
  private final ObjectMapper objectMapper =
      JsonMapper.builder()
          .addModule(new AwsSdkV2Module())
          .enable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
          .disable(DateTimeFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
          .disable(DateTimeFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
          .build();

  @Test
  void cachesServerGroupCreatedTimeAsEpochMillis() {
    AutoScalingGroup asg =
        AutoScalingGroup.builder()
            .autoScalingGroupName("app-stack-v001")
            .createdTime(Instant.ofEpochMilli(EPOCH_MILLIS))
            .build();

    Map<String, Object> attributes =
        objectMapper.convertValue(asg, new TypeReference<Map<String, Object>>() {});

    assertThat(attributes.get("createdTime")).isEqualTo(EPOCH_MILLIS);
  }

  @Test
  void cachesInstanceLaunchTimeAsEpochMillis() {
    Instance instance =
        Instance.builder()
            .instanceId("i-0123456789abcdef0")
            .launchTime(Instant.ofEpochMilli(EPOCH_MILLIS))
            .build();

    Map<String, Object> attributes =
        objectMapper.convertValue(instance, new TypeReference<Map<String, Object>>() {});

    assertThat(attributes.get("launchTime")).isEqualTo(EPOCH_MILLIS);
  }

  /** EcsInstanceCacheClient reads the cached instance attributes back into the SDK v2 shape. */
  @Test
  void readsCachedInstanceAttributesBackIntoTheSameLaunchTime() {
    Instance instance =
        Instance.builder()
            .instanceId("i-0123456789abcdef0")
            .launchTime(Instant.ofEpochMilli(EPOCH_MILLIS))
            .build();

    Map<String, Object> attributes =
        objectMapper.convertValue(instance, new TypeReference<Map<String, Object>>() {});

    assertThat(objectMapper.convertValue(attributes, Instance.class).launchTime())
        .isEqualTo(Instant.ofEpochMilli(EPOCH_MILLIS));
  }
}
