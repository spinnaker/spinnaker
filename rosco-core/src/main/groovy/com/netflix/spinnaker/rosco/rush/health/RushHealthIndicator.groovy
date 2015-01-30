/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.rosco.rush.health

import com.netflix.spinnaker.rosco.rush.api.RushService
import groovy.transform.InheritConstructors
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.ResponseStatus
import retrofit.RetrofitError

import java.util.concurrent.atomic.AtomicReference

@Component
class RushHealthIndicator implements HealthIndicator {

  private static final Logger LOG = LoggerFactory.getLogger(RushHealthIndicator)

  @Autowired
  RushService rushService

  private final AtomicReference<Exception> lastException = new AtomicReference<>(null)

  @Override
  Health health() {
    def ex = lastException.get()

    if (ex) {
      throw ex
    }

    new Health.Builder().up().build()
  }

  @Scheduled(fixedDelay = 300000L)
  void checkHealth() {
    try {
      rushService.listScriptDetails().toBlocking().single()

      lastException.set(null)
    } catch (Exception ex) {
      if (ex instanceof RetrofitError) {
        ex = new RushIOException()
      }

      LOG.warn "Unhealthy", ex

      lastException.set(ex)
    }
  }

  @ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE, reason = "Problem communicating with Rush.")
  @InheritConstructors
  static class RushIOException extends RuntimeException {}
}
