/*
 * Copyright 2024 Salesforce, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.halyard.config.config.v1;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class HalconfigConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              UserConfigurations.of(ResourceConfig.class, HalconfigDirectoryStructure.class));

  @BeforeEach
  void init(TestInfo testInfo) {
    System.out.println("--------------- Test " + testInfo.getDisplayName());
  }

  @Test
  void testHalconfigDirectory() {
    runner.run(
        ctx -> {
          String homeDir = System.getProperty("user.home");
          assertThat(homeDir).isNotNull();

          assertThat(ctx).hasSingleBean(HalconfigDirectoryStructure.class);
          String halconfigDirectory = ctx.getBean("halconfigDirectory", String.class);
          assertThat(halconfigDirectory).isNotNull();
          assertThat(halconfigDirectory).isEqualTo(homeDir + "/.hal");
          HalconfigDirectoryStructure halconfigDirectoyStructure =
              ctx.getBean(HalconfigDirectoryStructure.class);
          assertThat(halconfigDirectoyStructure.getHalconfigDirectory())
              .isEqualTo(halconfigDirectory);
        });
  }
}
