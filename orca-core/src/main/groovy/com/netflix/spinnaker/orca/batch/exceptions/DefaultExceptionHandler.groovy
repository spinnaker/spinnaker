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

class DefaultExceptionHandler implements ExceptionHandler<RuntimeException> {
  @Override
  boolean handles(RuntimeException e) {
    return true
  }

  @Override
  ExceptionHandler.Response handle(String taskName, RuntimeException e) {
    def exceptionDetails = new ExceptionHandler.ResponseDetails("Unexpected Task Failure", [e.message])
    exceptionDetails.stackTrace = Throwables.getStackTraceAsString(e)
    return new ExceptionHandler.Response(
      exceptionType: e.class.simpleName, operation: taskName, details: exceptionDetails
    )
  }
}
