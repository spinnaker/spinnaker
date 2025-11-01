/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License')
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.igor.filters

import groovy.transform.CompileStatic
import org.springframework.stereotype.Component

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.FilterConfig
import jakarta.servlet.ServletException
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletResponse

/**
 * A filter to enable CORS access
 */
@Component
@CompileStatic
class IgorCorsFilter implements Filter {

    void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletResponse response = (HttpServletResponse) res
        response.setHeader('Access-Control-Allow-Origin', '*')
        response.setHeader('Access-Control-Allow-Methods', 'POST, GET, OPTIONS, DELETE')
        response.setHeader('Access-Control-Max-Age', '3600')
        response.setHeader('Access-Control-Allow-Headers', 'x-requested-with,Content-Type')
        chain.doFilter(req, res)
    }

    @SuppressWarnings(['EmptyMethod', 'UnusedMethodParameter'])
    void init(FilterConfig filterConfig) {

    }

    @SuppressWarnings('EmptyMethod')
    void destroy() {

    }

}
