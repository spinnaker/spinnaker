/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.orca.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class AbstractPollingNotificationAgentTest {

  @Test
  void keepsPollingAfterATickThrowsAnException() {
    NotificationClusterLock clusterLock = mock(NotificationClusterLock.class);
    when(clusterLock.tryAcquireLock(anyString(), anyLong())).thenReturn(true);

    AbstractPollingNotificationAgent agent =
        agentWithTick(
            clusterLock,
            () -> {
              throw new IllegalStateException("tick failed");
            });

    agent.startPolling();
    try {
      verify(clusterLock, timeout(5000).atLeast(2)).tryAcquireLock(anyString(), anyLong());
    } finally {
      agent.stopPolling();
    }
  }

  @Test
  void logsAnErrorThatStopsPolling() throws InterruptedException {
    Logger logger = (Logger) LoggerFactory.getLogger(AbstractPollingNotificationAgent.class);
    Level previousLevel = logger.getLevel();
    logger.setLevel(Level.ERROR);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    NotificationClusterLock clusterLock = mock(NotificationClusterLock.class);
    when(clusterLock.tryAcquireLock(anyString(), anyLong())).thenReturn(true);

    AbstractPollingNotificationAgent agent =
        agentWithTick(
            clusterLock,
            () -> {
              throw new OutOfMemoryError("Java heap space");
            });

    agent.startPolling();
    try {
      long deadline = System.currentTimeMillis() + 5000;
      while (errorLogs(appender).isEmpty() && System.currentTimeMillis() < deadline) {
        Thread.sleep(10);
      }
      assertThat(errorLogs(appender))
          .singleElement()
          .satisfies(
              event -> {
                assertThat(event.getFormattedMessage())
                    .isEqualTo("Fatal error running agent tick, the agent will stop polling");
                assertThat(event.getThrowableProxy().getClassName())
                    .isEqualTo(OutOfMemoryError.class.getName());
              });
    } finally {
      agent.stopPolling();
      logger.detachAppender(appender);
      logger.setLevel(previousLevel);
    }
  }

  private static List<ILoggingEvent> errorLogs(ListAppender<ILoggingEvent> appender) {
    return List.copyOf(appender.list).stream()
        .filter(event -> event.getLevel() == Level.ERROR)
        .toList();
  }

  private static AbstractPollingNotificationAgent agentWithTick(
      NotificationClusterLock clusterLock, Runnable tick) {
    return new AbstractPollingNotificationAgent(clusterLock) {
      @Override
      protected long getPollingInterval() {
        return 1;
      }

      @Override
      protected String getNotificationType() {
        return "test";
      }

      @Override
      protected void tick() {
        tick.run();
      }
    };
  }
}
