package com.netflix.spinnaker.igor.polling;

import com.netflix.spinnaker.kork.lock.LockManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Duration;
import java.util.Optional;

public class LockService {
    private static final Logger log = LoggerFactory.getLogger(LockService.class);
    private final LockManager delegate;
    private Duration configuredMaxLockDuration = null;

    public LockService(LockManager lockManager, Duration configuredMaxLockDuration) {
        this.delegate = lockManager;
        if (configuredMaxLockDuration != null) {
            // allows to override the maximum lock duration from config
            this.configuredMaxLockDuration = configuredMaxLockDuration;
            log.info("LockManager will use the configured maximum lock duration {}", configuredMaxLockDuration);
        }
    }

    public void acquire(final String lockName,
                        final Duration maximumLockDuration,
                        final Runnable runnable) {
        LockManager.LockOptions lockOptions = new LockManager.LockOptions()
            .withLockName(lockName)
            .withMaximumLockDuration(
                Optional.ofNullable(configuredMaxLockDuration).orElse(maximumLockDuration)
            );

        delegate.acquireLock(lockOptions, runnable);
    }
}
