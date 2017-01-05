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

package com.netflix.spinnaker.gate.filters

import com.netflix.spinnaker.gate.config.Headers

import javax.servlet.Filter
import javax.servlet.FilterChain
import javax.servlet.FilterConfig
import javax.servlet.ServletException
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class CorsFilter implements Filter {

  private final OriginValidator originValidator

  CorsFilter(OriginValidator originValidator) {
    this.originValidator = originValidator
  }

  @Override
  void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
      throws IOException, ServletException {
    HttpServletResponse response = (HttpServletResponse) res
    HttpServletRequest request = (HttpServletRequest) req
    String origin = request.getHeader("Origin")
    if (!originValidator.isValidOrigin(origin)) {
      origin = '*'
    }
    response.setHeader("Access-Control-Allow-Credentials", "true")
    response.setHeader("Access-Control-Allow-Origin", origin)
    response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS, DELETE, PUT, PATCH")
    response.setHeader("Access-Control-Max-Age", "3600")
    response.setHeader("Access-Control-Allow-Headers", "x-requested-with, content-type, authorization")
    response.setHeader("Access-Control-Expose-Headers", [Headers.AUTHENTICATION_REDIRECT_HEADER_NAME].join(", "))
    chain.doFilter(req, res)
  }

  @Override
  void init(FilterConfig filterConfig) {}

  @Override
  void destroy() {}

}
