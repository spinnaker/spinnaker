/*
 * Copyright 2026 DoorDash, Inc.
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

package com.netflix.spinnaker.orca.config;

import static java.lang.String.format;

import com.google.common.base.Throwables;
import com.netflix.spinnaker.orca.exceptions.ExceptionHandler;
import java.util.Collections;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.exceptions.JedisException;

/**
 * Handles transient Redis/Jedis connection exceptions and marks them as retryable. This prevents
 * pipelines from getting permanently stuck when a momentary Redis hiccup occurs during queue
 * operations.
 */
public class JedisExceptionHandler implements ExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(JedisExceptionHandler.class);

  @Override
  public boolean handles(Exception e) {
    Throwable current = e;
    while (current != null) {
      if (current instanceof JedisException) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  @Override
  public ExceptionHandler.Response handle(String taskName, Exception e) {
    Map<String, Object> exceptionDetails =
        ExceptionHandler.responseDetails(
            "Transient Redis Failure", Collections.singletonList(e.getMessage()));
    exceptionDetails.put("stackTrace", Throwables.getStackTraceAsString(e));
    log.warn(format("Transient Redis failure during task %s, will retry", taskName), e);
    return new ExceptionHandler.Response(
        e.getClass().getSimpleName(), taskName, exceptionDetails, true);
  }
}
