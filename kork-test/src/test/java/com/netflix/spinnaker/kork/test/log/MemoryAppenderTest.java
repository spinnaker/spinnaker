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
package com.netflix.spinnaker.kork.test.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import ch.qos.logback.classic.Level;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MemoryAppenderTest {
  private final Logger log = LoggerFactory.getLogger(MemoryAppenderTest.class);

  private MemoryAppender memoryAppender = new MemoryAppender(MemoryAppenderTest.class);

  @Test
  public void verifyCount() {
    log.info("hello world");
    assertEquals(1, memoryAppender.countEventsForLevel(Level.INFO));
    assertEquals(0, memoryAppender.countEventsForLevel(Level.ERROR));
  }

  @Test
  public void verifySearch() {
    log.info("hello world");
    assertThat(memoryAppender.search("not there", Level.INFO)).isEmpty();
    assertThat(memoryAppender.search("hello", Level.INFO)).hasSize(1);

    // Demonstrate that log level filtering works
    assertThat(memoryAppender.search("hello", Level.ERROR)).isEmpty();
  }

  @Test
  public void verifyLayoutSearch() {
    log.info("hello world");
    assertThat(memoryAppender.layoutSearch("not there", Level.INFO)).isEmpty();
    assertThat(memoryAppender.layoutSearch("hello", Level.INFO)).hasSize(1);

    // Test something that depends on the layout.  Assume the layout includes
    // the class name that logged it.
    assertThat(memoryAppender.layoutSearch(MemoryAppenderTest.class.getName(), Level.INFO))
        .hasSize(1);

    // To demonstrate the difference, the search method uses messages before
    // layout, so it doesn't find the class name.
    assertThat(memoryAppender.search(MemoryAppenderTest.class.getName(), Level.INFO)).isEmpty();

    // Demonstrate that log level filtering works
    assertThat(memoryAppender.layoutSearch("hello", Level.ERROR)).isEmpty();
  }
}
