/*
 * Copyright 2018 Netflix, Inc.
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

import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

public class RequestContextInterceptor extends HandlerInterceptorAdapter {

  private static Pattern applicationPattern = Pattern.compile("/applications/([^/]+)");
  private static Pattern orchestrationMatch = Pattern.compile("/(?:tasks$|tasks/)");

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws Exception {
    String requestURI = request.getRequestURI();
    Matcher m = applicationPattern.matcher(requestURI);
    if (m.find()) {
      AuthenticatedRequest.setApplication(m.group(1));
    }

    if (orchestrationMatch.matcher(requestURI).matches()) {
      AuthenticatedRequest.setExecutionType("orchestration");
    }

    return true;
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
      throws Exception {}
}
