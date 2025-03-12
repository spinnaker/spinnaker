/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.gate.ratelimit

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.gate.config.RateLimiterConfiguration
import com.netflix.spinnaker.gate.security.RequestIdentityExtractor
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.User
import spock.lang.Specification
import spock.lang.Unroll

import javax.servlet.FilterChain
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class RateLimitingFilterSpec extends Specification {

  Registry registry = new NoopRegistry()

  RateLimiter rateLimiter = Mock()

  Authentication authentication = Mock()

  RequestIdentityExtractor requestIdentityExtractor = Mock()

  SecurityContext securityContext = Mock() {
    getAuthentication() >> authentication
  }

  def setup() {
    SecurityContextHolder.context = securityContext
  }

  def cleanup() {
    SecurityContextHolder.clearContext()
  }

  def 'should use supplied identity extractor if there is no Authentication present'() {
    given:
    SecurityContext securityContext = Stub() {
      getAuthentication() >> null
    }
    SecurityContextHolder.context = securityContext

    and:
    def config = new RateLimiterConfiguration()

    def subject = new RateLimitingFilter(
      rateLimiter,
      registry,
      new StaticRateLimitPrincipalProvider(config),
      [requestIdentityExtractor]
    )

    and:
    def request = Mock(HttpServletRequest)

    when:
    subject.doFilter(request, Stub(HttpServletResponse), Stub(FilterChain))

    then:
    1 * request.getHeader('X-RateLimit-App') >> null
    1 * requestIdentityExtractor.supports(request) >> true
    1 * requestIdentityExtractor.extractIdentity(request) >> 'foo@example.com'
    1 * rateLimiter.incrementAndGetRate(_) >> { RateLimitPrincipal rlp ->
      assert rlp.name == 'foo@example.com'
      return new Rate().with {
        it.capacity = -1
        it.rateSeconds = -1
        it.remaining = -1
        it.reset = -1
        it.throttled = true
        return it
      }
    }

    cleanup:
    SecurityContextHolder.clearContext()
  }

  @Unroll
  def 'should conditionally enforce rate limiting'() {
    given:
    def config = new RateLimiterConfiguration().with {
      it.learning = learning
      it.enforcing = ['foo@example.com']
      it.ignoring = ['bar@example.com']
      return it
    }
    def subject = new RateLimitingFilter(
      rateLimiter,
      registry,
      new StaticRateLimitPrincipalProvider(config),
      [requestIdentityExtractor]
    )

    and:
    def request = Mock(HttpServletRequest)
    def response = Mock(HttpServletResponse)
    def chain = Mock(FilterChain)

    when:
    subject.doFilter(request, response, chain)

    then:
    noExceptionThrown()
    2 * authentication.getPrincipal() >> User.withUsername(principal).password("").authorities().build();
    1 * rateLimiter.incrementAndGetRate(_) >> {
      new Rate().with {
        it.capacity = -1
        it.rateSeconds = -1
        it.remaining = -1
        it.reset = -1
        it.throttled = true
        return it
      }
    }
    (shouldEnforce ? 0 : 1) * chain.doFilter(_, _)
    (shouldEnforce ? 1 : 0) * response.setStatus(429)
    (shouldEnforce ? 1 : 0) * response.getWriter() >> { return new PrintWriter(System.out) }

    where:
    learning | principal         || shouldEnforce
    true     | 'foo@example.com' || true
    false    | 'foo@example.com' || true
    true     | 'bar@example.com' || false
    false    | 'bar@example.com' || false
    true     | 'baz@example.com' || false
    false    | 'baz@example.com' || true
  }

  def 'should ignore deck requests'() {
    given:
    def subject = new RateLimitingFilter(
      rateLimiter,
      registry,
      new StaticRateLimitPrincipalProvider(new RateLimiterConfiguration()),
      [requestIdentityExtractor]
    )

    and:
    def request = Mock(HttpServletRequest)
    def response = Mock(HttpServletResponse)
    def chain = Mock(FilterChain)

    when:
    subject.doFilter(request, response, chain)

    then:
    noExceptionThrown()
    1 * request.getHeader("X-RateLimit-App") >> { return "deck" }
    1 * chain.doFilter(_, _)
    1 * response.getStatus() >> { return 200 }
    0 * _
  }
}
