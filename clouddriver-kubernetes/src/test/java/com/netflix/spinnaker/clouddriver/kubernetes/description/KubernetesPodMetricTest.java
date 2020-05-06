/*
 * Copyright 2020 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.description;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesPodMetric.ContainerMetric;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
final class KubernetesPodMetricTest {
  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  public void deserializeContainerMetric() throws IOException {
    String json =
        Resources.toString(
            KubernetesPodMetricTest.class.getResource("pod-metric.json"), StandardCharsets.UTF_8);

    KubernetesPodMetric.ContainerMetric containerMetric =
        objectMapper.readValue(json, KubernetesPodMetric.ContainerMetric.class);
    assertThat(containerMetric.getContainerName()).isEqualTo("istio-proxy");
    assertThat(containerMetric.getMetrics())
        .containsOnly(entry("MEMORY(bytes)", "27Mi"), entry("CPU(cores)", "3m"));
  }

  @Test
  public void deserializeContainerMetricWithUnknownField() throws IOException {
    String json =
        Resources.toString(
            KubernetesPodMetricTest.class.getResource("pod-metric-extra-property.json"),
            StandardCharsets.UTF_8);

    KubernetesPodMetric.ContainerMetric containerMetric =
        objectMapper.readValue(json, KubernetesPodMetric.ContainerMetric.class);
    assertThat(containerMetric.getContainerName()).isEqualTo("istio-proxy");
    assertThat(containerMetric.getMetrics())
        .containsOnly(entry("MEMORY(bytes)", "27Mi"), entry("CPU(cores)", "3m"));
  }

  @Test
  public void serializeContainerMetric() throws IOException {
    String expectedResult =
        Resources.toString(
            KubernetesPodMetricTest.class.getResource("pod-metric.json"), StandardCharsets.UTF_8);

    KubernetesPodMetric.ContainerMetric metric =
        new ContainerMetric(
            "istio-proxy",
            ImmutableMap.of(
                "MEMORY(bytes)", "27Mi",
                "CPU(cores)", "3m"));
    String result = objectMapper.writeValueAsString(metric);

    // Compare the parsed trees of the two results, which is agnostic to key order
    AssertionsForClassTypes.assertThat(objectMapper.readTree(result))
        .isEqualTo(objectMapper.readTree(expectedResult));
  }
}
