/*
 * Copyright 2025 Salesforce, Inc.
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

package com.netflix.spinnaker.gate.filters;

import static com.netflix.spinnaker.kork.common.Header.EXECUTION_ID;
import static com.netflix.spinnaker.kork.common.Header.REQUEST_ID;
import static com.netflix.spinnaker.kork.common.Header.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import ch.qos.logback.classic.Level;
import com.netflix.spinnaker.gate.Main;
import com.netflix.spinnaker.gate.health.DownstreamServicesHealthIndicator;
import com.netflix.spinnaker.gate.services.internal.ClouddriverService;
import com.netflix.spinnaker.kork.test.log.MemoryAppender;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import retrofit2.mock.Calls;

public class ProvidedIdRequestFilterTest {

  private static final String API_BASE = "/test-provided-id-request-filter";
  private static final String API_PATH = "/api";
  private static final String TEST_REQUEST_ID = "test-request-id";
  private static final String TEST_EXECUTION_ID = "test-execution-id";
  private static final String TEST_USER_ID = "test-user-id";

  @RestController
  @RequestMapping(value = API_BASE)
  static class TestController {

    final org.slf4j.Logger log = LoggerFactory.getLogger(TestController.class);

    @RequestMapping(value = API_PATH, method = RequestMethod.GET)
    public void api() {
      log.info("MDC: {}", MDC.getCopyOfContextMap());
    }
  }

  private MockMvc mockMvc;

  @BeforeEach
  void setup(TestInfo testInfo) {
    System.out.println("--------------- Test " + testInfo.getDisplayName());
    MDC.clear();
  }

  @Nested
  @SpringBootTest(classes = {Main.class, ProvidedIdRequestFilterTest.TestController.class})
  @TestPropertySource(
      properties = {
        "spring.config.location=classpath:gate-test.yml",
        "services.front50.applicationRefreshInitialDelayMs=3600000",
        "provided-id-request-filter.enabled=true"
      })
  class DefaultBehaviorTest {
    @Autowired private WebApplicationContext webApplicationContext;

    @Autowired FilterRegistrationBean<ProvidedIdRequestFilter> providedIdRequestFilter;

    @MockBean ClouddriverService clouddriverService;

    /** To prevent periodic calls to service's /health endpoints */
    @MockBean DownstreamServicesHealthIndicator downstreamServicesHealthIndicator;

    @BeforeEach
    void setup() {
      mockMvc =
          webAppContextSetup(webApplicationContext)
              .addFilters(providedIdRequestFilter.getFilter())
              .build();

      // To keep DefaultProviderLookupService.loadAccounts happy
      when(clouddriverService.getAccountDetails()).thenReturn(Calls.response(List.of()));
    }

    @Test
    public void testHeadersInMDC() throws Exception {
      // Capture the log messages that our test endpoint generates
      MemoryAppender memoryAppender = new MemoryAppender(TestController.class);

      // Include all the headers that ProvidedIdRequestFilter looks at by default.
      mockMvc
          .perform(
              get(API_BASE + API_PATH)
                  .header(REQUEST_ID.getHeader(), TEST_REQUEST_ID)
                  .header(EXECUTION_ID.getHeader(), TEST_EXECUTION_ID)
                  .characterEncoding(StandardCharsets.UTF_8.toString()))
          .andDo(print())
          .andExpect(status().isOk());

      // Verify that the value of those headers is in the MDC by examining the log.
      List<String> logMessages =
          memoryAppender.search(
              "MDC: {X-SPINNAKER-REQUEST-ID="
                  + TEST_REQUEST_ID
                  + ", X-SPINNAKER-EXECUTION-ID="
                  + TEST_EXECUTION_ID
                  + "}",
              Level.INFO);
      assertThat(logMessages).hasSize(1);
    }
  }

  @Nested
  @SpringBootTest(classes = {Main.class, ProvidedIdRequestFilterTest.TestController.class})
  @TestPropertySource(
      properties = {
        "spring.config.location=classpath:gate-test.yml",
        "services.front50.applicationRefreshInitialDelayMs=3600000",
        "provided-id-request-filter.enabled=true",
        "provided-id-request-filter.headers=X-SPINNAKER-REQUEST-ID", // arbitrary, different than
        // the default
        "provided-id-request-filter.additionalHeaders=X-SPINNAKER-USER" // arbitrary
      })
  class CustomConfigurationTest {
    @Autowired private WebApplicationContext webApplicationContext;

    @Autowired FilterRegistrationBean<ProvidedIdRequestFilter> providedIdRequestFilter;

    @MockBean ClouddriverService clouddriverService;

    /** To prevent periodic calls to service's /health endpoints */
    @MockBean DownstreamServicesHealthIndicator downstreamServicesHealthIndicator;

    @BeforeEach
    void setup() {
      mockMvc =
          webAppContextSetup(webApplicationContext)
              .addFilters(providedIdRequestFilter.getFilter())
              .build();

      // To keep DefaultProviderLookupService.loadAccounts happy
      when(clouddriverService.getAccountDetails()).thenReturn(Calls.response(List.of()));
    }

    @Test
    public void testHeadersInMDC() throws Exception {
      // Capture the log messages that our test endpoint generates
      MemoryAppender memoryAppender = new MemoryAppender(TestController.class);

      // Include all the headers that ProvidedIdRequestFilter looks at by
      // default, and the additional ones we've configured.  Verify that only
      // the configured headers make it into the MDC.
      mockMvc
          .perform(
              get(API_BASE + API_PATH)
                  .header(REQUEST_ID.getHeader(), TEST_REQUEST_ID)
                  .header(EXECUTION_ID.getHeader(), TEST_EXECUTION_ID)
                  .header(USER.getHeader(), TEST_USER_ID)
                  .characterEncoding(StandardCharsets.UTF_8.toString()))
          .andDo(print())
          .andExpect(status().isOk());

      // Verify that the value of those headers is in the MDC by examining the log.
      List<String> logMessages =
          memoryAppender.search(
              "MDC: {X-SPINNAKER-REQUEST-ID="
                  + TEST_REQUEST_ID
                  + ", X-SPINNAKER-USER="
                  + TEST_USER_ID
                  + "}",
              Level.INFO);
      assertThat(logMessages).hasSize(1);
    }
  }
}
