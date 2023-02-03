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

package com.netflix.spinnaker.gate.interceptors;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.netflix.spinnaker.filters.AuthenticatedRequestFilter;
import com.netflix.spinnaker.gate.Main;
import com.netflix.spinnaker.kork.common.Header;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.util.EnumSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

public class ResponseHeaderInterceptorTest {

  private static final String API_BASE = "/responseHeader";
  private static final String API_PATH = "/api";
  private static final String TEST_REQUEST_ID = "Test-Request-ID";
  private static final String TEST_USER = "Test-User";
  private static final String TEST_EXECUTION_ID = "Test-Execution-ID";
  private static final String TEST_EXECUTION_TYPE = "Test-Execution-Type";
  private static final String TEST_APPLICATION = "Test-Application";

  @RestController
  @RequestMapping(value = API_BASE)
  static class TestController {
    @RequestMapping(value = API_PATH, method = RequestMethod.GET)
    public void api() {}
  }

  private MockMvc mockMvc;

  private AuthenticatedRequestFilter authenticatedRequestFilter;

  @BeforeEach
  private void setup() {
    AuthenticatedRequest.clear();
    authenticatedRequestFilter = new AuthenticatedRequestFilter(true);
  }

  @Nested
  @SpringBootTest(classes = {Main.class, ResponseHeaderInterceptorTest.TestController.class})
  @TestPropertySource(
      properties = {
        "spring.config.location=classpath:gate-test.yml",
        "interceptors.responseHeader.fields=X-SPINNAKER-REQUEST-ID, X-SPINNAKER-USER, X-SPINNAKER-EXECUTION-ID, X-SPINNAKER-EXECUTION-TYPE, X-SPINNAKER-APPLICATION"
      })
  @DisplayName("All fields defined in response header property")
  class AllFieldsDefinedInPropertyTest {
    @Autowired private WebApplicationContext webApplicationContext;

    @BeforeEach
    private void setup() {
      mockMvc =
          webAppContextSetup(webApplicationContext).addFilters(authenticatedRequestFilter).build();
    }

    @Test
    public void testRequestIdExistsInAuthenticatedRequest() throws Exception {
      AuthenticatedRequest.setRequestId(TEST_REQUEST_ID);

      mockMvc
          .perform(get(API_BASE + API_PATH))
          .andDo(print())
          .andExpect(status().isOk())
          .andExpect(header().exists(Header.REQUEST_ID.getHeader()))
          .andExpect(header().string(Header.REQUEST_ID.getHeader(), equalTo(TEST_REQUEST_ID)))
          .andExpect(header().doesNotExist((Header.USER.getHeader())))
          .andExpect(header().doesNotExist((Header.EXECUTION_ID.getHeader())))
          .andExpect(header().doesNotExist((Header.EXECUTION_TYPE.getHeader())))
          .andExpect(header().doesNotExist((Header.APPLICATION.getHeader())));
    }

    @Test
    public void testAllHeaderFieldsInAuthenticatedRequest() throws Exception {
      AuthenticatedRequest.setRequestId(TEST_REQUEST_ID);
      AuthenticatedRequest.setUser(TEST_USER);
      AuthenticatedRequest.setExecutionId(TEST_EXECUTION_ID);
      AuthenticatedRequest.setExecutionType(TEST_EXECUTION_TYPE);
      AuthenticatedRequest.setApplication(TEST_APPLICATION);

      mockMvc
          .perform(get(API_BASE + API_PATH))
          .andDo(print())
          .andExpect(status().isOk())
          .andExpect(header().exists(Header.REQUEST_ID.getHeader()))
          .andExpect(header().string(Header.REQUEST_ID.getHeader(), equalTo(TEST_REQUEST_ID)))
          .andExpect(header().exists(Header.USER.getHeader()))
          .andExpect(header().string(Header.USER.getHeader(), equalTo(TEST_USER)))
          .andExpect(header().exists(Header.EXECUTION_ID.getHeader()))
          .andExpect(header().string(Header.EXECUTION_ID.getHeader(), equalTo(TEST_EXECUTION_ID)))
          .andExpect(header().exists(Header.EXECUTION_TYPE.getHeader()))
          .andExpect(
              header().string(Header.EXECUTION_TYPE.getHeader(), equalTo(TEST_EXECUTION_TYPE)))
          .andExpect(header().exists(Header.APPLICATION.getHeader()))
          .andExpect(header().string(Header.APPLICATION.getHeader(), equalTo(TEST_APPLICATION)));
    }

