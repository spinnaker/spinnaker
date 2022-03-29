/*
 * Copyright 2022 Salesforce, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
package com.netflix.spinnaker.clouddriver.aws.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.amazonaws.Request;
import java.net.URI;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.slf4j.LoggerFactory;

class LogEndpointRequestHandlerTest {

  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(LogEndpointRequestHandlerTest.class);

  private ListAppender<ILoggingEvent> listAppender;

  @BeforeEach
  void setup() {
    // Capture the log messages that LogEndpointRequestHandler generates
    Logger logger = (Logger) LoggerFactory.getLogger(LogEndpointRequestHandler.class);
    listAppender = new ListAppender<>();
    listAppender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
    logger.addAppender(listAppender);
    listAppender.start();
  }

  // If LogEndpointRequestHandler uses, say a HashSet instead of
  // ConcurrentHashMap.newKeySet(), this test only fails sometimes, even with
  // 1000 repetitions.  Each iteration takes less than 100 msec on my machine,
  // but leaving with 10 iterations to keep from increasing test times
  // unnecessarily.
  @RepeatedTest(10)
  void endpointLoggingIsThreadSafe() throws Exception {
    LogEndpointRequestHandler logEndpointRequestHandler = new LogEndpointRequestHandler();

    // Use the same service name and endpoint in all the threads to exercise
    // both the map-level operations, as well as the set operations.
    Request request = mock(Request.class);
    URI uri = new URI("https://example.com");
    when(request.getServiceName()).thenReturn("fooService");
    when(request.getEndpoint()).thenReturn(uri);

    int numberOfThreads = 100; // arbitrary
    ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
    final ArrayList<Future<Exception>> futures = new ArrayList<>(numberOfThreads);
    for (int i = 0; i < numberOfThreads; i++) {
      futures.add(
          executor.submit(
              () -> {
                try {
                  logEndpointRequestHandler.beforeRequest(request);
                  return null;
                } catch (Exception e) {
                  log.error("exception in logEndpointRequestHandler.beforeRequest", e);
                  // Return the exception as a way to communicate it to the caller
                  // since throwing, by itself, doesn't.
                  return e;
                }
              }));
    }

    for (Future<Exception> future : futures) {
      // Make sure none of the threads returned an exception
      assertNull(future.get(5, TimeUnit.SECONDS)); // arbitrary timeout
    }

    // No matter how many threads there are, since there's one service name and
    // one endpoint, expect one log message.
    assertEquals(1, listAppender.list.size());

    executor.shutdown();
  }
}
