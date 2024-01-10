/*
 * Copyright 2022 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.Main;
import com.netflix.spinnaker.clouddriver.kubernetes.model.KubernetesJobStatus;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodCondition;
import io.kubernetes.client.openapi.models.V1PodStatus;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@SpringBootTest(classes = Main.class)
@TestPropertySource(
    properties = {
      "redis.enabled = false",
      "sql.enabled = false",
      "spring.application.name = clouddriver"
    })
public class ObjectMapperTest {

  @Autowired private ObjectMapper objectMapper;

  @Test
  public void testJodaTimeSerializationForKubernetesJob() {
    V1Job job = new V1Job();
    V1ObjectMeta metadata = new V1ObjectMeta();
    metadata.setCreationTimestamp(OffsetDateTime.now());
    job.setMetadata(metadata);
    KubernetesJobStatus kubernetesJobStatus = new KubernetesJobStatus(job, "kubernetesAccount");

    V1Pod pod = new V1Pod();
    V1PodStatus status = new V1PodStatus();
    V1PodCondition condition = new V1PodCondition();
    condition.setLastTransitionTime(OffsetDateTime.now());
    status.setConditions(List.of(condition));
    pod.setStatus(status);
    V1ObjectMeta metadataPod = new V1ObjectMeta();
    metadataPod.setName("podName");
    pod.setMetadata(metadata);

    KubernetesJobStatus.PodStatus podStatus = new KubernetesJobStatus.PodStatus(pod);
    kubernetesJobStatus.setPods(List.of(podStatus));

    assertDoesNotThrow(() -> objectMapper.writeValueAsString(kubernetesJobStatus));
  }
}
