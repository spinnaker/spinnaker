/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.ops;

import org.junit.jupiter.api.Test;

import static com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.ops.DeployCloudFoundryServerGroupAtomicOperation.convertToMb;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeployCloudFoundryServerGroupAtomicOperationTest {

  @Test
  void convertToMbHandling() {
    assertThat(convertToMb("memory", "123")).isEqualTo(123);
    assertThat(convertToMb("memory", "1G")).isEqualTo(1024);
    assertThat(convertToMb("memory", "1M")).isEqualTo(1);

    assertThatThrownBy(() -> convertToMb("memory", "abc")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> convertToMb("memory", "123.45")).isInstanceOf(IllegalArgumentException.class);
  }
}