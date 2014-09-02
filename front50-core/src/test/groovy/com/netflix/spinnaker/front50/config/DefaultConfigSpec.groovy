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

package com.netflix.spinnaker.front50.config

import com.netflix.spinnaker.amos.AccountCredentials
import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.amos.DefaultAccountCredentialsProvider
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import spock.lang.Specification

class DefaultConfigSpec extends Specification {

  void "DefaultNamedAccountProvider is chosen when no other NamedAccountProvider is specified"() {
    setup:
    def ctx = new AnnotationConfigApplicationContext(DefaultConfig)

    when:
    def bean = ctx.getBean(AccountCredentialsProvider)

    then:
    bean instanceof DefaultAccountCredentialsProvider
  }

  void "explicit NamedAccountProvider is chosen in favor of DefaultNamedAccountProvider"() {
    setup:
    def ctx = new AnnotationConfigApplicationContext(NonDefaultConfig, DefaultConfig)

    when:
    def bean = ctx.getBean(AccountCredentialsProvider)

    then:
    bean instanceof TestNamedAccountProvider
  }

  @Configuration
  static class NonDefaultConfig {
    @Bean
    AccountCredentialsProvider namedAccountProvider() {
      new TestNamedAccountProvider()
    }
  }

  static class TestNamedAccountProvider implements AccountCredentialsProvider {

    @Override
    Set<AccountCredentials> getAll() {
      return null
    }

    @Override
    AccountCredentials getCredentials(String name) {
      return null
    }
  }
}
