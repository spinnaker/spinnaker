/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.gate.interceptors;

import static com.netflix.spinnaker.kork.common.Header.REQUEST_ID;

import com.netflix.spinnaker.security.AuthenticatedRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

/**
 * Return value of SPINNAKER_REQUEST_ID (set via
 * com.netflix.spinnaker.filters.AuthenticatedRequestFilter) to gate callers as a response header.
 */
public class RequestIdInterceptor extends HandlerInterceptorAdapter {
  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    AuthenticatedRequest.getSpinnakerRequestId()
        .ifPresent(requestId -> response.setHeader(REQUEST_ID.getHeader(), requestId));
    return true;
  }
}
