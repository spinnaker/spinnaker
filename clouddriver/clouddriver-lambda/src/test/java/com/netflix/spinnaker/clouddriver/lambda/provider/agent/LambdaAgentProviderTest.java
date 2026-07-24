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

package com.netflix.spinnaker.clouddriver.lambda.provider.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.core.limits.ServiceLimitConfiguration;
import com.netflix.spinnaker.config.LambdaServiceConfig;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.lambda.model.FunctionConfiguration;

class LambdaAgentProviderTest {

  /**
   * Guards the production wiring: the ObjectMapper built by the provider must register {@code
   * AwsSdkV2Module} so caching-path conversions of AWS SDK v2 model objects (which are not standard
   * Jackson beans) produce a populated Map. Without the module, the default mapper emits empty
   * output and this test fails.
   */
  @Test
  void objectMapperSerializesSdkPojoFields() {
    LambdaAgentProvider provider =
        new LambdaAgentProvider(
            mock(AmazonClientProvider.class),
            mock(LambdaServiceConfig.class),
            mock(ServiceLimitConfiguration.class));

    ObjectMapper objectMapper =
        (ObjectMapper) ReflectionTestUtils.getField(provider, "objectMapper");

    FunctionConfiguration config =
        FunctionConfiguration.builder()
            .functionName("testFunction")
            .runtime("java17")
            .memorySize(512)
            .build();

    @SuppressWarnings("unchecked")
    Map<String, Object> serialized = objectMapper.convertValue(config, Map.class);

    assertThat(serialized).containsEntry("functionName", "testFunction");
    assertThat(serialized).containsEntry("runtime", "java17");
    assertThat(serialized).containsEntry("memorySize", 512);
  }
}
