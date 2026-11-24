/*
 * Copyright 2026 spinnaker.io
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

package com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class AzureLoadBalancerTest {

  private static AzureLoadBalancer loadBalancer(String name) {
    AzureLoadBalancer loadBalancer = new AzureLoadBalancer();
    loadBalancer.setAccount("my-account");
    loadBalancer.setName(name);
    loadBalancer.setRegion("westus");
    return loadBalancer;
  }

  @Test
  void equalsComparesAccountNameTypeAndRegion() {
    assertThat(loadBalancer("lb")).isEqualTo(loadBalancer("lb"));
    assertThat(loadBalancer("lb")).isNotEqualTo(loadBalancer("other-lb"));
  }

  @Test
  void equalsReturnsFalseForNull() {
    assertThat(loadBalancer("lb").equals(null)).isFalse();
  }

  @Test
  void equalsReturnsFalseForOtherTypes() {
    assertThat(loadBalancer("lb").equals("lb")).isFalse();
  }
}
