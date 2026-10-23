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

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class AbstractPollingNotificationAgentTest {

  @Test
  void keepsPollingAfterATickThrowsAnError() {
    NotificationClusterLock clusterLock = mock(NotificationClusterLock.class);
    when(clusterLock.tryAcquireLock(anyString(), anyLong())).thenReturn(true);

    AbstractPollingNotificationAgent agent =
        new AbstractPollingNotificationAgent(clusterLock) {
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
            throw new OutOfMemoryError("Java heap space");
          }
        };

    agent.startPolling();
    try {
      verify(clusterLock, timeout(5000).atLeast(2)).tryAcquireLock(anyString(), anyLong());
    } finally {
      agent.stopPolling();
    }
  }
}
