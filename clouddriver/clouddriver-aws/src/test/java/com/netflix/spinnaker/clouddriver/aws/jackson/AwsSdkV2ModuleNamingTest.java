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
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.autoscaling.model.AutoScalingGroup;
import software.amazon.awssdk.services.autoscaling.model.Instance;
import software.amazon.awssdk.services.autoscaling.model.TagDescription;
import software.amazon.awssdk.services.cloudwatch.model.MetricAlarm;
import software.amazon.awssdk.services.elasticloadbalancing.model.Listener;
import software.amazon.awssdk.services.elasticloadbalancing.model.ListenerDescription;
import software.amazon.awssdk.services.elasticloadbalancing.model.LoadBalancerDescription;
import software.amazon.awssdk.services.lambda.model.EventSourceMappingConfiguration;

/**
 * Cached SDK v2 attributes are keyed by the builder's property names, and read back through the
 * same builder, so members whose AWS name starts with an acronym (VPCZoneIdentifier, DNSName, UUID,
 * ...) survive the cache round trip.
 */
final class AwsSdkV2ModuleNamingTest {

  private static final TypeReference<Map<String, Object>> ATTRIBUTES = new TypeReference<>() {};

  /** {@code amazonObjectMapper}, which has no NON_NULL inclusion of its own. */
  private final ObjectMapper objectMapper =
      AwsObjectMapperFactory.createConfigured()
          .registerModule(new JavaTimeModule())
          .registerModule(new AwsSdkV2Module());

  @Test
  void serverGroupRoundTripsWithBuilderPropertyNames() {
    AutoScalingGroup asg =
        AutoScalingGroup.builder()
            .autoScalingGroupName("app-v001")
            .vpcZoneIdentifier("subnet-1,subnet-2")
            .createdTime(Instant.ofEpochMilli(1790816562123L))
            .instances(Instance.builder().instanceId("i-1").lifecycleState("InService").build())
            .tags(TagDescription.builder().key("k").value("v").build())
            .suspendedProcesses(List.of())
            .build();

    Map<String, Object> attributes = objectMapper.convertValue(asg, ATTRIBUTES);

    assertThat(attributes)
        .containsEntry("vpcZoneIdentifier", "subnet-1,subnet-2")
        .containsEntry("suspendedProcesses", List.of())
        .doesNotContainKeys("vPCZoneIdentifier", "vpczoneIdentifier", "launchConfigurationName");
    AutoScalingGroup roundTripped = objectMapper.convertValue(attributes, AutoScalingGroup.class);
    assertThat(objectMapper.convertValue(roundTripped, ATTRIBUTES)).isEqualTo(attributes);
    assertThat(roundTripped.vpcZoneIdentifier()).isEqualTo("subnet-1,subnet-2");
  }

  @Test
  void loadBalancerRoundTripsWithBuilderPropertyNames() {
    LoadBalancerDescription elb =
        LoadBalancerDescription.builder()
            .loadBalancerName("app-frontend")
            .dnsName("app-frontend.elb.amazonaws.com")
            .vpcId("vpc-1")
            .listenerDescriptions(
                ListenerDescription.builder()
                    .listener(
                        Listener.builder()
                            .loadBalancerPort(443)
                            .sslCertificateId("arn:cert")
                            .build())
                    .build())
            .build();

    Map<String, Object> attributes = objectMapper.convertValue(elb, ATTRIBUTES);

    assertThat(attributes)
        .containsEntry("dnsName", "app-frontend.elb.amazonaws.com")
        .containsEntry("vpcId", "vpc-1");
    assertThat(attributes.get("listenerDescriptions").toString()).contains("sslCertificateId");
    LoadBalancerDescription roundTripped =
        objectMapper.convertValue(attributes, LoadBalancerDescription.class);
    assertThat(objectMapper.convertValue(roundTripped, ATTRIBUTES)).isEqualTo(attributes);
    assertThat(roundTripped.dnsName()).isEqualTo("app-frontend.elb.amazonaws.com");
    assertThat(roundTripped.listenerDescriptions().get(0).listener().sslCertificateId())
        .isEqualTo("arn:cert");
  }

  @Test
  void alarmAndEventSourceMappingUseBuilderPropertyNames() {
    MetricAlarm alarm = MetricAlarm.builder().alarmName("high-cpu").okActions("arn:ok").build();
    EventSourceMappingConfiguration mapping =
        EventSourceMappingConfiguration.builder().uuid("1234").kmsKeyArn("arn:kms").build();

    assertThat(objectMapper.convertValue(alarm, ATTRIBUTES))
        .containsEntry("okActions", List.of("arn:ok"));
    assertThat(objectMapper.convertValue(mapping, ATTRIBUTES))
        .containsEntry("uuid", "1234")
        .containsEntry("kmsKeyArn", "arn:kms");
  }

  @Test
  void unsetListsAndMapsAreWrittenEmptyLikeTheModelAccessors() {
    AutoScalingGroup asg = AutoScalingGroup.builder().autoScalingGroupName("app-v001").build();
    EventSourceMappingConfiguration mapping =
        EventSourceMappingConfiguration.builder().uuid("1234").build();

    assertThat(objectMapper.convertValue(asg, ATTRIBUTES))
        .containsEntry("suspendedProcesses", List.of())
        .containsEntry("instances", List.of())
        .doesNotContainKey("vpcZoneIdentifier");
    assertThat(objectMapper.convertValue(mapping, ATTRIBUTES))
        .containsEntry("topics", List.of())
        .doesNotContainKey("kmsKeyArn");
  }

  @Test
  void builderSerializesTheSameAsItsModel() {
    AutoScalingGroup asg =
        AutoScalingGroup.builder().autoScalingGroupName("app-v001").vpcZoneIdentifier("s").build();

    assertThat(objectMapper.convertValue(asg.toBuilder(), ATTRIBUTES))
        .isEqualTo(objectMapper.convertValue(asg, ATTRIBUTES));
  }
}
