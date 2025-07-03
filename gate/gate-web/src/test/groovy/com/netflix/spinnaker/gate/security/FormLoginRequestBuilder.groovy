/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.gate.security

import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.RequestBuilder
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder

import jakarta.servlet.ServletContext
import jakarta.servlet.http.Cookie

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

/**
 * @author Rob Winch
 * @author Travis Tomsu
 *
 * Class is derived from
 * https://github.com/spring-projects/spring-security/blob/master/test/src/main/java/org/springframework/security/test/web/servlet/request/SecurityMockMvcRequestBuilders.java
 */
class FormLoginRequestBuilder implements RequestBuilder {
  private String usernameParam = "username"
  private String passwordParam = "password"
  private String username = "user"
  private String password = "password"
  private String loginProcessingUrl = "/login"
  private Cookie[] cookies = null
  private MediaType acceptMediaType = MediaType.APPLICATION_FORM_URLENCODED
  private MockHttpSession session

  @Override
  MockHttpServletRequest buildRequest(ServletContext servletContext) {
    MockHttpServletRequestBuilder request = post(this.loginProcessingUrl)
        .accept(this.acceptMediaType).param(this.usernameParam, this.username)
        .param(this.passwordParam, this.password)

    if (session != null) {
      request.session(session)
    }

    if (cookies != null && cookies.length > 0) {
      request.cookie(cookies)
    }


    return request.buildRequest(servletContext)
  }

  FormLoginRequestBuilder cookie(Cookie... cookies) {
    this.cookies = cookies
    return this
  }

  /**
   * Specifies the URL to POST to. Default is "/login"
   *
   * @param loginProcessingUrl the URL to POST to. Default is "/login"
   * @return
   */
  FormLoginRequestBuilder loginProcessingUrl(String loginProcessingUrl) {
    this.loginProcessingUrl = loginProcessingUrl
    return this
  }

  /**
   * The HTTP parameter to place the username. Default is "username".
   * @param usernameParameter the HTTP parameter to place the username. Default is
   * "username".
   * @return the {@link FormLoginRequestBuilder} for additional customizations
   */
  FormLoginRequestBuilder userParameter(String usernameParameter) {
    this.usernameParam = usernameParameter
    return this
  }

  /**
   * The HTTP parameter to place the password. Default is "password".
   * @param passwordParameter the HTTP parameter to place the password. Default is
   * "password".
   * @return the {@link FormLoginRequestBuilder} for additional customizations
   */
  FormLoginRequestBuilder passwordParam(String passwordParameter) {
    this.passwordParam = passwordParameter
    return this
  }

  /**
   * The value of the password parameter. Default is "password".
   * @param password the value of the password parameter. Default is "password".
   * @return the {@link FormLoginRequestBuilder} for additional customizations
   */
  FormLoginRequestBuilder password(String password) {
    this.password = password
    return this
  }

  /**
   * The value of the username parameter. Default is "user".
   * @param username the value of the username parameter. Default is "user".
   * @return the {@link FormLoginRequestBuilder} for additional customizations
   */
  FormLoginRequestBuilder user(String username) {
    this.username = username
    return this
  }

  /**
   * Specify both the password parameter name and the password.
   *
   * @param passwordParameter the HTTP parameter to place the password. Default is
   * "password".
   * @param password the value of the password parameter. Default is "password".
   * @return the {@link FormLoginRequestBuilder} for additional customizations
   */
  FormLoginRequestBuilder password(String passwordParameter,
                                          String password) {
    passwordParam(passwordParameter)
    this.password = password
    return this
  }

  /**
   * Specify both the password parameter name and the password.
   *
   * @param usernameParameter the HTTP parameter to place the username. Default is
   * "username".
   * @param username the value of the username parameter. Default is "user".
   * @return the {@link FormLoginRequestBuilder} for additional customizations
   */
  FormLoginRequestBuilder user(String usernameParameter, String username) {
    userParameter(usernameParameter)
    this.username = username
    return this
  }

  /**
   * Specify a media type to to set as the Accept header in the request.
   *
   * @param acceptMediaType the {@link MediaType} to set the Accept header to.
   * Default is: MediaType.APPLICATION_FORM_URLENCODED
   * @return the {@link FormLoginRequestBuilder} for additional customizations
   */
  FormLoginRequestBuilder acceptMediaType(MediaType acceptMediaType) {
    this.acceptMediaType = acceptMediaType
    return this
  }

  FormLoginRequestBuilder session(MockHttpSession session) {
    this.session = session
    return this
  }
}
