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

package com.netflix.spinnaker.kork.lock;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.kork.exceptions.ConstraintViolationException;
import com.netflix.spinnaker.kork.exceptions.SystemException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Callable;
import javax.annotation.Nonnull;

public interface LockManager {
  <R> AcquireLockResponse<R> acquireLock(
      @Nonnull final LockOptions lockOptions, @Nonnull final Callable<R> onLockAcquiredCallback);

  AcquireLockResponse<Void> acquireLock(
      @Nonnull final LockOptions lockOptions, @Nonnull final Runnable onLockAcquiredCallback);

  <R> AcquireLockResponse<R> acquireLock(
      @Nonnull final String lockName,
      final long maximumLockDurationMillis,
      @Nonnull final Callable<R> onLockAcquiredCallback);

  AcquireLockResponse<Void> acquireLock(
      @Nonnull final String lockName,
      final long maximumLockDurationMillis,
      @Nonnull final Runnable onLockAcquiredCallback);

  boolean releaseLock(@Nonnull final Lock lock, boolean wasWorkSuccessful);

  // VisibleForTesting
  Lock tryCreateLock(final LockOptions lockOptions);

  String NAME_FALLBACK = UUID.randomUUID().toString();

  /** Used only if an ownerName is not provided in the constructor. */
  default String getOwnerName() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      return NAME_FALLBACK;
    }
  }

  default String lockKey(String name) {
    return String.format("{korkLock:%s}", name.toLowerCase());
  }

  class AcquireLockResponse<R> {
    private final Lock lock;
    private final R onLockAcquiredCallbackResult;
    private final LockStatus lockStatus;
    private final Exception exception;
    private boolean released;

    public AcquireLockResponse(
        final Lock lock,
        final R onLockAcquiredCallbackResult,
        final LockStatus lockStatus,
        final Exception exception,
        final boolean released) {
      this.lock = lock;
      this.onLockAcquiredCallbackResult = onLockAcquiredCallbackResult;
      this.lockStatus = lockStatus;
      this.exception = exception;
      this.released = released;
    }

    public Lock getLock() {
      return lock;
    }

    public R getOnLockAcquiredCallbackResult() {
      return onLockAcquiredCallbackResult;
    }

    public LockStatus getLockStatus() {
      return lockStatus;
    }

    public Exception getException() {
      return exception;
    }

    public boolean isReleased() {
      return released;
    }
  }

  enum LockStatus {
    ACQUIRED,
    TAKEN,
    ERROR,
    EXPIRED
  }

  interface LockReleaseStatus {
    String SUCCESS = "SUCCESS";
    String SUCCESS_GONE = "SUCCESS_GONE"; // lock no longer exists
    String FAILED_NOT_OWNER = "FAILED_NOT_OWNER"; // found lock but belongs to someone else
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  class Lock implements Named {
    private final String name;
    private final String ownerName;
    private final long leaseDurationMillis;
    private final long successIntervalMillis;
    private final long failureIntervalMillis;
    private final long version;
    private final long ownerSystemTimestamp;
    private final String attributes; // arbitrary string to store data along side the lock

    @JsonCreator
    public Lock(
        @JsonProperty("name") String name,
        @JsonProperty("ownerName") String ownerName,
        @JsonProperty("version") long version,
        @JsonProperty("leaseDurationMillis") long leaseDurationMillis,
        @JsonProperty("successIntervalMillis") Long successIntervalMillis,
        @JsonProperty("failureIntervalMillis") Long failureIntervalMillis,
        @JsonProperty("ownerSystemTimestamp") long ownerSystemTimestamp,
        @JsonProperty("attributes") String attributes) {
      this.name = name;
      this.ownerName = ownerName;
      this.leaseDurationMillis = leaseDurationMillis;
      this.successIntervalMillis = Optional.ofNullable(successIntervalMillis).orElse(0L);
      this.failureIntervalMillis = Optional.ofNullable(failureIntervalMillis).orElse(0L);
      this.ownerSystemTimestamp = ownerSystemTimestamp;
      this.version = version;
      this.attributes = attributes;
    }

    @Override
    public String getName() {
      return name;
    }

    public String getOwnerName() {
      return ownerName;
    }

    public long getLeaseDurationMillis() {
      return leaseDurationMillis;
    }

    public long getSuccessIntervalMillis() {
      return successIntervalMillis;
    }

    public long getFailureIntervalMillis() {
      return failureIntervalMillis;
    }

    public long getVersion() {
      return version;
    }

    public long nextVersion() {
      return version + 1;
    }

    public long getOwnerSystemTimestamp() {
      return ownerSystemTimestamp;
    }

    public String getAttributes() {
      return attributes;
    }

    @Override
    public String toString() {
      return "Lock{"
          + "name='"
          + name
          + '\''
          + ", ownerName='"
          + ownerName
          + '\''
          + ", leaseDurationMillis="
          + leaseDurationMillis
          + ", successIntervalMillis="
          + successIntervalMillis
          + ", failureIntervalMillis="
          + failureIntervalMillis
          + ", version="
          + version
          + ", ownerSystemTimestamp="
          + ownerSystemTimestamp
          + ", attributes='"
          + attributes
          + '\''
          + '}';
    }

    @Override
    public boolean equals(Object o) {
      // Two locks are identical if the lock name, owner and version match.
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Lock lock = (Lock) o;
      return Objects.equals(name, lock.name)
          && Objects.equals(ownerName, lock.ownerName)
          && Objects.equals(version, lock.version);
    }

    @Override
    public int hashCode() {
      // Two locks are equal if lockName, ownerName and version match.
      return Objects.hash(name, ownerName, version);
    }
  }

  interface Named {
    String getName();
  }

  interface LockMetricsConstants {
    String ACQUIRE = "kork.lock.acquire";
    String RELEASE = "kork.lock.release";
    String HEARTBEATS = "kork.lock.heartbeat";
    String ACQUIRE_DURATION = "kork.lock.acquire.duration";
  }

  class LockOptions {
    private String lockName;
    private Duration maximumLockDuration;
    private Duration successInterval = Duration.ZERO;
    private Duration failureInterval = Duration.ZERO;
    private long version;
    private List<String> attributes =
        new ArrayList<>(); // the list will be joined with a ';' delimiter for brevity
    private boolean reuseVersion;

    public LockOptions withLockName(String name) {
      this.lockName = name;
      return this;
    }

    public LockOptions withMaximumLockDuration(Duration maximumLockDuration) {
      this.maximumLockDuration = maximumLockDuration;
      return this;
    }

    public LockOptions withSuccessInterval(Duration successInterval) {
      this.successInterval = successInterval;
      return this;
    }

    public LockOptions withFailureInterval(Duration failureInterval) {
      this.failureInterval = failureInterval;
      return this;
    }

    public LockOptions withAttributes(List<String> attributes) {
      this.attributes = attributes;
      return this;
    }

    public LockOptions withVersion(Long version) {
      this.version = version;
      this.reuseVersion = true;
      return this;
    }

    public String getLockName() {
      return lockName;
    }

    public Duration getMaximumLockDuration() {
      return maximumLockDuration;
    }

    public Duration getSuccessInterval() {
      return successInterval;
    }

    public Duration getFailureInterval() {
      return failureInterval;
    }

    public List<String> getAttributes() {
      return attributes;
    }

    public Long getVersion() {
      return version;
    }

    public boolean isReuseVersion() {
      return reuseVersion;
    }

    public void validateInputs() {
      if (!this.lockName.matches("^[a-zA-Z0-9.-]+$")) {
        throw new ConstraintViolationException("Lock name must be alphanumeric, may contain dots");
      }

      Objects.requireNonNull(this.lockName, "Lock name must be provided");
      Objects.requireNonNull(this.maximumLockDuration, "Lock max duration must be provided");
    }

    public void setVersion(long version) {
      this.version = version;
    }

    @Override
    public String toString() {
      return "LockOptions{"
          + "lockName='"
          + lockName
          + '\''
          + ", maximumLockDuration="
          + maximumLockDuration
          + ", version="
          + version
          + ", attributes="
          + attributes
          + '}';
    }
  }

  class LockException extends SystemException {
    public LockException(String message) {
      super(message);
    }

    public LockException(String message, Throwable cause) {
      super(message, cause);
    }

    public LockException(Throwable cause) {
      super(cause);
    }
  }

  class LockCallbackException extends LockException {
    public LockCallbackException(String message) {
      super(message);
    }

    public LockCallbackException(String message, Throwable cause) {
      super(message, cause);
    }

    public LockCallbackException(Throwable cause) {
      super(cause);
    }
  }

  class LockNotAcquiredException extends LockException {
    public LockNotAcquiredException(String message) {
      super(message);
    }

    public LockNotAcquiredException(String message, Throwable cause) {
      super(message, cause);
    }

    public LockNotAcquiredException(Throwable cause) {
      super(cause);
    }
  }

  class LockExpiredException extends LockException {
    public LockExpiredException(String message) {
      super(message);
    }

    public LockExpiredException(String message, Throwable cause) {
      super(message, cause);
    }

    public LockExpiredException(Throwable cause) {
      super(cause);
    }
  }
}
