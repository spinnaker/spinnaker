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

package com.netflix.spinnaker.config

import com.netflix.spinnaker.kork.web.controllers.GenericErrorController
import com.netflix.spinnaker.kork.web.exceptions.GenericExceptionHandlers
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes
import org.springframework.boot.web.servlet.error.ErrorAttributes
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.context.request.WebRequest

@Configuration
class ErrorConfiguration {
  @Bean
  ErrorAttributes errorAttributes() {
    final DefaultErrorAttributes defaultErrorAttributes = new DefaultErrorAttributes()
    return new ErrorAttributes() {
      @Override
      Map<String, Object> getErrorAttributes(WebRequest webRequest, boolean includeStackTrace) {
        // By default, Spring echoes back the user's requested path. This opens up a potential XSS vulnerability where a
        // user, for example, requests "GET /<script>alert('Hi')</script> HTTP/1.1".
        Map<String, Object> errorAttributes = defaultErrorAttributes.getErrorAttributes(webRequest, includeStackTrace)
        errorAttributes.remove("path")
        return errorAttributes
      }

      @Override
      Throwable getError(WebRequest webRequest) {
        return defaultErrorAttributes.getError(webRequest)
      }
    }
  }

  @Bean
  GenericErrorController genericErrorController(ErrorAttributes errorAttributes) {
    return new GenericErrorController(errorAttributes)
  }

  @Bean
  GenericExceptionHandlers genericExceptionHandlers() {
    return new GenericExceptionHandlers()
  }
}
