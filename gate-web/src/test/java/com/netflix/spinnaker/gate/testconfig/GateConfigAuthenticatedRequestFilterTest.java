/*
 * Copyright 2023 Salesforce, Inc.
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

package com.netflix.spinnaker.gate.testconfig;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import ch.qos.logback.classic.Level;
import com.netflix.spinnaker.fiat.shared.FiatService;
import com.netflix.spinnaker.gate.Main;
import com.netflix.spinnaker.gate.services.ApplicationService;
import com.netflix.spinnaker.gate.services.PermissionService;
import com.netflix.spinnaker.gate.services.internal.ClouddriverService;
import com.netflix.spinnaker.gate.services.internal.ClouddriverServiceSelector;
import com.netflix.spinnaker.gate.services.internal.ExtendedFiatService;
import com.netflix.spinnaker.gate.services.internal.Front50Service;
import com.netflix.spinnaker.kork.client.ServiceClientProvider;
import com.netflix.spinnaker.kork.common.Header;
import com.netflix.spinnaker.kork.test.log.MemoryAppender;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(
    classes = {Main.class, GateConfigAuthenticatedRequestFilterTest.TestController.class})
@TestPropertySource(properties = {"spring.config.location=classpath:gate-test.yml"})
public class GateConfigAuthenticatedRequestFilterTest {

  private static final String API_BASE = "/test";
  private static final String API_PATH = "/api";
  private static final String TEST_USER = "Test-User";
  private static final String TEST_EXECUTION_ID = "Test-Execution-ID";
  private static final String TEST_EXECUTION_TYPE = "Test-Execution-Type";
  private static final String TEST_APPLICATION = "Test-Application";

  private static final String LOG_MESSAGE = " logged in api: ";
  private static final String NULL_VALUE = "null";

  @MockBean private ServiceClientProvider mockServiceClientProvider;

  @MockBean private ClouddriverService mockClouddriverService;

  @MockBean private ClouddriverServiceSelector mockClouddriverServiceSelector;

  @MockBean private ApplicationService mockApplicationService;

  @MockBean private FiatService mockFiatService;

  @MockBean private PermissionService mockPermissionService;

  @MockBean private ExtendedFiatService mockExtendedFiatService;

  @MockBean private Front50Service mockFront50Service;

  @RestController
  @RequestMapping(value = API_BASE)
  static class TestController {
    private final Logger log = LoggerFactory.getLogger(getClass());

    @RequestMapping(value = API_PATH, method = RequestMethod.GET)
    public void api() {
      log.info(
          Header.USER.name()
              + LOG_MESSAGE
              + AuthenticatedRequest.get(Header.USER).orElse(NULL_VALUE));
      log.info(
          Header.APPLICATION.name()
              + LOG_MESSAGE
              + AuthenticatedRequest.get(Header.APPLICATION).orElse(NULL_VALUE));
      log.info(
          Header.EXECUTION_ID.name()
              + LOG_MESSAGE
              + AuthenticatedRequest.get(Header.EXECUTION_ID).orElse(NULL_VALUE));
      log.info(
          Header.EXECUTION_TYPE.name()
              + LOG_MESSAGE
              + AuthenticatedRequest.get(Header.EXECUTION_TYPE).orElse(NULL_VALUE));
    }
  }

  @Autowired
  @Qualifier("authenticatedRequestFilter")
  private FilterRegistrationBean filterRegistrationBean;

  @Autowired private WebApplicationContext webApplicationContext;

  private MockMvc mockMvc;

  // Without setting the extractSpinnakerHeaders flag to true when creating
  // AuthenticatedRequestFilter, X-SPINNAKER-* headers would not be copied into
  // AuthenticatedRequest MDC for downstream consumption
  @Test
  void TestHeaderAvailableInAuthenticatedRequestMDC() throws Exception {
    mockMvc =
        webAppContextSetup(webApplicationContext)
            .addFilters(filterRegistrationBean.getFilter())
            .build();

    MemoryAppender memoryAppender =
        new MemoryAppender(GateConfigAuthenticatedRequestFilterTest.TestController.class);

    mockMvc
        .perform(
            get(API_BASE + API_PATH)
                .header(Header.USER.getHeader(), TEST_USER)
                .header(Header.APPLICATION.getHeader(), TEST_APPLICATION)
                .header(Header.EXECUTION_ID.getHeader(), TEST_EXECUTION_ID)
                .header(Header.EXECUTION_TYPE.getHeader(), TEST_EXECUTION_TYPE))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(header().exists(Header.REQUEST_ID.getHeader()));

    List<String> messages = memoryAppender.layoutSearch(LOG_MESSAGE, Level.INFO);
    String expectedUserLog = Header.USER.name() + LOG_MESSAGE + TEST_USER;
    String expectedApplicationLog = Header.APPLICATION.name() + LOG_MESSAGE + TEST_APPLICATION;
    String expectedExecutionIdLog = Header.EXECUTION_ID.name() + LOG_MESSAGE + TEST_EXECUTION_ID;
    String expectedExecutionTypeLog =
        Header.EXECUTION_TYPE.name() + LOG_MESSAGE + TEST_EXECUTION_TYPE;

    assertThat(messages.size(), equalTo(4));
    assertThat(
        messages,
        contains(
            containsString(expectedUserLog),
            containsString(expectedApplicationLog),
            containsString(expectedExecutionIdLog),
            containsString(expectedExecutionTypeLog)));
  }
}