    @Test
    public void testNoHeaderFieldsInAuthenticatedRequest() throws Exception {
      // AuthenticatedRequest generates an uuid as request id if none exists
      // so there will always be a request id in the response header if the
      // interceptor is enabled
      mockMvc
          .perform(get(API_BASE + API_PATH))
          .andDo(print())
          .andExpect(status().isOk())
          .andExpect(header().exists(Header.REQUEST_ID.getHeader()))
          .andExpect(header().string(Header.REQUEST_ID.getHeader(), notNullValue()));
    }
  }

  @Nested
  @SpringBootTest(classes = {Main.class, ResponseHeaderInterceptorTest.TestController.class})
  @TestPropertySource(
      properties = {
        "spring.config.location=classpath:gate-test.yml",
        "interceptors.responseHeader.fields=X-SPINNAKER-USER, X-SPINNAKER-EXECUTION-ID, X-SPINNAKER-APPLICATION"
      })
  @DisplayName("Partial list of fields defined in response header property")
  class PartialFieldsDefinedInPropertyTest {
    @Autowired private WebApplicationContext webApplicationContext;

    @BeforeEach
    private void setup() {
      mockMvc =
          webAppContextSetup(webApplicationContext).addFilters(authenticatedRequestFilter).build();
    }

    @Test
    public void testPartialFieldsConfiguredAllHeaderFieldsInAuthenticatedRequest()
        throws Exception {
      AuthenticatedRequest.setRequestId(TEST_REQUEST_ID);
      AuthenticatedRequest.setUser(TEST_USER);
      AuthenticatedRequest.setExecutionId(TEST_EXECUTION_ID);
      AuthenticatedRequest.setExecutionType(TEST_EXECUTION_TYPE);
      AuthenticatedRequest.setApplication(TEST_APPLICATION);

      mockMvc
          .perform(get(API_BASE + API_PATH))
          .andDo(print())
          .andExpect(status().isOk())
          .andExpect(header().doesNotExist(Header.REQUEST_ID.getHeader()))
          .andExpect(header().exists(Header.USER.getHeader()))
          .andExpect(header().string(Header.USER.getHeader(), equalTo(TEST_USER)))
          .andExpect(header().exists(Header.EXECUTION_ID.getHeader()))
          .andExpect(header().string(Header.EXECUTION_ID.getHeader(), equalTo(TEST_EXECUTION_ID)))
          .andExpect(header().doesNotExist(Header.EXECUTION_TYPE.getHeader()))
          .andExpect(header().exists(Header.APPLICATION.getHeader()))
          .andExpect(header().string(Header.APPLICATION.getHeader(), equalTo(TEST_APPLICATION)));
    }
  }

  @Nested
  @SpringBootTest(classes = {Main.class, ResponseHeaderInterceptorTest.TestController.class})
  @TestPropertySource(
      properties = {
        "spring.config.location=classpath:gate-test.yml",
        "interceptors.responseHeader.fields="
      })
  @DisplayName("Empty fields defined in response header property")
  class EmptyFieldsDefinedInPropertyTest {
    @Autowired private WebApplicationContext webApplicationContext;

    @BeforeEach
    private void setup() {
      mockMvc =
          webAppContextSetup(webApplicationContext).addFilters(authenticatedRequestFilter).build();
    }

    @Test
    public void testNoHeaderFieldsInAuthenticatedRequest() throws Exception {
      // test scenario where fields property is configured to empty list so no
      // fields should be added to response header, overriding default behavior
      // where request id is added
      EnumSet<Header> headers =
          EnumSet.of(
              Header.REQUEST_ID,
              Header.USER,
              Header.EXECUTION_ID,
              Header.EXECUTION_TYPE,
              Header.APPLICATION);

      ResultActions actions =
          mockMvc.perform(get(API_BASE + API_PATH)).andDo(print()).andExpect(status().isOk());

      for (Header header : headers) {
        actions.andExpect(header().doesNotExist(header.getHeader()));
      }
    }
  }
}
