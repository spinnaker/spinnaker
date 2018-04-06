/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.client

import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import groovy.transform.PackageScope
import lombok.SneakyThrows

import java.util.concurrent.TimeUnit

/**
 * This class abstracts the algorithm to continually poll until a status is obtained or a timeout occurs.  Openstack
 * API requires that the load balancer be in an ACTIVE state for it to create associated relationships (i.e. listeners, pools,
 * monitors).  Each modification will cause the load balancer to go into a PENDING state and back to ACTIVE once the change
 * has been made.  Depending on your implementation, the timeout and polling intervals would need to be tweaked.
 */
class BlockingStatusChecker {
  final long timeout
  final long pollInterval
  final StatusChecker statusChecker

  private BlockingStatusChecker(StatusChecker statusChecker, long timeout, long pollInterval) {
    this.statusChecker = statusChecker
    this.timeout = timeout
    this.pollInterval = pollInterval
  }

  /**
   * Creation method.
   * @param pollTimeout - defined in seconds
   * @param pollInterval - defined in seconds
   * @param s
   * @return
   */
  static BlockingStatusChecker from(long pollTimeout, long pollInterval, StatusChecker s) {
    new BlockingStatusChecker(s, TimeUnit.SECONDS.toMillis(pollTimeout), TimeUnit.SECONDS.toMillis(pollInterval))
  }

  @PackageScope
  @SneakyThrows // used for the Thread.sleep(pollInterval)
  <T> T execute(Closure<T> closure) {
    long startTime = System.currentTimeMillis()
    T result

    while(true) {
      result = closure.call()
      if (statusChecker.isReady(result)) {
        return result
      }
      if ((System.currentTimeMillis() - startTime) > timeout) {
        throw new OpenstackProviderException('Operation timed out')
      }
      sleep(pollInterval)
    }
  }

  @PackageScope
  void execute() {
    execute ( { null } )
  }

  static interface StatusChecker<T> {
    boolean isReady(T input)
  }
}
