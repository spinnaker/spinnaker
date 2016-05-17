/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.controllers

import groovy.util.logging.Slf4j
import org.apache.commons.lang.exception.ExceptionUtils
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

import javax.servlet.http.HttpServletRequest

@Slf4j
@ControllerAdvice
class LoggingExceptionHandler {

  @ExceptionHandler(value = Exception.class)
  def defaultErrorHandler(HttpServletRequest request, Exception e) {
    log.error("Error occurred handling request for ${request.requestURL}: ${ExceptionUtils.getFullStackTrace(e)}")
    throw e
  }
}
