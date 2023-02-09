/*
 * Copyright 2022 Salesforce, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.kork.web.context;

import static com.netflix.spinnaker.kork.common.Header.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import ch.qos.logback.classic.Level;
import com.netflix.spinnaker.filters.AuthenticatedRequestFilter;
import com.netflix.spinnaker.kork.test.log.MemoryAppender;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

// Give the underlying thread pool one thread to make it easier to verify that
// information from the MDC in one invocation doesn't leak into a subsequent
// one.
@SpringBootTest(
    classes = MdcCopyingAsyncTaskExecutorTest.TestConfigurationAndAsyncEndpoint.class,
    properties = {"spring.task.execution.pool.coreSize=1", "spring.task.execution.pool.maxSize=1"})
@WebAppConfiguration
public class MdcCopyingAsyncTaskExecutorTest {

  private MockMvc mvc;

  @Autowired private WebApplicationContext webApplicationContext;

  /** This takes X-SPINNAKER-* headers from requests and puts them in the MDC. */
  AuthenticatedRequestFilter authenticatedRequestFilter =
      new AuthenticatedRequestFilter(
          true /* extractSpinnakerHeaders */,
          false /* extractSpinnakerUserOriginHeader */,
          false /* forceNewSpinnakerRequestId */,
          true /* clearAuthenticatedRequestPostFilter */);

  @BeforeEach
  public void setup() {
    this.mvc =
        webAppContextSetup(webApplicationContext).addFilters(authenticatedRequestFilter).build();
  }

  @Test
  public void testAsyncEndpoint() throws Exception {
    // Capture the log messages that our test endpoint generates
    MemoryAppender memoryAppender = new MemoryAppender(MdcCopyingAsyncTaskExecutorTest.class);

    // Arbitrary X-SPINNAKER-* that AuthenticatedRequestFilter puts into the
    // MDC.
    String userValue = "some user";

    MvcResult result =
        mvc.perform(get("/dummy/streamingResponseBody").header(USER.getHeader(), userValue))
            .andReturn();

    mvc.perform(asyncDispatch(result))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(content().string(is("test response")));

    List<String> userMessages =
        memoryAppender.search(USER.getHeader() + "=" + userValue, Level.INFO);
    assertThat(userMessages).hasSize(1);

    // Try again with no headers, so we expect an empty MDC.
    MvcResult resultTwo = mvc.perform(get("/dummy/streamingResponseBody")).andReturn();

    mvc.perform(asyncDispatch(resultTwo))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(content().string(is("test response")));

    List<String> emptyMdcMessages = memoryAppender.search("contextMap: null", Level.INFO);
    assertThat(emptyMdcMessages).hasSize(1);
  }

  @SpringBootApplication
  static class TestConfigurationAndAsyncEndpoint implements WebMvcConfigurer {

    private final AsyncTaskExecutor asyncTaskExecutor;

    public TestConfigurationAndAsyncEndpoint(AsyncTaskExecutor asyncTaskExecutor) {
      this.asyncTaskExecutor = asyncTaskExecutor;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
      configurer.setTaskExecutor(new MdcCopyingAsyncTaskExecutor(asyncTaskExecutor));
    }

    @RestController
    @RequestMapping("/dummy")
    static class TestController {

      final org.slf4j.Logger log = LoggerFactory.getLogger(MdcCopyingAsyncTaskExecutorTest.class);

      @RequestMapping(value = "/streamingResponseBody", method = RequestMethod.GET)
      StreamingResponseBody streamingResponseBody() {
        return outputStream -> {
          // Note: It's important for the log message to be inside the lambda to
          // verify that the MDC is set up properly for the AsyncTaskExecutor.
          Map<String, String> contextMap = MDC.getCopyOfContextMap();
          log.info(
              "streamingResponseBody: thread id: {}, contextMap: {}",
              Thread.currentThread().getId(),
              contextMap);
          outputStream.write("test response".getBytes(StandardCharsets.UTF_8));
        };
      }
    }
  }
}
