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

package com.netflix.spinnaker.gate.security.ldap

import org.springframework.ldap.AuthenticationException
import org.springframework.security.ldap.DefaultSpringSecurityContextSource
import spock.lang.Specification

class ApacheDSServerSpec extends Specification {

  def setup() {
    ApacheDSServer.startServer("classpath:ldap-server.ldif")
  }

  def cleanup() {
    ApacheDSServer.stopServer()
  }

  def "should connect to LDAP server"() {
    setup:
    int port = ApacheDSServer.getServerPort()
    def contextSource = new DefaultSpringSecurityContextSource("ldap://127.0.0.1:${port}/dc=unit,dc=test")
    contextSource.afterPropertiesSet()

    expect:
    contextSource.getContext("uid=batman,ou=users,dc=unit,dc=test", "batman")

    when:
    contextSource.getContext("uid=batman,ou=users,dc=unit,dc=test", "badPassword")

    then:
    thrown AuthenticationException
  }
}
