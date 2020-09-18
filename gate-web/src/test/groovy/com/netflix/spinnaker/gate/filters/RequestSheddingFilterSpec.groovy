/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.gate.filters

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.gate.filters.RequestSheddingFilter
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import javax.servlet.FilterChain
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import static com.netflix.spinnaker.gate.filters.RequestSheddingFilter.*

class RequestSheddingFilterSpec extends Specification {

  DynamicConfigService configService = Mock()

  @Subject
  RequestSheddingFilter subject = new RequestSheddingFilter(configService, new NoopRegistry(), null)

  HttpServletRequest request = Mock()
  HttpServletResponse response = Mock()
  FilterChain chain = Mock()

  PrintWriter printWriter = Mock()

  def "should do nothing when disabled"() {
    when:
    subject.doFilter(request, response, chain)

    then:
    noExceptionThrown()
    1 * configService.isEnabled(ENABLED_KEY, false) >> false
    1 * chain.doFilter(request, response)
    0 * _
  }

  def "should do nothing if request is not low priority"() {
    when:
    subject.doFilter(request, response, chain)

    then:
    noExceptionThrown()
    1 * configService.isEnabled(ENABLED_KEY, false) >> true
    1 * request.getHeader(PRIORITY_HEADER) >> "normal"
    1 * request.getRequestURI() >> "/foo"
    1 * request.getMethod() >> "GET"
    1 * chain.doFilter(request, response)
    0 * _
  }

  @Unroll
  def "should allow requests not matching paths"() {
    when:
    subject.compilePatterns()
    subject.doFilter(request, response, chain)

    then:
    noExceptionThrown()
    1 * configService.isEnabled(ENABLED_KEY, false) >> true
    1 * request.getHeader(PRIORITY_HEADER) >> "low"
    1 * request.getRequestURI() >> requestPath
    1 * request.getMethod() >> "GET"
    1 * configService.getConfig(String, PATHS_KEY, "") >> pathMatchers
    1 * chain.doFilter(request, response)
    0 * _

    where:
    requestPath | pathMatchers
    "/foo"      | null
    "/foo"      | "/bar"
    "/foo/bar"  | "^/bar"
    "/foo/bar"  | "/bar"
    "/foo"      | "/bar,/baz"
  }

  @Unroll
  def "should drop requests matching #pathMatchers"() {
    when:
    subject.compilePatterns()
    subject.doFilter(request, response, chain)

    then:
    1 * configService.isEnabled(ENABLED_KEY, false) >> true
    1 * request.getHeader(PRIORITY_HEADER) >> "low"
    2 * request.getRequestURI() >> requestPath
    1 * request.getMethod() >> "GET"
    1 * configService.getConfig(String, PATHS_KEY, "") >> pathMatchers
    1 * configService.getConfig(Integer, CHANCE_KEY, 100) >> 100
    1 * response.setDateHeader("Retry-After", _)
    1 * response.setStatus(503)
    1 * response.getWriter() >> printWriter
    1 * printWriter.write(_)
    0 * _

    where:
    requestPath | pathMatchers
    "/foo"      | ".*"
    "/foo"      | "/f.*,/bar"
  }
}
