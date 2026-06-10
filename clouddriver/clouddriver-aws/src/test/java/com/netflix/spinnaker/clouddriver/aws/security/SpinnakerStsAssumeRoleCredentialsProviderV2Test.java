/*
 * Copyright 2025 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link SpinnakerStsAssumeRoleCredentialsProviderV2} helper logic. */
class SpinnakerStsAssumeRoleCredentialsProviderV2Test {

  @Test
  void fullArnPassedThroughUnchanged() {
    String arn = "arn:aws:iam::123456789012:role/SpinnakerManaged";
    assertThat(SpinnakerStsAssumeRoleCredentialsProviderV2.resolveRoleArn(arn, "123456789012"))
        .isEqualTo(arn);
  }

  @Test
  void shortRoleExpandedToFullArn() {
    assertThat(
            SpinnakerStsAssumeRoleCredentialsProviderV2.resolveRoleArn(
                "role/SpinnakerManaged", "123456789012"))
        .isEqualTo("arn:aws:iam::123456789012:role/SpinnakerManaged");
  }

  @Test
  void govCloudArnPassedThroughUnchanged() {
    String arn = "arn:aws-us-gov:iam::123456789012:role/SpinnakerManaged";
    assertThat(SpinnakerStsAssumeRoleCredentialsProviderV2.resolveRoleArn(arn, "123456789012"))
        .isEqualTo(arn);
  }

  @Test
  void chinaArnPassedThroughUnchanged() {
    String arn = "arn:aws-cn:iam::123456789012:role/SpinnakerManaged";
    assertThat(SpinnakerStsAssumeRoleCredentialsProviderV2.resolveRoleArn(arn, "123456789012"))
        .isEqualTo(arn);
  }
}
