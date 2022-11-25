/*
 * Copyright 2022 OpsMx Inc.
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

package com.netflix.spinnaker.clouddriver.cloudrun.health;

import groovy.transform.InheritConstructors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ResponseStatus;

@Component
public class CloudrunHealthIndicator implements HealthIndicator {

  private static final Logger LOG = LoggerFactory.getLogger(CloudrunHealthIndicator.class);

  @Override
  public Health health() {
    return new Health.Builder().up().build();
  }

  @Scheduled(fixedDelay = 300000L)
  void checkHealth() {}

  @ResponseStatus(
      value = HttpStatus.SERVICE_UNAVAILABLE,
      reason = "Problem communicating with Cloud run")
  @InheritConstructors
  static class CloudrunIOException extends RuntimeException {}
}
