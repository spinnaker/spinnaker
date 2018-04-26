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

import com.netflix.spinnaker.gate.security.RequestContext;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RequestContextInterceptor extends HandlerInterceptorAdapter {

  private static Pattern applicationPattern = Pattern.compile("/applications/([^/]+)");
  private static Pattern orchestrationMatch = Pattern.compile("/(?:tasks$|tasks/)");

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
    RequestContext.set(
      new RequestContext(
        AuthenticatedRequest.getSpinnakerUserOrigin().orElse(null),
        AuthenticatedRequest.getSpinnakerUser().orElse(null)
      )
    );

    Matcher m = applicationPattern.matcher(request.getRequestURI());
    if (m.find()) {
      RequestContext.setApplication(m.group(1));
    }

    if (orchestrationMatch.matcher(request.getRequestURI()).matches()) {
      RequestContext.setExecutionType("orchestration");
    }

    return true;
  }

  @Override
  public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
    throws Exception {
    RequestContext.clear();
  }
}
