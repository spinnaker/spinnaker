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


package com.netflix.spinnaker.orca.batch.exceptions

import com.google.common.base.Throwables
import groovy.util.logging.Slf4j

@Slf4j
class DefaultExceptionHandler implements ExceptionHandler {
  @Override
  boolean handles(Exception e) {
    return true
  }

  @Override
  ExceptionHandler.Response handle(String taskName, Exception e) {
    def exceptionDetails = ExceptionHandler.responseDetails("Unexpected Task Failure", [e.message])
    exceptionDetails.stackTrace = Throwables.getStackTraceAsString(e)
    log.warn("Error ocurred during task ${taskName}", e)
    return new ExceptionHandler.Response(e.class.simpleName, taskName, exceptionDetails, false)
  }
}
