/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.front50.interceptors

import com.netflix.spectator.api.ExtendedRegistry
import groovy.util.logging.Slf4j
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerMapping
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import java.util.concurrent.TimeUnit

@Slf4j
class MetricsInterceptor extends HandlerInterceptorAdapter {
  private static final String TIMER_ATTRIBUTE = "Metrics_startTime"

  private final ExtendedRegistry extendedRegistry

  MetricsInterceptor(ExtendedRegistry extendedRegistry) {
    this.extendedRegistry = extendedRegistry
  }

  @Override
  boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
    request.setAttribute(TIMER_ATTRIBUTE, System.currentTimeMillis())
    return true
  }

  @Override
  void afterCompletion(HttpServletRequest request,
                       HttpServletResponse response,
                       Object handler,
                       Exception ex) throws Exception {
    if (handler instanceof HandlerMethod) {
      def handlerMethod = (HandlerMethod) handler

      def id = extendedRegistry.createId('controller.executions')
          .withTag("controller", handlerMethod.method.declaringClass.simpleName)
          .withTag("method", handler.method.name)
          .withTag("status", "${(response.status as String)[0]}xx")
          .withTag("statusCode", response.status as String)

      def variables = (Map) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
      if (variables?.account) {
        id = id.withTag("account", variables?.account as String)
      }
      if (variables?.application) {
        id = id.withTag("application", variables?.application as String)
      }

      extendedRegistry.timer(id).record(
          System.currentTimeMillis() - (request.getAttribute(TIMER_ATTRIBUTE) as Long), TimeUnit.MILLISECONDS
      )

      if (ex || response.status >= 400) {
        id = id.withTag("success", "false")
        if (ex) {
          id = id.withTag("cause", ex.class.simpleName)
        }
      } else {
        id = id.withTag("success", "true")
      }
      extendedRegistry.counter(id).increment()
    }
  }
}
