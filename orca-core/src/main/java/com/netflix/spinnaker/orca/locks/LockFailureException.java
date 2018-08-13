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
