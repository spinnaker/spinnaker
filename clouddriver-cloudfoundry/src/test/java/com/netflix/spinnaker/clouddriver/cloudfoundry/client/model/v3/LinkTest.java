/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LinkTest {
  @Test
  void getGuid() {
    Link link = new Link();
    link.setHref(
        "https://api.sys.calabasas.cf-app.com/v3/spaces/72d50cd9-434e-4738-9349-cb146987b963");
    assertThat(link.getGuid()).isEqualTo("72d50cd9-434e-4738-9349-cb146987b963");
  }
}
