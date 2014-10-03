/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.front50.filters

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.core.annotation.Order


import javax.servlet.*
import javax.servlet.http.HttpServletRequest

@Component
@Order(Integer.MAX_VALUE)
class AccountHostnameFilter implements Filter {
  private static final String FORWARDED = "com.netflix.spinnaker.front50.FORWARDED"

  @Value('${front50.prefix:front50}')
  private String front50Prefix

  @Value('${front50.domain:netflix.net}')
  private String front50Domain

  void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
    HttpServletRequest request = (HttpServletRequest) req
    def host = request.requestURL.toURL().host

    // in the format: front50.<account>.netflix.net
    if (host.startsWith("${front50Prefix}.") && host.endsWith(front50Domain) && !req.getAttribute(FORWARDED)) {
      def hostParts = host.tokenize('.')
      def account = hostParts[1]
      def reqParts = request.requestURI.tokenize('/')
      def reqAccount = reqParts.remove(0)
      if (account != reqAccount && reqParts.size()) {
        req.setAttribute(FORWARDED, true)
        req.getRequestDispatcher("/${reqAccount}/${reqParts.join('/')}").forward(req, res)
        return
      } else if (account != reqAccount && !reqParts.size()) {
        req.setAttribute(FORWARDED, true)
        req.getRequestDispatcher("/${account}/${reqAccount}").forward(req, res)
        return
      } else if (account == reqAccount && !reqParts.size()) {
        req.setAttribute(FORWARDED, true)
        req.getRequestDispatcher("/${account}/${[reqAccount]?.flatten()?.join('/')}${reqParts.join('/')}").forward(req, res)
        return
      } else {
        req.setAttribute(FORWARDED, true)
        req.getRequestDispatcher("/${account}/${reqParts.join('/')}").forward(req, res)
        return
      }
    }
    chain.doFilter(req, res)
  }

  void init(FilterConfig filterConfig) throws ServletException {}

  void destroy() {}
}
