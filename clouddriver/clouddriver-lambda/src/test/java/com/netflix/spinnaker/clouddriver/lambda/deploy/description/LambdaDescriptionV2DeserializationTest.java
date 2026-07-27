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

package com.netflix.spinnaker.clouddriver.lambda.deploy.description;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.aws.jackson.AwsSdkV2Module;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Verifies that Lambda descriptions carrying AWS SDK v2 model fields (which are immutable and
 * builder-only) deserialize from a JSON-like input Map — the same path the atomic-operation
 * converters use — thanks to {@link AwsSdkV2Module}'s builder-based deserializer. This is what
 * makes it possible to drop the previous hand-written *Description POJOs.
 */
class LambdaDescriptionV2DeserializationTest {

  private final ObjectMapper mapper =
      new ObjectMapper()
          .registerModule(new AwsSdkV2Module())
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  @Test
  void createDescriptionDeserializesV2DeadLetterAndTracingConfig() {
    Map<String, Object> input =
        Map.of(
            "functionName", "fn",
            "deadLetterConfig", Map.of("targetArn", "arn:aws:sqs:us-west-2:123:dlq"),
            "tracingConfig", Map.of("mode", "Active"));

    CreateLambdaFunctionDescription desc =
        mapper.convertValue(input, CreateLambdaFunctionDescription.class);

    assertThat(desc.getDeadLetterConfig().targetArn()).isEqualTo("arn:aws:sqs:us-west-2:123:dlq");
    assertThat(desc.getTracingConfig().modeAsString()).isEqualTo("Active");
  }

  @Test
  void eventMappingDescriptionDeserializesNestedV2DestinationConfig() {
    Map<String, Object> input =
        Map.of(
            "functionName", "fn",
            "eventSourceArn", "arn:aws:sqs:us-west-2:123:src",
            "destinationConfig",
                Map.of(
                    "onSuccess", Map.of("destination", "arn:aws:sqs:us-west-2:123:ok"),
                    "onFailure", Map.of("destination", "arn:aws:sqs:us-west-2:123:fail")));

    UpsertLambdaFunctionEventMappingDescription desc =
        mapper.convertValue(input, UpsertLambdaFunctionEventMappingDescription.class);

    assertThat(desc.getDestinationConfig().onSuccess().destination())
        .isEqualTo("arn:aws:sqs:us-west-2:123:ok");
    assertThat(desc.getDestinationConfig().onFailure().destination())
        .isEqualTo("arn:aws:sqs:us-west-2:123:fail");
  }

  @Test
  void createDescriptionRoundTripsThroughSerialization() {
    Map<String, Object> input =
        Map.of(
            "functionName", "fn",
            "deadLetterConfig", Map.of("targetArn", "arn:dlq"),
            "tracingConfig", Map.of("mode", "PassThrough"));

    CreateLambdaFunctionDescription desc =
        mapper.convertValue(input, CreateLambdaFunctionDescription.class);

    // Serialize back out (SdkPojoSerializer) and re-read to confirm both directions work.
    @SuppressWarnings("unchecked")
    Map<String, Object> serialized = mapper.convertValue(desc, Map.class);
    CreateLambdaFunctionDescription reparsed =
        mapper.convertValue(serialized, CreateLambdaFunctionDescription.class);

    assertThat(reparsed.getDeadLetterConfig().targetArn()).isEqualTo("arn:dlq");
    assertThat(reparsed.getTracingConfig().modeAsString()).isEqualTo("PassThrough");
  }
}
