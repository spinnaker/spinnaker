/*
 * Copyright 2025 OpsMx, Inc.
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
package com.netflix.spinnaker.gate.security.oauth2;

import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.UserInfoRestTemplateFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2ClientContext;
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.authentication.BearerTokenExtractor;
import org.springframework.stereotype.Component;

/**
 * This class supports the use case of an externally provided OAuth access token, for example, a
 * Github-issued personal access token.
 */
@Component
public class ExternalAuthTokenFilter implements Filter {

  @Autowired(required = false)
  private UserInfoRestTemplateFactory userInfoRestTemplateFactory;

  private BearerTokenExtractor extractor = new BearerTokenExtractor();

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest httpServletRequest = (HttpServletRequest) request;
    Authentication auth = extractor.extract(httpServletRequest);
    if (auth != null && auth.getPrincipal() != null && !auth.getPrincipal().toString().isEmpty()) {
      DefaultOAuth2AccessToken token = new DefaultOAuth2AccessToken(auth.getPrincipal().toString());
      // Reassign token type to be capitalized "Bearer",
      // see https://github.com/spinnaker/spinnaker/issues/2074
      token.setTokenType(OAuth2AccessToken.BEARER_TYPE);
      if (userInfoRestTemplateFactory != null) {
        OAuth2ClientContext ctx =
            userInfoRestTemplateFactory.getUserInfoRestTemplate().getOAuth2ClientContext();
        ctx.setAccessToken(token);
      }
    }
    chain.doFilter(request, response);
  }

  @Override
  public void init(FilterConfig filterConfig) {}

  @Override
  public void destroy() {}
}
