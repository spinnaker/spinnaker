/*
 * Copyright 2020 Armory
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

package com.netflix.spinnaker.clouddriver.jobs.local;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.Watchdog;

/**
 * Extension of {@link org.apache.commons.exec.ExecuteWatchdog} that sends SIGKILL signal to the
 * watched process if not finished by SIGTERM.
 */
@Slf4j
public class ForceDestroyWatchdog extends ExecuteWatchdog {

  private static final long GRACE_PERIOD_MS = 250;

  private final long timeout;
  private Process process;

  public ForceDestroyWatchdog(final long timeout) {
    super(timeout);
    this.timeout = timeout;
  }

  @Override
  public synchronized void start(Process processToMonitor) {
    super.start(processToMonitor);
    this.process = processToMonitor;
  }

  @Override
  public synchronized void timeoutOccured(final Watchdog w) {
    super.timeoutOccured(w);

    try {
      Thread.sleep(GRACE_PERIOD_MS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    if (process.isAlive()) {
      log.warn(
          "Timeout: Waited {} ms for process to finish and process is still alive after sending SIGTERM signal. Sending SIGKILL.",
          timeout + GRACE_PERIOD_MS);
      process.destroyForcibly();
    }
  }
}
