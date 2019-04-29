/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.igor.polling;

import com.netflix.spinnaker.kork.lock.LockManager;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LockService {
  private static final Logger log = LoggerFactory.getLogger(LockService.class);
  private final LockManager delegate;
  private Duration configuredMaxLockDuration = null;

  public LockService(LockManager lockManager, Duration configuredMaxLockDuration) {
    this.delegate = lockManager;
    if (configuredMaxLockDuration != null) {
      // allows to override the maximum lock duration from config
      this.configuredMaxLockDuration = configuredMaxLockDuration;
      log.info(
          "LockManager will use the configured maximum lock duration {}",
          configuredMaxLockDuration);
    }
  }

  public void acquire(
      final String lockName, final Duration maximumLockDuration, final Runnable runnable) {
    LockManager.LockOptions lockOptions =
        new LockManager.LockOptions()
            .withLockName(lockName)
            .withMaximumLockDuration(
                Optional.ofNullable(configuredMaxLockDuration).orElse(maximumLockDuration));

    delegate.acquireLock(lockOptions, runnable);
  }
}
