/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.jackson;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.autoscaling.model.AutoScalingGroup;

/**
 * Cached ASG attribute names must match the AWS SDK v1 names that Deck and other consumers read.
 */
final class SdkPojoSerializerNamingTest {

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new AwsSdkV2Module());

  @Test
  void lowercasesLeadingAcronymLikeSdkV1() {
    AutoScalingGroup asg =
        AutoScalingGroup.builder()
            .autoScalingGroupName("app-v001")
            .vpcZoneIdentifier("subnet-1,subnet-2")
            .build();

    Map<String, Object> attributes =
        objectMapper.convertValue(asg, new TypeReference<Map<String, Object>>() {});

    assertThat(attributes)
        .containsEntry("vpczoneIdentifier", "subnet-1,subnet-2")
        .containsEntry("autoScalingGroupName", "app-v001")
        .doesNotContainKey("vPCZoneIdentifier");
  }

  @Test
  void roundTripsLeadingAcronymField() {
    AutoScalingGroup asg = AutoScalingGroup.builder().vpcZoneIdentifier("subnet-1").build();

    Map<String, Object> attributes =
        objectMapper.convertValue(asg, new TypeReference<Map<String, Object>>() {});

    assertThat(objectMapper.convertValue(attributes, AutoScalingGroup.class).vpcZoneIdentifier())
        .isEqualTo("subnet-1");
  }
}
