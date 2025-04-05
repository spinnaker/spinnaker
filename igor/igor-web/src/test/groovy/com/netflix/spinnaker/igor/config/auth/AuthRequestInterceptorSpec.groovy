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

package com.netflix.spinnaker.igor.config.auth

import com.netflix.spinnaker.igor.config.JenkinsProperties
import okhttp3.Interceptor
import okhttp3.Request
import spock.lang.Specification

class AuthRequestInterceptorSpec extends Specification {

    def "should append each auth header supplier's value"() {
        setup:
        def interceptor = new AuthRequestInterceptor(host)
        def chain = Mock(Interceptor.Chain) { request() >> new Request.Builder().url("http://test.com").build()}
        when:
        interceptor.intercept(chain)
        then:
        1 * chain.proceed({Request req -> req.header("Authorization") == expectedHeader})

        where:
        host                                                                                    || expectedHeader
        new JenkinsProperties.JenkinsHost(username: "user", password: "password")               || "Basic dXNlcjpwYXNzd29yZA=="
        new JenkinsProperties.JenkinsHost(token: "foo")                                         || "Bearer foo"
        new JenkinsProperties.JenkinsHost(username: "user", password: "password", token: "foo") || "Basic dXNlcjpwYXNzd29yZA==, Bearer foo"
    }
}
