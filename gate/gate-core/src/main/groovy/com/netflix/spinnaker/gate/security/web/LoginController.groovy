/*
 * Copyright 2026 Google, Inc.
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

package com.netflix.spinnaker.gate.security.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.core.io.Resource
import org.springframework.http.MediaType
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

import java.nio.charset.StandardCharsets

/**
 * Serves the branded Spinnaker sign-in page for the auth providers that rely on the default Spring
 * Security form login (LDAP via {@code ldap.enabled} and basic auth via
 * {@code security.basicform.enabled}). The form posts the {@code username}/{@code password}
 * parameters to the default login processing URL ({@code POST /login}) which is handled by Spring
 * Security.
 *
 * <p>The template lives in {@code login/} on the classpath: {@code page.html} contains the page
 * markup (CSS is inlined via the {@code {{css}}} placeholder and the dynamic error/success banner
 * via {@code {{banner}}}). By loading the page from resources, the markup can be edited without
 * touching Java/Groovy code.
 *
 * <p>The page is written straight to the response instead of going through {@code @ResponseBody}
 * so it is served regardless of the {@code Accept} header (gate configures
 * {@code application/json} as the default content type, which would otherwise yield a 406 for
 * requests that do not send {@code text/html}).
 */
@Controller
@ConditionalOnExpression('${ldap.enabled:false} || ${security.basicform.enabled:false}')
class LoginController {

  @Value('classpath:login/page.html')
  Resource pageResource

  @Value('classpath:login/styles.css')
  Resource stylesResource

  @Value('classpath:login/banner-error.html')
  Resource errorBannerResource

  @Value('classpath:login/banner-success.html')
  Resource successBannerResource

  private static final String CSS_PLACEHOLDER = '{{css}}'
  private static final String BANNER_PLACEHOLDER = '{{banner}}'

  @GetMapping('/login')
  void login(HttpServletRequest request, HttpServletResponse response) throws IOException {

    // Spring Security redirects failed logins (and logouts) to /login?error (and /login?logout)
    // with an empty (null-valued) parameter, so presence must be checked via the parameter map
    // rather than a bound value.
    def showError = request.getParameterMap().containsKey('error')
    def showLogout = request.getParameterMap().containsKey('logout')

    def banner = showLogout ? successBannerResource.getContentAsString(StandardCharsets.UTF_8)
      : showError ? errorBannerResource.getContentAsString(StandardCharsets.UTF_8)
      : ''

    def page = pageResource.getContentAsString(StandardCharsets.UTF_8)
      .replace(CSS_PLACEHOLDER, stylesResource.getContentAsString(StandardCharsets.UTF_8))
      .replace(BANNER_PLACEHOLDER, banner)

    response.setStatus(HttpServletResponse.SC_OK)
    response.setContentType(MediaType.TEXT_HTML_VALUE)
    response.setCharacterEncoding('UTF-8')
    response.writer.write(page)
  }
}
