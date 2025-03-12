/*
 * Copyright 2022 Salesforce, Inc.
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

package com.netflix.spinnaker.gate.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import ch.qos.logback.classic.Level;
import com.netflix.spinnaker.gate.Main;
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
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@SpringBootTest(
    classes = {Main.class, GateWebConfigAuthenticatedRequestFilterTest.TestController.class})
@TestPropertySource(properties = {"spring.config.location=classpath:gate-test.yml"})
public class GateWebConfigAuthenticatedRequestFilterTest {

  private static final String API_BASE = "/test";
  private static final String API_PATH = "/asyncApi";

  private static final String LOG_MESSAGE = " logged in async api: ";

  @RestController
  @RequestMapping(value = API_BASE)
  static class TestController {
    private final Logger log = LoggerFactory.getLogger(getClass());

    @RequestMapping(value = API_PATH, method = RequestMethod.GET)
    public StreamingResponseBody asyncApi() {
      return outputStream -> {
        AuthenticatedRequest.get(Header.USER)
            .ifPresent(
                v -> {
                  log.info(Header.USER.name() + LOG_MESSAGE + v);
                });
        AuthenticatedRequest.get(Header.APPLICATION)
            .ifPresent(
                v -> {
                  log.info(Header.APPLICATION.name() + LOG_MESSAGE + v);
                });
        AuthenticatedRequest.get(Header.EXECUTION_TYPE)
            .ifPresent(
                v -> {
                  log.info(Header.EXECUTION_TYPE.name() + LOG_MESSAGE + v);
                });
      };
    }
  }

  @Autowired
  @Qualifier("authenticatedRequestFilter")
  private FilterRegistrationBean filterRegistrationBean;

  @Autowired private WebApplicationContext webApplicationContext;

  private MockMvc mockMvc;

  @Test
  void TestHeaderAvailableInAuthenticatedRequestMDCDuringAsyncApi() throws Exception {
    mockMvc =
        webAppContextSetup(webApplicationContext)
            .addFilters(filterRegistrationBean.getFilter())
            .build();

    MemoryAppender memoryAppender = new MemoryAppender(TestController.class);

    String testUser = "Test User";
    String testApplication = "Test Application";
    String testExecutionType = "Test Execution Type";

    MvcResult result =
        mockMvc
            .perform(
                get(API_BASE + API_PATH)
                    .header(Header.USER.getHeader(), testUser)
                    .header(Header.APPLICATION.getHeader(), testApplication)
                    .header(Header.EXECUTION_TYPE.getHeader(), testExecutionType))
            .andExpect(request().asyncStarted())
            .andDo(print())
            .andReturn();

    mockMvc.perform(asyncDispatch(result)).andDo(print());

    List<String> messages = memoryAppender.layoutSearch(LOG_MESSAGE, Level.INFO);
    String expectedUserLog = Header.USER.name() + LOG_MESSAGE + testUser;
    String expectedApplicationLog = Header.APPLICATION.name() + LOG_MESSAGE + testApplication;
    String expectedExecutionTypeLog =
        Header.EXECUTION_TYPE.name() + LOG_MESSAGE + testExecutionType;

    assertThat(messages.size(), equalTo(3));
    assertThat(
        messages,
        contains(
            containsString(expectedUserLog),
            containsString(expectedApplicationLog),
            containsString(expectedExecutionTypeLog)));
  }
}
