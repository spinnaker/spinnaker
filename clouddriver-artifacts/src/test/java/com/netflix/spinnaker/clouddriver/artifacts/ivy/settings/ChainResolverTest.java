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

package com.netflix.spinnaker.clouddriver.artifacts.ivy.settings;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Java6Assertions.assertThat;

class ChainResolverTest {
  @Test
  void resolversAreUnwrapped() throws IOException {
    ChainResolver chainResolver = new XmlMapper().readValue(
      "<chain name=\"main\">\n" +
        "  <ibiblio name=\"public\" m2compatible=\"true\" root=\"https://repo.spring.io/libs-release\" />\n" +
        "</chain>", ChainResolver.class);

    assertThat(chainResolver.getResolvers().getIbiblio()).hasSize(1);
  }
}