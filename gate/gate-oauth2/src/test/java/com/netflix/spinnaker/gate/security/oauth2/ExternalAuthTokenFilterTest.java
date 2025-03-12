/*
 * Copyright 2025 OpsMx, Inc.
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

package com.netflix.spinnaker.gate.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import javax.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.autoconfigure.security.oauth2.resource.UserInfoRestTemplateFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.OAuth2ClientContext;
import org.springframework.security.oauth2.client.OAuth2RestTemplate;
import org.springframework.security.oauth2.common.OAuth2AccessToken;

public class ExternalAuthTokenFilterTest {

  @Mock private UserInfoRestTemplateFactory restTemplateFactory;

  @Mock private OAuth2RestTemplate restTemplate;

  @Mock private OAuth2ClientContext oauth2ClientContext;

  @InjectMocks private ExternalAuthTokenFilter filter;

  private MockHttpServletRequest request;
  private MockHttpServletResponse response;
  private FilterChain chain;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
    chain = mock(FilterChain.class);
  }

  @Test
  void shouldEnsureBearerTokenIsForwardedProperly()
      throws javax.servlet.ServletException, IOException {
    // Arrange
    request.addHeader("Authorization", "bearer foo");

    OAuth2AccessToken token = mock(OAuth2AccessToken.class);
    when(token.getTokenType()).thenReturn("Bearer");
    when(token.getValue()).thenReturn("foo");

    when(restTemplateFactory.getUserInfoRestTemplate()).thenReturn(restTemplate);
    when(restTemplate.getOAuth2ClientContext()).thenReturn(oauth2ClientContext);
    when(oauth2ClientContext.getAccessToken()).thenReturn(token);

    // Act
    filter.doFilter(request, response, chain);

    // Assert
    verify(chain).doFilter(request, response);
    assertThat(token.getTokenType()).isEqualTo("Bearer");
    assertThat(token.getValue()).isEqualTo("foo");
  }
}
