/*
 * Copyright 2023 Salesforce, Inc.
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

package com.netflix.spinnaker.gate.services;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import com.netflix.spinnaker.kork.test.log.MemoryAppender;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

class MdcWrappedCallableTest {
  private static final Logger log = LoggerFactory.getLogger(MdcWrappedCallableTest.class);

  @Test
  void verifyMdcWrappedCallableIncludesTheMdc() throws Exception {
    // Capture the log messages that our test callable generates
    MemoryAppender memoryAppender = new MemoryAppender(MdcWrappedCallableTest.class);

    // Provide a way to execute callables in some other thread
    ExecutorService executorService = Executors.newCachedThreadPool();

    // Put something in the MDC here, to see if it makes it into the thread that
    // executes the operation.
    String mdcKey = "myKey";
    String mdcValue = "myValue";
    MDC.put(mdcKey, mdcValue);

    // The contents of the MDC at construction time of the MdcWrappedCallable
    // are what's available when it executes, so construct it after the MDC is set.
    Callable testCallable = new TestCallable();

    // Execute the callable in another thread
    executorService.submit(testCallable).get();

    // Verify that messages logged in the MdcWrappedCallable include the info from the MDC
    List<String> logMessages = memoryAppender.search(mdcKey + "=" + mdcValue, Level.INFO);
    assertThat(logMessages).hasSize(1);

    // And now clear the MDC and make sure the resulting operation gets the empty MDC.
    MDC.clear();
    Callable emptyMdcCallable = new TestCallable();
    executorService.submit(emptyMdcCallable).get();

    List<String> emptyMdcMessages = memoryAppender.search("contextMap: null", Level.INFO);
    assertThat(emptyMdcMessages).hasSize(1);
  }

  static class TestCallable extends MdcWrappedCallable<Void> {
    @Override
    public Void callWithMdc() {
      Map<String, String> contextMap = MDC.getCopyOfContextMap();
      log.info("contextMap: {}", contextMap);
      return null;
    }
  }
}
