/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.gate.config;

import com.netflix.spinnaker.gate.services.PermissionService;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PermissionRevokingLogoutSuccessHandler
    implements LogoutSuccessHandler, InitializingBean {
  public static final String LOGGED_OUT_URL = "/auth/loggedOut";

  private final PermissionService permissionService;
  private final SimpleUrlLogoutSuccessHandler delegate = new SimpleUrlLogoutSuccessHandler();

  @Override
  public void afterPropertiesSet() throws Exception {
    delegate.setDefaultTargetUrl(LOGGED_OUT_URL);
  }

  @Override
  public void onLogoutSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException, ServletException {
    if (authentication != null) {
      var principal = authentication.getPrincipal();
      if (principal instanceof UserDetails) {
        var username = ((UserDetails) principal).getUsername();
        permissionService.logout(username);
      }
    }
    delegate.onLogoutSuccess(request, response, authentication);
  }
}
