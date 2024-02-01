/*
 * Copyright 2024 Harness, Inc.
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

package com.netflix.spinnaker.gate.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.gate.Main;
import com.netflix.spinnaker.gate.services.internal.EchoService;
import com.squareup.okhttp.mockwebserver.Dispatcher;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import com.squareup.okhttp.mockwebserver.RecordedRequest;
import groovy.util.logging.Slf4j;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@ActiveProfiles("echo")
@Slf4j
@DirtiesContext
@SpringBootTest(classes = {Main.class})
@TestPropertySource("/application-echo.properties")
class EchoServiceTest {

  @Autowired EchoService echoService;

  private static MockWebServer echoServer;
  private static MockWebServer clouddriverServer;
  private static MockWebServer front50Server;

  @BeforeAll
  static void setUp() throws IOException {
    clouddriverServer = new MockWebServer();
    clouddriverServer.start(7002);

    Dispatcher clouddriverDispatcher =
        new Dispatcher() {
          @Override
          public MockResponse dispatch(RecordedRequest request) {
            return new MockResponse().setResponseCode(200);
          }
        };
    clouddriverServer.setDispatcher(clouddriverDispatcher);

    front50Server = new MockWebServer();
    front50Server.start(8081);
    Dispatcher front50Dispatcher =
        new Dispatcher() {
          @Override
          public MockResponse dispatch(RecordedRequest request) {
            return new MockResponse().setResponseCode(200);
          }
        };
    front50Server.setDispatcher(front50Dispatcher);

    echoServer = new MockWebServer();
    echoServer.start(8089);
  }

  @AfterAll
  static void tearDown() throws IOException {
    echoServer.shutdown();
    clouddriverServer.shutdown();
    front50Server.shutdown();
  }

  @Test
  void shouldNotOrderTheKeysWhenCallingEcho() throws InterruptedException {

    echoServer.enqueue(new MockResponse().setResponseCode(200));
    Map<String, Object> body = new HashMap<>();
    body.put("ref", "refs/heads/main");
    body.put("before", "ca7376e4b730f1f2878760abaeaed6c039fc5414");
    body.put("after", "c2420ce6e341ef0042f2e12591bdbe9eec29a032");
    body.put("id", 105648914);

    echoService.webhooks("git", "github", body);
    RecordedRequest recordedRequest = echoServer.takeRequest(2, TimeUnit.SECONDS);
    String requestBody = recordedRequest.getBody().readUtf8();
    assertThat(requestBody)
        .isEqualTo(
            "{\"ref\":\"refs/heads/main\",\"before\":\"ca7376e4b730f1f2878760abaeaed6c039fc5414\",\"after\":\"c2420ce6e341ef0042f2e12591bdbe9eec29a032\",\"id\":105648914}");
  }
}
