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

package com.netflix.spinnaker.clouddriver.cloudfoundry.client.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClientUtils;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class ErrorCodeTest {

  @Test
  void deserialize() throws IOException {
    ObjectMapper mapper = CloudFoundryClientUtils.getMapper();
    assertThat(mapper.readValue("\"CF-RouteHostTaken\"", ErrorDescription.Code.class))
        .isEqualTo(ErrorDescription.Code.ROUTE_HOST_TAKEN);
  }

  @Test
  void deserializeV2() throws IOException {
    ObjectMapper mapper = CloudFoundryClientUtils.getMapper();
    ErrorDescription err =
        mapper.readValue(
            "{\"description\":\"The host is taken: tester\",\"error_code\":\"CF-RouteHostTaken\",\"code\":210003}",
            ErrorDescription.class);

    assertThat(err.getCode()).isEqualTo(ErrorDescription.Code.ROUTE_HOST_TAKEN);
    assertThat(err.getErrors()).contains("The host is taken: tester");
  }

  @Test
  void deserializeV3() throws IOException {
    ObjectMapper mapper = CloudFoundryClientUtils.getMapper();
    ErrorDescription err =
        mapper.readValue(
            "{\"errors\":[{\"code\":210003,\"title\":\"CF-RouteHostTaken\",\"detail\":\"The host is taken: tester\"}]}",
            ErrorDescription.class);

    assertThat(err.getCode()).isEqualTo(ErrorDescription.Code.ROUTE_HOST_TAKEN);
    assertThat(err.getErrors()).contains("The host is taken: tester");
  }
}
