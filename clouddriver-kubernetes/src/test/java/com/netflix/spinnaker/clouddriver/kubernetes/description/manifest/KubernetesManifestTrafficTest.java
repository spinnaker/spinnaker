/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.description.manifest;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
final class KubernetesManifestTrafficTest {
  @Test
  final void createNullTraffic() {
    KubernetesManifestTraffic traffic = new KubernetesManifestTraffic(null);
    assertThat(traffic.getLoadBalancers()).isNotNull();
    assertThat(traffic.getLoadBalancers()).isEmpty();
  }

  @Test
  final void createEmptyTraffic() {
    KubernetesManifestTraffic traffic = new KubernetesManifestTraffic(ImmutableList.of());
    assertThat(traffic.getLoadBalancers()).isEmpty();
  }

  @Test
  final void createNonEmptyTraffic() {
    KubernetesManifestTraffic traffic =
        new KubernetesManifestTraffic(ImmutableList.of("abc", "def"));
    assertThat(traffic.getLoadBalancers()).containsExactly("abc", "def");
  }

  @Test
  final void listIsImmutable() {
    List<String> loadBalancers = new ArrayList<>();
    loadBalancers.add("abc");
    KubernetesManifestTraffic traffic = new KubernetesManifestTraffic(loadBalancers);
    assertThat(traffic.getLoadBalancers()).containsExactly("abc");

    loadBalancers.add("def");
    assertThat(traffic.getLoadBalancers()).containsExactly("abc");
  }
}
