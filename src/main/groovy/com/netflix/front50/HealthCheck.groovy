/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.front50

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.ResponseStatus

/**
 * Created by aglover on 5/9/14.
 */
@Component
public class HealthCheck implements HealthIndicator<String> {

  @Autowired
  SimpleDBDAO dao;

  @Override
  public String health() {
    try {
      if (!this.dao.isHealthly()) {
        throw new NotHealthlyException();
      }
      return "Ok";
    } catch (Throwable thr) {
      throw new NotHealthlyException();
    }
  }

  @ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE, reason = "Not Healthy!")
  public class NotHealthlyException extends RuntimeException {
    public NotHealthlyException() {
      super();
    }
  }
}
