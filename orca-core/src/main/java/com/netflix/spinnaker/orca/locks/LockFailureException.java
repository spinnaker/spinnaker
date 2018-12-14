/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.orca.locks;

import javax.annotation.Nullable;
import java.util.Optional;

public class LockFailureException extends RuntimeException {
  private final String lockName;
  private final LockManager.LockValue currentLockValue;

  private static String buildMessage(String lockName, @Nullable LockManager.LockValue currentLockValue) {
    Optional<LockManager.LockValue> lv = Optional.ofNullable(currentLockValue);
    return "Failed to acquire lock " + lockName
      + " currently held by " + lv.map(LockManager.LockValue::getApplication).orElse("UNKNOWN")
      + "/" + lv.map(LockManager.LockValue::getType).orElse("UNKNOWN")
      + "/" + lv.map(LockManager.LockValue::getId).orElse("UNKNOWN");
  }

  public LockFailureException(String lockName, @Nullable LockManager.LockValue currentLockValue) {
    super(buildMessage(lockName, currentLockValue));
    this.lockName = lockName;
    this.currentLockValue = currentLockValue;
  }

  public String getLockName() {
    return lockName;
  }

  @Nullable
  public LockManager.LockValue getCurrentLockValue() {
    return currentLockValue;
  }
}
