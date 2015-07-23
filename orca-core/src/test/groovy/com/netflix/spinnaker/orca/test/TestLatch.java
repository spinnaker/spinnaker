package com.netflix.spinnaker.orca.test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * A resettable version of {@link CountDownLatch} that's useful for tests that want to wait for a mock to get invoked.
 * I'm too stupid to figure out how {@link CyclicBarrier} works so I wrote this.
 * <p>
 * It also uses a default timeout of 1 second instead of waiting forever if the latch never gets decremented.
 */
public class TestLatch {

  public static final int DEFAULT_TIMEOUT_SECONDS = 1;

  public TestLatch(int count) {
    this.count = count;
    delegate = new CountDownLatch(count);
  }

  public void countDown() {
    delegate.countDown();
  }

  public void await() throws InterruptedException {
    await(DEFAULT_TIMEOUT_SECONDS, SECONDS);
  }

  public void await(long timeout, TimeUnit unit) throws InterruptedException {
    delegate.await(timeout, unit);
  }

  public synchronized void reset() {
    while (delegate.getCount() > 0) delegate.countDown();
    delegate = new CountDownLatch(count);
  }

  private final int count;
  private CountDownLatch delegate;
}
