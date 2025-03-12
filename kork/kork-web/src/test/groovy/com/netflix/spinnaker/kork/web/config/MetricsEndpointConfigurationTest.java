/*
 * Copyright 2021 Salesforce, Inc.
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

package com.netflix.spinnaker.kork.web.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.netflix.spinnaker.config.MetricsEndpointConfiguration;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.util.UriComponentsBuilder;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = {
      MetricsEndpointConfigurationTest.TestConfiguration.class,
      MetricsEndpointConfiguration.class
    })
@TestPropertySource(properties = {"spectator.web-endpoint.enabled = true"})
public class MetricsEndpointConfigurationTest {

  @LocalServerPort int port;

  @Autowired TestRestTemplate restTemplate;

  @Test
  public void spectatorMetricsAccess() {
    URI uri =
        UriComponentsBuilder.fromHttpUrl("http://localhost/spectator/metrics")
            .port(port)
            .build()
            .toUri();

    ResponseEntity<String> entity = restTemplate.getForEntity(uri, String.class);
    assertEquals(HttpStatus.OK, entity.getStatusCode());
  }

  @SpringBootApplication
  public static class TestConfiguration {}
}
