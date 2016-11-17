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

package com.netflix.spinnaker.gate.security.oauth2

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.client.OAuth2RestOperations
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken
import org.springframework.security.oauth2.provider.authentication.BearerTokenExtractor

import javax.servlet.Filter
import javax.servlet.FilterChain
import javax.servlet.FilterConfig
import javax.servlet.ServletException
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletRequest

/**
 * This class supports the use case of an externally provided OAuth access token, for example, a
 * Github-issued personal access token.
 */
@Slf4j
class ExternalAuthTokenFilter implements Filter {

  @Autowired
  @Qualifier("userInfoRestTemplate")
  OAuth2RestOperations restTemplate

  BearerTokenExtractor extractor = new BearerTokenExtractor()

  @Override
  void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
    def httpServletRequest = (HttpServletRequest) request
    Authentication auth = extractor.extract(httpServletRequest)
    if (auth?.principal) {
      restTemplate.OAuth2ClientContext.accessToken = new DefaultOAuth2AccessToken(auth.principal.toString())
    }
    chain.doFilter(request, response)
  }

  @Override
  void init(FilterConfig filterConfig) throws ServletException {}

  @Override
  void destroy() {}
}
